package xin.v5ai.nb.workflow.core;

import xin.v5ai.nb.workflow.core.enums.WorkflowNodeType;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class WorkflowNodeExecutorRegistry {
    private final Map<WorkflowNodeType, WorkflowNodeExecutor> executors;

    public WorkflowNodeExecutorRegistry(List<WorkflowNodeExecutor> candidates) {
        Map<WorkflowNodeType, WorkflowNodeExecutor> registry = new EnumMap<>(WorkflowNodeType.class);
        for (WorkflowNodeExecutor executor : candidates) {
            if (registry.putIfAbsent(executor.type(), executor) != null) {
                throw new IllegalStateException("duplicate workflow node executor: " + executor.type());
            }
        }
        this.executors = Map.copyOf(registry);
    }

    public WorkflowNodeExecutor require(WorkflowNodeType type) {
        WorkflowNodeExecutor executor = executors.get(type);
        if (executor == null) throw new IllegalArgumentException("unsupported workflow node type: " + type);
        return executor;
    }

    public boolean supports(WorkflowNodeType type) {
        return executors.containsKey(type);
    }
}
