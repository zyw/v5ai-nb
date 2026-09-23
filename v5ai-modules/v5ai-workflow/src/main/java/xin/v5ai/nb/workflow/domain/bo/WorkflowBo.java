package xin.v5ai.nb.workflow.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import xin.v5ai.nb.common.core.validate.AddGroup;
import xin.v5ai.nb.workflow.core.WorkflowDefinition;
import xin.v5ai.nb.workflow.domain.Workflow;

/**
 * Workflow 创建/更新请求体（更新时 workflowKey 来自路径参数）。
 *
 * @author ZYW
 * @since 2026-08-22
 */
@Data
@AutoMapper(target = Workflow.class, reverseConvertGenerate = false)
public class WorkflowBo {

    /**
     * 对外标识（创建时必填，更新时忽略）
     */
    @NotBlank(message = "workflowKey is required", groups = {AddGroup.class})
    private String workflowKey;

    /**
     * 名称（创建时必填）
     */
    @NotBlank(message = "name is required", groups = {AddGroup.class})
    private String name;

    private String description;

    /**
     * 状态（DRAFT / PUBLISHED / DISABLED），列表查询筛选条件
     */
    private String status;

    /**
     * 草稿定义（nodes/edges），更新时提交
     */
    private WorkflowDefinition definition;
}
