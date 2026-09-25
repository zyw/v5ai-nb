package xin.v5ai.nb.workflow.core;

import java.util.Map;
import java.util.Collections;
import java.util.LinkedHashMap;

public record NodeExecutionResult(Map<String, Object> outputs, String nextHandle) {
    public NodeExecutionResult {
        outputs = outputs == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(outputs));
    }

    public static NodeExecutionResult of(Map<String, Object> outputs) {
        return new NodeExecutionResult(outputs, null);
    }
}
