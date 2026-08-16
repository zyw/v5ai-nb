package xin.v5ai.nb.api.workflow;

import java.util.List;

public record WorkflowRunDetailResponse(
        WorkflowRunResponse run,
        List<WorkflowNodeRunResponse> nodeRuns
) {
}
