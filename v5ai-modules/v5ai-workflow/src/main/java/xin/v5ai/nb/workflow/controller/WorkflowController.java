package xin.v5ai.nb.workflow.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
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
import xin.v5ai.nb.common.core.page.PageQuery;
import xin.v5ai.nb.common.core.page.PageResult;
import xin.v5ai.nb.common.core.platform.AuditRecorder;
import xin.v5ai.nb.common.core.validate.AddGroup;
import xin.v5ai.nb.common.satoken.utils.LoginHelper;
import xin.v5ai.nb.common.web.core.BaseController;
import xin.v5ai.nb.workflow.WorkflowEngine;
import xin.v5ai.nb.workflow.WorkflowRun;
import xin.v5ai.nb.workflow.domain.bo.WorkflowBo;
import xin.v5ai.nb.workflow.domain.vo.WorkflowVo;
import xin.v5ai.nb.workflow.service.IWorkflowService;

import java.util.List;

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

    @Autowired(required = false)
    private AuditRecorder auditRecorder;

    @GetMapping
    public R<PageResult<WorkflowVo>> list(WorkflowBo bo, PageQuery pageQuery) {
        return R.ok(workflowService.queryPageList(bo, pageQuery));
    }

    @PostMapping
    public R<WorkflowVo> create(@Validated(AddGroup.class) @RequestBody WorkflowBo bo) {
        var created = workflowService.create(bo);
        audit("CREATE", "workflow", created.getWorkflowKey(), "create workflow " + created.getName());
        return R.ok(created);
    }

    @GetMapping("/{key}")
    public R<WorkflowVo> get(@PathVariable("key") String key) {
        return R.ok(workflowService.get(key));
    }

    @PutMapping("/{key}")
    public R<WorkflowVo> update(@PathVariable("key") String key, @RequestBody WorkflowBo bo) {
        audit("UPDATE", "workflow", key, "update workflow draft");
        return R.ok(workflowService.update(key, bo));
    }

    @DeleteMapping("/{key}")
    public R<Void> disable(@PathVariable("key") String key) {
        workflowService.disable(key);
        audit("DISABLE", "workflow", key, "disable workflow");
        return R.ok();
    }

    @PostMapping("/{key}/publish")
    public R<WorkflowVo> publish(@PathVariable("key") String key) {
        audit("PUBLISH", "workflow", key, "publish workflow");
        return R.ok(workflowService.publish(key));
    }

    @PostMapping("/{key}/run")
    public R<WorkflowRun> run(@PathVariable("key") String key,
                              @RequestBody(required = false) RunWorkflowRequest request) {
        var inputs = request == null ? null : request.inputs();
        audit("RUN", "workflow", key, "run workflow");
        return R.ok(engine.execute(key, inputs));
    }

    @GetMapping("/{key}/runs")
    public R<PageResult<WorkflowRun>> listRuns(@PathVariable("key") String key, PageQuery pageQuery) {
        var list = workflowService.listRuns(key);
        return R.ok(PageResult.build(list, (long) list.size()));
    }

    @GetMapping("/runs/{runId}")
    public R<WorkflowRunDetailResponse> getRun(@PathVariable("runId") String runId) {
        var run = workflowService.getRun(runId);
        var nodeRuns = workflowService.getNodeRuns(runId);
        return R.ok(new WorkflowRunDetailResponse(run, nodeRuns));
    }

    private void audit(String action, String targetType, String targetId, String detail) {
        if (auditRecorder == null) {
            return;
        }
        String actor;
        try {
            actor = LoginHelper.getUsername();
        } catch (Exception exception) {
            actor = null;
        }
        auditRecorder.record(null, actor, action, targetType, targetId, detail, null);
    }
}
