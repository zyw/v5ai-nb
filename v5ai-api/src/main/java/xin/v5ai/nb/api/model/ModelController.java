package xin.v5ai.nb.api.model;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xin.v5ai.nb.application.ApplicationRepository;
import xin.v5ai.nb.common.exception.ErrorCode;
import xin.v5ai.nb.common.exception.V5aiException;
import xin.v5ai.nb.model.CreateModelCommand;
import xin.v5ai.nb.model.ModelManagementService;
import xin.v5ai.nb.model.UpdateModelCommand;

import java.util.List;

@RestController
@RequestMapping("/api/admin/models")
public class ModelController {
    private final ModelManagementService modelManagementService;
    private final ApplicationRepository applicationRepository;

    public ModelController(ModelManagementService modelManagementService, ApplicationRepository applicationRepository) {
        this.modelManagementService = modelManagementService;
        this.applicationRepository = applicationRepository;
    }

    @PostMapping
    public ModelResponse createModel(@RequestBody CreateModelRequest request) {
        return ModelResponse.from(modelManagementService.createModel(new CreateModelCommand(
                request.providerKey(), request.modelKey(), request.type(), request.credentials())));
    }

    @GetMapping
    public List<ModelResponse> listModels() {
        return modelManagementService.listModels().stream()
                .map(ModelResponse::from)
                .toList();
    }

    @PutMapping("/{id}")
    public ModelResponse updateModel(@PathVariable("id") Long id, @RequestBody UpdateModelRequest request) {
        return ModelResponse.from(modelManagementService.updateModel(new UpdateModelCommand(
                id, request.modelKey(), request.type(), request.credentials(),
                request.enabled() == null || request.enabled())));
    }

    @DeleteMapping("/{id}")
    public void deleteModel(@PathVariable("id") Long id) {
        if (modelManagementService.findModel(id) == null) {
            throw new V5aiException(ErrorCode.NOT_FOUND, "model does not exist: " + id);
        }
        boolean referenced = applicationRepository.list().stream()
                .anyMatch(application -> application.modelId() != null && application.modelId().equals(id));
        if (referenced) {
            throw new V5aiException(ErrorCode.CONFLICT,
                    "model is used by an application and cannot be deleted");
        }
        modelManagementService.deleteModel(id);
    }

    @PostMapping("/{id}/test")
    public TestConnectionResponse testConnection(@PathVariable("id") Long id) {
        try {
            return modelManagementService.testConnection(id)
                    ? TestConnectionResponse.ok(id)
                    : TestConnectionResponse.failed(id, "model did not respond");
        } catch (Exception exception) {
            return TestConnectionResponse.failed(id, exception.getMessage());
        }
    }
}
