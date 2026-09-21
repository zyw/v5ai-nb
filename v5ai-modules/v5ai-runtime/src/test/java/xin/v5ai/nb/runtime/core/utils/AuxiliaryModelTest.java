package xin.v5ai.nb.runtime.core.utils;

import org.junit.jupiter.api.Test;
import xin.v5ai.nb.common.agentscope.core.domain.dto.AgentDTO;
import xin.v5ai.nb.common.agentscope.enums.RagCallMode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 次要模型的两级回退链：次要模型 → 绑定的对话模型 → 无。
 *
 * <p>标题改写与会话摘要两处共用这条链，所以「回退」的语义单独钉在这里，
 * 两侧的 Writer 测试只管「拿到了正确的 modelId」。</p>
 */
class AuxiliaryModelTest {

    private static AgentDTO agent(Long modelId, Long secondaryModelId) {
        return new AgentDTO("a1", "Demo", null, null, modelId, null, null,
                null, null, null, false, false, false, false, false,
                RagCallMode.FORCED.value(), secondaryModelId, true);
    }

    @Test
    void secondaryModelWinsWhenConfigured() {
        var agent = agent(7L, 9L);

        assertThat(AuxiliaryModel.resolve(agent)).isEqualTo(9L);
        assertThat(AuxiliaryModel.isSecondary(agent)).isTrue();
    }

    @Test
    void fallsBackToTheBoundChatModel() {
        var agent = agent(7L, null);

        assertThat(AuxiliaryModel.resolve(agent)).isEqualTo(7L);
        assertThat(AuxiliaryModel.isSecondary(agent)).isFalse();
    }

    @Test
    void returnsNullWhenNoModelIsBoundAtAll() {
        assertThat(AuxiliaryModel.resolve(agent(null, null))).isNull();
        assertThat(AuxiliaryModel.isSecondary(agent(null, null))).isFalse();
    }

    /** 解析不到 Agent（未发布 / 已删除）时同样返回 null，由调用方短路。 */
    @Test
    void nullAgentResolvesToNull() {
        assertThat(AuxiliaryModel.resolve(null)).isNull();
        assertThat(AuxiliaryModel.isSecondary(null)).isFalse();
    }
}
