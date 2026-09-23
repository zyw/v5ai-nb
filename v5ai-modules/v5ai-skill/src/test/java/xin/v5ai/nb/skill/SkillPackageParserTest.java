package xin.v5ai.nb.skill;

import org.junit.jupiter.api.Test;
import xin.v5ai.nb.skill.core.SkillPackageParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SkillPackageParserTest {

    @Test
    void parsesValidPackageWithResources() {
        var files = new LinkedHashMap<String, String>();
        files.put("SKILL.md", skillMd("weather", "Get weather", "Use the weather tool."));
        files.put("prompts/guide.md", "Guide content");
        files.put("resources/data.txt", "data");

        var parsed = new SkillPackageParser().parse(zip(files));

        assertThat(parsed.name()).isEqualTo("weather");
        assertThat(parsed.description()).isEqualTo("Get weather");
        assertThat(parsed.files()).containsKeys("SKILL.md", "prompts/guide.md", "resources/data.txt");
        assertThat(parsed.skillMarkdown()).contains("Use the weather tool.");
    }

    @Test
    void mergesMetadataYaml() {
        var files = new LinkedHashMap<String, String>();
        files.put("SKILL.md", skillMd("calc", "Calculate", "content"));
        files.put("metadata.yaml", "author: v5ai-team\nversion: 2");

        var parsed = new SkillPackageParser().parse(zip(files));

        assertThat(parsed.metadata()).containsEntry("author", "v5ai-team");
        assertThat(parsed.metadata()).containsEntry("version", "2");
        // frontmatter 优先于 metadata.yaml
        assertThat(parsed.name()).isEqualTo("calc");
    }

    @Test
    void rejectsPackageWithoutSkillMd() {
        var files = new LinkedHashMap<String, String>();
        files.put("README.md", "no skill here");
        assertThatThrownBy(() -> new SkillPackageParser().parse(zip(files)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SKILL.md");
    }

    @Test
    void rejectsMissingNameOrDescription() {
        var noName = Map.of("SKILL.md", "---\ndescription: x\n---\ncontent");
        assertThatThrownBy(() -> new SkillPackageParser().parse(zip(noName)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");

        var noDescription = Map.of("SKILL.md", "---\nname: x\n---\ncontent");
        assertThatThrownBy(() -> new SkillPackageParser().parse(zip(noDescription)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("description");
    }

    @Test
    void rejectsEmptySkillContent() {
        var files = Map.of("SKILL.md", "---\nname: x\ndescription: y\n---");
        assertThatThrownBy(() -> new SkillPackageParser().parse(zip(files)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("content");
    }

    @Test
    void rejectsPathTraversalAndAbsolutePaths() {
        var traversal = new LinkedHashMap<String, String>();
        traversal.put("SKILL.md", skillMd("x", "y", "content"));
        traversal.put("../evil.txt", "boom");
        assertThatThrownBy(() -> new SkillPackageParser().parse(zip(traversal)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("traversal");

        var absolute = new LinkedHashMap<String, String>();
        absolute.put("SKILL.md", skillMd("x", "y", "content"));
        absolute.put("/etc/passwd", "boom");
        assertThatThrownBy(() -> new SkillPackageParser().parse(zip(absolute)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("absolute");
    }

    @Test
    void rejectsBinaryFiles() {
        // 0xFF 0xFE 不是合法 UTF-8 序列
        var raw = new LinkedHashMap<String, byte[]>();
        raw.put("SKILL.md", skillMd("x", "y", "content").getBytes(StandardCharsets.UTF_8));
        raw.put("resources/logo.bin", new byte[]{(byte) 0xFF, (byte) 0xFE, 0x00});
        assertThatThrownBy(() -> new SkillPackageParser().parse(zipRaw(raw)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UTF-8");
    }

    @Test
    void ignoresMacOsZipMetadataEntries() {
        // macOS 打包产物：__MACOSX/ 元数据目录、AppleDouble 伴随文件与 .DS_Store 均为二进制，应被过滤而非报 UTF-8 错误
        var raw = new LinkedHashMap<String, byte[]>();
        raw.put("SKILL.md", skillMd("weather", "查询天气", "content").getBytes(StandardCharsets.UTF_8));
        raw.put("__MACOSX/", new byte[0]);
        raw.put("__MACOSX/._SKILL.md", new byte[]{(byte) 0x00, 0x05, 0x16, (byte) 0x07});
        raw.put(".DS_Store", new byte[]{(byte) 0x00, (byte) 0x01, (byte) 0xFF});
        raw.put("prompts/._guide.md", new byte[]{(byte) 0x00, 0x05, 0x16, (byte) 0x07});

        var parsed = new SkillPackageParser().parse(zipRaw(raw));

        assertThat(parsed.name()).isEqualTo("weather");
        assertThat(parsed.files()).containsOnlyKeys("SKILL.md");
    }

    @Test
    void ignoresWindowsThumbsDb() {
        var raw = new LinkedHashMap<String, byte[]>();
        raw.put("SKILL.md", skillMd("x", "y", "content").getBytes(StandardCharsets.UTF_8));
        raw.put("Thumbs.db", new byte[]{(byte) 0xFF, (byte) 0xFE, 0x00});

        var parsed = new SkillPackageParser().parse(zipRaw(raw));

        assertThat(parsed.files()).containsOnlyKeys("SKILL.md");
    }

    @Test
    void rejectsEmptyPackage() {
        assertThatThrownBy(() -> new SkillPackageParser().parse(new byte[0]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void rejectsSkillNameWithPathSeparators() {
        var files = Map.of("SKILL.md", "---\nname: ../evil\ndescription: x\n---\ncontent");
        assertThatThrownBy(() -> new SkillPackageParser().parse(zip(files)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("path separators");
    }

    @Test
    void normalizesBackslashPaths() {
        var files = new LinkedHashMap<String, String>();
        files.put("SKILL.md", skillMd("x", "y", "content"));
        files.put("resources\\data.txt", "data");
        var parsed = new SkillPackageParser().parse(zip(files));
        assertThat(parsed.files()).containsKey("resources/data.txt");
    }

    static String skillMd(String name, String description, String content) {
        return "---\nname: " + name + "\ndescription: " + description + "\n---\n" + content;
    }

    static byte[] zip(Map<String, String> files) {
        var raw = new LinkedHashMap<String, byte[]>();
        files.forEach((path, content) -> raw.put(path, content.getBytes(StandardCharsets.UTF_8)));
        return zipRaw(raw);
    }

    static byte[] zipRaw(Map<String, byte[]> files) {
        var baos = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(baos)) {
            for (Map.Entry<String, byte[]> entry : files.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
        return baos.toByteArray();
    }
}
