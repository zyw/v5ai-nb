package xin.v5ai.nb.api.model;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xin.v5ai.nb.model.CreateProviderCommand;
import xin.v5ai.nb.model.ModelManagementService;
import xin.v5ai.nb.model.UpdateProviderCommand;

import java.util.List;

@RestController
@RequestMapping("/api/admin/providers")
public class ModelProviderController {
    private final ModelManagementService modelManagementService;

    public ModelProviderController(ModelManagementService modelManagementService) {
        this.modelManagementService = modelManagementService;
    }

    @PostMapping
    public ModelProviderResponse createProvider(@RequestBody CreateProviderRequest request) {
        return ModelProviderResponse.from(modelManagementService.createProvider(new CreateProviderCommand(
                request.providerKey(), request.name(), request.providerType(), request.enabled())));
    }

    @GetMapping
    public List<ModelProviderResponse> listProviders() {
        return modelManagementService.listProviders().stream()
                .map(ModelProviderResponse::from)
                .toList();
    }

    @PutMapping("/{providerKey}")
    public ModelProviderResponse updateProvider(@PathVariable("providerKey") String providerKey,
                                                @RequestBody UpdateProviderRequest request) {
        return ModelProviderResponse.from(modelManagementService.updateProvider(new UpdateProviderCommand(
                providerKey, request.name(), request.providerType(), request.enabled())));
    }

    @DeleteMapping("/{providerKey}")
    public void deleteProvider(@PathVariable("providerKey") String providerKey) {
        modelManagementService.deleteProvider(providerKey);
    }
}
