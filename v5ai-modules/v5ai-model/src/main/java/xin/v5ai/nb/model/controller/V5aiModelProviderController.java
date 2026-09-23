package xin.v5ai.nb.model.controller;

import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import xin.v5ai.nb.common.core.domain.R;
import xin.v5ai.nb.common.core.domain.PageResult;
import xin.v5ai.nb.common.core.validate.AddGroup;
import xin.v5ai.nb.common.core.validate.EditGroup;
import xin.v5ai.nb.common.mybatis.core.page.PageQuery;
import xin.v5ai.nb.common.web.core.BaseController;
import xin.v5ai.nb.model.domain.bo.ModelProviderBo;
import xin.v5ai.nb.model.domain.vo.V5aiModelProviderVo;
import xin.v5ai.nb.model.service.IV5aiModelProviderService;

import java.util.List;

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

    @GetMapping
    public R<PageResult<V5aiModelProviderVo>> listProviders(ModelProviderBo bo, PageQuery pageQuery) {
        return R.ok(service.queryPageList(bo, pageQuery));
    }

    @PostMapping
    public R<Void> createProvider(@Validated(AddGroup.class) @RequestBody ModelProviderBo bo) {
        return toAjax(service.insertByBo(bo));
    }

    @PutMapping
    public R<Void> updateProvider(@Validated(EditGroup.class) @RequestBody ModelProviderBo request) {
        return toAjax(service.updateByBo(request));
    }

    @DeleteMapping("/{ids}")
    public R<Void> deleteProvider(
            @NotEmpty(message = "主键不能为空")
            @PathVariable("ids") Long[] ids) {
        return toAjax(service.deleteWithValidByIds(List.of(ids), true));
    }
}
