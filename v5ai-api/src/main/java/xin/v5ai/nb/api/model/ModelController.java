package xin.v5ai.nb.api.model;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xin.v5ai.nb.model.CreateModelCommand;
import xin.v5ai.nb.model.ModelManagementService;

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
}
