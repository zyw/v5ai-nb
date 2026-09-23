package xin.v5ai.nb.common.agentscope.core.domain.bo;

import xin.v5ai.nb.common.agentscope.core.domain.AttachmentRef;

import java.util.List;

/**
 * Chat 接口请求体（不可变 record）。
 *
 * @param conversationId       会话 ID（可为空，为空时后端会自动创建新会话）
 * @param query                用户提问内容
 * @param webSearch            本次请求是否使用联网搜索：{@code null}=按 Agent 配置（默认），
 *                             {@code false}=本次显式关闭，{@code true}=请求开启（仍需 Agent 本身允许）
 * @param attachments          本次提问携带的图片附件（先上传为资源，这里只提交引用）；
 *                             仅当 Agent 所绑 CHAT 模型声明支持图片输入时可用
 * @param disabledMcpServerIds 本次运行要收窄掉的 MCP Server（只能减少绑定，不能增加）
 * @param disabledSkillIds     本次运行要收窄掉的 Skill（只能减少绑定，不能增加）
 */
public record ChatBo(
        String conversationId,
        String query,
        Boolean webSearch,
        List<AttachmentRef> attachments,
        List<Long> disabledMcpServerIds,
        List<Long> disabledSkillIds) {

    /**
     * 归一化：收窄项与附件列表永不为 null。
     */
    public ChatBo {
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
        disabledMcpServerIds = disabledMcpServerIds == null ? List.of() : List.copyOf(disabledMcpServerIds);
        disabledSkillIds = disabledSkillIds == null ? List.of() : List.copyOf(disabledSkillIds);
    }

    /**
     * 兼容构造器：不指定联网搜索（按 Agent 配置），也不带附件与收窄项。
     */
    public ChatBo(String conversationId, String query) {
        this(conversationId, query, null, List.of(), List.of(), List.of());
    }

    /**
     * 兼容构造器：不指定附件与收窄项。
     */
    public ChatBo(String conversationId, String query, Boolean webSearch) {
        this(conversationId, query, webSearch, List.of(), List.of(), List.of());
    }
}
