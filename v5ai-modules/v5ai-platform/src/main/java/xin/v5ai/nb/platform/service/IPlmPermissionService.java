package xin.v5ai.nb.platform.service;

import xin.v5ai.nb.platform.api.domain.RoleDTO;

import java.util.*;

/**
 * 用户权限处理
 *
 * @author Lion Li
 */
public interface IPlmPermissionService {

    /**
     * 获取角色数据权限
     *
     * @param userId 用户id
     * @return 角色权限信息
     */
    Set<String> getRolePermission(Long userId);

    /**
     * 获取菜单数据权限
     *
     * @param userId 用户id
     * @return 菜单权限信息
     */
    Set<String> getMenuPermission(Long userId);

    /**
     * 根据角色列表构建数据权限角色映射
     *
     * @param roles 角色列表
     * @return key 为权限码 value 为命中的角色ID列表
     */
    Map<String, List<Long>> getDataScopeRoleMap(List<RoleDTO> roles);
}
