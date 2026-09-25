package xin.v5ai.nb.workflow.core;

import xin.v5ai.nb.workflow.core.enums.WorkflowNodeType;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record WorkflowNode(
        String id,
        WorkflowNodeType type,
        String name,
        Map<String, Object> config,
        Map<String, Object> position
) {
    public WorkflowNode(String id, WorkflowNodeType type, String name, Map<String, Object> config) {
        this(id, type, name, config, Map.of());
    }

    public WorkflowNode {
        config = config == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(config));
        position = position == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(position));
    }
}
