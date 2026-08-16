package xin.v5ai.nb.api.application;

import cn.dev33.satoken.stp.StpUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xin.v5ai.nb.application.ApplicationManagementService;
import xin.v5ai.nb.application.CreateApplicationCommand;
import xin.v5ai.nb.application.PublishApplicationCommand;
import xin.v5ai.nb.application.UpdateApplicationCommand;
import xin.v5ai.nb.common.platform.AuditRecorder;

import java.util.List;

@RestController
@RequestMapping("/api/admin/applications")
public class ApplicationController {
    private final ApplicationManagementService applicationManagementService;

    @Autowired(required = false)
    private AuditRecorder auditRecorder;

    public ApplicationController(ApplicationManagementService applicationManagementService) {
        this.applicationManagementService = applicationManagementService;
    }

    @PostMapping
    public ApplicationResponse createApplication(@RequestBody CreateApplicationRequest request) {
        var created = ApplicationResponse.from(applicationManagementService.createApplication(
                new CreateApplicationCommand(request.appKey(), request.name(), request.description(), request.modelId())));
        audit("CREATE", "application", request.appKey(), "create application");
        return created;
    }

    @GetMapping
    public List<ApplicationResponse> listApplications() {
        return applicationManagementService.listApplications().stream()
                .map(ApplicationResponse::from)
                .toList();
    }

    @PutMapping("/{appKey}")
    public ApplicationResponse updateApplication(@PathVariable("appKey") String appKey,
                                                 @RequestBody UpdateApplicationRequest request) {
        audit("UPDATE", "application", appKey, "update application");
        return ApplicationResponse.from(applicationManagementService.updateApplication(
                new UpdateApplicationCommand(appKey, request.name(), request.description(), request.modelId())));
    }

    @DeleteMapping("/{appKey}")
    public void disableApplication(@PathVariable("appKey") String appKey) {
        applicationManagementService.disable(appKey);
        audit("DISABLE", "application", appKey, "disable application");
    }

    @GetMapping("/{appKey}/versions")
    public List<ApplicationVersionResponse> listVersions(@PathVariable("appKey") String appKey) {
        return applicationManagementService.listVersions(appKey).stream()
                .map(ApplicationVersionResponse::from)
                .toList();
    }

    @PostMapping("/{appKey}/publish")
    public void publishApplication(@PathVariable("appKey") String appKey,
                                   @RequestBody PublishApplicationRequest request) {
        applicationManagementService.publish(new PublishApplicationCommand(appKey, request.description()));
        audit("PUBLISH", "application", appKey, "publish application");
    }

    private void audit(String action, String targetType, String targetId, String detail) {
        if (auditRecorder == null) {
            return;
        }
        String actor;
        try {
            actor = StpUtil.getLoginIdAsString();
        } catch (Exception exception) {
            actor = null;
        }
        auditRecorder.record(null, actor, action, targetType, targetId, detail, null);
    }
}
