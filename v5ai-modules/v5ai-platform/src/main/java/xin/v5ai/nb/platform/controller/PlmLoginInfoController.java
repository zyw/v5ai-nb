package xin.v5ai.nb.platform.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.lock.annotation.Lock4j;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import xin.v5ai.nb.common.core.constant.CacheNames;
import xin.v5ai.nb.common.core.domain.PageResult;
import xin.v5ai.nb.common.core.domain.R;
import xin.v5ai.nb.common.log.annotation.Log;
import xin.v5ai.nb.common.log.enums.BusinessType;
import xin.v5ai.nb.common.mybatis.core.page.PageQuery;
import xin.v5ai.nb.common.redis.annotation.RepeatSubmit;
import xin.v5ai.nb.common.redis.utils.RedisUtils;
import xin.v5ai.nb.common.web.core.BaseController;
import xin.v5ai.nb.platform.domain.bo.PlmLoginInfoBo;
import xin.v5ai.nb.platform.domain.vo.PlmLoginInfoVo;
import xin.v5ai.nb.platform.service.IPlmLoginInfoService;

/**
 * <p>
 * 系统访问记录 前端控制器
 * </p>
 *
 * @author ZYW
 * @since 2026-08-24
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/login/log")
public class PlmLoginInfoController extends BaseController {

    private final IPlmLoginInfoService loginInfoService;

    /**
     * 分页查询系统访问记录。
     *
     * @param loginInfo 查询条件
     * @param pageQuery 分页参数
     * @return 登录日志分页结果
     */
    @SaCheckPermission("monitor:logininfo:list")
    @GetMapping("/list")
    public R<PageResult<PlmLoginInfoVo>> list(PlmLoginInfoBo loginInfo, PageQuery pageQuery) {
        return R.ok(loginInfoService.selectPageLoginInfoList(loginInfo, pageQuery));
    }

    /**
     * 导出系统访问记录列表。
     *
     * @param loginInfo 查询条件
     * @param response  HTTP 响应
     */
//    @Log(title = "登录日志", businessType = BusinessType.EXPORT)
//    @SaCheckPermission("monitor:logininfo:export")
//    @PostMapping("/export")
//    public void export(PlmLoginInfoBo loginInfo, HttpServletResponse response) {
//        List<PlmLoginInfoVo> list = loginInfoService.selectLoginInfoList(loginInfo);
//        ExcelBuilder.of(list, PlmLoginInfoVo.class).sheetName("登录日志").toResponse(response);
//    }

    /**
     * 批量删除登录日志
     *
     * @param infoIds 日志ids
     * @return 操作结果
     */
    @SaCheckPermission("monitor:logininfo:remove")
    @Log(title = "登录日志", businessType = BusinessType.DELETE)
    @DeleteMapping("/{infoIds}")
    public R<Void> remove(@PathVariable Long[] infoIds) {
        return toAjax(loginInfoService.deleteLoginInfoByIds(infoIds));
    }

    /**
     * 清空系统访问记录。
     *
     * @return 操作结果
     */
    @SaCheckPermission("monitor:logininfo:remove")
    @Log(title = "登录日志", businessType = BusinessType.CLEAN)
    @Lock4j
    @DeleteMapping("/clean")
    public R<Void> clean() {
        loginInfoService.cleanLoginInfo();
        return R.ok();
    }

    /**
     * 清除指定用户的登录失败锁定状态。
     *
     * @param userName 用户名
     * @return 操作结果
     */
    @SaCheckPermission("monitor:logininfo:unlock")
    @Log(title = "账户解锁", businessType = BusinessType.OTHER)
    @RepeatSubmit()
    @GetMapping("/unlock/{userName}")
    public R<Void> unlock(@PathVariable("userName") String userName) {
        String loginName = CacheNames.PWD_ERR_CNT_KEY + userName;
        if (RedisUtils.hasKey(loginName)) {
            RedisUtils.deleteObject(loginName);
        }
        return R.ok();
    }
}
