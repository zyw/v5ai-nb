package xin.v5ai.nb.model.controller;

import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import xin.v5ai.nb.common.core.domain.PageResult;
import xin.v5ai.nb.common.core.domain.R;
import xin.v5ai.nb.common.core.domain.dto.OptionDTO;
import xin.v5ai.nb.common.core.validate.AddGroup;
import xin.v5ai.nb.common.core.validate.EditGroup;
import xin.v5ai.nb.common.log.annotation.Log;
import xin.v5ai.nb.common.log.enums.BusinessType;
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

    /**
     * 创建模型
     * @param bo 模型参数
     * @return 结果
     */
    @PostMapping
    @Log(title = "创建模型", businessType = BusinessType.INSERT)
    public R<Void> createModel(@Validated(AddGroup.class) @RequestBody ModelBo bo) {
        return toAjax(service.insertByBo(bo));
    }

    /**
     * 查询模型列表
     * @param bo 模型参数
     * @param pageQuery 分页参数
     * @return 模型列表
     */
    @GetMapping
    public R<PageResult<V5aiModelVo>> listModels(ModelBo bo, PageQuery pageQuery) {
        return R.ok(service.queryPageList(bo, pageQuery));
    }

    /**
     * 查询模型下拉列表
     * @param bo 模型参数
     * @return 模型列表
     */
    @GetMapping("/options")
    public R<List<OptionDTO>> listModelOptions(ModelBo bo) {
        return R.ok(service.queryOptionList(bo));
    }

    /**
     * 编辑模型
     * @param bo 模型参数
     * @return 结果
     */
    @PutMapping
    @Log(title = "编辑模型", businessType = BusinessType.UPDATE)
    public R<Void> updateModel(@Validated(EditGroup.class) @RequestBody ModelBo bo) {
        return toAjax(service.updateByBo(bo));
    }

    /**
     * 删除模型
     */
    @DeleteMapping("/{id}")
    @Log(title = "删除模型", businessType = BusinessType.DELETE)
    public R<Void> deleteModel(@NotNull(message = "主键不能为空")
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
