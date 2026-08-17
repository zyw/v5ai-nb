package xin.v5ai.nb.infrastructure.platform;

import org.springframework.stereotype.Repository;
import xin.v5ai.nb.common.core.platform.Menu;
import xin.v5ai.nb.common.core.platform.MenuStore;

import java.time.Instant;
import java.util.*;

@Repository
public class MyBatisMenuStore implements MenuStore {
    private final MenuMapper menuMapper;
    private final RoleMenuMapper roleMenuMapper;

    public MyBatisMenuStore(MenuMapper menuMapper, RoleMenuMapper roleMenuMapper) {
        this.menuMapper = menuMapper;
        this.roleMenuMapper = roleMenuMapper;
    }

    @Override
    public List<Menu> list() {
        return menuMapper.selectAll().stream().map(this::toDomain).toList();
    }

    @Override
    public Menu findById(Long id) {
        return toDomain(menuMapper.selectById(id));
    }

    @Override
    public Menu findByCode(String code) {
        return toDomain(menuMapper.selectByCode(code));
    }

    @Override
    public Menu create(Long parentId, String code, String name, String type, String routeName,
                       String path, String icon, int sortOrder, boolean visible) {
        var entity = new MenuEntity();
        entity.setParentId(parentId);
        entity.setCode(code);
        entity.setName(name);
        entity.setType(type);
        entity.setRouteName(routeName);
        entity.setPath(path);
        entity.setIcon(icon);
        entity.setSortOrder(sortOrder);
        entity.setVisible(visible);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        menuMapper.insert(entity);
        return toDomain(entity);
    }

    @Override
    public Menu update(Long id, Long parentId, String name, String type, String routeName,
                       String path, String icon, int sortOrder, boolean visible) {
        menuMapper.updateMeta(id, parentId, name, type, routeName, path, icon, sortOrder, visible);
        return findById(id);
    }

    @Override
    public void delete(Long id) {
        roleMenuMapper.deleteByMenuId(id);
        menuMapper.deleteById(id);
    }

    @Override
    public long countChildren(Long id) {
        return menuMapper.countChildren(id);
    }

    @Override
    public List<Long> listMenuIdsByRole(String roleKey) {
        return roleMenuMapper.selectMenuIdsByRole(roleKey);
    }

    @Override
    public void replaceRoleMenus(String roleKey, List<Long> menuIds) {
        roleMenuMapper.deleteByRole(roleKey);
        if (menuIds != null && !menuIds.isEmpty()) {
            roleMenuMapper.insertRoleMenus(roleKey, menuIds);
        }
    }

    @Override
    public List<Menu> listByRoleKeys(List<String> roleKeys) {
        if (roleKeys == null || roleKeys.isEmpty()) {
            return List.of();
        }
        var all = list();
        Map<Long, Menu> byId = new HashMap<>();
        for (Menu menu : all) {
            byId.put(menu.id(), menu);
        }
        var assignedIds = roleMenuMapper.selectMenuIdsByRoles(roleKeys);

        Set<Long> reachable = new HashSet<>();
        for (Long id : assignedIds) {
            Long cursor = id;
            while (cursor != null) {
                if (!reachable.add(cursor)) {
                    break;
                }
                var node = byId.get(cursor);
                cursor = node == null ? null : node.parentId();
            }
        }

        return all.stream()
                .filter(menu -> reachable.contains(menu.id()))
                .sorted(Comparator.comparingInt(Menu::sortOrder).thenComparing(m -> m.id() == null ? 0 : m.id()))
                .toList();
    }

    private Menu toDomain(MenuEntity entity) {
        if (entity == null) {
            return null;
        }
        return new Menu(entity.getId(), entity.getParentId(), entity.getCode(), entity.getName(),
                entity.getType(), entity.getRouteName(), entity.getPath(), entity.getIcon(),
                entity.getSortOrder() == null ? 0 : entity.getSortOrder(),
                Boolean.TRUE.equals(entity.getVisible()), entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
