package xin.v5ai.nb.agent.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import xin.v5ai.nb.agent.domain.Agent;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

/**
 * <p>
 * AgentDTO 视图对象（字段名与 v5ai-ui 的 AgentResponse 类型保持一致）。
 * </p>
 *
 * @author ZYW
 * @since 2026-08-22
 */
@Data
@AutoMapper(target = Agent.class)
public class AgentVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    /**
     * 对外运行标识
     */
    private String agentKey;

    private String name;

    private String description;

    /**
     * 状态（DRAFT / PUBLISHED / DISABLED）
     */
    private String status;

    /**
     * 绑定的模型 ID
     */
    private Long modelId;

    /**
     * 绑定模型名称；列表查询由服务端按模型 ID 批量补齐。
     */
    private String modelName;

    /**
     * 当前生效的已发布版本号
     */
    private Long publishedVersion;

    private String systemPrompt;

    private String avatar;

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
     * 次要模型 ID（为空表示回退绑定的对话模型）
     */
    private Long secondaryModelId;

    /**
     * 是否在聊天窗口展示 RAG 引用折叠块
     */
    private Boolean showCitations;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
