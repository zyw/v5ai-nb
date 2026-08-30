package xin.v5ai.nb.rag.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xin.v5ai.nb.common.core.domain.PageResult;
import xin.v5ai.nb.common.core.domain.R;
import xin.v5ai.nb.common.core.validate.AddGroup;
import xin.v5ai.nb.common.core.validate.EditGroup;
import xin.v5ai.nb.common.log.annotation.Log;
import xin.v5ai.nb.common.log.enums.BusinessType;
import xin.v5ai.nb.common.mybatis.core.page.PageQuery;
import xin.v5ai.nb.common.web.core.BaseController;
import xin.v5ai.nb.rag.domain.bo.StoreInstanceBo;
import xin.v5ai.nb.rag.domain.vo.StoreInstanceVo;
import xin.v5ai.nb.rag.service.IStoreInstanceService;

import java.util.List;

/**
 * <p>
 * 存储实例前端控制器
 * </p>
 *
 * @author ZYW
 * @since 2026-08-30
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/store-instances")
public class StoreInstanceController extends BaseController {

    private final IStoreInstanceService storeInstanceService;

    /**
     * 查询存储实例列表
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 存储实例列表
     */
    @SaCheckPermission("rag:store:list")
    @GetMapping
    public R<PageResult<StoreInstanceVo>> list(StoreInstanceBo bo, PageQuery pageQuery) {
        return R.ok(storeInstanceService.queryPageList(bo, pageQuery));
    }

    /**
     * 查询存储实例详情
     *
     * @param id 主键
     * @return 存储实例详情
     */
    @SaCheckPermission("rag:store:query")
    @GetMapping("/{id}")
    public R<StoreInstanceVo> getInfo(@NotNull(message = "主键不能为空")
                                      @PathVariable("id") Long id) {
        return R.ok(storeInstanceService.queryById(id));
    }

    /**
     * 新增存储实例
     *
     * @param bo 存储实例参数
     * @return 操作结果
     */
    @SaCheckPermission("rag:store:add")
    @Log(title = "存储实例", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated(AddGroup.class) @RequestBody StoreInstanceBo bo) {
        return toAjax(storeInstanceService.insertByBo(bo));
    }

    /**
     * 修改存储实例
     *
     * @param bo 存储实例参数
     * @return 操作结果
     */
    @SaCheckPermission("rag:store:edit")
    @Log(title = "存储实例", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated(EditGroup.class) @RequestBody StoreInstanceBo bo) {
        return toAjax(storeInstanceService.updateByBo(bo));
    }

    /**
     * 删除存储实例
     *
     * @param ids 主键数组
     * @return 操作结果
     */
    @SaCheckPermission("rag:store:remove")
    @Log(title = "存储实例", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@NotNull(message = "主键不能为空")
                          @PathVariable("ids") Long[] ids) {
        return toAjax(storeInstanceService.deleteWithValidByIds(List.of(ids), true));
    }
}
