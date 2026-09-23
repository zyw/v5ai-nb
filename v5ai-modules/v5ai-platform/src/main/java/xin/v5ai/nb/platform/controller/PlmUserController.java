package xin.v5ai.nb.platform.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.crypto.digest.BCrypt;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import xin.v5ai.nb.common.core.constant.CacheNames;
import xin.v5ai.nb.common.core.constant.SystemConstants;
import xin.v5ai.nb.common.core.domain.PageResult;
import xin.v5ai.nb.common.core.domain.R;
import xin.v5ai.nb.common.core.utils.StreamUtils;
import xin.v5ai.nb.common.core.utils.StringUtils;
import xin.v5ai.nb.common.encrypt.annotation.ApiEncrypt;
import xin.v5ai.nb.common.log.annotation.Log;
import xin.v5ai.nb.common.log.enums.BusinessType;
import xin.v5ai.nb.common.mybatis.core.page.PageQuery;
import xin.v5ai.nb.common.redis.annotation.RepeatSubmit;
import xin.v5ai.nb.common.redis.utils.RedisUtils;
import xin.v5ai.nb.common.satoken.utils.LoginHelper;
import xin.v5ai.nb.common.web.core.BaseController;
import xin.v5ai.nb.platform.domain.bo.PlmRoleBo;
import xin.v5ai.nb.platform.domain.bo.PlmUserBo;
import xin.v5ai.nb.platform.domain.vo.PlmRoleVo;
import xin.v5ai.nb.platform.domain.vo.PlmUserInfoVo;
import xin.v5ai.nb.platform.domain.vo.PlmUserVo;
import xin.v5ai.nb.platform.service.IPlmRoleService;
import xin.v5ai.nb.platform.service.IPlmUserService;

import java.util.List;

/**
 * 用户信息
 *
 * @author Lion Li
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/admin/users")
public class PlmUserController extends BaseController {

    private final IPlmUserService userService;
    private final IPlmRoleService roleService;

    /**
     * 分页查询用户列表。
     *
     * @param user      用户查询条件
     * @param pageQuery 分页参数
     * @return 用户分页列表
     */
    @SaCheckPermission("system:user:list")
    @GetMapping("/list")
    public R<PageResult<PlmUserVo>> list(PlmUserBo user, PageQuery pageQuery) {
        return R.ok(userService.selectPageUserList(user, pageQuery));
    }

    /**
     * 导出符合条件的用户列表。
     *
     * @param user     用户查询条件
     * @param response HTTP 响应
     */
//    @Log(title = "用户管理", businessType = BusinessType.EXPORT)
//    @SaCheckPermission("system:user:export")
//    @PostMapping("/export")
//    public void export(SysUserBo user, HttpServletResponse response) {
//        List<SysUserExportVo> list = userService.selectUserExportList(user);
//        ExcelBuilder.of(list, SysUserExportVo.class).sheetName("用户数据").toResponse(response);
//    }

    /**
     * 导入数据
     *
     * @param file          导入文件
     * @param updateSupport 是否更新已存在数据
     */
//    @Log(title = "用户管理", businessType = BusinessType.IMPORT)
//    @SaCheckPermission("system:user:import")
//    @PostMapping(value = "/importData", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
//    public R<Void> importData(@RequestPart("file") MultipartFile file, boolean updateSupport) throws Exception {
//        ExcelResult<SysUserImportVo> result = ExcelBuilder.read(file.getInputStream(), SysUserImportVo.class)
//            .listener(new SysUserImportListener(updateSupport))
//            .doRead();
//        return R.ok(result.getAnalysis());
//    }

    /**
     * 导出用户导入模板。
     *
     * @param response HTTP 响应
     */
//    @PostMapping("/importTemplate")
//    public void importTemplate(HttpServletResponse response) {
//        ExcelBuilder.of(new ArrayList<>(), SysUserImportVo.class).sheetName("用户数据").toResponse(response);
//    }

    /**
     * 根据用户编号获取详细信息
     *
     * @param userId 用户ID
     * @return 用户详情、角色与岗位信息
     */
    @SaCheckPermission("system:user:query")
    @GetMapping(value = {"/", "/{userId}"})
    public R<PlmUserInfoVo> getInfo(@PathVariable(value = "userId", required = false) Long userId) {
        PlmUserInfoVo userInfoVo = new PlmUserInfoVo();
        if (ObjectUtil.isNotNull(userId)) {
            userService.checkUserDataScope(userId);
            PlmUserVo sysUser = userService.selectUserById(userId);
            userInfoVo.setUser(sysUser);
            userInfoVo.setRoleIds(roleService.selectRoleListByUserId(userId));
//            Long deptId = sysUser.getDeptId();
//            if (ObjectUtil.isNotNull(deptId)) {
//                SysPostBo postBo = new SysPostBo();
//                postBo.setDeptId(deptId);
//                userInfoVo.setPosts(postService.selectPostList(postBo));
//                userInfoVo.setPostIds(postService.selectPostListByUserId(userId));
//            }
        }
        PlmRoleBo roleBo = new PlmRoleBo();
        roleBo.setStatus(SystemConstants.NORMAL);
        List<PlmRoleVo> roles = roleService.selectRoleList(roleBo);
        userInfoVo.setRoles(LoginHelper.isSuperAdmin(userId) ? roles : StreamUtils.filter(roles, r -> !r.isSuperAdmin()));
        return R.ok(userInfoVo);
    }

    /**
     * 新增用户。
     *
     * @param user 用户新增参数
     * @return 操作结果
     */
    @SaCheckPermission("system:user:add")
    @Log(title = "用户管理", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping
    public R<Void> add(@Validated @RequestBody PlmUserBo user) {
//        deptService.checkDeptDataScope(user.getDeptId());
        if (!userService.checkUserNameUnique(user)) {
            return R.fail("新增用户'" + user.getUserName() + "'失败，登录账号已存在");
        } else if (StringUtils.isNotEmpty(user.getPhoneNumber()) && !userService.checkPhoneUnique(user)) {
            return R.fail("新增用户'" + user.getUserName() + "'失败，手机号码已存在");
        } else if (StringUtils.isNotEmpty(user.getEmail()) && !userService.checkEmailUnique(user)) {
            return R.fail("新增用户'" + user.getUserName() + "'失败，邮箱账号已存在");
        }
        user.setPassword(BCrypt.hashpw(user.getPassword()));
        return toAjax(userService.insertUser(user));
    }

    /**
     * 修改用户。
     *
     * @param user 用户编辑参数
     * @return 操作结果
     */
    @SaCheckPermission("system:user:edit")
    @Log(title = "用户管理", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping
    public R<Void> edit(@Validated @RequestBody PlmUserBo user) {
        userService.checkUserAllowed(user.getId());
        userService.checkUserDataScope(user.getId());
//        deptService.checkDeptDataScope(user.getDeptId());
        if (!userService.checkUserNameUnique(user)) {
            return R.fail("修改用户'" + user.getUserName() + "'失败，登录账号已存在");
        } else if (StringUtils.isNotEmpty(user.getPhoneNumber()) && !userService.checkPhoneUnique(user)) {
            return R.fail("修改用户'" + user.getUserName() + "'失败，手机号码已存在");
        } else if (StringUtils.isNotEmpty(user.getEmail()) && !userService.checkEmailUnique(user)) {
            return R.fail("修改用户'" + user.getUserName() + "'失败，邮箱账号已存在");
        }
        return toAjax(userService.updateUser(user));
    }

    /**
     * 删除用户
     *
     * @param userIds 用户ID数组
     * @return 操作结果
     */
    @SaCheckPermission("system:user:remove")
    @Log(title = "用户管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{userIds}")
    public R<Void> remove(@PathVariable Long[] userIds) {
        if (ArrayUtil.contains(userIds, LoginHelper.getUserId())) {
            return R.fail("当前用户不能删除");
        }
        return toAjax(userService.deleteUserByIds(userIds));
    }

    /**
     * 根据用户ID串批量获取用户基础信息
     *
     * @param userIds 用户ID串
     * @param deptId  部门ID
     * @return 用户基础信息列表
     */
    @SaCheckPermission("system:user:query")
    @GetMapping("/optionselect")
    public R<List<PlmUserVo>> optionselect(@RequestParam(required = false) Long[] userIds,
                                           @RequestParam(required = false) Long deptId) {
        return R.ok(userService.selectUserByIds(ArrayUtil.isEmpty(userIds) ? null : List.of(userIds), deptId));
    }

    /**
     * 重置指定用户密码。
     *
     * @param user 用户参数
     * @return 操作结果
     */
    @ApiEncrypt
    @SaCheckPermission("system:user:resetPwd")
    @Log(title = "用户管理", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping("/resetPwd")
    public R<Void> resetPwd(@RequestBody PlmUserBo user) {
        userService.checkUserAllowed(user.getId());
        userService.checkUserDataScope(user.getId());
        user.setPassword(BCrypt.hashpw(user.getPassword()));
        return toAjax(userService.resetUserPwd(user.getId(), user.getPassword()));
    }

    /**
     * 修改用户状态。
     *
     * @param user 用户参数
     * @return 操作结果
     */
    @SaCheckPermission("system:user:edit")
    @Log(title = "用户管理", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping("/changeStatus")
    public R<Void> changeStatus(@RequestBody PlmUserBo user) {
        userService.checkUserAllowed(user.getId());
        userService.checkUserDataScope(user.getId());
        return toAjax(userService.updateUserStatus(user.getId(), user.getStatus()));
    }

    /**
     * 解锁用户
     *
     * @param userId 用户ID
     * @return 操作结果
     */
    @SaCheckPermission("system:user:edit")
    @Log(title = "用户解锁", businessType = BusinessType.OTHER)
    @RepeatSubmit()
    @GetMapping("/unlock/{userId}")
    public R<Void> unlock(@PathVariable Long userId) {
        PlmUserVo user = userService.selectUserById(userId);
        if (ObjectUtil.isNull(user)) {
            return R.fail("用户不存在");
        }
        String loginName = CacheNames.PWD_ERR_CNT_KEY + user.getUserName();
        if (RedisUtils.hasKey(loginName)) {
            RedisUtils.deleteObject(loginName);
        }
        return R.ok();
    }

    /**
     * 根据用户编号获取授权角色
     *
     * @param userId 用户ID
     * @return 用户及其可授权角色信息
     */
    @SaCheckPermission("system:user:query")
    @GetMapping("/authRole/{userId}")
    public R<PlmUserInfoVo> authRole(@PathVariable Long userId) {
        userService.checkUserDataScope(userId);
        PlmUserVo user = userService.selectUserById(userId);
        List<PlmRoleVo> roles = roleService.selectRolesAuthByUserId(userId);
        PlmUserInfoVo userInfoVo = new PlmUserInfoVo();
        userInfoVo.setUser(user);
        userInfoVo.setRoles(LoginHelper.isSuperAdmin(userId) ? roles : StreamUtils.filter(roles, r -> !r.isSuperAdmin()));
        return R.ok(userInfoVo);
    }

    /**
     * 用户授权角色
     *
     * @param userId  用户Id
     * @param roleIds 角色ID串
     * @return 操作结果
     */
    @SaCheckPermission("system:user:edit")
    @Log(title = "用户管理", businessType = BusinessType.GRANT)
    @RepeatSubmit()
    @PutMapping("/authRole")
    public R<Void> insertAuthRole(Long userId, Long[] roleIds) {
        userService.checkUserDataScope(userId);
        userService.insertUserAuth(userId, roleIds);
        return R.ok();
    }

}
