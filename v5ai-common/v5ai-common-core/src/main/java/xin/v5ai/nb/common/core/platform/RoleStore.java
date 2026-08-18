package xin.v5ai.nb.common.core.platform;

import java.util.List;

/**
 * 角色定义存储：角色 CRUD + 占用统计。
 */
public interface RoleStore {
    List<Role> list();

    Role findByKey(String roleKey);

    Role create(String roleKey, String roleName, String description, String dataScope);

    Role update(String roleKey, String roleName, String description, String dataScope);

    void delete(String roleKey);

    /** 当前被多少用户分配了该角色（用于删除保护）。 */
    long countUsers(String roleKey);

    /** 按用户 ID 返回其分配的角色详情（含数据范围）。 */
    List<Role> selectRolesByUserId(Long userId);
}
