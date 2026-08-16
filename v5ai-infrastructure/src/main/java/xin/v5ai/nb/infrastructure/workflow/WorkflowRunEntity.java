package xin.v5ai.nb.infrastructure.workflow;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

@TableName("v5ai_workflow_run")
public class WorkflowRunEntity {
    @TableId
    private String runId;
    private String workflowKey;
    private Long workflowVersion;
    private String status;
    private String inputs;
    private String outputs;
    private String error;
    private Instant startedAt;
    private Instant finishedAt;
    private Instant createdAt;

    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }
    public String getWorkflowKey() { return workflowKey; }
    public void setWorkflowKey(String workflowKey) { this.workflowKey = workflowKey; }
    public Long getWorkflowVersion() { return workflowVersion; }
    public void setWorkflowVersion(Long workflowVersion) { this.workflowVersion = workflowVersion; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getInputs() { return inputs; }
    public void setInputs(String inputs) { this.inputs = inputs; }
    public String getOutputs() { return outputs; }
    public void setOutputs(String outputs) { this.outputs = outputs; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
