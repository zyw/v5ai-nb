package xin.v5ai.nb.platform.service;

import xin.v5ai.nb.domain.vo.SysUserRoleVo;
import xin.v5ai.nb.domain.bo.SysUserRoleBo;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.mybatis.core.page.PageQuery;

import java.util.Collection;
import java.util.List;

/**
 * 用户和角色关联Service接口
 *
 * @author zyw
 * @date 2026-08-26
 */
public interface ISysUserRoleService {

    /**
     * 查询用户和角色关联
     *
     * @param ${pkColumn.javaField} 主键
     * @return 用户和角色关联
     */
    SysUserRoleVo queryById(Long userId);

    /**
     * 分页查询用户和角色关联列表
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 用户和角色关联分页列表
     */
    TableDataInfo queryPageList(SysUserRoleBo bo, PageQuery pageQuery);

    /**
     * 查询符合条件的用户和角色关联列表
     *
     * @param bo 查询条件
     * @return 用户和角色关联列表
     */
    List queryList(SysUserRoleBo bo);

    /**
     * 新增用户和角色关联
     *
     * @param bo 用户和角色关联
     * @return 是否新增成功
     */
    Boolean insertByBo(SysUserRoleBo bo);

    /**
     * 修改用户和角色关联
     *
     * @param bo 用户和角色关联
     * @return 是否修改成功
     */
    Boolean updateByBo(SysUserRoleBo bo);

    /**
     * 校验并批量删除用户和角色关联信息
     *
     * @param ids     待删除的主键集合
     * @param isValid 是否进行有效性校验
     * @return 是否删除成功
     */
    Boolean deleteWithValidByIds(Collection ids, Boolean isValid);
}
