package xin.v5ai.nb.model.controller;

import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import xin.v5ai.nb.common.core.domain.dto.OptionDTO;
import xin.v5ai.nb.common.core.domain.R;
import xin.v5ai.nb.common.core.page.PageResult;
import xin.v5ai.nb.common.core.validate.AddGroup;
import xin.v5ai.nb.common.core.validate.EditGroup;
import xin.v5ai.nb.common.mybatis.core.page.PageQuery;
import xin.v5ai.nb.common.web.core.BaseController;
import xin.v5ai.nb.model.domain.bo.ModelBo;
import xin.v5ai.nb.model.domain.vo.TestModelConnectionVo;
import xin.v5ai.nb.model.domain.vo.V5aiModelVo;
import xin.v5ai.nb.model.service.IV5aiModelService;

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
@RequestMapping("/api/admin/models")
public class V5aiModelController extends BaseController {

    private final IV5aiModelService service;

    @PostMapping
    public R<Void> createModel(@Validated(AddGroup.class) @RequestBody ModelBo bo) {
        return toAjax(service.insertByBo(bo));
    }

    @GetMapping
    public R<PageResult<V5aiModelVo>> listModels(ModelBo bo, PageQuery pageQuery) {
        return R.ok(service.queryPageList(bo, pageQuery));
    }

    @GetMapping("/options")
    public R<List<OptionDTO>> listModelOptions(ModelBo bo) {
        return R.ok(service.queryOptionList(bo));
    }

    @PutMapping
    public R<Void> updateModel(@Validated(EditGroup.class) @RequestBody ModelBo bo) {
        return toAjax(service.updateByBo(bo));
    }

    @DeleteMapping("/{id}")
    public R<Void> deleteModel(@NotEmpty(message = "主键不能为空")
                               @PathVariable("id") Long id) {
        return toAjax(service.deleteWithValidById(id, true));
    }

    @PostMapping("/{id}/test")
    public R<TestModelConnectionVo> testConnection(@PathVariable("id") Long id) {
        try {;
            return R.ok(service.testConnection(id));
        } catch (Exception exception) {
            return R.ok(TestModelConnectionVo.failed(id, exception.getMessage()));
        }
    }
}
