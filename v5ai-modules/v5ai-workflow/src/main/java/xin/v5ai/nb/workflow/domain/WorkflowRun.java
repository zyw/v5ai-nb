package xin.v5ai.nb.workflow.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;

/**
 * <p>
 * Workflow 运行实体（v5ai_workflow_run）：run_id 为 VARCHAR 主键，无 updated_at 列。
 * </p>
 *
 * @author ZYW
 * @since 2026-08-22
 */
@Getter
@Setter
@ToString
@TableName("v5ai_workflow_run")
public class WorkflowRun {

    @TableId(value = "run_id")
    private String runId;

    private String workflowKey;

    private Long workflowVersion;

    /**
     * 状态（RUNNING / SUCCEEDED / FAILED）
     */
    private String status;

    /**
     * 输入 JSON，列名 inputs
     */
    @TableField("inputs")
    private String inputsJson;

    /**
     * 输出 JSON，列名 outputs
     */
    @TableField("outputs")
    private String outputsJson;

    private String error;

    private Instant startedAt;

    private Instant finishedAt;

    private Instant createdAt;
}
