package xin.v5ai.nb.common.core.platform;

import java.util.List;

/**
 * 菜单存储：菜单树 CRUD + 角色-菜单授权。
 */
public interface MenuStore {
    List<Menu> list();

    Menu findById(Long id);

    Menu findByCode(String code);

    Menu create(Long parentId, String code, String name, String type, String routeName,
                String path, String icon, int sortOrder, boolean visible);

    Menu update(Long id, Long parentId, String name, String type, String routeName,
                String path, String icon, int sortOrder, boolean visible);

    void delete(Long id);

    /** 直接子节点数量（用于删除保护）。 */
    long countChildren(Long id);

    /** 某角色已分配的菜单 id 集合。 */
    List<Long> listMenuIdsByRole(String roleKey);

    /** 覆盖某角色的菜单授权。 */
    void replaceRoleMenus(String roleKey, List<Long> menuIds);

    /**
     * 按角色集合返回可访问菜单（去重并补齐祖先 DIR 节点，保证树连通，按 sortOrder 排序）。
     */
    List<Menu> listByRoleKeys(List<String> roleKeys);
}
