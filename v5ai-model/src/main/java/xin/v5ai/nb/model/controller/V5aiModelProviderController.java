package xin.v5ai.nb.model.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import xin.v5ai.nb.common.core.domain.R;
import xin.v5ai.nb.common.core.page.PageQuery;
import xin.v5ai.nb.common.core.page.PageResult;
import xin.v5ai.nb.common.core.validate.AddGroup;
import xin.v5ai.nb.common.core.validate.EditGroup;
import xin.v5ai.nb.common.web.core.BaseController;
import xin.v5ai.nb.model.UpdateProviderCommand;
import xin.v5ai.nb.model.domain.bo.ModelProviderBo;
import xin.v5ai.nb.model.domain.vo.ModelProviderResponse;
import xin.v5ai.nb.model.domain.vo.V5aiModelProviderVo;
import xin.v5ai.nb.model.service.IV5aiModelProviderService;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author ZYW
 * @since 2026-08-20
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/providers")
public class V5aiModelProviderController extends BaseController {

    private final IV5aiModelProviderService service;

    @PostMapping
    public R<Void> createProvider(@Validated(AddGroup.class) @RequestBody ModelProviderBo bo) {
        return toAjax(service.insertByBo(bo));
    }

    @GetMapping
    public R<PageResult<V5aiModelProviderVo>> listProviders(ModelProviderBo bo,PageQuery pageQuery) {
        return R.ok(service.queryPageList(bo, pageQuery));
    }

    @PutMapping("/{providerKey}")
    public R<ModelProviderResponse> updateProvider(@PathVariable("providerKey") String providerKey,
                                                   @Validated(EditGroup.class) @RequestBody ModelProviderBo request) {
        return R.ok(ModelProviderResponse.from(providerService.updateProvider(new UpdateProviderCommand(
                providerKey, request.name(), request.providerType(), request.enabled()))));
    }

    @DeleteMapping("/{providerKey}")
    public R<Void> deleteProvider(@PathVariable("providerKey") String providerKey) {
        providerService.deleteProvider(providerKey);
        return R.ok();
    }
}
