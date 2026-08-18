package xin.v5ai.nb.api.model;

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
import xin.v5ai.nb.common.core.page.PageUtils;
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
    public R<ModelProviderResponse> createProvider(@RequestBody CreateProviderRequest request) {
        return R.ok(ModelProviderResponse.from(modelManagementService.createProvider(new CreateProviderCommand(
                request.providerKey(), request.name(), request.providerType(), request.enabled()))));
    }

    @GetMapping
    public R<PageResult<ModelProviderResponse>> listProviders(PageQuery pageQuery) {
        var list = modelManagementService.listProviders().stream()
                .map(ModelProviderResponse::from)
                .toList();
        return R.ok(PageUtils.page(list, pageQuery));
    }

    @PutMapping("/{providerKey}")
    public R<ModelProviderResponse> updateProvider(@PathVariable("providerKey") String providerKey,
                                                @RequestBody UpdateProviderRequest request) {
        return R.ok(ModelProviderResponse.from(modelManagementService.updateProvider(new UpdateProviderCommand(
                providerKey, request.name(), request.providerType(), request.enabled()))));
    }

    @DeleteMapping("/{providerKey}")
    public R<Void> deleteProvider(@PathVariable("providerKey") String providerKey) {
        modelManagementService.deleteProvider(providerKey);
        return R.ok();
    }
}
