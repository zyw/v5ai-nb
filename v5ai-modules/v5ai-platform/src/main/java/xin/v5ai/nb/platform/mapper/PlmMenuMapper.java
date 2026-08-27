package xin.v5ai.nb.platform.mapper;

import cn.hutool.core.collection.CollUtil;
import com.github.yulichang.base.MPJBaseMapper;
import xin.v5ai.nb.common.core.constant.SystemConstants;
import xin.v5ai.nb.common.core.utils.StreamUtils;
import xin.v5ai.nb.common.core.utils.StringUtils;
import xin.v5ai.nb.common.mybatis.core.mapper.BaseMapperPlus;
import xin.v5ai.nb.common.mybatis.core.query.QueryBuilder;
import xin.v5ai.nb.platform.domain.PlmMenu;
import xin.v5ai.nb.platform.domain.PlmRole;
import xin.v5ai.nb.platform.domain.PlmRoleMenu;
import xin.v5ai.nb.platform.domain.PlmUserRole;
import xin.v5ai.nb.platform.domain.bo.PlmMenuBo;
import xin.v5ai.nb.platform.domain.vo.PlmMenuVo;
import xin.v5ai.nb.platform.domain.vo.PlmRoleMenuPermVo;

import java.util.*;

/**
 * 菜单权限Mapper接口
 *
 * @author zyw
 * @date 2026-08-26
 */
public interface PlmMenuMapper extends BaseMapperPlus<PlmMenu, PlmMenuVo>, MPJBaseMapper<PlmMenu> {
    /**
     * 根据用户ID查询权限
     *
     * @param userId 用户ID
     * @return 权限列表
     */
    default Set<String> selectMenuPermsByUserId(Long userId) {
        List<PlmMenu> list = this.selectJoinList(PlmMenu.class, QueryBuilder.lambdaJoin("m", PlmMenu.class)
                .distinct()
                .select(PlmMenu::getPerms)
                .leftJoin(PlmRoleMenu.class, "srm", PlmRoleMenu::getMenuId, PlmMenu::getId)
                .leftJoin(PlmUserRole.class, "sur", PlmUserRole::getRoleId, PlmRoleMenu::getRoleId)
                .leftJoin(PlmRole.class, "sr", PlmRole::getId, PlmRoleMenu::getRoleId)
                .eq("sur", PlmUserRole::getUserId, userId)
                .eq("sr", PlmRole::getStatus, SystemConstants.NORMAL)
                .isNotNull("m", PlmMenu::getPerms)
                .build());
        return new HashSet<>(StreamUtils.filter(StreamUtils.toList(list, PlmMenu::getPerms), StringUtils::isNotBlank));
    }

    /**
     * 根据角色ID查询权限
     *
     * @param roleId 角色ID
     * @return 权限列表
     */
    default Set<String> selectMenuPermsByRoleId(Long roleId) {
        List<PlmMenu> list = this.selectJoinList(PlmMenu.class, QueryBuilder.lambdaJoin("m", PlmMenu.class)
                .distinct()
                .select(PlmMenu::getPerms)
                .leftJoin(PlmRoleMenu.class, "srm", PlmRoleMenu::getMenuId, PlmMenu::getId)
                .leftJoin(PlmRole.class, "sr", PlmRole::getId, PlmRoleMenu::getRoleId)
                .eq("srm", PlmRoleMenu::getRoleId, roleId)
                .eq("sr", PlmRole::getStatus, SystemConstants.NORMAL)
                .isNotNull("m", PlmMenu::getPerms)
                .build());
        return new HashSet<>(StreamUtils.filter(StreamUtils.toList(list, PlmMenu::getPerms), StringUtils::isNotBlank));
    }

    /**
     * 根据角色ID列表批量查询权限
     *
     * @param roleIds 角色ID列表
     * @return 角色权限映射
     */
    default Map<Long, Set<String>> selectMenuPermsByRoleIds(Collection<Long> roleIds) {
        if (CollUtil.isEmpty(roleIds)) {
            return Map.of();
        }
        List<PlmRoleMenuPermVo> list = this.selectJoinList(PlmRoleMenuPermVo.class, QueryBuilder.lambdaJoin("m", PlmMenu.class)
                .distinct()
                .selectAs("srm", PlmRoleMenu::getRoleId, PlmRoleMenuPermVo::getRoleId)
                .selectAs(PlmMenu::getPerms, PlmRoleMenuPermVo::getPerms)
                .leftJoin(PlmRoleMenu.class, "srm", PlmRoleMenu::getMenuId, PlmMenu::getId)
                .leftJoin(PlmRole.class, "sr", PlmRole::getId, PlmRoleMenu::getRoleId)
                .in("srm", PlmRoleMenu::getRoleId, roleIds)
                .eq("sr", PlmRole::getStatus, SystemConstants.NORMAL)
                .isNotNull("m", PlmMenu::getPerms)
                .build());
        Map<Long, Set<String>> result = new LinkedHashMap<>();
        for (PlmRoleMenuPermVo item : list) {
            if (StringUtils.isBlank(item.getPerms())) {
                continue;
            }
            result.computeIfAbsent(item.getRoleId(), key -> new LinkedHashSet<>()).add(item.getPerms());
        }
        return result;
    }

    /**
     * 查询全部正常状态的目录和菜单
     *
     * @return 菜单列表
     */
    default List<PlmMenu> selectMenuTreeAll() {
        return this.lambda()
                .in(PlmMenu::getMenuType, SystemConstants.TYPE_DIR, SystemConstants.TYPE_MENU)
                .eq(PlmMenu::getStatus, SystemConstants.NORMAL)
                .orderByAsc(PlmMenu::getParentId)
                .orderByAsc(PlmMenu::getOrderNum)
                .list();
    }

    /**
     * 根据角色ID查询菜单树信息
     *
     * @param roleId            角色ID
     * @param menuCheckStrictly 菜单树选择项是否关联显示
     * @return 选中菜单列表
     */
    default List<Long> selectMenuListByRoleId(Long roleId, boolean menuCheckStrictly) {
        List<PlmMenu> menus = this.selectJoinList(PlmMenu.class, QueryBuilder.lambdaJoin("m", PlmMenu.class)
                .distinct()
                .select(PlmMenu::getId, PlmMenu::getParentId, PlmMenu::getOrderNum)
                .leftJoin(PlmRoleMenu.class, "srm", PlmRoleMenu::getMenuId, PlmMenu::getId)
                .leftJoin(PlmRole.class, "sr", PlmRole::getId, PlmRoleMenu::getRoleId)
                .eq("srm", PlmRoleMenu::getRoleId, roleId)
                .eq("sr", PlmRole::getStatus, SystemConstants.NORMAL)
                .orderByAsc("m", PlmMenu::getParentId)
                .orderByAsc("m", PlmMenu::getOrderNum)
                .build());
        Set<Long> parentIds = menuCheckStrictly ? new HashSet<>(StreamUtils.toList(menus, PlmMenu::getParentId)) : Collections.emptySet();
        return menus.stream()
                .map(PlmMenu::getId)
                .filter(menuId -> !parentIds.contains(menuId))
                .toList();
    }

    /**
     * 根据用户ID和查询条件查询菜单列表
     *
     * @param menu   菜单查询条件
     * @param userId 用户ID
     * @return 菜单列表
     */
    default List<PlmMenuVo> selectMenuListByUserId(PlmMenuBo menu, Long userId) {
        return this.selectJoinList(PlmMenuVo.class, QueryBuilder.lambdaJoin("m", PlmMenu.class)
                .distinct()
                .selectAll(PlmMenu.class)
                .leftJoin(PlmRoleMenu.class, "srm", PlmRoleMenu::getMenuId, PlmMenu::getId)
                .leftJoin(PlmUserRole.class, "sur", PlmUserRole::getRoleId, PlmRoleMenu::getRoleId)
                .leftJoin(PlmRole.class, "sr", PlmRole::getId, PlmRoleMenu::getRoleId)
                .eq("sur", PlmUserRole::getUserId, userId)
                .eq("sr", PlmRole::getStatus, SystemConstants.NORMAL)
                .likeIfText("m", PlmMenu::getMenuName, menu.getMenuName())
                .eqIfText("m", PlmMenu::getVisible, menu.getVisible())
                .eqIfText("m", PlmMenu::getStatus, menu.getStatus())
                .eqIfText("m", PlmMenu::getMenuType, menu.getMenuType())
                .eqIfPresent("m", PlmMenu::getParentId, menu.getParentId())
                .orderByAsc("m", PlmMenu::getParentId)
                .orderByAsc("m", PlmMenu::getOrderNum)
                .build());
    }

    /**
     * 根据用户ID查询正常状态的目录和菜单
     *
     * @param userId 用户ID
     * @return 菜单列表
     */
    default List<PlmMenu> selectMenuTreeByUserId(Long userId) {
        return this.selectJoinList(PlmMenu.class, QueryBuilder.lambdaJoin("m", PlmMenu.class)
                .distinct()
                .selectAll(PlmMenu.class)
                .leftJoin(PlmRoleMenu.class, "srm", PlmRoleMenu::getMenuId, PlmMenu::getId)
                .leftJoin(PlmUserRole.class, "sur", PlmUserRole::getRoleId, PlmRoleMenu::getRoleId)
                .leftJoin(PlmRole.class, "sr", PlmRole::getId, PlmRoleMenu::getRoleId)
                .eq("sur", PlmUserRole::getUserId, userId)
                .eq("sr", PlmRole::getStatus, SystemConstants.NORMAL)
                .in("m", PlmMenu::getMenuType, SystemConstants.TYPE_DIR, SystemConstants.TYPE_MENU)
                .eq("m", PlmMenu::getStatus, SystemConstants.NORMAL)
                .orderByAsc("m", PlmMenu::getParentId)
                .orderByAsc("m", PlmMenu::getOrderNum)
                .build());
    }
}