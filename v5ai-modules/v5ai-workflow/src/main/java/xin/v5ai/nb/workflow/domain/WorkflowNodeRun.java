package xin.v5ai.nb.workflow.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;

/**
 * <p>
 * Workflow 节点运行实体（v5ai_workflow_node_run）：无 updated_at 列。
 * </p>
 *
 * @author ZYW
 * @since 2026-08-22
 */
@Getter
@Setter
@ToString
@TableName("v5ai_workflow_node_run")
public class WorkflowNodeRun {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String runId;

    private String nodeId;

    /**
     * 节点类型（START / AGENT / CONDITION / END）
     */
    private String nodeType;

    /**
     * 状态（PENDING / RUNNING / SUCCEEDED / FAILED / SKIPPED）
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
