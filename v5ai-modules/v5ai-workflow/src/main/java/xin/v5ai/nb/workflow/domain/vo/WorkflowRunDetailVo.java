package xin.v5ai.nb.workflow.domain.vo;

import xin.v5ai.nb.workflow.core.WorkflowRun;

import java.util.List;

/**
 * Workflow 运行详情响应体（运行记录 + 节点记录）。
 *
 * @param run      运行记录
 * @param nodeRuns 节点记录（包含本次运行定义中的节点名称）
 */
public record WorkflowRunDetailVo(WorkflowRun run, List<WorkflowNodeRunDetailVo> nodeRuns) {
}
