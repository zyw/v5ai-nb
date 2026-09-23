package xin.v5ai.nb.platform.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.lock.annotation.Lock4j;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import xin.v5ai.nb.common.core.domain.PageResult;
import xin.v5ai.nb.common.core.domain.R;
import xin.v5ai.nb.common.log.annotation.Log;
import xin.v5ai.nb.common.log.enums.BusinessType;
import xin.v5ai.nb.common.mybatis.core.page.PageQuery;
import xin.v5ai.nb.common.web.core.BaseController;
import xin.v5ai.nb.platform.domain.bo.PlmOperLogBo;
import xin.v5ai.nb.platform.domain.vo.PlmOperLogVo;
import xin.v5ai.nb.platform.service.IPlmOperLogService;

import java.util.List;

/**
 * <p>
 * 操作日志记录 前端控制器
 * </p>
 *
 * @author ZYW
 * @since 2026-08-24
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/oper/log")
public class PlmOperLogController extends BaseController {

    private final IPlmOperLogService operLogService;

    /**
     * 分页查询操作日志记录。
     *
     * @param operLog   查询条件
     * @param pageQuery 分页参数
     * @return 操作日志分页结果
     */
    @SaCheckPermission("monitor:operlog:list")
    @GetMapping("/list")
    public R<PageResult<PlmOperLogVo>> list(PlmOperLogBo operLog, PageQuery pageQuery) {
        return R.ok(operLogService.selectPageOperLogList(operLog, pageQuery));
    }

    /**
     * 导出操作日志记录列表。
     *
     * @param operLog  查询条件
     * @param response HTTP 响应
     */
//    @Log(title = "操作日志", businessType = BusinessType.EXPORT)
//    @SaCheckPermission("monitor:operlog:export")
//    @PostMapping("/export")
//    public void export(SysOperLogBo operLog, HttpServletResponse response) {
//        List<SysOperLogVo> list = operLogService.selectOperLogList(operLog);
//        ExcelBuilder.of(list, SysOperLogVo.class).sheetName("操作日志").toResponse(response);
//    }

    /**
     * 批量删除操作日志记录
     *
     * @param operIds 日志ids
     * @return 操作结果
     */
    @Log(title = "操作日志", businessType = BusinessType.DELETE)
    @SaCheckPermission("monitor:operlog:remove")
    @DeleteMapping("/{operIds}")
    public R<Void> remove(@PathVariable Long[] operIds) {
        return toAjax(operLogService.deleteOperLogByIds(operIds));
    }

    /**
     * 清空操作日志记录。
     *
     * @return 操作结果
     */
    @Log(title = "操作日志", businessType = BusinessType.CLEAN)
    @SaCheckPermission("monitor:operlog:remove")
    @Lock4j
    @DeleteMapping("/clean")
    public R<Void> clean() {
        operLogService.cleanOperLog();
        return R.ok();
    }
}
