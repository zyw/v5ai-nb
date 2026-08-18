package xin.v5ai.nb.api.workflow;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import xin.v5ai.nb.common.core.domain.R;
import xin.v5ai.nb.common.core.page.PageQuery;
import xin.v5ai.nb.common.core.page.PageResult;
import xin.v5ai.nb.common.core.page.PageUtils;
import xin.v5ai.nb.common.core.platform.AuditRecorder;
import xin.v5ai.nb.common.satoken.utils.LoginHelper;
import xin.v5ai.nb.workflow.WorkflowEngine;
import xin.v5ai.nb.workflow.WorkflowManagementService;

import java.util.List;

/**
 * Workflow 管理 API：草稿编辑、发布、测试运行与运行记录查询。
 */
@RestController
@RequestMapping("/api/admin/workflows")
public class WorkflowController {
    private final WorkflowManagementService managementService;
    private final WorkflowEngine engine;

    @Autowired(required = false)
    private AuditRecorder auditRecorder;

    public WorkflowController(WorkflowManagementService managementService, WorkflowEngine engine) {
        this.managementService = managementService;
        this.engine = engine;
    }

    @GetMapping
    public R<PageResult<WorkflowResponse>> list(PageQuery pageQuery) {
        var list = managementService.list().stream()
                .map(WorkflowResponse::from)
                .toList();
        return R.ok(PageUtils.page(list, pageQuery));
    }

    @PostMapping
    public R<WorkflowResponse> create(@RequestBody CreateWorkflowRequest request) {
        var created = WorkflowResponse.from(
                managementService.create(request.workflowKey(), request.name(), request.description()));
        audit("CREATE", "workflow", request.workflowKey(), "create workflow " + created.name());
        return R.ok(created);
    }

    @GetMapping("/{key}")
    public R<WorkflowResponse> get(@PathVariable("key") String key) {
        return R.ok(WorkflowResponse.from(managementService.get(key)));
    }

    @PutMapping("/{key}")
    public R<WorkflowResponse> update(@PathVariable("key") String key,
                                   @RequestBody UpdateWorkflowRequest request) {
        audit("UPDATE", "workflow", key, "update workflow draft");
        return R.ok(WorkflowResponse.from(
                managementService.update(key, request.name(), request.description(), request.definition())));
    }

    @DeleteMapping("/{key}")
    public R<Void> disable(@PathVariable("key") String key) {
        managementService.disable(key);
        audit("DISABLE", "workflow", key, "disable workflow");
        return R.ok();
    }

    @PostMapping("/{key}/publish")
    public R<WorkflowResponse> publish(@PathVariable("key") String key) {
        audit("PUBLISH", "workflow", key, "publish workflow");
        return R.ok(WorkflowResponse.from(managementService.publish(key)));
    }

    @PostMapping("/{key}/run")
    public R<WorkflowRunResponse> run(@PathVariable("key") String key,
                                   @RequestBody(required = false) RunWorkflowRequest request) {
        var inputs = request == null ? null : request.inputs();
        audit("RUN", "workflow", key, "run workflow");
        return R.ok(WorkflowRunResponse.from(engine.execute(key, inputs)));
    }

    @GetMapping("/{key}/runs")
    public R<PageResult<WorkflowRunResponse>> listRuns(@PathVariable("key") String key, PageQuery pageQuery) {
        var list = managementService.listRuns(key).stream()
                .map(WorkflowRunResponse::from)
                .toList();
        return R.ok(PageUtils.page(list, pageQuery));
    }

    @GetMapping("/runs/{runId}")
    public R<WorkflowRunDetailResponse> getRun(@PathVariable("runId") String runId) {
        var run = WorkflowRunResponse.from(managementService.getRun(runId));
        var nodeRuns = managementService.getNodeRuns(runId).stream()
                .map(WorkflowNodeRunResponse::from)
                .toList();
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
