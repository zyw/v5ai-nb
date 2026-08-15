package xin.v5ai.nb.infrastructure.skill;

import org.junit.jupiter.api.Test;
import xin.v5ai.nb.application.Application;
import xin.v5ai.nb.application.ApplicationStatus;
import xin.v5ai.nb.skill.ApplicationSkillBindingRepository;
import xin.v5ai.nb.skill.Skill;
import xin.v5ai.nb.skill.SkillFile;
import xin.v5ai.nb.skill.SkillFileRepository;
import xin.v5ai.nb.skill.SkillRepository;
import xin.v5ai.nb.skill.SkillStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationSkillWorkspaceResolverTest {

    @Test
    void resolvesNothingWhenNoBindings() {
        var resolver = new ApplicationSkillWorkspaceResolver(
                new RecordingBindingRepository(List.of()),
                new RecordingSkillRepository(),
                new RecordingSkillFileRepository());
        assertThat(resolver.resolveSkills(app())).isEmpty();
    }

    @Test
    void resolvesPublishedSkillFiles() {
        var resolver = new ApplicationSkillWorkspaceResolver(
                new RecordingBindingRepository(List.of(1L)),
                new RecordingSkillRepository(skill(1L, "weather", SkillStatus.ACTIVE, 10L)),
                new RecordingSkillFileRepository(Map.of(
                        10L, List.of(
                                file("SKILL.md", "# weather\n---"),
                                file("resources/data.txt", "data")))));

        var resolved = resolver.resolveSkills(app());

        assertThat(resolved).hasSize(1);
        assertThat(resolved.get(0).skillName()).isEqualTo("weather");
        assertThat(resolved.get(0).versionId()).isEqualTo(10L);
        assertThat(resolved.get(0).files()).containsKeys("SKILL.md", "resources/data.txt");
    }

    @Test
    void skipsDisabledAndUnpublishedSkills() {
        var resolver = new ApplicationSkillWorkspaceResolver(
                new RecordingBindingRepository(List.of(1L, 2L, 3L)),
                new RecordingSkillRepository(List.of(
                        skill(1L, "disabled", SkillStatus.DISABLED, 10L),
                        skill(2L, "unpublished", SkillStatus.ACTIVE, null),
                        skill(3L, "ok", SkillStatus.ACTIVE, 30L))),
                new RecordingSkillFileRepository(Map.of(
                        30L, List.of(file("SKILL.md", "# ok\n---")))));

        var resolved = resolver.resolveSkills(app());

        assertThat(resolved).extracting(r -> r.skillName()).containsExactly("ok");
    }

    private static Application app() {
        return new Application("demo", "Demo", null, ApplicationStatus.PUBLISHED, 1L, 1L);
    }

    private static Skill skill(Long id, String name, SkillStatus status, Long currentVersionId) {
        return new Skill(id, name, "desc", status, currentVersionId, 1L, Instant.now(), Instant.now());
    }

    private static SkillFile file(String path, String content) {
        return new SkillFile(null, 1L, null, path, content);
    }

    private static final class RecordingBindingRepository implements ApplicationSkillBindingRepository {
        private final List<Long> skillIds;

        private RecordingBindingRepository(List<Long> skillIds) {
            this.skillIds = skillIds;
        }

        @Override
        public void save(String appKey, List<Long> skillIds) {
        }

        @Override
        public List<Long> findByAppKey(String appKey) {
            return skillIds;
        }

        @Override
        public void delete(String appKey) {
        }
    }

    private static final class RecordingSkillRepository implements SkillRepository {
        private final List<Skill> skills;

        private RecordingSkillRepository(Skill... skills) {
            this.skills = List.of(skills);
        }

        private RecordingSkillRepository(List<Skill> skills) {
            this.skills = skills;
        }

        @Override
        public Skill save(Skill skill) {
            return skill;
        }

        @Override
        public Skill update(Skill skill) {
            return skill;
        }

        @Override
        public Skill findById(Long id) {
            return skills.stream().filter(s -> s.id().equals(id)).findFirst().orElse(null);
        }

        @Override
        public Skill findByName(String name) {
            return null;
        }

        @Override
        public List<Skill> list() {
            return skills;
        }

        @Override
        public void disable(Long id) {
        }
    }

    private static final class RecordingSkillFileRepository implements SkillFileRepository {
        private final Map<Long, List<SkillFile>> byVersion;

        private RecordingSkillFileRepository(Map<Long, List<SkillFile>> byVersion) {
            this.byVersion = byVersion;
        }

        private RecordingSkillFileRepository() {
            this(Map.of());
        }

        @Override
        public void saveFiles(Long skillId, Long versionId, Map<String, String> files) {
        }

        @Override
        public List<SkillFile> findByVersionId(Long versionId) {
            return byVersion.getOrDefault(versionId, List.of());
        }

        @Override
        public void deleteByVersionId(Long versionId) {
        }
    }
}
