package xin.v5ai.nb.api.workflow;

import java.util.Map;

public record RunWorkflowRequest(
        Map<String, Object> inputs
) {
}
