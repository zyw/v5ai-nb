package xin.v5ai.nb.common.platform;

import java.util.List;

/**
 * 角色定义存储：角色 CRUD + 占用统计。
 */
public interface RoleStore {
    List<Role> list();

    Role findByKey(String roleKey);

    Role create(String roleKey, String name, String description);

    Role update(String roleKey, String name, String description);

    void delete(String roleKey);

    /** 当前被多少用户分配了该角色（用于删除保护）。 */
    long countUsers(String roleKey);
}
