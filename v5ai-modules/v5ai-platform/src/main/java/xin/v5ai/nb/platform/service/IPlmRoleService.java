package xin.v5ai.nb.platform.service;

import xin.v5ai.nb.domain.vo.SysRoleVo;
import xin.v5ai.nb.domain.bo.SysRoleBo;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.mybatis.core.page.PageQuery;

import java.util.Collection;
import java.util.List;

/**
 * 角色信息Service接口
 *
 * @author zyw
 * @date 2026-08-26
 */
public interface ISysRoleService {

    /**
     * 查询角色信息
     *
     * @param ${pkColumn.javaField} 主键
     * @return 角色信息
     */
    SysRoleVo queryById(Long roleId);

    /**
     * 分页查询角色信息列表
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 角色信息分页列表
     */
    TableDataInfo queryPageList(SysRoleBo bo, PageQuery pageQuery);

    /**
     * 查询符合条件的角色信息列表
     *
     * @param bo 查询条件
     * @return 角色信息列表
     */
    List queryList(SysRoleBo bo);

    /**
     * 新增角色信息
     *
     * @param bo 角色信息
     * @return 是否新增成功
     */
    Boolean insertByBo(SysRoleBo bo);

    /**
     * 修改角色信息
     *
     * @param bo 角色信息
     * @return 是否修改成功
     */
    Boolean updateByBo(SysRoleBo bo);

    /**
     * 校验并批量删除角色信息信息
     *
     * @param ids     待删除的主键集合
     * @param isValid 是否进行有效性校验
     * @return 是否删除成功
     */
    Boolean deleteWithValidByIds(Collection ids, Boolean isValid);
}
