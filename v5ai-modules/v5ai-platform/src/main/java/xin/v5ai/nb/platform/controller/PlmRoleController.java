package xin.v5ai.nb.platform.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import xin.v5ai.nb.common.core.domain.PageResult;
import xin.v5ai.nb.common.core.domain.R;
import xin.v5ai.nb.common.core.utils.SpringUtils;
import xin.v5ai.nb.common.log.annotation.Log;
import xin.v5ai.nb.common.log.enums.BusinessType;
import xin.v5ai.nb.common.mybatis.core.page.PageQuery;
import xin.v5ai.nb.common.redis.annotation.RepeatSubmit;
import xin.v5ai.nb.common.web.core.BaseController;
import xin.v5ai.nb.platform.domain.PlmUserRole;
import xin.v5ai.nb.platform.domain.bo.PlmRoleBo;
import xin.v5ai.nb.platform.domain.bo.PlmUserBo;
import xin.v5ai.nb.platform.domain.vo.PlmRoleVo;
import xin.v5ai.nb.platform.domain.vo.PlmUserVo;
import xin.v5ai.nb.platform.event.OnlineUserCleanEvent;
import xin.v5ai.nb.platform.service.IPlmRoleService;
import xin.v5ai.nb.platform.service.IPlmUserService;

import java.util.List;

/**
 * 角色信息
 *
 * @author Lion Li
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/admin/roles")
public class PlmRoleController extends BaseController {

    private final IPlmRoleService roleService;
    private final IPlmUserService userService;

    /**
     * 分页查询角色列表。
     *
     * @param role      查询条件
     * @param pageQuery 分页参数
     * @return 角色分页结果
     */
    @SaCheckPermission("system:role:list")
    @GetMapping("/list")
    public R<PageResult<PlmRoleVo>> list(PlmRoleBo role, PageQuery pageQuery) {
        return R.ok(roleService.selectPageRoleList(role, pageQuery));
    }

    /**
     * 导出角色信息列表。
     *
     * @param role     查询条件
     * @param response HTTP 响应
     */
//    @Log(title = "角色管理", businessType = BusinessType.EXPORT)
//    @SaCheckPermission("system:role:export")
//    @PostMapping("/export")
//    public void export(SysRoleBo role, HttpServletResponse response) {
//        List<SysRoleVo> list = roleService.selectRoleList(role);
//        ExcelBuilder.of(list, SysRoleVo.class).sheetName("角色数据").toResponse(response);
//    }

    /**
     * 根据角色编号获取详细信息
     *
     * @param roleId 角色ID
     * @return 角色详情
     */
    @SaCheckPermission("system:role:query")
    @GetMapping(value = "/{roleId}")
    public R<PlmRoleVo> getInfo(@PathVariable Long roleId) {
        roleService.checkRoleDataScope(roleId);
        return R.ok(roleService.selectRoleById(roleId));
    }

    /**
     * 新增角色。
     *
     * @param role 角色参数
     * @return 操作结果
     */
    @SaCheckPermission("system:role:add")
    @Log(title = "角色管理", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping
    public R<Void> add(@Validated @RequestBody PlmRoleBo role) {
        roleService.checkRoleAllowed(role);
        if (!roleService.checkRoleNameUnique(role)) {
            return R.fail("新增角色'" + role.getRoleName() + "'失败，角色名称已存在");
        } else if (!roleService.checkRoleKeyUnique(role)) {
            return R.fail("新增角色'" + role.getRoleName() + "'失败，角色权限已存在");
        }
        return toAjax(roleService.insertRole(role));

    }

    /**
     * 修改角色基础信息（不包含菜单权限、数据权限）。
     *
     * @param role 角色参数
     * @return 操作结果
     */
    @SaCheckPermission("system:role:edit")
    @Log(title = "角色管理", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping
    public R<Void> edit(@Validated @RequestBody PlmRoleBo role) {
        roleService.checkRoleAllowed(role);
        roleService.checkRoleDataScope(role.getId());
        if (!roleService.checkRoleNameUnique(role)) {
            return R.fail("修改角色'" + role.getRoleName() + "'失败，角色名称已存在");
        } else if (!roleService.checkRoleKeyUnique(role)) {
            return R.fail("修改角色'" + role.getRoleName() + "'失败，角色权限已存在");
        }

        if (roleService.updateRoleBaseInfo(role) > 0) {
            SpringUtils.context().publishEvent(OnlineUserCleanEvent.byRole(role.getId()));
            return R.ok();
        }
        return R.fail("修改角色'" + role.getRoleName() + "'失败，请联系管理员");
    }

    /**
     * 修改角色权限信息（菜单权限 + 数据权限）。
     *
     * @param role 角色参数
     * @return 操作结果
     */
    @SaCheckPermission("system:role:edit")
    @Log(title = "角色管理", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping("/permission")
    public R<Void> editPermission(@RequestBody PlmRoleBo role) {
        roleService.checkRoleAllowed(role);
        roleService.checkRoleDataScope(role.getId());
        if (roleService.updateRolePermission(role) > 0) {
            SpringUtils.context().publishEvent(OnlineUserCleanEvent.byRole(role.getId()));
            return R.ok();
        }
        return R.fail("修改角色'" + role.getRoleName() + "'权限失败，请联系管理员");
    }

    /**
     * 修改角色状态。
     *
     * @param role 角色参数
     * @return 操作结果
     */
    @SaCheckPermission("system:role:edit")
    @Log(title = "角色管理", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping("/changeStatus")
    public R<Void> changeStatus(@RequestBody PlmRoleBo role) {
        roleService.checkRoleAllowed(role);
        roleService.checkRoleDataScope(role.getId());
        if (roleService.updateRoleStatus(role.getId(), role.getStatus()) > 0) {
            SpringUtils.context().publishEvent(OnlineUserCleanEvent.byRole(role.getId()));
            return R.ok();
        }
        return R.fail("修改角色'" + role.getRoleName() + "'状态失败，请联系管理员");
    }

    /**
     * 删除角色
     *
     * @param roleIds 角色ID串
     * @return 操作结果
     */
    @SaCheckPermission("system:role:remove")
    @Log(title = "角色管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{roleIds}")
    public R<Void> remove(@PathVariable Long[] roleIds) {
        return toAjax(roleService.deleteRoleByIds(List.of(roleIds)));
    }

    /**
     * 获取角色选择框列表
     *
     * @param roleIds 角色ID串
     * @return 角色列表
     */
    @SaCheckPermission("system:role:query")
    @GetMapping("/optionselect")
    public R<List<PlmRoleVo>> optionselect(@RequestParam(required = false) Long[] roleIds) {
        return R.ok(roleService.selectRoleByIds(roleIds == null ? null : List.of(roleIds)));
    }

    /**
     * 查询已分配用户角色列表。
     *
     * @param user      查询条件
     * @param pageQuery 分页参数
     * @return 用户分页结果
     */
    @SaCheckPermission("system:role:list")
    @GetMapping("/authUser/allocatedList")
    public R<PageResult<PlmUserVo>> allocatedList(PlmUserBo user, PageQuery pageQuery) {
        return R.ok(userService.selectAllocatedList(user, pageQuery));
    }

    /**
     * 查询未分配用户角色列表。
     *
     * @param user      查询条件
     * @param pageQuery 分页参数
     * @return 用户分页结果
     */
    @SaCheckPermission("system:role:list")
    @GetMapping("/authUser/unallocatedList")
    public R<PageResult<PlmUserVo>> unallocatedList(PlmUserBo user, PageQuery pageQuery) {
        return R.ok(userService.selectUnallocatedList(user, pageQuery));
    }

    /**
     * 取消授权用户。
     *
     * @param userRole 用户角色关系
     * @return 操作结果
     */
    @SaCheckPermission("system:role:edit")
    @Log(title = "角色管理", businessType = BusinessType.GRANT)
    @RepeatSubmit()
    @PutMapping("/authUser/cancel")
    public R<Void> cancelAuthUser(@RequestBody PlmUserRole userRole) {
        return toAjax(roleService.deleteAuthUser(userRole));
    }

    /**
     * 批量取消授权用户
     *
     * @param roleId  角色ID
     * @param userIds 用户ID串
     * @return 操作结果
     */
    @SaCheckPermission("system:role:edit")
    @Log(title = "角色管理", businessType = BusinessType.GRANT)
    @RepeatSubmit()
    @PutMapping("/authUser/cancelAll")
    public R<Void> cancelAuthUserAll(Long roleId, Long[] userIds) {
        return toAjax(roleService.deleteAuthUsers(roleId, List.of(userIds)));
    }

    /**
     * 批量选择用户授权
     *
     * @param roleId  角色ID
     * @param userIds 用户ID串
     * @return 操作结果
     */
    @SaCheckPermission("system:role:edit")
    @Log(title = "角色管理", businessType = BusinessType.GRANT)
    @RepeatSubmit()
    @PutMapping("/authUser/selectAll")
    public R<Void> selectAuthUserAll(Long roleId, Long[] userIds) {
        roleService.checkRoleDataScope(roleId);
        return toAjax(roleService.insertAuthUsers(roleId, List.of(userIds)));
    }

//    /**
//     * 获取对应角色部门树列表
//     *
//     * @param roleId 角色ID
//     * @return 角色部门树信息
//     */
//    @SaCheckPermission("system:role:list")
//    @GetMapping(value = "/deptTree/{roleId}")
//    public R<DeptTreeSelectVo> roleDeptTreeselect(@PathVariable("roleId") Long roleId) {
//        DeptTreeSelectVo selectVo = new DeptTreeSelectVo(
//            deptService.selectDeptListByRoleId(roleId),
//            deptService.selectDeptTreeList(new SysDeptBo()));
//        return R.ok(selectVo);
//    }

    /**
     * 角色部门列表树信息
     *
     * @param checkedKeys 选中部门列表
     * @param depts       下拉树结构列表
     */
//    public record DeptTreeSelectVo(Collection<Long> checkedKeys, List<Tree<Long>> depts) {
//    }

}
