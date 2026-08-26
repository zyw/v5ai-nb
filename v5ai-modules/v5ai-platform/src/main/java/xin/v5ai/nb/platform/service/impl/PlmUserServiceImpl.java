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
import xin.v5ai.nb.domain.bo.SysUserBo;
import xin.v5ai.nb.domain.vo.SysUserVo;
import xin.v5ai.nb.domain.SysUser;
import xin.v5ai.nb.mapper.SysUserMapper;
import xin.v5ai.nb.service.ISysUserService;

import java.util.List;
import java.util.Map;
import java.util.Collection;

/**
 * 用户信息Service业务层处理
 *
 * @author zyw
 * @date 2026-08-26
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class SysUserServiceImpl implements ISysUserService {

    private final SysUserMapper baseMapper;

    /**
     * 查询用户信息
     *
     * @param userId 主键
     * @return 用户信息
     */
    @Override
    public SysUserVo queryById(Long userId){
        return baseMapper.selectVoById(userId);
    }

    /**
     * 分页查询用户信息列表
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 用户信息分页列表
     */
    @Override
    public TableDataInfo queryPageList(SysUserBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper lqw = buildQueryWrapper(bo);
        Page result = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(result);
    }

    /**
     * 查询符合条件的用户信息列表
     *
     * @param bo 查询条件
     * @return 用户信息列表
     */
    @Override
    public List queryList(SysUserBo bo) {
        LambdaQueryWrapper lqw = buildQueryWrapper(bo);
        return baseMapper.selectVoList(lqw);
    }

    private LambdaQueryWrapper buildQueryWrapper(SysUserBo bo) {
        Map params = bo.getParams();
        LambdaQueryWrapper lqw = Wrappers.lambdaQuery();
        lqw.orderByAsc(SysUser::getUserId);
        lqw.eq(bo.getDeptId() != null, SysUser::getDeptId, bo.getDeptId());
        lqw.like(bo.getUserName() != null, SysUser::getUserName, bo.getUserName());
        lqw.like(bo.getNickName() != null, SysUser::getNickName, bo.getNickName());
        lqw.eq(bo.getUserType() != null, SysUser::getUserType, bo.getUserType());
        lqw.eq(bo.getEmail() != null, SysUser::getEmail, bo.getEmail());
        lqw.eq(bo.getPhonenumber() != null, SysUser::getPhonenumber, bo.getPhonenumber());
        lqw.eq(bo.getSex() != null, SysUser::getSex, bo.getSex());
        lqw.eq(bo.getAvatar() != null, SysUser::getAvatar, bo.getAvatar());
        lqw.eq(bo.getPassword() != null, SysUser::getPassword, bo.getPassword());
        lqw.eq(bo.getStatus() != null, SysUser::getStatus, bo.getStatus());
        lqw.eq(bo.getLoginIp() != null, SysUser::getLoginIp, bo.getLoginIp());
        lqw.eq(bo.getLoginDate() != null, SysUser::getLoginDate, bo.getLoginDate());
        return lqw;
    }

    /**
     * 新增用户信息
     *
     * @param bo 用户信息
     * @return 是否新增成功
     */
    @Override
    public Boolean insertByBo(SysUserBo bo) {
        SysUser add = MapstructUtils.convert(bo, SysUser.class);
        validEntityBeforeSave(add);
        boolean flag = baseMapper.insert(add) > 0;
        if (flag) {
            bo.setUserId(add.getUserId());
        }
        return flag;
    }

    /**
     * 修改用户信息
     *
     * @param bo 用户信息
     * @return 是否修改成功
     */
    @Override
    public Boolean updateByBo(SysUserBo bo) {
        SysUser update = MapstructUtils.convert(bo, SysUser.class);
        validEntityBeforeSave(update);
        return baseMapper.updateById(update) > 0;
    }

    /**
     * 保存前的数据校验
     */
    private void validEntityBeforeSave(SysUser entity){
        //TODO 做一些数据校验,如唯一约束
    }

    /**
     * 校验并批量删除用户信息信息
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
