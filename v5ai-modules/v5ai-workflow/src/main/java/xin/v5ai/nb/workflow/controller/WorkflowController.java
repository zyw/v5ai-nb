package xin.v5ai.nb.workflow.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xin.v5ai.nb.common.core.domain.R;
import xin.v5ai.nb.common.core.domain.PageResult;
import xin.v5ai.nb.common.core.validate.AddGroup;
import xin.v5ai.nb.common.log.annotation.Log;
import xin.v5ai.nb.common.log.enums.BusinessType;
import xin.v5ai.nb.common.mybatis.core.page.PageQuery;
import xin.v5ai.nb.common.web.core.BaseController;
import xin.v5ai.nb.workflow.core.WorkflowEngine;
import xin.v5ai.nb.workflow.core.WorkflowRun;
import xin.v5ai.nb.workflow.domain.bo.RunWorkflowBo;
import xin.v5ai.nb.workflow.domain.bo.WorkflowBo;
import xin.v5ai.nb.workflow.domain.vo.WorkflowRunDetailVo;
import xin.v5ai.nb.workflow.domain.vo.WorkflowVo;
import xin.v5ai.nb.workflow.service.IWorkflowService;

/**
 * Workflow 管理 API：草稿编辑、发布、测试运行与运行记录查询。
 *
 * @author ZYW
 * @since 2026-08-22
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/workflows")
public class WorkflowController extends BaseController {

    private final IWorkflowService workflowService;
    private final WorkflowEngine engine;

    @GetMapping
    public R<PageResult<WorkflowVo>> list(WorkflowBo bo, PageQuery pageQuery) {
        return R.ok(workflowService.queryPageList(bo, pageQuery));
    }

    @PostMapping
    @Log(title = "新建工作流", businessType = BusinessType.INSERT)
    public R<WorkflowVo> create(@Validated(AddGroup.class) @RequestBody WorkflowBo bo) {
        var created = workflowService.create(bo);
        return R.ok(created);
    }

    @GetMapping("/{key}")
    public R<WorkflowVo> get(@PathVariable("key") String key) {
        return R.ok(workflowService.get(key));
    }

    @PutMapping("/{key}")
    @Log(title = "更新工作流", businessType = BusinessType.UPDATE)
    public R<WorkflowVo> update(@PathVariable("key") String key, @RequestBody WorkflowBo bo) {
        return R.ok(workflowService.update(key, bo));
    }

    @DeleteMapping("/{key}")
    @Log(title = "禁用工作流", businessType = BusinessType.DISABLE)
    public R<Void> disable(@PathVariable("key") String key) {
        workflowService.disable(key);
        return R.ok();
    }

    @PostMapping("/{key}/publish")
    @Log(title = "发布工作流", businessType = BusinessType.PUBLISH)
    public R<WorkflowVo> publish(@PathVariable("key") String key) {
        return R.ok(workflowService.publish(key));
    }

    @PostMapping("/{key}/run")
    @Log(title = "运行工作流", businessType = BusinessType.RUN)
    public R<WorkflowRun> run(@PathVariable("key") String key,
                              @RequestBody(required = false) RunWorkflowBo request) {
        var inputs = request == null ? null : request.inputs();
        return R.ok(engine.execute(key, inputs));
    }

    @GetMapping("/{key}/runs")
    public R<PageResult<WorkflowRun>> listRuns(@PathVariable("key") String key, PageQuery pageQuery) {
        var list = workflowService.listRuns(key);
        return R.ok(PageResult.build(list, (long) list.size()));
    }

    @GetMapping("/runs/{runId}")
    public R<WorkflowRunDetailVo> getRun(@PathVariable("runId") String runId) {
        var run = workflowService.getRun(runId);
        var nodeRuns = workflowService.getNodeRuns(runId);
        return R.ok(new WorkflowRunDetailVo(run, nodeRuns));
    }
}
