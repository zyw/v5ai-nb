package xin.v5ai.nb.api.skill;

import org.junit.jupiter.api.Test;
import xin.v5ai.nb.common.core.page.PageQuery;
import xin.v5ai.nb.skill.AgentSkillBindingRepository;
import xin.v5ai.nb.skill.Skill;
import xin.v5ai.nb.skill.SkillFileRepository;
import xin.v5ai.nb.skill.SkillManagementService;
import xin.v5ai.nb.skill.SkillPackageParser;
import xin.v5ai.nb.skill.SkillRepository;
import xin.v5ai.nb.skill.SkillStatus;
import xin.v5ai.nb.skill.SkillVersion;
import xin.v5ai.nb.skill.SkillVersionRepository;
import xin.v5ai.nb.skill.SkillVersionStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SkillControllerTest {

    @Test
    void uploadsListsAndPublishes() {
        var service = new SkillManagementService(
                new RecordingSkillRepository(),
                new RecordingVersionRepository(),
                new RecordingFileRepository(),
                new RecordingBindingRepository(),
                new SkillPackageParser());
        var controller = new SkillController(service);

        var uploaded = controller.uploadSkill(new org.springframework.mock.web.MockMultipartFile(
                "file", "weather.zip", "application/zip",
                packageBytes("weather", "content")), "v1").getData();
        assertThat(uploaded.name()).isEqualTo("weather");

        var versions = controller.listVersions(uploaded.id(), new PageQuery()).getData().getRows().stream().toList();
        assertThat(versions).extracting(SkillVersionResponse::status).containsExactly(SkillVersionStatus.DRAFT);

        var published = controller.publishVersion(uploaded.id(), versions.get(0).id()).getData();
        assertThat(published.currentVersion()).isEqualTo(1L);

        assertThat(controller.listSkills(new PageQuery()).getData().getRows())
                .extracting(SkillResponse::name).containsExactly("weather");
    }

    @Test
    void rollsBackToPreviousPublishedVersion() {
        var service = new SkillManagementService(
                new RecordingSkillRepository(),
                new RecordingVersionRepository(),
                new RecordingFileRepository(),
                new RecordingBindingRepository(),
                new SkillPackageParser());
        var controller = new SkillController(service);

        var skill = controller.uploadSkill(new org.springframework.mock.web.MockMultipartFile(
                "file", "weather.zip", "application/zip",
                packageBytes("weather", "v1")), "v1").getData();
        var v1 = controller.listVersions(skill.id(), new PageQuery()).getData().getRows().stream().toList().get(0);
        controller.publishVersion(skill.id(), v1.id());

        controller.uploadSkill(new org.springframework.mock.web.MockMultipartFile(
                "file", "weather.zip", "application/zip",
                packageBytes("weather", "v2")), "v2");
        var v2 = controller.listVersions(skill.id(), new PageQuery()).getData().getRows().stream().toList().get(0);
        controller.publishVersion(skill.id(), v2.id());

        var rolledBack = controller.rollback(skill.id(), new RollbackRequest(v1.id())).getData();
        assertThat(rolledBack.currentVersion()).isEqualTo(1L);
    }

    private static byte[] packageBytes(String name, String content) {
        return zip(Map.of(
                "SKILL.md", "---\nname: " + name + "\ndescription: " + name + " skill\n---\n" + content));
    }

    private static byte[] zip(Map<String, String> files) {
        var baos = new java.io.ByteArrayOutputStream();
        try (var zip = new java.util.zip.ZipOutputStream(baos)) {
            for (Map.Entry<String, String> entry : files.entrySet()) {
                zip.putNextEntry(new java.util.zip.ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
        return baos.toByteArray();
    }

    private static final class RecordingSkillRepository implements SkillRepository {
        private final List<Skill> stored = new ArrayList<>();
        private long nextId = 1;

        @Override
        public Skill save(Skill skill) {
            var withId = new Skill(nextId++, skill.name(), skill.description(), skill.status(),
                    skill.currentVersionId(), skill.currentVersion(), Instant.now(), Instant.now());
            stored.add(withId);
            return withId;
        }

        @Override
        public Skill update(Skill skill) {
            for (int i = 0; i < stored.size(); i++) {
                if (stored.get(i).id().equals(skill.id())) {
                    stored.set(i, skill);
                }
            }
            return skill;
        }

        @Override
        public Skill findById(Long id) {
            return stored.stream().filter(s -> s.id().equals(id)).findFirst().orElse(null);
        }

        @Override
        public Skill findByName(String name) {
            return stored.stream().filter(s -> s.name().equals(name)).findFirst().orElse(null);
        }

        @Override
        public List<Skill> list() {
            return stored;
        }

        @Override
        public void disable(Long id) {
        }
    }

    private static final class RecordingVersionRepository implements SkillVersionRepository {
        private final List<SkillVersion> stored = new ArrayList<>();
        private long nextId = 1;

        @Override
        public SkillVersion save(SkillVersion version) {
            var withId = new SkillVersion(nextId++, version.skillId(), version.version(), version.status(),
                    version.description(), version.publishedAt(), Instant.now());
            stored.add(withId);
            return withId;
        }

        @Override
        public SkillVersion findById(Long id) {
            return stored.stream().filter(v -> v.id().equals(id)).findFirst().orElse(null);
        }

        @Override
        public List<SkillVersion> findBySkillId(Long skillId) {
            return stored.stream()
                    .filter(v -> v.skillId().equals(skillId))
                    .sorted((a, b) -> Long.compare(b.version(), a.version()))
                    .toList();
        }

        @Override
        public long nextVersion(Long skillId) {
            return stored.stream().filter(v -> v.skillId().equals(skillId))
                    .mapToLong(SkillVersion::version).max().orElse(0L) + 1;
        }

        @Override
        public void updateStatus(Long versionId, SkillVersionStatus status, Instant publishedAt) {
            var version = findById(versionId);
            if (version != null) {
                stored.remove(version);
                stored.add(new SkillVersion(version.id(), version.skillId(), version.version(), status,
                        version.description(), publishedAt, version.createdAt()));
            }
        }
    }

    private static final class RecordingFileRepository implements SkillFileRepository {
        @Override
        public void saveFiles(Long skillId, Long versionId, Map<String, String> files) {
        }

        @Override
        public List<xin.v5ai.nb.skill.SkillFile> findByVersionId(Long versionId) {
            return List.of();
        }

        @Override
        public void deleteByVersionId(Long versionId) {
        }
    }

    private static final class RecordingBindingRepository implements AgentSkillBindingRepository {
        private final Map<String, List<Long>> bindings = new LinkedHashMap<>();

        @Override
        public void save(String agentKey, List<Long> skillIds) {
            bindings.put(agentKey, new ArrayList<>(skillIds));
        }

        @Override
        public List<Long> findByAgentKey(String agentKey) {
            return bindings.getOrDefault(agentKey, List.of());
        }

        @Override
        public void delete(String agentKey) {
            bindings.remove(agentKey);
        }
    }
}
