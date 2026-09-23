package xin.v5ai.nb.platform.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.tree.Tree;
import cn.hutool.core.util.ObjectUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xin.v5ai.nb.common.core.constant.Constants;
import xin.v5ai.nb.common.core.constant.SystemConstants;
import xin.v5ai.nb.common.core.utils.MapstructUtils;
import xin.v5ai.nb.common.core.utils.StringUtils;
import xin.v5ai.nb.common.core.utils.TreeBuildUtils;
import xin.v5ai.nb.common.satoken.utils.LoginHelper;
import xin.v5ai.nb.platform.domain.PlmMenu;
import xin.v5ai.nb.platform.domain.PlmRole;
import xin.v5ai.nb.platform.domain.PlmRoleMenu;
import xin.v5ai.nb.platform.domain.bo.PlmMenuBo;
import xin.v5ai.nb.platform.domain.vo.MetaVo;
import xin.v5ai.nb.platform.domain.vo.PlmMenuVo;
import xin.v5ai.nb.platform.domain.vo.RouterVo;
import xin.v5ai.nb.platform.mapper.PlmMenuMapper;
import xin.v5ai.nb.platform.mapper.PlmRoleMapper;
import xin.v5ai.nb.platform.mapper.PlmRoleMenuMapper;
import xin.v5ai.nb.platform.service.IPlmMenuService;

import java.util.*;

/**
 * 菜单权限Service业务层处理
 *
 * @author zyw
 * @date 2026-08-26
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class PlmMenuServiceImpl implements IPlmMenuService {

    private final PlmMenuMapper baseMapper;
    private final PlmRoleMapper roleMapper;
    private final PlmRoleMenuMapper roleMenuMapper;

    /**
     * 根据用户查询系统菜单列表
     *
     * @param userId 用户ID
     * @return 菜单列表
     */
    @Override
    public List<PlmMenuVo> selectMenuList(Long userId) {
        return selectMenuList(new PlmMenuBo(), userId);
    }

    /**
     * 查询系统菜单列表
     *
     * @param menu   菜单筛选条件
     * @param userId 当前查询的用户主键
     * @return 菜单列表
     */
    @Override
    public List<PlmMenuVo> selectMenuList(PlmMenuBo menu, Long userId) {
        // 管理员显示所有菜单信息 不是管理员 按用户id过滤菜单
        if (LoginHelper.isSuperAdmin(userId)) {
            return baseMapper.lambda()
                    .likeIfText(PlmMenu::getMenuName, menu.getMenuName())
                    .eqIfText(PlmMenu::getVisible, menu.getVisible())
                    .eqIfText(PlmMenu::getStatus, menu.getStatus())
                    .eqIfText(PlmMenu::getMenuType, menu.getMenuType())
                    .eqIfPresent(PlmMenu::getParentId, menu.getParentId())
                    .orderByAsc(PlmMenu::getParentId)
                    .orderByAsc(PlmMenu::getOrderNum)
                    .voList();
        }
        return baseMapper.selectMenuListByUserId(menu, userId);
    }

    /**
     * 根据用户ID查询权限
     *
     * @param userId 用户ID
     * @return 权限列表
     */
    @Override
    public Set<String> selectMenuPermsByUserId(Long userId) {
        return baseMapper.selectMenuPermsByUserId(userId);
    }

    /**
     * 根据角色ID查询权限
     *
     * @param roleId 角色ID
     * @return 权限列表
     */
    @Override
    public Set<String> selectMenuPermsByRoleId(Long roleId) {
        return baseMapper.selectMenuPermsByRoleId(roleId);
    }

    /**
     * 根据角色ID列表批量查询权限
     *
     * @param roleIds 角色ID列表
     * @return 角色权限映射
     */
    @Override
    public Map<Long, Set<String>> selectMenuPermsByRoleIds(Collection<Long> roleIds) {
        return baseMapper.selectMenuPermsByRoleIds(roleIds);
    }

    /**
     * 根据用户ID查询菜单树信息
     *
     * @param userId 用户ID
     * @return 按树结构组织的菜单列表
     */
    @Override
    public List<PlmMenu> selectMenuTreeByUserId(Long userId) {
        List<PlmMenu> menus;
        if (LoginHelper.isSuperAdmin(userId)) {
            menus = baseMapper.selectMenuTreeAll();
        } else {
            menus = baseMapper.selectMenuTreeByUserId(userId);
        }
        if (CollUtil.isEmpty(menus)) {
            return CollUtil.newArrayList();
        }

        List<PlmMenu> menuTree = TreeBuildUtils.build(menus, Constants.TOP_PARENT_ID, PlmMenu::getParentId, (menu, nodeTreeMaps) -> {
            // 将当前节点的菜单ID用作父节点ID
            Long menuParentId = menu.getId();
            // 从动态规划表中取出子节点列表
            // 如果不存在子节点，则返回一个空的列表，确保数据在进行JSON序列化时该字段的类型和结构是正确的
            List<PlmMenu> childMenus = nodeTreeMaps.getOrDefault(menuParentId, Collections.emptyList());
            // 设置子节点
            // 如果存在根节点指向尾节点的情况，则会出现环形依赖。但在菜单表中基本不会出现这种情况...
            menu.setChildren(childMenus);
        });
        return CollUtil.isEmpty(menuTree) ? CollUtil.newArrayList() : menuTree;
    }

    /**
     * 根据角色ID查询菜单树信息
     *
     * @param roleId 角色ID
     * @return 选中菜单列表
     */
    @Override
    public List<Long> selectMenuListByRoleId(Long roleId) {
        PlmRole role = roleMapper.selectById(roleId);
        return baseMapper.selectMenuListByRoleId(roleId, role.getMenuCheckStrictly());
    }

    /**
     * 构建前端路由所需要的菜单
     * 路由name命名规则 path首字母转大写 + id
     *
     * @param menus 菜单列表
     * @return 路由列表
     */
    @Override
    public List<RouterVo> buildMenus(List<PlmMenu> menus) {
        if (CollUtil.isEmpty(menus)) {
            return CollUtil.newArrayList();
        }
        List<RouterVo> routers = new LinkedList<>();
        for (PlmMenu menu : menus) {
            String name = menu.getRouteName() + menu.getId();
            RouterVo router = new RouterVo();
            router.setHidden("1".equals(menu.getVisible()));
            router.setName(name);
            router.setPath(menu.getRouterPath());
            router.setComponent(menu.getComponentInfo());
            router.setQuery(menu.getQueryParam());
            router.setExt(menu.getExt());
            router.setMeta(new MetaVo(menu.getMenuName(), menu.getIcon(), StringUtils.equals(SystemConstants.NO, menu.getIsCache()), menu.getPath(), menu.getActiveMenu()));
            List<PlmMenu> cMenus = menu.getChildren();
            if (CollUtil.isNotEmpty(cMenus) && SystemConstants.TYPE_DIR.equals(menu.getMenuType())) {
                router.setAlwaysShow(true);
                router.setRedirect("noRedirect");
                router.setChildren(buildMenus(cMenus));
            } else if (menu.isMenuFrame()) {
                String frameName = StringUtils.capitalize(menu.getPath()) + menu.getId();
                router.setMeta(null);
                List<RouterVo> childrenList = new ArrayList<>();
                RouterVo children = new RouterVo();
                children.setPath(menu.getPath());
                children.setComponent(menu.getComponent());
                children.setName(frameName);
                children.setMeta(new MetaVo(menu.getMenuName(), menu.getIcon(), StringUtils.equals(SystemConstants.NO, menu.getIsCache()), menu.getPath(), menu.getActiveMenu()));
                children.setQuery(menu.getQueryParam());
                children.setExt(menu.getExt());
                childrenList.add(children);
                router.setChildren(childrenList);
            } else if (menu.getParentId().equals(Constants.TOP_PARENT_ID) && menu.isInnerLink()) {
                router.setMeta(new MetaVo(menu.getMenuName(), menu.getIcon()));
                router.setPath("/");
                List<RouterVo> childrenList = new ArrayList<>();
                RouterVo children = new RouterVo();
                String routerPath = PlmMenu.innerLinkReplaceEach(menu.getPath());
                String innerLinkName = StringUtils.capitalize(routerPath) + menu.getId();
                children.setPath(routerPath);
                children.setComponent(SystemConstants.INNER_LINK);
                children.setName(innerLinkName);
                children.setMeta(new MetaVo(menu.getMenuName(), menu.getIcon(), menu.getPath()));
                children.setExt(menu.getExt());
                childrenList.add(children);
                router.setChildren(childrenList);
            }
            routers.add(router);
        }
        return routers;
    }

    /**
     * 构建前端所需要下拉树结构
     *
     * @param menus 菜单列表
     * @return 下拉树结构列表
     */
    @Override
    public List<Tree<Long>> buildMenuTreeSelect(List<PlmMenuVo> menus) {
        if (CollUtil.isEmpty(menus)) {
            return CollUtil.newArrayList();
        }
        return TreeBuildUtils.build(menus, (menu, tree) -> {
            Tree<Long> menuTree = tree.setId(menu.getId())
                    .setParentId(menu.getParentId())
                    .setName(menu.getMenuName())
                    .setWeight(menu.getOrderNum());
            menuTree.put("menuType", menu.getMenuType());
            menuTree.put("icon", menu.getIcon());
            menuTree.put("visible", menu.getVisible());
            menuTree.put("status", menu.getStatus());
        });
    }

    /**
     * 根据菜单ID查询信息
     *
     * @param menuId 菜单ID
     * @return 菜单信息
     */
    @Override
    public PlmMenuVo selectMenuById(Long menuId) {
        return baseMapper.selectVoById(menuId);
    }

    /**
     * 是否存在菜单子节点
     *
     * @param menuId 菜单ID
     * @return 结果
     */
    @Override
    public boolean hasChildByMenuId(Long menuId) {
        return baseMapper.lambda().eq(PlmMenu::getParentId, menuId).exists();
    }

    /**
     * 是否存在菜单子节点
     *
     * @param menuIds 菜单ID列表
     * @return 结果
     */
    @Override
    public boolean hasChildByMenuId(Collection<Long> menuIds) {
        return baseMapper.lambda()
                .in(PlmMenu::getParentId, menuIds)
                .notIn(PlmMenu::getId, menuIds)
                .exists();
    }

    /**
     * 查询菜单使用数量
     *
     * @param menuId 菜单ID
     * @return 结果
     */
    @Override
    public boolean checkMenuExistRole(Long menuId) {
        return roleMenuMapper.lambda().eq(PlmRoleMenu::getMenuId, menuId).exists();
    }

    /**
     * 新增保存菜单信息
     *
     * @param bo 菜单信息
     * @return 结果
     */
    @Override
    public int insertMenu(PlmMenuBo bo) {
        PlmMenu menu = MapstructUtils.convert(bo, PlmMenu.class);
        return baseMapper.insert(menu);
    }

    /**
     * 修改保存菜单信息
     *
     * @param bo 菜单信息
     * @return 结果
     */
    @Override
    public int updateMenu(PlmMenuBo bo) {
        PlmMenu menu = MapstructUtils.convert(bo, PlmMenu.class);
        return baseMapper.updateById(menu);
    }

    /**
     * 修改菜单状态
     *
     * @param menuId 菜单ID
     * @param status 菜单状态（0正常 1停用）
     * @return 结果
     */
    @Override
    public int updateMenuStatus(Long menuId, String status) {
        return baseMapper.lambda()
                .set(PlmMenu::getStatus, status)
                .eq(PlmMenu::getId, menuId)
                .updateCount();
    }

    /**
     * 删除菜单管理信息
     *
     * @param menuId 菜单ID
     * @return 结果
     */
    @Override
    public int deleteMenuById(Long menuId) {
        return baseMapper.deleteById(menuId);
    }

    /**
     * 批量删除菜单管理信息
     *
     * @param menuIds 菜单ID串
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteMenuById(Collection<Long> menuIds) {
        baseMapper.deleteByIds(menuIds);
        roleMenuMapper.deleteByMenuIds(menuIds);
    }

    /**
     * 校验菜单名称是否唯一
     *
     * @param menu 菜单信息
     * @return 结果
     */
    @Override
    public boolean checkMenuNameUnique(PlmMenuBo menu) {
        boolean exist = baseMapper.lambda()
                .eq(PlmMenu::getMenuName, menu.getMenuName())
                .eq(PlmMenu::getParentId, menu.getParentId())
                .neIfPresent(PlmMenu::getId, menu.getId())
                .exists();
        return !exist;
    }

    /**
     * 校验路由组合是否唯一
     *
     * @param menuBo 菜单信息
     * @return 结果
     */
    @Override
    public boolean checkRouteConfigUnique(PlmMenuBo menuBo) {
        PlmMenu menu = MapstructUtils.convert(menuBo, PlmMenu.class);
        if (SystemConstants.TYPE_BUTTON.equals(menu.getMenuType())) {
            return true;
        }
        long menuId = ObjectUtil.isNull(menu.getId()) ? -1L : menu.getId();
        Long parentId = menu.getParentId();
        String path = menu.getPath();
        String routeName = StringUtils.isEmpty(menu.getRouteName()) ? path : menu.getRouteName();
        List<PlmMenu> sysMenuList = baseMapper.lambda()
                .in(PlmMenu::getMenuType, SystemConstants.TYPE_DIR, SystemConstants.TYPE_MENU)
                .and(w ->
                        w.eq(PlmMenu::getPath, path).or().eq(PlmMenu::getPath, routeName)
                ).list();
        for (PlmMenu sysMenu : sysMenuList) {
            if (!sysMenu.getId().equals(menuId)) {
                Long dbParentId = sysMenu.getParentId();
                String dbPath = sysMenu.getPath();
                String dbRouteName = StringUtils.isEmpty(sysMenu.getRouteName()) ? dbPath : sysMenu.getRouteName();
                if (StringUtils.equalsAnyIgnoreCase(path, dbPath) && parentId.equals(dbParentId)) {
                    log.warn("[同级路由冲突] 同级下已存在相同路由路径 '{}'，冲突菜单：{}", dbPath, sysMenu.getMenuName());
                    return false;
                } else if (StringUtils.equalsAnyIgnoreCase(path, dbPath)
                        && Constants.TOP_PARENT_ID.equals(parentId)
                        && Constants.TOP_PARENT_ID.equals(dbParentId)) {
                    log.warn("[根目录路由冲突] 根目录下路由 '{}' 必须唯一，已被菜单 '{}' 占用", path, sysMenu.getMenuName());
                    return false;
                } else if (StringUtils.equalsAnyIgnoreCase(routeName, dbRouteName)
                        && sysMenu.getMenuType().equals(menu.getMenuType())) {
                    log.warn("[路由名称冲突] 路由名称 '{}' 需全局唯一，已被菜单 '{}' 使用", routeName, sysMenu.getMenuName());
                    return false;
                }
            }
        }
        return true;
    }
}
