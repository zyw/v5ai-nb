package xin.v5ai.nb.agent.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import xin.v5ai.nb.agent.domain.AgentVersion;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * <p>
 * AgentDTO 版本视图对象
 * </p>
 *
 * @author ZYW
 * @since 2026-08-22
 */
@Data
@AutoMapper(target = AgentVersion.class)
public class AgentVersionVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    /**
     * 所属 AgentDTO 的 agentKey
     */
    private String agentKey;

    /**
     * 版本号
     */
    private Long version;

    /**
     * 发布时固化的配置快照（JSON 文本）
     */
    private String snapshotJson;

    private String description;

    private OffsetDateTime createdAt;
}
