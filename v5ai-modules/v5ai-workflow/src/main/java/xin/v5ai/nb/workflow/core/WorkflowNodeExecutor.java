package xin.v5ai.nb.workflow.core;

import xin.v5ai.nb.workflow.core.enums.WorkflowNodeType;

public interface WorkflowNodeExecutor {
    WorkflowNodeType type();

    NodeExecutionResult execute(WorkflowNode node, WorkflowExecutionContext context);
}
