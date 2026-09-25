package xin.v5ai.nb.workflow.core.executor;

import org.springframework.stereotype.Component;
import xin.v5ai.nb.workflow.core.ConditionEvaluator;
import xin.v5ai.nb.workflow.core.NodeExecutionResult;
import xin.v5ai.nb.workflow.core.TemplateResolver;
import xin.v5ai.nb.workflow.core.WorkflowExecutionContext;
import xin.v5ai.nb.workflow.core.WorkflowNode;
import xin.v5ai.nb.workflow.core.WorkflowNodeExecutor;
import xin.v5ai.nb.workflow.core.enums.WorkflowNodeType;

import java.util.Map;

@Component
public class ConditionNodeExecutor implements WorkflowNodeExecutor {
    @Override public WorkflowNodeType type() { return WorkflowNodeType.CONDITION; }

    @Override
    public NodeExecutionResult execute(WorkflowNode node, WorkflowExecutionContext context) {
        Object left = TemplateResolver.resolveRefOrLiteral(AgentNodeExecutor.text(node.config().get("left")), context);
        Object right = TemplateResolver.resolveRefOrLiteral(AgentNodeExecutor.text(node.config().get("right")), context);
        boolean result = ConditionEvaluator.evaluate(left, AgentNodeExecutor.text(node.config().get("operator")), right);
        return new NodeExecutionResult(Map.of("result", result, "left", String.valueOf(left), "right", String.valueOf(right)), String.valueOf(result));
    }
}
