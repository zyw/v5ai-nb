package xin.v5ai.nb.platform.service;

import xin.v5ai.nb.domain.vo.SysMenuVo;
import xin.v5ai.nb.domain.bo.SysMenuBo;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.mybatis.core.page.PageQuery;

import java.util.Collection;
import java.util.List;

/**
 * 菜单权限Service接口
 *
 * @author zyw
 * @date 2026-08-26
 */
public interface ISysMenuService {

    /**
     * 查询菜单权限
     *
     * @param ${pkColumn.javaField} 主键
     * @return 菜单权限
     */
    SysMenuVo queryById(Long menuId);

    /**
     * 分页查询菜单权限列表
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 菜单权限分页列表
     */
    TableDataInfo queryPageList(SysMenuBo bo, PageQuery pageQuery);

    /**
     * 查询符合条件的菜单权限列表
     *
     * @param bo 查询条件
     * @return 菜单权限列表
     */
    List queryList(SysMenuBo bo);

    /**
     * 新增菜单权限
     *
     * @param bo 菜单权限
     * @return 是否新增成功
     */
    Boolean insertByBo(SysMenuBo bo);

    /**
     * 修改菜单权限
     *
     * @param bo 菜单权限
     * @return 是否修改成功
     */
    Boolean updateByBo(SysMenuBo bo);

    /**
     * 校验并批量删除菜单权限信息
     *
     * @param ids     待删除的主键集合
     * @param isValid 是否进行有效性校验
     * @return 是否删除成功
     */
    Boolean deleteWithValidByIds(Collection ids, Boolean isValid);
}
