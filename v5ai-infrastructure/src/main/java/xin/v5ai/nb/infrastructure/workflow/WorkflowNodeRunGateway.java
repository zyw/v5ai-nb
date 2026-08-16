package xin.v5ai.nb.infrastructure.workflow;

import java.util.List;

public interface WorkflowNodeRunGateway {
    void insertNodeRun(WorkflowNodeRunEntity entity);

    List<WorkflowNodeRunEntity> selectNodeRunsByRun(String runId);
}
