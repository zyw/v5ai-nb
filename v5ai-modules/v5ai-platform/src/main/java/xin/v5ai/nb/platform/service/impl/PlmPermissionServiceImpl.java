package xin.v5ai.nb.platform.service.impl;

import cn.hutool.core.collection.CollUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import xin.v5ai.nb.common.core.constant.SystemConstants;
import xin.v5ai.nb.common.core.service.PermissionService;
import xin.v5ai.nb.common.core.utils.StreamUtils;
import xin.v5ai.nb.common.satoken.utils.LoginHelper;
import xin.v5ai.nb.platform.api.domain.RoleDTO;
import xin.v5ai.nb.platform.service.IPlmMenuService;
import xin.v5ai.nb.platform.service.IPlmPermissionService;
import xin.v5ai.nb.platform.service.IPlmRoleService;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlmPermissionServiceImpl implements IPlmPermissionService, PermissionService {

    private final IPlmRoleService roleService;
    private final IPlmMenuService menuService;

    /**
     * 获取角色数据权限
     *
     * @param userId 用户id
     * @return 角色权限信息
     */
    @Override
    public Set<String> getRolePermission(Long userId) {
        Set<String> roles = new HashSet<>();
        // 管理员拥有所有权限
        if (LoginHelper.isSuperAdmin(userId)) {
            roles.add(SystemConstants.SUPER_ADMIN_ROLE_KEY);
        } else {
            roles.addAll(roleService.selectRolePermissionByUserId(userId));
        }
        return roles;
    }

    /**
     * 获取菜单数据权限
     *
     * @param userId 用户id
     * @return 菜单权限信息
     */
    @Override
    public Set<String> getMenuPermission(Long userId) {
        Set<String> perms = new HashSet<>();
        // 管理员拥有所有权限
        if (LoginHelper.isSuperAdmin(userId)) {
            perms.add("*:*:*");
        } else {
            perms.addAll(menuService.selectMenuPermsByUserId(userId));
        }
        return perms;
    }

    /**
     * 按权限标识汇总具备数据权限的角色集合。
     *
     * @param roles 角色传输对象列表
     * @return key 为权限标识、value 为拥有该权限的角色主键列表
     */
    @Override
    public Map<String, List<Long>> getDataScopeRoleMap(List<RoleDTO> roles) {
        if (CollUtil.isEmpty(roles)) {
            return Map.of();
        }
        List<Long> roleIds = StreamUtils.toList(roles, RoleDTO::getRoleId);
        Map<Long, Set<String>> permsRoleIds = menuService.selectMenuPermsByRoleIds(roleIds);
        Map<String, List<Long>> rolePermsMap = new LinkedHashMap<>();
        permsRoleIds.forEach((roleId, perms) ->
                perms.forEach(perm ->
                        rolePermsMap.computeIfAbsent(perm, key -> new ArrayList<>()).add(roleId)
                )
        );
        return rolePermsMap;
    }
}
