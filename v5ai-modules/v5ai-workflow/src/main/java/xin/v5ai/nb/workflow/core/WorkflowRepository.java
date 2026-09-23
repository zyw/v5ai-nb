package xin.v5ai.nb.workflow.core;

import java.util.List;

public interface WorkflowRepository {
    Workflow save(Workflow workflow);

    Workflow update(Workflow workflow);

    Workflow findById(Long id);

    Workflow findByKey(String workflowKey);

    List<Workflow> list();

    void disable(Long id);
}
