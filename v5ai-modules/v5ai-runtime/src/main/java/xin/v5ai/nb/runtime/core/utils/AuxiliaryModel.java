package xin.v5ai.nb.runtime.core.utils;

import xin.v5ai.nb.common.agentscope.core.domain.dto.AgentDTO;

/**
 * 次要模型的解析：决定「会话标题改写」与「会话摘要压缩」这两处**平台内部**调用用哪个模型。
 *
 * <p>两者共用同一个字段（{@code v5ai_agent.secondary_model_id}）与同一条回退链，理由一致：
 * 都是轻量、非主对话的内部调用，给一个便宜模型更划算；拆成两个字段只会让 Agent 表单与发布快照
 * 各多一份冗余，而实际诉求相同。</p>
 *
 * <p><b>回退链只有两级</b>：次要模型 → Agent 绑定的对话模型。中间**没有**全局配置项——
 * 原先的 {@code v5ai.chat.conversation-summary.model-id} 已下线（见
 * {@code docs/次要模型与引用展示开关实现方案.md} §3.1）：它与「Agent 级次要模型」职责重叠，
 * 留着会让「这个 Agent 的摘要到底用了哪个模型」取决于一处离 Agent 很远的全局配置。</p>
 *
 * <p>纯函数，不依赖 Spring：两个调用方各自已经持有解析好的 {@link AgentDTO}，
 * 这里只做取值判断，也就没有可注入的状态。</p>
 */
public final class AuxiliaryModel {

    private AuxiliaryModel() {
    }

    /**
     * 解析内部调用应使用的模型 id。
     *
     * @param agent 已发布（或调试态）的 Agent；为 null 时返回 null
     * @return 次要模型 id；未配置时回退该 Agent 绑定的对话模型 id；两者皆空则为 null
     */
    public static Long resolve(AgentDTO agent) {
        if (agent == null) {
            return null;
        }
        return agent.secondaryModelId() != null ? agent.secondaryModelId() : agent.modelId();
    }

    /**
     * 本次解析命中的是不是次要模型（未命中即为回退到主模型）。
     *
     * <p>只服务于日志归因：这两处调用链**不记用量、不落事件**，出问题时唯一能查的就是日志，
     * 而「用的到底是次要模型还是主模型」正是最需要先确认的一件事。</p>
     */
    public static boolean isSecondary(AgentDTO agent) {
        return agent != null && agent.secondaryModelId() != null;
    }
}
