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
public class VariableNodeExecutor implements WorkflowNodeExecutor {
    @Override public WorkflowNodeType type() { return WorkflowNodeType.VARIABLE; }

    @Override
    public NodeExecutionResult execute(WorkflowNode node, WorkflowExecutionContext context) {
        Object entries = node.config().get("assignments");
        Map<String, Object> result = new LinkedHashMap<>();
        if (entries instanceof List<?> list) {
            for (Object entry : list) {
                if (!(entry instanceof Map<?, ?> assignment)) continue;
                String name = AgentNodeExecutor.text(assignment.get("name"));
                Object value = TemplateResolver.resolveRefOrLiteral(AgentNodeExecutor.text(assignment.get("value")), context);
                context.setVariable(name, value);
                result.put(name, value);
            }
        } else {
            String name = AgentNodeExecutor.text(node.config().get("name"));
            if (name != null && !name.isBlank()) {
                Object value = TemplateResolver.resolveRefOrLiteral(AgentNodeExecutor.text(node.config().get("value")), context);
                context.setVariable(name, value);
                result.put(name, value);
            }
        }
        return NodeExecutionResult.of(result);
    }
}
