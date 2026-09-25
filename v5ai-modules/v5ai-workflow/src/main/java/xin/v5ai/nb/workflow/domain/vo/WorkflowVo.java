package xin.v5ai.nb.workflow.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import xin.v5ai.nb.workflow.core.WorkflowDefinition;
import xin.v5ai.nb.workflow.domain.Workflow;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

/**
 * <p>
 * Workflow 视图对象：draft/publishedDefinition 为解析后的 JSON 定义。
 * </p>
 *
 * @author ZYW
 * @since 2026-08-22
 */
@Data
@AutoMapper(target = Workflow.class)
public class WorkflowVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private String workflowKey;

    private String name;

    private String description;

    /**
     * 状态（DRAFT / PUBLISHED / DISABLED）
     */
    private String status;

    /**
     * 草稿定义（由服务解析 JSON 填充）
     */
    private WorkflowDefinition draftDefinition;

    /**
     * 已发布定义（由服务解析 JSON 填充）
     */
    private WorkflowDefinition publishedDefinition;

    private Long publishedVersion;

    private OffsetDateTime publishedAt;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    private Long draftRevision;
}
