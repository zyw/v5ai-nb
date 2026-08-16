package xin.v5ai.nb.infrastructure.workflow;

import java.util.List;

public interface WorkflowGateway {
    void insertWorkflow(WorkflowEntity entity);

    void updateWorkflow(WorkflowEntity entity);

    WorkflowEntity selectWorkflowById(Long id);

    WorkflowEntity selectWorkflowByKey(String workflowKey);

    List<WorkflowEntity> selectAllWorkflows();

    void disableWorkflow(Long id);
}
