package xin.v5ai.nb.infrastructure.workflow;

import org.springframework.stereotype.Repository;
import xin.v5ai.nb.workflow.WorkflowJson;
import xin.v5ai.nb.workflow.WorkflowNodeRun;
import xin.v5ai.nb.workflow.WorkflowNodeRunStatus;
import xin.v5ai.nb.workflow.WorkflowNodeType;
import xin.v5ai.nb.workflow.WorkflowRun;
import xin.v5ai.nb.workflow.WorkflowRunRepository;
import xin.v5ai.nb.workflow.WorkflowRunStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Repository
public class MyBatisWorkflowRunRepository implements WorkflowRunRepository {
    private final WorkflowRunGateway runGateway;
    private final WorkflowNodeRunGateway nodeRunGateway;

    public MyBatisWorkflowRunRepository(WorkflowRunGateway runGateway, WorkflowNodeRunGateway nodeRunGateway) {
        this.runGateway = runGateway;
        this.nodeRunGateway = nodeRunGateway;
    }

    @Override
    public void start(WorkflowRun run) {
        var entity = new WorkflowRunEntity();
        entity.setRunId(run.runId());
        entity.setWorkflowKey(run.workflowKey());
        entity.setWorkflowVersion(run.workflowVersion());
        entity.setStatus(run.status().name());
        entity.setInputs(WorkflowJson.toJson(run.inputs()));
        entity.setStartedAt(run.startedAt());
        entity.setCreatedAt(run.createdAt());
        runGateway.insertRun(entity);
    }

    @Override
    public void complete(String runId, Map<String, Object> outputs, Instant finishedAt) {
        runGateway.markCompleted(runId, WorkflowJson.toJson(outputs), finishedAt);
    }

    @Override
    public void fail(String runId, String error, Instant finishedAt) {
        runGateway.markFailed(runId, error, finishedAt);
    }

    @Override
    public void saveNodeRun(WorkflowNodeRun nodeRun) {
        var entity = new WorkflowNodeRunEntity();
        entity.setRunId(nodeRun.runId());
        entity.setNodeId(nodeRun.nodeId());
        entity.setNodeType(nodeRun.nodeType().name());
        entity.setStatus(nodeRun.status().name());
        entity.setInputs(WorkflowJson.toJson(nodeRun.inputs()));
        entity.setOutputs(WorkflowJson.toJson(nodeRun.outputs()));
        entity.setError(nodeRun.error());
        entity.setStartedAt(nodeRun.startedAt());
        entity.setFinishedAt(nodeRun.finishedAt());
        entity.setCreatedAt(Instant.now());
        nodeRunGateway.insertNodeRun(entity);
    }

    @Override
    public WorkflowRun findRun(String runId) {
        return toDomain(runGateway.selectRun(runId));
    }

    @Override
    public List<WorkflowNodeRun> findNodeRuns(String runId) {
        return nodeRunGateway.selectNodeRunsByRun(runId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<WorkflowRun> listRuns(String workflowKey) {
        return runGateway.selectRunsByKey(workflowKey).stream()
                .map(this::toDomain)
                .toList();
    }

    private WorkflowRun toDomain(WorkflowRunEntity entity) {
        if (entity == null) {
            return null;
        }
        return new WorkflowRun(entity.getRunId(), entity.getWorkflowKey(), entity.getWorkflowVersion(),
                WorkflowRunStatus.valueOf(entity.getStatus()),
                WorkflowJson.parseMap(entity.getInputs()), WorkflowJson.parseMap(entity.getOutputs()),
                entity.getError(), entity.getStartedAt(), entity.getFinishedAt(), entity.getCreatedAt());
    }

    private WorkflowNodeRun toDomain(WorkflowNodeRunEntity entity) {
        if (entity == null) {
            return null;
        }
        return new WorkflowNodeRun(entity.getId(), entity.getRunId(), entity.getNodeId(),
                WorkflowNodeType.valueOf(entity.getNodeType()),
                WorkflowNodeRunStatus.valueOf(entity.getStatus()),
                WorkflowJson.parseMap(entity.getInputs()), WorkflowJson.parseMap(entity.getOutputs()),
                entity.getError(), entity.getStartedAt(), entity.getFinishedAt());
    }
}
