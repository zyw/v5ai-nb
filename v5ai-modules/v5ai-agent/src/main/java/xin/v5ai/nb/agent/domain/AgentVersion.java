package xin.v5ai.nb.agent.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * <p>
 * AgentDTO 版本实体（v5ai_agent_version）：发布时生成的配置快照，版本号按 AgentDTO 内自增。
 * 表无 updated_at 列，故不继承 {@code BaseEntity}，仅含 createdAt。
 * </p>
 *
 * @author ZYW
 * @since 2026-08-22
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
@TableName("v5ai_agent_version")
public class AgentVersion implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属 AgentDTO 的 agentKey
     */
    private String agentKey;

    /**
     * 版本号（每个 AgentDTO 内自增，从 1 起）
     */
    private Long version;

    /**
     * 发布时固化的配置快照（JSON 文本）
     */
    private String snapshotJson;

    /**
     * 版本描述
     */
    private String description;

    /**
     * 创建时间
     */
    private OffsetDateTime createdAt;
}
