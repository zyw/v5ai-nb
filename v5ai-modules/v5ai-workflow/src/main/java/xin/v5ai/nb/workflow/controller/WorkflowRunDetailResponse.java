package xin.v5ai.nb.workflow.controller;

import xin.v5ai.nb.workflow.core.WorkflowNodeRun;
import xin.v5ai.nb.workflow.core.WorkflowRun;

import java.util.List;

/**
 * Workflow 运行详情响应体（运行记录 + 节点记录）。
 *
 * @param run      运行记录
 * @param nodeRuns 节点记录
 */
public record WorkflowRunDetailResponse(WorkflowRun run, List<WorkflowNodeRun> nodeRuns) {
}
