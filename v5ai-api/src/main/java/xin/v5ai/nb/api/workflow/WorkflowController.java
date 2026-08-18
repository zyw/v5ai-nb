package xin.v5ai.nb.api.workflow;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
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
    public List<WorkflowResponse> list() {
        return managementService.list().stream()
                .map(WorkflowResponse::from)
                .toList();
    }

    @PostMapping
    public WorkflowResponse create(@RequestBody CreateWorkflowRequest request) {
        var created = WorkflowResponse.from(
                managementService.create(request.workflowKey(), request.name(), request.description()));
        audit("CREATE", "workflow", request.workflowKey(), "create workflow " + created.name());
        return created;
    }

    @GetMapping("/{key}")
    public WorkflowResponse get(@PathVariable("key") String key) {
        return WorkflowResponse.from(managementService.get(key));
    }

    @PutMapping("/{key}")
    public WorkflowResponse update(@PathVariable("key") String key,
                                   @RequestBody UpdateWorkflowRequest request) {
        audit("UPDATE", "workflow", key, "update workflow draft");
        return WorkflowResponse.from(
                managementService.update(key, request.name(), request.description(), request.definition()));
    }

    @DeleteMapping("/{key}")
    public void disable(@PathVariable("key") String key) {
        managementService.disable(key);
        audit("DISABLE", "workflow", key, "disable workflow");
    }

    @PostMapping("/{key}/publish")
    public WorkflowResponse publish(@PathVariable("key") String key) {
        audit("PUBLISH", "workflow", key, "publish workflow");
        return WorkflowResponse.from(managementService.publish(key));
    }

    @PostMapping("/{key}/run")
    public WorkflowRunResponse run(@PathVariable("key") String key,
                                   @RequestBody(required = false) RunWorkflowRequest request) {
        var inputs = request == null ? null : request.inputs();
        audit("RUN", "workflow", key, "run workflow");
        return WorkflowRunResponse.from(engine.execute(key, inputs));
    }

    @GetMapping("/{key}/runs")
    public List<WorkflowRunResponse> listRuns(@PathVariable("key") String key) {
        return managementService.listRuns(key).stream()
                .map(WorkflowRunResponse::from)
                .toList();
    }

    @GetMapping("/runs/{runId}")
    public WorkflowRunDetailResponse getRun(@PathVariable("runId") String runId) {
        var run = WorkflowRunResponse.from(managementService.getRun(runId));
        var nodeRuns = managementService.getNodeRuns(runId).stream()
                .map(WorkflowNodeRunResponse::from)
                .toList();
        return new WorkflowRunDetailResponse(run, nodeRuns);
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
