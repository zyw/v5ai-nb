package xin.v5ai.nb.skill.core;

import org.springframework.stereotype.Component;
import xin.v5ai.nb.skill.core.domain.SkillPackage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Skill 包解析与校验。
 *
 * 包格式（.zip，兼容 .skill 别名）：
 * <pre>
 * skill-package/
 * ├── SKILL.md        # 必填：YAML Front Matter（name、description 必填）+ 正文
 * ├── metadata.yaml   # 可选：其余元数据
 * ├── prompts/        # 可选提示词资源
 * └── resources/      # 可选资源文件
 * </pre>
 *
 * 校验项：zip 大小、文件数量、路径穿越、单文件大小、UTF-8 文本编码、SKILL.md 结构与必填字段。
 * 本阶段仅支持文本文件（二进制资源留待后续阶段）。
 */
@Component
public class SkillPackageParser {
    private static final int MAX_PACKAGE_BYTES = 10 * 1024 * 1024;      // 压缩包 10MB
    private static final int MAX_UNCOMPRESSED_TOTAL = 20 * 1024 * 1024; // 解压总量 20MB（防 zip bomb）
    private static final int MAX_FILE_COUNT = 200;
    /** 单文件大小上限（512KB），上传解析与在线编辑器共用。 */
    public static final int MAX_FILE_BYTES = 512 * 1024;

    public SkillPackage parse(byte[] packageBytes) {
        if (packageBytes == null || packageBytes.length == 0) {
            throw new IllegalArgumentException("skill package is empty");
        }
        if (packageBytes.length > MAX_PACKAGE_BYTES) {
            throw new IllegalArgumentException("skill package exceeds " + (MAX_PACKAGE_BYTES / 1024 / 1024) + "MB limit");
        }
        Map<String, String> files = new LinkedHashMap<>();
        long totalUncompressed = 0;
        try (var zip = new ZipInputStream(new ByteArrayInputStream(packageBytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String path = normalizePath(entry.getName());
                if (path.isEmpty() || isIgnoredEntry(path)) {
                    continue;
                }
                validatePath(path);
                if (files.size() >= MAX_FILE_COUNT) {
                    throw new IllegalArgumentException("skill package has too many files (max " + MAX_FILE_COUNT + ")");
                }
                if (entry.getSize() > MAX_FILE_BYTES) {
                    throw new IllegalArgumentException("skill file too large: " + path);
                }
                var raw = readEntry(zip, path);
                totalUncompressed += raw.length;
                if (totalUncompressed > MAX_UNCOMPRESSED_TOTAL) {
                    throw new IllegalArgumentException("skill package uncompressed size exceeds "
                            + (MAX_UNCOMPRESSED_TOTAL / 1024 / 1024) + "MB limit");
                }
                String content = decodeUtf8(path, raw);
                if (files.put(path, content) != null) {
                    throw new IllegalArgumentException("duplicate path in skill package: " + path);
                }
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("skill package is not a valid zip: " + exception.getMessage(), exception);
        }

        String skillMd = files.get("SKILL.md");
        if (skillMd == null || skillMd.isBlank()) {
            throw new IllegalArgumentException("skill package must contain a SKILL.md at the package root");
        }
        var parsed = SkillFrontmatterParser.parseMarkdown(skillMd);
        Map<String, String> metadata = new LinkedHashMap<>(parsed.metadata());
        // 可选 metadata.yaml 补充元数据（frontmatter 优先）
        String metadataYaml = files.get("metadata.yaml");
        if (metadataYaml != null) {
            var yaml = SkillFrontmatterParser.parseYaml(metadataYaml);
            yaml.forEach(metadata::putIfAbsent);
        }
        String name = metadata.get("name");
        String description = metadata.get("description");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("SKILL.md frontmatter must declare a non-empty `name`");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("SKILL.md frontmatter must declare a non-empty `description`");
        }
        name = name.trim();
        if (name.contains("/") || name.contains("\\") || name.equals(".") || name.equals("..")) {
            throw new IllegalArgumentException("skill name must not contain path separators: " + name);
        }
        if (parsed.content() == null || parsed.content().isBlank()) {
            throw new IllegalArgumentException("SKILL.md must have content besides the YAML frontmatter");
        }
        return new SkillPackage(name, description.trim(), metadata, files);
    }

    private static byte[] readEntry(ZipInputStream zip, String path) throws IOException {
        var buffer = new ByteBufferProvider();
        byte[] chunk = new byte[8192];
        int read;
        while ((read = zip.read(chunk)) != -1) {
            buffer.append(chunk, read);
            if (buffer.size() > MAX_FILE_BYTES) {
                throw new IllegalArgumentException("skill file too large: " + path);
            }
        }
        return buffer.toByteArray();
    }

    /**
     * zip 路径规整：统一反斜杠、去掉前导 ./，返回包内相对路径。
     * 供在线编辑器复用（文件路径以包内相对路径存储）。
     */
    public static String normalizePath(String rawName) {
        String name = rawName == null ? "" : rawName.replace('\\', '/');
        while (name.startsWith("./")) {
            name = name.substring(2);
        }
        return name;
    }

    /**
     * 包内路径安全校验：禁止绝对路径与路径穿越。
     * 供在线编辑器复用。
     */
    public static void validatePath(String path) {
        if (path.startsWith("/") || path.matches("^[A-Za-z]:.*")) {
            throw new IllegalArgumentException("absolute paths are not allowed in skill package: " + path);
        }
        for (String segment : path.split("/")) {
            if (segment.equals("..") || segment.equals(".")) {
                throw new IllegalArgumentException("path traversal is not allowed in skill package: " + path);
            }
        }
    }

    /**
     * 过滤打包工具产生的无用条目（不参与技能内容、不应触发 UTF-8 等校验）：
     * macOS 的 __MACOSX/ 元数据目录、AppleDouble 伴随文件（._*）、.DS_Store、Windows 的 Thumbs.db。
     */
    private static boolean isIgnoredEntry(String path) {
        if (path.startsWith("__MACOSX") && (path.equals("__MACOSX") || path.startsWith("__MACOSX/"))) {
            return true;
        }
        String fileName = path.substring(path.lastIndexOf('/') + 1);
        return fileName.startsWith("._")
                || fileName.equals(".DS_Store")
                || fileName.equals("Thumbs.db");
    }

    private static String decodeUtf8(String path, byte[] raw) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(raw))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException(
                    "skill file is not valid UTF-8 text (binary resources are not supported yet): " + path);
        }
    }

    /** 简单可增长的字节缓冲。 */
    private static final class ByteBufferProvider {
        private byte[] data = new byte[4096];
        private int length;

        void append(byte[] chunk, int count) {
            ensureCapacity(length + count);
            System.arraycopy(chunk, 0, data, length, count);
            length += count;
        }

        int size() {
            return length;
        }

        byte[] toByteArray() {
            byte[] copy = new byte[length];
            System.arraycopy(data, 0, copy, 0, length);
            return copy;
        }

        private void ensureCapacity(int needed) {
            if (needed <= data.length) {
                return;
            }
            int newSize = data.length;
            while (newSize < needed) {
                newSize *= 2;
            }
            byte[] grown = new byte[newSize];
            System.arraycopy(data, 0, grown, 0, length);
            data = grown;
        }
    }
}
