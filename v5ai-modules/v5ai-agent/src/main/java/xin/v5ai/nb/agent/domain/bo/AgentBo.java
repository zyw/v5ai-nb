package xin.v5ai.nb.agent.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import xin.v5ai.nb.agent.domain.Agent;
import xin.v5ai.nb.common.core.validate.AddGroup;
import xin.v5ai.nb.common.core.validate.EditGroup;

/**
 * AgentDTO 请求体（创建/更新/查询）。
 *
 * @author ZYW
 * @since 2026-08-22
 */
@Data
@AutoMapper(target = Agent.class, reverseConvertGenerate = false)
public class AgentBo {

    /**
     * 主键，编辑时必填
     */
    @NotNull(message = "主键不能为空", groups = {EditGroup.class})
    private Long id;

    /**
     * 对外运行标识（创建时必填）
     */
    @NotBlank(message = "agent key is required", groups = {AddGroup.class})
    private String agentKey;

    /**
     * 名称
     */
    @NotBlank(message = "agent name is required")
    private String name;

    /**
     * 描述
     */
    private String description;

    /**
     * 绑定的模型 ID
     */
    @NotNull(message = "model is required")
    private Long modelId;

    /**
     * 次要模型 ID（可空）：供会话标题改写与会话摘要压缩调用；留空则回退绑定的对话模型。
     * 保存时校验「存在 + CHAT 类型 + 已启用」。
     */
    private Long secondaryModelId;

    /**
     * 系统提示词
     */
    private String systemPrompt;

    /**
     * 头像 URL
     */
    private String avatar;

    /**
     * 欢迎语
     */
    private String greeting;

    /**
     * 预设问题列表（JSON 数组字符串）
     */
    private String presetQuestions;

    /**
     * 是否启用记忆库
     */
    private Boolean memoryEnabled;

    /**
     * 是否启用 MCP
     */
    private Boolean mcpEnabled;

    /**
     * 是否启用 Skill
     */
    private Boolean skillEnabled;

    /**
     * 是否启用联网搜索
     */
    private Boolean webSearchEnabled;

    /**
     * 是否启用 RAG
     */
    private Boolean ragEnabled;

    /**
     * RAG 调用方式（1=智能调用，2=强制调用；仅在 ragEnabled 开启时生效）
     */
    private Integer ragCallMode;

    /**
     * 是否在聊天窗口展示 RAG 引用折叠块（可空；缺省按 true 解析，与存量行为一致）
     */
    private Boolean showCitations;

    /**
     * 启用状态（查询条件：DRAFT / PUBLISHED / DISABLED，精确匹配）
     */
    private String status;

    /**
     * 模糊搜索关键词（查询条件：name 或 agentKey 任一命中，大小写不敏感）。
     * 仅用于列表查询，不参与创建/更新。
     */
    private String keyword;
}
