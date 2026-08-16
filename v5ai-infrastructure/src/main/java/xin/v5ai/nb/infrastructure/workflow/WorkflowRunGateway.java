package xin.v5ai.nb.infrastructure.workflow;

import java.time.Instant;
import java.util.List;

public interface WorkflowRunGateway {
    void insertRun(WorkflowRunEntity entity);

    void markCompleted(String runId, String outputs, Instant finishedAt);

    void markFailed(String runId, String error, Instant finishedAt);

    WorkflowRunEntity selectRun(String runId);

    List<WorkflowRunEntity> selectRunsByKey(String workflowKey);
}
