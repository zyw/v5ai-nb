package xin.v5ai.nb.common.core.service;

import xin.v5ai.nb.common.core.domain.RoleDTO;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 用户权限处理
 *
 * @author Lion Li
 */
public interface PermissionService {

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
     * 数据权限角色映射：key 为 dataScope 码，value 为具备该数据范围的角色 roleKey 列表。
     */
    default Map<String, List<String>> getDataScopeRoleMap(List<RoleDTO> roleList) {
        Map<String, List<String>> map = new LinkedHashMap<>();
        if (roleList == null) {
            return map;
        }
        for (RoleDTO role : roleList) {
            String dataScope = role.getDataScope() == null ? "1" : role.getDataScope();
            map.computeIfAbsent(dataScope, key -> new ArrayList<>()).add(role.getRoleKey());
        }
        return map;
    }
}
