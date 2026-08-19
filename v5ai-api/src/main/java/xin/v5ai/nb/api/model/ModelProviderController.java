package xin.v5ai.nb.api.model;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import xin.v5ai.nb.common.core.domain.R;
import xin.v5ai.nb.common.core.page.PageQuery;
import xin.v5ai.nb.common.core.page.PageResult;
import xin.v5ai.nb.common.core.page.PageUtils;
import xin.v5ai.nb.model.CreateProviderCommand;
import xin.v5ai.nb.model.UpdateProviderCommand;
import xin.v5ai.nb.model.service.IModelProviderService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/providers")
public class ModelProviderController {

    private final IModelProviderService providerService;

    @PostMapping
    public R<ModelProviderResponse> createProvider(@RequestBody CreateProviderRequest request) {
        return R.ok(ModelProviderResponse.from(providerService.createProvider(new CreateProviderCommand(
                request.providerKey(), request.name(), request.providerType(), request.enabled()))));
    }

    @GetMapping
    public R<PageResult<ModelProviderResponse>> listProviders(PageQuery pageQuery) {
        var list = providerService.listProviders().stream()
                .map(ModelProviderResponse::from)
                .toList();
        return R.ok(PageUtils.page(list, pageQuery));
    }

    @PutMapping("/{providerKey}")
    public R<ModelProviderResponse> updateProvider(@PathVariable("providerKey") String providerKey,
                                                @RequestBody UpdateProviderRequest request) {
        return R.ok(ModelProviderResponse.from(providerService.updateProvider(new UpdateProviderCommand(
                providerKey, request.name(), request.providerType(), request.enabled()))));
    }

    @DeleteMapping("/{providerKey}")
    public R<Void> deleteProvider(@PathVariable("providerKey") String providerKey) {
        providerService.deleteProvider(providerKey);
        return R.ok();
    }
}
