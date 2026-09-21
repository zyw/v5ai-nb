package xin.v5ai.nb.runtime.core.resolver;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xin.v5ai.nb.common.agentscope.core.domain.dto.AgentDTO;
import xin.v5ai.nb.common.agentscope.core.domain.dto.AgentVersionDTO;
import xin.v5ai.nb.common.agentscope.core.exception.AgentPublishException;
import xin.v5ai.nb.common.agentscope.core.service.AgentService;
import xin.v5ai.nb.common.agentscope.enums.AgentStatus;
import xin.v5ai.nb.common.agentscope.enums.RagCallMode;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 发布快照 → 运行时 {@link AgentDTO} 的还原口径。
 *
 * <p>重点是**存量快照兼容**：已在库里的快照没有 {@code secondaryModelId} / {@code showCitations}
 * 两个 key，解析必须给出「加字段之前」的行为（回退主模型、展示引用），否则升级即回归。
 * 快照是按 key 取值的普通 JSON，不涉及数据库。</p>
 */
class AgentScopePublishedAgentResolverTest {

    private AgentService agentService;
    private AgentScopePublishedAgentResolver resolver;

    @BeforeEach
    void setUp() {
        agentService = mock(AgentService.class);
        resolver = new AgentScopePublishedAgentResolver(agentService);
    }

    /** 发布态 Agent（状态与版本号不在快照 JSON 里，由 resolve 补齐）指向给定快照。 */
    private void givenPublishedSnapshot(String snapshotJson) {
        when(agentService.findByAgentKey("a1"))
                .thenReturn(new AgentDTO("a1", "Demo", null, AgentStatus.PUBLISHED, 7L, 1L, null));
        when(agentService.listVersions("a1"))
                .thenReturn(List.of(new AgentVersionDTO("a1", 1L, snapshotJson, "first release")));
    }

    @Test
    void legacySnapshotWithoutTheNewKeysKeepsPreChangeBehaviour() {
        // 本次改动之前的快照长这样：没有 secondaryModelId、没有 showCitations
        givenPublishedSnapshot("""
                {"agentKey":"a1","name":"Demo","description":"desc","modelId":7,"systemPrompt":"sys",
                 "avatar":"","greeting":"你好","presetQuestions":"[]","memoryEnabled":true,"mcpEnabled":false,
                 "skillEnabled":false,"webSearchEnabled":false,"ragEnabled":true,"ragCallMode":2}""");

        var agent = resolver.resolve("a1");

        assertThat(agent.secondaryModelId()).isNull();
        assertThat(agent.showCitations()).isTrue();
        // 其余字段照旧（顺带确认新 key 的默认值没有把旧字段挤位）
        assertThat(agent.modelId()).isEqualTo(7L);
        assertThat(agent.ragCallMode()).isEqualTo(RagCallMode.FORCED.value());
        assertThat(agent.memoryEnabled()).isTrue();
        assertThat(agent.status()).isEqualTo(AgentStatus.PUBLISHED);
        assertThat(agent.publishedVersion()).isEqualTo(1L);
    }

    @Test
    void snapshotRestoresSecondaryModelAndCitationsToggle() {
        givenPublishedSnapshot("""
                {"agentKey":"a1","name":"Demo","modelId":7,"secondaryModelId":9,"ragCallMode":1,
                 "showCitations":false}""");

        var agent = resolver.resolve("a1");

        assertThat(agent.secondaryModelId()).isEqualTo(9L);
        assertThat(agent.showCitations()).isFalse();
        assertThat(agent.ragCallMode()).isEqualTo(1);
    }

    @Test
    void resolveRejectsAgentsThatAreNotPublished() {
        when(agentService.findByAgentKey("a1"))
                .thenReturn(new AgentDTO("a1", "Demo", null, AgentStatus.DRAFT, 7L, null, null));

        assertThatThrownBy(() -> resolver.resolve("a1"))
                .isInstanceOf(AgentPublishException.class)
                .hasMessageContaining("not published");
    }

    /** 调试路径允许草稿：用实时编辑行，因此改完不必先发布就能在调试面板看到效果。 */
    @Test
    void resolveForDebugAcceptsDraftsButRejectsDisabled() {
        var draft = new AgentDTO("a1", "Demo", null, AgentStatus.DRAFT, 7L, null, null);
        when(agentService.findByAgentKey("a1")).thenReturn(draft);
        assertThat(resolver.resolveForDebug("a1")).isSameAs(draft);

        when(agentService.findByAgentKey("a1"))
                .thenReturn(new AgentDTO("a1", "Demo", null, AgentStatus.DISABLED, 7L, null, null));
        assertThatThrownBy(() -> resolver.resolveForDebug("a1"))
                .isInstanceOf(AgentPublishException.class)
                .hasMessageContaining("disabled");
    }
}
