package xin.v5ai.nb.workflow.service;

import xin.v5ai.nb.common.core.domain.PageResult;
import xin.v5ai.nb.common.mybatis.core.page.PageQuery;
import xin.v5ai.nb.workflow.core.WorkflowNodeRun;
import xin.v5ai.nb.workflow.core.WorkflowRun;
import xin.v5ai.nb.workflow.core.WorkflowEngine;
import xin.v5ai.nb.workflow.domain.bo.WorkflowBo;
import xin.v5ai.nb.workflow.domain.vo.WorkflowVo;
import xin.v5ai.nb.workflow.domain.vo.WorkflowRunDetailVo;
import xin.v5ai.nb.workflow.domain.WorkflowVersion;

import java.util.List;

/**
 * Workflow 管理服务：草稿编辑、发布（DAG 校验 + 版本快照）、禁用与运行记录查询。
 * 执行逻辑见 {@link WorkflowEngine}。
 *
 * @author ZYW
 * @since 2026-08-22
 */
public interface IWorkflowService {

    /**
     * 分页查询 Workflow。
     */
    PageResult<WorkflowVo> queryPageList(WorkflowBo bo, PageQuery pageQuery);

    /**
     * 查询 Workflow 列表。
     */
    List<WorkflowVo> queryList(WorkflowBo bo);

    /**
     * 创建 Workflow（DRAFT）。
     */
    WorkflowVo create(WorkflowBo bo);

    /**
     * 更新草稿（name/description/definition 为 null 时保留原值）。
     */
    WorkflowVo update(String workflowKey, WorkflowBo bo);

    /**
     * 查询单个 Workflow。
     */
    WorkflowVo get(String workflowKey);

    /**
     * 禁用 Workflow。
     */
    void disable(String workflowKey);

    /** Physically deletes a batch of unpublished workflows and their version/run history. */
    void deleteBatch(List<String> workflowKeys);

    /**
     * 启用 Workflow；有已发布版本时恢复为 PUBLISHED，否则恢复为 DRAFT。
     */
    void enable(String workflowKey);

    /**
     * 发布：校验 DAG 后快照草稿为已发布版本。
     */
    WorkflowVo publish(String workflowKey);

    List<WorkflowVersion> listVersions(String workflowKey);

    WorkflowVersion getVersion(String workflowKey, long version);

    WorkflowVo restoreVersion(String workflowKey, long version, Long expectedRevision);

    /**
     * 查询某 Workflow 的运行记录（新在前）。
     */
    List<WorkflowRun> listRuns(String workflowKey);

    /**
     * 查询单个运行记录。
     */
    WorkflowRun getRun(String runId);

    /** 查询运行记录及其节点名称（来自本次运行的定义快照）。 */
    WorkflowRunDetailVo getRunDetail(String runId);

    /**
     * 查询某运行的节点记录。
     */
    List<WorkflowNodeRun> getNodeRuns(String runId);
}
