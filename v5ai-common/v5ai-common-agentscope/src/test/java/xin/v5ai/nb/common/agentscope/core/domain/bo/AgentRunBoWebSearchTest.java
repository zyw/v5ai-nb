package xin.v5ai.nb.common.agentscope.core.domain.bo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 联网搜索意图的「只能收窄、不能放大」规则：Agent 未开启时请求怎么传都不会联网；
 * Agent 开启时请求可以显式关闭（门户开关），其余 wither 方法必须保留该意图。
 */
class AgentRunBoWebSearchTest {

    @Test
    void requestCannotEnableWebSearchForAgentThatDisallowsIt() {
        assertThat(new AgentRunBo("a", "c", "q").resolveWebSearch(false)).isFalse();
        assertThat(new AgentRunBo("a", "c", "q").withWebSearch(true).resolveWebSearch(false)).isFalse();
    }

    @Test
    void requestFollowsAgentSettingWhenNotSpecified() {
        assertThat(new AgentRunBo("a", "c", "q").resolveWebSearch(true)).isTrue();
        assertThat(new AgentRunBo("a", "c", "q").withWebSearch(true).resolveWebSearch(true)).isTrue();
    }

    @Test
    void requestCanExplicitlyTurnWebSearchOff() {
        assertThat(new AgentRunBo("a", "c", "q").withWebSearch(false).resolveWebSearch(true)).isFalse();
    }

    @Test
    void otherWithersPreserveWebSearchIntent() {
        var bo = new AgentRunBo("a", "c", "q").withWebSearch(false).withRunId("r1").withRagContext("ctx");

        assertThat(bo.webSearch()).isFalse();
        assertThat(bo.runId()).isEqualTo("r1");
        assertThat(bo.ragContext()).isEqualTo("ctx");
    }
}
