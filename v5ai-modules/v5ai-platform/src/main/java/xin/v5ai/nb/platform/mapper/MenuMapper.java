package xin.v5ai.nb.platform.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.github.yulichang.base.MPJBaseMapper;
import org.apache.ibatis.annotations.Mapper;
import xin.v5ai.nb.common.core.constant.SystemConstants;
import xin.v5ai.nb.common.mybatis.core.mapper.BaseMapperPlus;
import xin.v5ai.nb.common.mybatis.core.query.QueryBuilder;
import xin.v5ai.nb.platform.domain.Menu;
import xin.v5ai.nb.platform.domain.Role;
import xin.v5ai.nb.platform.domain.RoleMenu;
import xin.v5ai.nb.platform.domain.UserRole;
import xin.v5ai.nb.platform.domain.vo.MenuVo;

import java.time.Instant;
import java.util.List;

@Mapper
public interface MenuMapper extends BaseMapperPlus<Menu, MenuVo>, MPJBaseMapper<Menu> {
    default List<Menu> selectAll() {
        return selectList(new LambdaQueryWrapper<Menu>().orderByAsc(Menu::getSortOrder));
    }

    default Menu selectByCode(String code) {
        return selectOne(new LambdaQueryWrapper<Menu>().eq(Menu::getCode, code));
    }

    default Menu selectByRouteName(String routeName) {
        return selectOne(new LambdaQueryWrapper<Menu>().eq(Menu::getRouteName, routeName));
    }

    default long countChildren(Long parentId) {
        return selectCount(new LambdaQueryWrapper<Menu>().eq(Menu::getParentId, parentId));
    }

    default int updateMeta(Long id, Long parentId, String name, String type, String routeName,
                           String path, String icon, Integer sortOrder, Boolean visible) {
        return update(null, new LambdaUpdateWrapper<Menu>()
                .eq(Menu::getId, id)
                .set(Menu::getParentId, parentId)
                .set(Menu::getName, name)
                .set(Menu::getType, type)
                .set(Menu::getRouteName, routeName)
                .set(Menu::getPath, path)
                .set(Menu::getIcon, icon)
                .set(Menu::getSortOrder, sortOrder)
                .set(Menu::getVisible, visible)
                .set(Menu::getUpdatedAt, Instant.now()));
    }


    /**
     * 查询全部正常状态的目录和菜单
     *
     * @return 菜单列表
     */
    default List<Menu> selectMenuTreeAll() {
        return this.lambda()
                .in(Menu::getType, SystemConstants.TYPE_DIR, SystemConstants.TYPE_MENU)
                .eq(Menu::getVisible, true)
                .orderByAsc(Menu::getParentId)
                .orderByAsc(Menu::getSortOrder)
                .list();
    }

    /**
     * 根据用户ID查询正常状态的目录和菜单
     *
     * @param userId 用户ID
     * @return 菜单列表
     */
    default List<Menu> selectMenuTreeByUserId(Long userId) {
        return this.selectJoinList(Menu.class, QueryBuilder.lambdaJoin("m", Menu.class)
                .distinct()
                .selectAll(Menu.class)
                .leftJoin(RoleMenu.class, "srm", RoleMenu::getMenuId, Menu::getId)
                .leftJoin(UserRole.class, "sur", UserRole::getRoleKey, RoleMenu::getRoleKey)
                .leftJoin(Role.class, "sr", Role::getRoleKey, RoleMenu::getRoleKey)
                .eq("sur", UserRole::getUserId, userId)
//                .eq("sr", Role::getStatus, SystemConstants.NORMAL)
                .in("m", Menu::getType, SystemConstants.TYPE_DIR, SystemConstants.TYPE_MENU)
                .eq("m", Menu::getVisible, true)
                .orderByAsc("m", Menu::getParentId)
                .orderByAsc("m", Menu::getSortOrder)
                .build());
    }
}
