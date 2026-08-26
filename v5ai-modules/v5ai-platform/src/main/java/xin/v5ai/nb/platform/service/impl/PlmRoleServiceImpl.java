package xin.v5ai.nb.platform.service.impl;

import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.mybatis.core.page.PageQuery;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import xin.v5ai.nb.domain.bo.SysRoleBo;
import xin.v5ai.nb.domain.vo.SysRoleVo;
import xin.v5ai.nb.domain.SysRole;
import xin.v5ai.nb.mapper.SysRoleMapper;
import xin.v5ai.nb.service.ISysRoleService;

import java.util.List;
import java.util.Map;
import java.util.Collection;

/**
 * 角色信息Service业务层处理
 *
 * @author zyw
 * @date 2026-08-26
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class SysRoleServiceImpl implements ISysRoleService {

    private final SysRoleMapper baseMapper;

    /**
     * 查询角色信息
     *
     * @param roleId 主键
     * @return 角色信息
     */
    @Override
    public SysRoleVo queryById(Long roleId){
        return baseMapper.selectVoById(roleId);
    }

    /**
     * 分页查询角色信息列表
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 角色信息分页列表
     */
    @Override
    public TableDataInfo queryPageList(SysRoleBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper lqw = buildQueryWrapper(bo);
        Page result = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(result);
    }

    /**
     * 查询符合条件的角色信息列表
     *
     * @param bo 查询条件
     * @return 角色信息列表
     */
    @Override
    public List queryList(SysRoleBo bo) {
        LambdaQueryWrapper lqw = buildQueryWrapper(bo);
        return baseMapper.selectVoList(lqw);
    }

    private LambdaQueryWrapper buildQueryWrapper(SysRoleBo bo) {
        Map params = bo.getParams();
        LambdaQueryWrapper lqw = Wrappers.lambdaQuery();
        lqw.orderByAsc(SysRole::getRoleId);
        lqw.like(bo.getRoleName() != null, SysRole::getRoleName, bo.getRoleName());
        lqw.eq(bo.getRoleKey() != null, SysRole::getRoleKey, bo.getRoleKey());
        lqw.eq(bo.getRoleSort() != null, SysRole::getRoleSort, bo.getRoleSort());
        lqw.eq(bo.getDataScope() != null, SysRole::getDataScope, bo.getDataScope());
        lqw.eq(bo.getMenuCheckStrictly() != null, SysRole::getMenuCheckStrictly, bo.getMenuCheckStrictly());
        lqw.eq(bo.getDeptCheckStrictly() != null, SysRole::getDeptCheckStrictly, bo.getDeptCheckStrictly());
        lqw.eq(bo.getStatus() != null, SysRole::getStatus, bo.getStatus());
        return lqw;
    }

    /**
     * 新增角色信息
     *
     * @param bo 角色信息
     * @return 是否新增成功
     */
    @Override
    public Boolean insertByBo(SysRoleBo bo) {
        SysRole add = MapstructUtils.convert(bo, SysRole.class);
        validEntityBeforeSave(add);
        boolean flag = baseMapper.insert(add) > 0;
        if (flag) {
            bo.setRoleId(add.getRoleId());
        }
        return flag;
    }

    /**
     * 修改角色信息
     *
     * @param bo 角色信息
     * @return 是否修改成功
     */
    @Override
    public Boolean updateByBo(SysRoleBo bo) {
        SysRole update = MapstructUtils.convert(bo, SysRole.class);
        validEntityBeforeSave(update);
        return baseMapper.updateById(update) > 0;
    }

    /**
     * 保存前的数据校验
     */
    private void validEntityBeforeSave(SysRole entity){
        //TODO 做一些数据校验,如唯一约束
    }

    /**
     * 校验并批量删除角色信息信息
     *
     * @param ids     待删除的主键集合
     * @param isValid 是否进行有效性校验
     * @return 是否删除成功
     */
    @Override
    public Boolean deleteWithValidByIds(Collection ids, Boolean isValid) {
        if(isValid){
            //TODO 做一些业务上的校验,判断是否需要校验
        }
        return baseMapper.deleteByIds(ids) > 0;
    }
}
