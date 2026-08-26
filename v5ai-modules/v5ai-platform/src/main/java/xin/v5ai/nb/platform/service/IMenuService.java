package xin.v5ai.nb.platform.service;

import xin.v5ai.nb.platform.domain.Menu;
import xin.v5ai.nb.platform.domain.vo.RouterVo;

import java.util.List;

public interface IMenuService {

    /**
     * 根据用户ID查询菜单树信息
     *
     * @param userId 用户ID
     * @return 菜单列表
     */
    List<Menu> selectMenuTreeByUserId(Long userId);

    /**
     * 构建前端路由所需要的菜单
     *
     * @param menus 菜单列表
     * @return 路由列表
     */
    List<RouterVo> buildMenus(List<Menu> menus);
}
