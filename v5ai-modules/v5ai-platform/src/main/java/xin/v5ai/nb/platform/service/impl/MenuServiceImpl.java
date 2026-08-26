package xin.v5ai.nb.platform.service.impl;

import cn.hutool.core.collection.CollUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import xin.v5ai.nb.common.core.constant.Constants;
import xin.v5ai.nb.common.core.constant.SystemConstants;
import xin.v5ai.nb.common.core.utils.StringUtils;
import xin.v5ai.nb.common.core.utils.TreeBuildUtils;
import xin.v5ai.nb.common.satoken.utils.LoginHelper;
import xin.v5ai.nb.platform.domain.Menu;
import xin.v5ai.nb.platform.domain.vo.MetaVo;
import xin.v5ai.nb.platform.domain.vo.RouterVo;
import xin.v5ai.nb.platform.mapper.MenuMapper;
import xin.v5ai.nb.platform.service.IMenuService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MenuServiceImpl implements IMenuService {

    private final MenuMapper menuMapper;

    /**
     * 根据用户ID查询菜单树信息
     *
     * @param userId 用户ID
     * @return 按树结构组织的菜单列表
     */
    @Override
    public List<Menu> selectMenuTreeByUserId(Long userId) {
        List<Menu> menus;
        if (LoginHelper.isSuperAdmin(userId)) {
            menus = menuMapper.selectMenuTreeAll();
        } else {
            menus = menuMapper.selectMenuTreeByUserId(userId);
        }
        if (CollUtil.isEmpty(menus)) {
            return CollUtil.newArrayList();
        }

        List<Menu> menuTree = TreeBuildUtils.build(menus, Constants.TOP_PARENT_ID, Menu::getParentId, (menu, nodeTreeMaps) -> {
            // 将当前节点的菜单ID用作父节点ID
            Long menuParentId = menu.getId();
            // 从动态规划表中取出子节点列表
            // 如果不存在子节点，则返回一个空的列表，确保数据在进行JSON序列化时该字段的类型和结构是正确的
            List<Menu> childMenus = nodeTreeMaps.getOrDefault(menuParentId, Collections.emptyList());
            // 设置子节点
            // 如果存在根节点指向尾节点的情况，则会出现环形依赖。但在菜单表中基本不会出现这种情况...
            menu.setChildren(childMenus);
        });
        return CollUtil.isEmpty(menuTree) ? CollUtil.newArrayList() : menuTree;
    }

    /**
     * 构建前端路由所需要的菜单
     * 路由name命名规则 path首字母转大写 + id
     *
     * @param menus 菜单列表
     * @return 路由列表
     */
    @Override
    public List<RouterVo> buildMenus(List<Menu> menus) {
        if (CollUtil.isEmpty(menus)) {
            return CollUtil.newArrayList();
        }
        List<RouterVo> routers = new LinkedList<>();
        for (Menu menu : menus) {
            String name = menu.getRouteName() + menu.getId();
            RouterVo router = new RouterVo();
            router.setHidden("1".equals(menu.getVisible()));
            router.setName(name);
            router.setPath(menu.getPath());
            router.setComponent(menu.getComponentInfo());
            router.setQuery(menu.getQueryParam());
            router.setExt(menu.getExt());
            router.setMeta(new MetaVo(menu.getName(), menu.getIcon(), StringUtils.equals(SystemConstants.NO, menu.getIsCache()), menu.getPath(), menu.getActiveMenu()));
            List<Menu> cMenus = menu.getChildren();
            if (CollUtil.isNotEmpty(cMenus) && SystemConstants.TYPE_DIR.equals(menu.getType())) {
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
                children.setMeta(new MetaVo(menu.getName(), menu.getIcon(), StringUtils.equals(SystemConstants.NO, menu.getIsCache()), menu.getPath(), menu.getActiveMenu()));
                children.setQuery(menu.getQueryParam());
                children.setExt(menu.getExt());
                childrenList.add(children);
                router.setChildren(childrenList);
            } else if (menu.getParentId().equals(Constants.TOP_PARENT_ID) && menu.isInnerLink()) {
                router.setMeta(new MetaVo(menu.getName(), menu.getIcon()));
                router.setPath("/");
                List<RouterVo> childrenList = new ArrayList<>();
                RouterVo children = new RouterVo();
                String routerPath = Menu.innerLinkReplaceEach(menu.getPath());
                String innerLinkName = StringUtils.capitalize(routerPath) + menu.getId();
                children.setPath(routerPath);
                children.setComponent(SystemConstants.INNER_LINK);
                children.setName(innerLinkName);
                children.setMeta(new MetaVo(menu.getName(), menu.getIcon(), menu.getPath()));
                children.setExt(menu.getExt());
                childrenList.add(children);
                router.setChildren(childrenList);
            }
            routers.add(router);
        }
        return routers;
    }
}
