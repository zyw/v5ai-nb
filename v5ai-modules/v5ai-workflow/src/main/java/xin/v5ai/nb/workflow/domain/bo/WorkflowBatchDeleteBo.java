package xin.v5ai.nb.workflow.domain.bo;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public class WorkflowBatchDeleteBo {
    @NotEmpty
    private List<@NotBlank String> workflowKeys;

    public List<String> getWorkflowKeys() {
        return workflowKeys;
    }

    public void setWorkflowKeys(List<String> workflowKeys) {
        this.workflowKeys = workflowKeys;
    }
}
