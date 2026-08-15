package xin.v5ai.nb.runtime;

import org.junit.jupiter.api.Test;
import xin.v5ai.nb.skill.ResolvedSkill;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ResolvedSkillAgentSkillRepositoryTest {

    @Test
    void buildsAgentSkillsFromResolvedSkills() {
        var resolved = List.of(new ResolvedSkill("weather", "Get weather", Map.of(
                "SKILL.md", "---\nname: weather\ndescription: Get weather\n---\nUse the tool.",
                "resources/data.txt", "data"), 5L));

        var repo = new ResolvedSkillAgentSkillRepository(resolved);

        assertThat(repo.getSource()).isEqualTo("v5ai-db");
        assertThat(repo.isWriteable()).isFalse();
        assertThat(repo.getAllSkillNames()).containsExactly("weather");
        var skill = repo.getSkill("weather");
        assertThat(skill).isNotNull();
        assertThat(skill.getDescription()).isEqualTo("Get weather");
        assertThat(skill.getSkillContent()).contains("Use the tool.");
        assertThat(skill.getResource("resources/data.txt")).isEqualTo("data");
    }

    @Test
    void skipsSkillsWithoutValidSkillMd() {
        var resolved = List.of(new ResolvedSkill("broken", "x",
                Map.of("README.md", "no frontmatter"), 1L));

        var repo = new ResolvedSkillAgentSkillRepository(resolved);

        assertThat(repo.getAllSkills()).isEmpty();
        assertThat(repo.skillExists("broken")).isFalse();
    }

    @Test
    void readOnlyRepoRejectsWrites() {
        var repo = new ResolvedSkillAgentSkillRepository(List.of());
        assertThat(repo.save(List.of(), true)).isFalse();
        assertThat(repo.delete("anything")).isFalse();
    }
}
