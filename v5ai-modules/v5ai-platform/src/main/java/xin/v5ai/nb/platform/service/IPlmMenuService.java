package xin.v5ai.nb.platform.service;

import cn.hutool.core.lang.tree.Tree;
import xin.v5ai.nb.platform.domain.PlmMenu;
import xin.v5ai.nb.platform.domain.bo.PlmMenuBo;
import xin.v5ai.nb.platform.domain.vo.PlmMenuVo;
import xin.v5ai.nb.platform.domain.vo.RouterVo;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 菜单权限Service接口
 *
 * @author zyw
 * @date 2026-08-26
 */
public interface IPlmMenuService {
    /**
     * 根据用户查询系统菜单列表
     *
     * @param userId 用户ID
     * @return 菜单列表
     */
    List<PlmMenuVo> selectMenuList(Long userId);

    /**
     * 根据用户查询系统菜单列表
     *
     * @param menu   菜单信息
     * @param userId 用户ID
     * @return 菜单列表
     */
    List<PlmMenuVo> selectMenuList(PlmMenuBo menu, Long userId);

    /**
     * 根据用户ID查询权限
     *
     * @param userId 用户ID
     * @return 权限列表
     */
    Set<String> selectMenuPermsByUserId(Long userId);

    /**
     * 根据角色ID查询权限
     *
     * @param roleId 角色ID
     * @return 权限列表
     */
    Set<String> selectMenuPermsByRoleId(Long roleId);

    /**
     * 根据角色ID列表批量查询权限
     *
     * @param roleIds 角色ID列表
     * @return 角色权限映射
     */
    Map<Long, Set<String>> selectMenuPermsByRoleIds(Collection<Long> roleIds);

    /**
     * 根据用户ID查询菜单树信息
     *
     * @param userId 用户ID
     * @return 菜单列表
     */
    List<PlmMenu> selectMenuTreeByUserId(Long userId);

    /**
     * 根据角色ID查询菜单树信息
     *
     * @param roleId 角色ID
     * @return 选中菜单列表
     */
    List<Long> selectMenuListByRoleId(Long roleId);

    /**
     * 构建前端路由所需要的菜单
     *
     * @param menus 菜单列表
     * @return 路由列表
     */
    List<RouterVo> buildMenus(List<PlmMenu> menus);

    /**
     * 构建前端所需要下拉树结构
     *
     * @param menus 菜单列表
     * @return 下拉树结构列表
     */
    List<Tree<Long>> buildMenuTreeSelect(List<PlmMenuVo> menus);

    /**
     * 根据菜单ID查询信息
     *
     * @param menuId 菜单ID
     * @return 菜单信息
     */
    PlmMenuVo selectMenuById(Long menuId);

    /**
     * 是否存在菜单子节点
     *
     * @param menuId 菜单ID
     * @return 结果 true 存在 false 不存在
     */
    boolean hasChildByMenuId(Long menuId);

    /**
     * 是否存在菜单子节点
     *
     * @param menuIds 菜单ID列表
     * @return 结果 true 存在 false 不存在
     */
    boolean hasChildByMenuId(Collection<Long> menuIds);

    /**
     * 查询菜单是否存在角色
     *
     * @param menuId 菜单ID
     * @return 结果 true 存在 false 不存在
     */
    boolean checkMenuExistRole(Long menuId);

    /**
     * 新增保存菜单信息
     *
     * @param bo 菜单信息
     * @return 结果
     */
    int insertMenu(PlmMenuBo bo);

    /**
     * 修改保存菜单信息
     *
     * @param bo 菜单信息
     * @return 结果
     */
    int updateMenu(PlmMenuBo bo);

    /**
     * 修改菜单状态
     *
     * @param menuId 菜单ID
     * @param status 菜单状态（0正常 1停用）
     * @return 结果
     */
    int updateMenuStatus(Long menuId, String status);

    /**
     * 删除菜单管理信息
     *
     * @param menuId 菜单ID
     * @return 结果
     */
    int deleteMenuById(Long menuId);

    /**
     * 批量删除菜单管理信息
     *
     * @param menuIds 菜单ID列表
     */
    void deleteMenuById(Collection<Long> menuIds);

    /**
     * 校验菜单名称是否唯一
     *
     * @param menu 菜单信息
     * @return 结果
     */
    boolean checkMenuNameUnique(PlmMenuBo menu);

    /**
     * 校验路由组合是否唯一
     *
     * @param menu 菜单信息
     * @return 结果
     */
    boolean checkRouteConfigUnique(PlmMenuBo menu);
}
