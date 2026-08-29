package xin.v5ai.nb.platform.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import xin.v5ai.nb.common.core.domain.PageResult;
import xin.v5ai.nb.common.core.domain.R;
import xin.v5ai.nb.common.core.validate.AddGroup;
import xin.v5ai.nb.common.core.validate.EditGroup;
import xin.v5ai.nb.common.log.annotation.Log;
import xin.v5ai.nb.common.log.enums.BusinessType;
import xin.v5ai.nb.common.mybatis.core.page.PageQuery;
import xin.v5ai.nb.common.redis.annotation.RepeatSubmit;
import xin.v5ai.nb.common.web.core.BaseController;
import xin.v5ai.nb.platform.domain.bo.PlmClientBo;
import xin.v5ai.nb.platform.domain.vo.PlmClientVo;
import xin.v5ai.nb.platform.service.IPlmClientService;

import java.util.List;

/**
 * 客户端管理
 *
 * @author Michelle.Chung
 * @date 2023-06-18
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/admin/client")
public class PlmClientController extends BaseController {

    private final IPlmClientService sysClientService;

    /**
     * 分页查询客户端管理列表。
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 客户端分页数据
     */
    @SaCheckPermission("system:client:list")
    @GetMapping("/list")
    public R<PageResult<PlmClientVo>> list(PlmClientBo bo, PageQuery pageQuery) {
        return R.ok(sysClientService.queryPageList(bo, pageQuery));
    }

    /**
     * 导出客户端管理列表，便于离线审计与配置核查。
     *
     * @param bo       查询条件
     * @param response 响应流
     */
//    @SaCheckPermission("system:client:export")
//    @Log(title = "客户端管理", businessType = BusinessType.EXPORT)
//    @PostMapping("/export")
//    public void export(PlmClientBo bo, HttpServletResponse response) {
//        List<PlmClientVo> list = sysClientService.queryList(bo);
//        ExcelBuilder.of(list, PlmClientVo.class).sheetName("客户端管理").toResponse(response);
//    }

    /**
     * 获取单个客户端的详细配置信息。
     *
     * @param id 主键
     * @return 客户端详情
     */
    @SaCheckPermission("system:client:query")
    @GetMapping("/{id}")
    public R<PlmClientVo> getInfo(@NotNull(message = "主键不能为空")
                                  @PathVariable Long id) {
        return R.ok(sysClientService.queryById(id));
    }

    /**
     * 新增客户端配置，入库前先校验客户端 key 是否唯一。
     *
     * @param bo 客户端信息
     * @return 操作结果
     */
    @SaCheckPermission("system:client:add")
    @Log(title = "客户端管理", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping()
    public R<Void> add(@Validated(AddGroup.class) @RequestBody PlmClientBo bo) {
        if (!sysClientService.checkClickKeyUnique(bo)) {
            return R.fail("新增客户端'" + bo.getClientKey() + "'失败，客户端key已存在");
        }
        return toAjax(sysClientService.insertByBo(bo));
    }

    /**
     * 修改客户端配置，避免重复占用同一个客户端 key。
     *
     * @param bo 客户端信息
     * @return 操作结果
     */
    @SaCheckPermission("system:client:edit")
    @Log(title = "客户端管理", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping()
    public R<Void> edit(@Validated(EditGroup.class) @RequestBody PlmClientBo bo) {
        if (!sysClientService.checkClickKeyUnique(bo)) {
            return R.fail("修改客户端'" + bo.getClientKey() + "'失败，客户端key已存在");
        }
        return toAjax(sysClientService.updateByBo(bo));
    }

    /**
     * 修改客户端启停状态。
     *
     * @param bo 客户端状态信息
     * @return 操作结果
     */
    @SaCheckPermission("system:client:edit")
    @Log(title = "客户端管理", businessType = BusinessType.UPDATE)
    @PutMapping("/changeStatus")
    public R<Void> changeStatus(@RequestBody PlmClientBo bo) {
        return toAjax(sysClientService.updateClientStatus(bo.getClientId(), bo.getStatus()));
    }

    /**
     * 批量删除客户端配置。
     *
     * @param ids 主键串
     * @return 操作结果
     */
    @SaCheckPermission("system:client:remove")
    @Log(title = "客户端管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@NotEmpty(message = "主键不能为空")
                          @PathVariable Long[] ids) {
        return toAjax(sysClientService.deleteWithValidByIds(List.of(ids), true));
    }
}
