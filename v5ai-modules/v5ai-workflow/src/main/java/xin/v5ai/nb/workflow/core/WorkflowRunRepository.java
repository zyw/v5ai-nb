package xin.v5ai.nb.workflow.core;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public interface WorkflowRunRepository {
    void start(WorkflowRun run);

    void complete(String runId, Map<String, Object> outputs, Instant finishedAt);

    void fail(String runId, String error, Instant finishedAt);

    void saveNodeRun(WorkflowNodeRun nodeRun);

    default void startNodeRun(WorkflowNodeRun nodeRun) { saveNodeRun(nodeRun); }

    default void finishNodeRun(WorkflowNodeRun nodeRun) { saveNodeRun(nodeRun); }

    WorkflowRun findRun(String runId);

    List<WorkflowNodeRun> findNodeRuns(String runId);

    List<WorkflowRun> listRuns(String workflowKey);
}
