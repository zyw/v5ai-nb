//package xin.v5ai.nb.platform.mapper;
//
//import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
//import com.baomidou.mybatisplus.core.mapper.BaseMapper;
//import org.apache.ibatis.annotations.Mapper;
//import xin.v5ai.nb.platform.domain.RoleMenu;
//
//import java.util.Collection;
//import java.util.List;
//
//@Mapper
//public interface RoleMenuMapper extends BaseMapper<RoleMenu> {
//    default void insertRoleMenus(String roleKey, List<Long> menuIds) {
//        for (Long menuId : menuIds) {
//            var entity = new RoleMenu();
//            entity.setRoleKey(roleKey);
//            entity.setMenuId(menuId);
//            insert(entity);
//        }
//    }
//
//    default void deleteByRole(String roleKey) {
//        delete(new LambdaQueryWrapper<RoleMenu>().eq(RoleMenu::getRoleKey, roleKey));
//    }
//
//    default void deleteByMenuId(Long menuId) {
//        delete(new LambdaQueryWrapper<RoleMenu>().eq(RoleMenu::getMenuId, menuId));
//    }
//
//    default List<Long> selectMenuIdsByRole(String roleKey) {
//        return selectList(new LambdaQueryWrapper<RoleMenu>()
//                        .eq(RoleMenu::getRoleKey, roleKey))
//                .stream().map(RoleMenu::getMenuId).toList();
//    }
//
//    default List<Long> selectMenuIdsByRoles(Collection<String> roleKeys) {
//        return selectList(new LambdaQueryWrapper<RoleMenu>()
//                        .in(RoleMenu::getRoleKey, roleKeys))
//                .stream().map(RoleMenu::getMenuId).distinct().toList();
//    }
//}
