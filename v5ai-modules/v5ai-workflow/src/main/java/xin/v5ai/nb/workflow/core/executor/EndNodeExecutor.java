package xin.v5ai.nb.workflow.core.executor;

import org.springframework.stereotype.Component;
import xin.v5ai.nb.workflow.core.NodeExecutionResult;
import xin.v5ai.nb.workflow.core.TemplateResolver;
import xin.v5ai.nb.workflow.core.WorkflowExecutionContext;
import xin.v5ai.nb.workflow.core.WorkflowNode;
import xin.v5ai.nb.workflow.core.WorkflowNodeExecutor;
import xin.v5ai.nb.workflow.core.enums.WorkflowNodeType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class EndNodeExecutor implements WorkflowNodeExecutor {
    @Override public WorkflowNodeType type() { return WorkflowNodeType.END; }

    @Override
    public NodeExecutionResult execute(WorkflowNode node, WorkflowExecutionContext context) {
        Map<String, Object> outputs = new LinkedHashMap<>();
        Object configured = node.config().get("outputs");
        if (configured instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> output && output.get("name") != null) {
                    String name = String.valueOf(output.get("name"));
                    Object value = TemplateResolver.resolveRefOrLiteral(AgentNodeExecutor.text(output.get("value")), context);
                    outputs.put(name, value);
                }
            }
        }
        context.finalOutputs().putAll(outputs);
        return NodeExecutionResult.of(outputs);
    }
}
