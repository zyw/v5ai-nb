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
import xin.v5ai.nb.domain.bo.SysUserRoleBo;
import xin.v5ai.nb.domain.vo.SysUserRoleVo;
import xin.v5ai.nb.domain.SysUserRole;
import xin.v5ai.nb.mapper.SysUserRoleMapper;
import xin.v5ai.nb.service.ISysUserRoleService;

import java.util.List;
import java.util.Map;
import java.util.Collection;

/**
 * 用户和角色关联Service业务层处理
 *
 * @author zyw
 * @date 2026-08-26
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class SysUserRoleServiceImpl implements ISysUserRoleService {

    private final SysUserRoleMapper baseMapper;

    /**
     * 查询用户和角色关联
     *
     * @param userId 主键
     * @return 用户和角色关联
     */
    @Override
    public SysUserRoleVo queryById(Long userId){
        return baseMapper.selectVoById(userId);
    }

    /**
     * 分页查询用户和角色关联列表
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 用户和角色关联分页列表
     */
    @Override
    public TableDataInfo queryPageList(SysUserRoleBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper lqw = buildQueryWrapper(bo);
        Page result = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(result);
    }

    /**
     * 查询符合条件的用户和角色关联列表
     *
     * @param bo 查询条件
     * @return 用户和角色关联列表
     */
    @Override
    public List queryList(SysUserRoleBo bo) {
        LambdaQueryWrapper lqw = buildQueryWrapper(bo);
        return baseMapper.selectVoList(lqw);
    }

    private LambdaQueryWrapper buildQueryWrapper(SysUserRoleBo bo) {
        Map params = bo.getParams();
        LambdaQueryWrapper lqw = Wrappers.lambdaQuery();
        lqw.orderByAsc(SysUserRole::getUserId);
        lqw.orderByAsc(SysUserRole::getRoleId);
        return lqw;
    }

    /**
     * 新增用户和角色关联
     *
     * @param bo 用户和角色关联
     * @return 是否新增成功
     */
    @Override
    public Boolean insertByBo(SysUserRoleBo bo) {
        SysUserRole add = MapstructUtils.convert(bo, SysUserRole.class);
        validEntityBeforeSave(add);
        boolean flag = baseMapper.insert(add) > 0;
        if (flag) {
            bo.setUserId(add.getUserId());
        }
        return flag;
    }

    /**
     * 修改用户和角色关联
     *
     * @param bo 用户和角色关联
     * @return 是否修改成功
     */
    @Override
    public Boolean updateByBo(SysUserRoleBo bo) {
        SysUserRole update = MapstructUtils.convert(bo, SysUserRole.class);
        validEntityBeforeSave(update);
        return baseMapper.updateById(update) > 0;
    }

    /**
     * 保存前的数据校验
     */
    private void validEntityBeforeSave(SysUserRole entity){
        //TODO 做一些数据校验,如唯一约束
    }

    /**
     * 校验并批量删除用户和角色关联信息
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
