package xin.v5ai.nb.skill;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SkillManagementServiceTest {

    @Test
    void uploadCreatesSkillWithDraftVersionAndFiles() {
        var service = service();
        var skill = service.uploadSkill(packageBytes("weather", "Get weather"), "v1");

        assertThat(skill.name()).isEqualTo("weather");
        assertThat(skill.currentVersionId()).isNull();

        var versions = service.listVersions(skill.id());
        assertThat(versions).hasSize(1);
        assertThat(versions.get(0).status()).isEqualTo(SkillVersionStatus.DRAFT);

        // 发布后文件可解析
        var version = versions.get(0);
        service.publishVersion(skill.id(), version.id());
        assertThat(service.publishedSkillFiles(skill.id()))
                .containsKeys("SKILL.md", "resources/data.txt");
    }

    @Test
    void reuploadIncrementsVersionNumber() {
        var service = service();
        service.uploadSkill(packageBytes("weather", "Get weather"), "v1");
        var skill = service.uploadSkill(packageBytes("weather", "Get weather v2"), "v2");

        var versions = service.listVersions(skill.id());
        assertThat(versions).extracting(SkillVersion::version).containsExactly(2L, 1L);
    }

    @Test
    void publishMakesVersionCurrent() {
        var service = service();
        var skill = service.uploadSkill(packageBytes("weather", "Get weather"), "v1");
        var version = service.listVersions(skill.id()).get(0);

        var published = service.publishVersion(skill.id(), version.id());

        assertThat(published.currentVersionId()).isEqualTo(version.id());
        assertThat(published.currentVersion()).isEqualTo(1L);
        var refreshed = service.listVersions(skill.id()).get(0);
        assertThat(refreshed.status()).isEqualTo(SkillVersionStatus.PUBLISHED);
        assertThat(refreshed.publishedAt()).isNotNull();
    }

    @Test
    void publishRejectsNonDraft() {
        var service = service();
        var skill = service.uploadSkill(packageBytes("weather", "Get weather"), "v1");
        var version = service.listVersions(skill.id()).get(0);
        service.publishVersion(skill.id(), version.id());
        assertThatThrownBy(() -> service.publishVersion(skill.id(), version.id()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("draft");
    }

    @Test
    void rollbackRepointsCurrentVersion() {
        var service = service();
        var skill = service.uploadSkill(packageBytes("weather", "Get weather v1"), "v1");
        var v1 = service.listVersions(skill.id()).get(0);
        service.publishVersion(skill.id(), v1.id());
        service.uploadSkill(packageBytes("weather", "Get weather v2"), "v2");
        var v2 = service.listVersions(skill.id()).get(0);
        service.publishVersion(skill.id(), v2.id());

        var rolledBack = service.rollback(skill.id(), v1.id());

        assertThat(rolledBack.currentVersionId()).isEqualTo(v1.id());
        assertThat(rolledBack.currentVersion()).isEqualTo(1L);
    }

    @Test
    void rollbackRejectsDraftVersion() {
        var service = service();
        var skill = service.uploadSkill(packageBytes("weather", "Get weather"), "v1");
        var v1 = service.listVersions(skill.id()).get(0);
        service.publishVersion(skill.id(), v1.id());
        service.uploadSkill(packageBytes("weather", "v2"), "v2");
        var v2 = service.listVersions(skill.id()).get(0);

        assertThatThrownBy(() -> service.rollback(skill.id(), v2.id()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("published");
    }

    @Test
    void bindRequiresPublishedActiveSkill() {
        var service = service();
        // 未发布不可绑定
        var draftSkill = service.uploadSkill(packageBytes("draft-skill", "x"), "v1");
        assertThatThrownBy(() -> service.bindSkills("demo", List.of(draftSkill.id())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("published");

        // 发布后可绑定
        var v1 = service.listVersions(draftSkill.id()).get(0);
        service.publishVersion(draftSkill.id(), v1.id());
        service.bindSkills("demo", List.of(draftSkill.id()));
        assertThat(service.getSkillBindings("demo")).containsExactly(draftSkill.id());

        // 禁用后不可再绑定（bind 是校验后全量替换）
        service.disableSkill(draftSkill.id());
        var other = service.uploadSkill(packageBytes("other", "y"), "v1");
        var otherV1 = service.listVersions(other.id()).get(0);
        service.publishVersion(other.id(), otherV1.id());
        assertThatThrownBy(() -> service.bindSkills("demo", List.of(draftSkill.id(), other.id())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("disabled");
    }

    @Test
    void disabledSkillRejectsNewUploads() {
        var service = service();
        var skill = service.uploadSkill(packageBytes("weather", "x"), "v1");
        service.disableSkill(skill.id());
        assertThatThrownBy(() -> service.uploadSkill(packageBytes("weather", "y"), "v2"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("disabled");
    }

    @Test
    void publishedSkillFilesReturnCurrentVersionOnly() {
        var service = service();
        var skill = service.uploadSkill(packageBytes("weather", "v1 content"), "v1");
        var v1 = service.listVersions(skill.id()).get(0);
        service.publishVersion(skill.id(), v1.id());
        service.uploadSkill(packageBytes("weather", "v2 content"), "v2");
        var v2 = service.listVersions(skill.id()).get(0);
        service.publishVersion(skill.id(), v2.id());

        var files = service.publishedSkillFiles(skill.id());
        assertThat(files.get("SKILL.md")).contains("v2 content");
    }

    private static SkillManagementService service() {
        return new SkillManagementService(
                new InMemorySkillRepository(),
                new InMemorySkillVersionRepository(),
                new InMemorySkillFileRepository(),
                new InMemoryBindingRepository(),
                new SkillPackageParser());
    }

    private static byte[] packageBytes(String name, String content) {
        var files = new LinkedHashMap<String, String>();
        files.put("SKILL.md", "---\nname: " + name + "\ndescription: " + name + " skill\n---\n" + content);
        files.put("resources/data.txt", "data");
        return SkillPackageParserTest.zip(files);
    }

    private static final class InMemorySkillRepository implements SkillRepository {
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
                    return skill;
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
            var skill = findById(id);
            if (skill != null) {
                stored.remove(skill);
                stored.add(new Skill(skill.id(), skill.name(), skill.description(), SkillStatus.DISABLED,
                        skill.currentVersionId(), skill.currentVersion(), skill.createdAt(), skill.updatedAt()));
            }
        }
    }

    private static final class InMemorySkillVersionRepository implements SkillVersionRepository {
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
            // 与 MyBatis 实现一致：按版本号降序
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

    private static final class InMemorySkillFileRepository implements SkillFileRepository {
        private final List<SkillFile> stored = new ArrayList<>();
        private long nextId = 1;

        @Override
        public void saveFiles(Long skillId, Long versionId, Map<String, String> files) {
            for (Map.Entry<String, String> entry : files.entrySet()) {
                stored.add(new SkillFile(nextId++, skillId, versionId, entry.getKey(), entry.getValue()));
            }
        }

        @Override
        public List<SkillFile> findByVersionId(Long versionId) {
            return stored.stream().filter(f -> f.versionId().equals(versionId)).toList();
        }

        @Override
        public void deleteByVersionId(Long versionId) {
            stored.removeIf(f -> f.versionId().equals(versionId));
        }
    }

    private static final class InMemoryBindingRepository implements ApplicationSkillBindingRepository {
        private final Map<String, List<Long>> bindings = new LinkedHashMap<>();

        @Override
        public void save(String appKey, List<Long> skillIds) {
            bindings.put(appKey, new ArrayList<>(skillIds));
        }

        @Override
        public List<Long> findByAppKey(String appKey) {
            return bindings.getOrDefault(appKey, List.of());
        }

        @Override
        public void delete(String appKey) {
            bindings.remove(appKey);
        }
    }
}
