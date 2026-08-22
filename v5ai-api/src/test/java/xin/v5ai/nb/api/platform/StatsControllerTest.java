package xin.v5ai.nb.api.platform;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import xin.v5ai.nb.agent.AgentRepository;
import xin.v5ai.nb.model.mapper.V5aiModelMapper;
import xin.v5ai.nb.rag.mapper.KnowledgeBaseMapper;
import xin.v5ai.nb.skill.mapper.SkillMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StatsControllerTest {

    @Test
    void statsControllerCountsResources() {
        var agents = mock(AgentRepository.class);
        var models = mock(V5aiModelMapper.class);
        var knowledge = mock(KnowledgeBaseMapper.class);
        var skills = mock(SkillMapper.class);
        when(agents.list()).thenReturn(List.of(new xin.v5ai.nb.agent.Agent(
                "a", "A", null, xin.v5ai.nb.agent.AgentStatus.PUBLISHED, 1L, null)));
        when(models.selectCount(any())).thenReturn(2L);
        when(knowledge.selectCount(any())).thenReturn(4L);
        when(skills.selectCount(any())).thenReturn(3L);
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(7L);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any())).thenReturn(3L);

        var overview = new StatsController(jdbc, agents, models, knowledge, skills).overview().getData();

        assertThat(overview.agents()).isEqualTo(1);
        assertThat(overview.models()).isEqualTo(2);
        assertThat(overview.skills()).isEqualTo(3);
        assertThat(overview.totalRuns()).isEqualTo(7);
        assertThat(overview.todayModelCalls()).isEqualTo(3);
    }
}
