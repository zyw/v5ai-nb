package xin.v5ai.nb.workflow.core.executor;

import org.springframework.stereotype.Component;
import xin.v5ai.nb.workflow.core.NodeExecutionResult;
import xin.v5ai.nb.workflow.core.WorkflowExecutionContext;
import xin.v5ai.nb.workflow.core.WorkflowNode;
import xin.v5ai.nb.workflow.core.WorkflowNodeExecutor;
import xin.v5ai.nb.workflow.core.enums.WorkflowNodeType;

import java.util.List;
import java.util.Map;

@Component
public class StartNodeExecutor implements WorkflowNodeExecutor {
    @Override public WorkflowNodeType type() { return WorkflowNodeType.START; }

    @Override
    public NodeExecutionResult execute(WorkflowNode node, WorkflowExecutionContext context) {
        Object configured = node.config().get("variables");
        if (configured instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> variable && variable.get("name") != null) {
                    String name = String.valueOf(variable.get("name"));
                    context.variables().putIfAbsent(name, variable.get("default"));
                }
            }
        }
        return NodeExecutionResult.of(Map.of());
    }
}
