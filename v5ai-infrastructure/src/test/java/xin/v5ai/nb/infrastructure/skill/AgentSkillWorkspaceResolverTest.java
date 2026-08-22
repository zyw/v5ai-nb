package xin.v5ai.nb.infrastructure.skill;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.Test;
import xin.v5ai.nb.agent.Agent;
import xin.v5ai.nb.agent.AgentStatus;
import xin.v5ai.nb.skill.domain.AgentSkillBinding;
import xin.v5ai.nb.skill.domain.Skill;
import xin.v5ai.nb.skill.domain.SkillFile;
import xin.v5ai.nb.skill.mapper.AgentSkillBindingMapper;
import xin.v5ai.nb.skill.mapper.SkillFileMapper;
import xin.v5ai.nb.skill.mapper.SkillMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentSkillWorkspaceResolverTest {

    @Test
    void resolvesNothingWhenNoBindings() {
        AgentSkillBindingMapper bindingMapper = mock(AgentSkillBindingMapper.class);
        when(bindingMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        var resolver = new AgentSkillWorkspaceResolver(bindingMapper, mock(SkillMapper.class), mock(SkillFileMapper.class));
        assertThat(resolver.resolveSkills(app())).isEmpty();
    }

    @Test
    void resolvesPublishedSkillFiles() {
        var resolver = new AgentSkillWorkspaceResolver(
                bindingMapper(binding("demo", 1L)),
                skillMapper(skill(1L, "weather", "ACTIVE", 10L)),
                fileMapper(file(1L, 10L, "SKILL.md", "# weather\n---"),
                        file(2L, 10L, "resources/data.txt", "data")));

        var resolved = resolver.resolveSkills(app());

        assertThat(resolved).hasSize(1);
        assertThat(resolved.get(0).skillName()).isEqualTo("weather");
        assertThat(resolved.get(0).versionId()).isEqualTo(10L);
        assertThat(resolved.get(0).files()).containsKeys("SKILL.md", "resources/data.txt");
    }

    @Test
    void skipsDisabledAndUnpublishedSkills() {
        var resolver = new AgentSkillWorkspaceResolver(
                bindingMapper(binding("demo", 1L), binding("demo", 2L), binding("demo", 3L)),
                skillMapper(skill(1L, "disabled", "DISABLED", 10L),
                        skill(2L, "unpublished", "ACTIVE", null),
                        skill(3L, "ok", "ACTIVE", 30L)),
                fileMapper(file(1L, 30L, "SKILL.md", "# ok\n---")));

        var resolved = resolver.resolveSkills(app());

        assertThat(resolved).extracting(r -> r.skillName()).containsExactly("ok");
    }

    private static Agent app() {
        return new Agent("demo", "Demo", null, AgentStatus.PUBLISHED, 1L, 1L);
    }

    private static AgentSkillBindingMapper bindingMapper(AgentSkillBinding... bindings) {
        AgentSkillBindingMapper mapper = mock(AgentSkillBindingMapper.class);
        when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(bindings));
        return mapper;
    }

    private static SkillMapper skillMapper(Skill... skills) {
        SkillMapper mapper = mock(SkillMapper.class);
        when(mapper.selectById(anyLong())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            return List.of(skills).stream().filter(s -> s.getId().equals(id)).findFirst().orElse(null);
        });
        return mapper;
    }

    private static SkillFileMapper fileMapper(SkillFile... files) {
        SkillFileMapper mapper = mock(SkillFileMapper.class);
        when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(files));
        return mapper;
    }

    private static AgentSkillBinding binding(String agentKey, Long skillId) {
        var binding = new AgentSkillBinding();
        binding.setAgentKey(agentKey);
        binding.setSkillId(skillId);
        return binding;
    }

    private static Skill skill(Long id, String name, String status, Long currentVersionId) {
        var skill = new Skill();
        skill.setId(id);
        skill.setName(name);
        skill.setDescription("desc");
        skill.setStatus(status);
        skill.setCurrentVersionId(currentVersionId);
        return skill;
    }

    private static SkillFile file(Long id, Long versionId, String path, String content) {
        var file = new SkillFile();
        file.setId(id);
        file.setVersionId(versionId);
        file.setFilePath(path);
        file.setContent(content);
        return file;
    }
}
