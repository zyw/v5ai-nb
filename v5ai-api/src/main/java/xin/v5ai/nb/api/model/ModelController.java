package xin.v5ai.nb.api.model;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xin.v5ai.nb.model.CreateModelCommand;
import xin.v5ai.nb.model.ModelManagementService;
import xin.v5ai.nb.model.UpdateModelCommand;

import java.util.List;

@RestController
@RequestMapping("/api/admin/models")
public class ModelController {
    private final ModelManagementService modelManagementService;

    public ModelController(ModelManagementService modelManagementService) {
        this.modelManagementService = modelManagementService;
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
