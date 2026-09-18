package xin.v5ai.nb.common.agentscope.core.domain.bo;

/**
 * Chat 接口请求体（不可变 record）。
 *
 * @param conversationId 会话 ID（可为空，为空时后端会自动创建新会话）
 * @param query          用户提问内容
 * @param webSearch      本次请求是否使用联网搜索：{@code null}=按 Agent 配置（默认），
 *                       {@code false}=本次显式关闭，{@code true}=请求开启（仍需 Agent 本身允许）
 */
public record ChatBo(String conversationId, String query, Boolean webSearch) {

    /**
     * 兼容构造器：不指定联网搜索（按 Agent 配置）。
     */
    public ChatBo(String conversationId, String query) {
        this(conversationId, query, null);
    }
}
