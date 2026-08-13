package xin.v5ai.nb.api.application;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xin.v5ai.nb.application.ApplicationManagementService;
import xin.v5ai.nb.application.CreateApplicationCommand;
import xin.v5ai.nb.application.PublishApplicationCommand;

import java.util.List;

@RestController
@RequestMapping("/api/admin/applications")
public class ApplicationController {
    private final ApplicationManagementService applicationManagementService;

    public ApplicationController(ApplicationManagementService applicationManagementService) {
        this.applicationManagementService = applicationManagementService;
    }

    @PostMapping
    public ApplicationResponse createApplication(@RequestBody CreateApplicationRequest request) {
        return ApplicationResponse.from(applicationManagementService.createApplication(
                new CreateApplicationCommand(request.appKey(), request.name(), request.modelId())));
    }

    @GetMapping
    public List<ApplicationResponse> listApplications() {
        return applicationManagementService.listApplications().stream()
                .map(ApplicationResponse::from)
                .toList();
    }

    @PostMapping("/{appKey}/publish")
    public void publishApplication(@PathVariable("appKey") String appKey,
                                   @RequestBody PublishApplicationRequest request) {
        applicationManagementService.publish(new PublishApplicationCommand(appKey, request.description()));
    }
}
