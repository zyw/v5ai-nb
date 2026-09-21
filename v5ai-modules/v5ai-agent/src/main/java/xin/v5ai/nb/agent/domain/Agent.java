package xin.v5ai.nb.agent.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import xin.v5ai.nb.common.mybatis.core.domain.BaseEntity;

/**
 * <p>
 * AgentDTO 实体（v5ai_agent）：agentKey 全局唯一，status 为 DRAFT/PUBLISHED/DISABLED。
 * </p>
 *
 * @author ZYW
 * @since 2026-08-22
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode(callSuper = true)
@TableName("v5ai_agent")
public class Agent extends BaseEntity {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 对外运行标识（全局唯一，运行时通过 agentKey 解析 AgentDTO）
     */
    private String agentKey;

    /**
     * 名称
     */
    private String name;

    /**
     * 描述
     */
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
     * 次要模型 ID（须为 CHAT 类型且已启用）：供会话标题改写与会话摘要压缩两处平台内部调用使用；
     * 为空则回退 {@link #modelId}
     */
    private Long secondaryModelId;

    /**
     * 当前生效的已发布版本号（未发布为 null）
     */
    private Long publishedVersion;

    /**
     * 系统提示词（随发布版本固化）
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
     * 是否在聊天窗口展示 RAG 引用折叠块（仅影响渲染；引用照常检索与落库）
     */
    private Boolean showCitations;
}
