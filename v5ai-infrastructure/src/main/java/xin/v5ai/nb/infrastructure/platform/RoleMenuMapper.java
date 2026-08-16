package xin.v5ai.nb.infrastructure.platform;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

@Mapper
public interface RoleMenuMapper extends BaseMapper<RoleMenuEntity> {
    default void insertRoleMenus(String roleKey, List<Long> menuIds) {
        for (Long menuId : menuIds) {
            var entity = new RoleMenuEntity();
            entity.setRoleKey(roleKey);
            entity.setMenuId(menuId);
            insert(entity);
        }
    }

    default void deleteByRole(String roleKey) {
        delete(new LambdaQueryWrapper<RoleMenuEntity>().eq(RoleMenuEntity::getRoleKey, roleKey));
    }

    default void deleteByMenuId(Long menuId) {
        delete(new LambdaQueryWrapper<RoleMenuEntity>().eq(RoleMenuEntity::getMenuId, menuId));
    }

    default List<Long> selectMenuIdsByRole(String roleKey) {
        return selectList(new LambdaQueryWrapper<RoleMenuEntity>()
                        .eq(RoleMenuEntity::getRoleKey, roleKey))
                .stream().map(RoleMenuEntity::getMenuId).toList();
    }

    default List<Long> selectMenuIdsByRoles(Collection<String> roleKeys) {
        return selectList(new LambdaQueryWrapper<RoleMenuEntity>()
                        .in(RoleMenuEntity::getRoleKey, roleKeys))
                .stream().map(RoleMenuEntity::getMenuId).distinct().toList();
    }
}
