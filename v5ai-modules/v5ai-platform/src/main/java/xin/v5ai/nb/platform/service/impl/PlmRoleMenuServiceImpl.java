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
import xin.v5ai.nb.domain.bo.SysRoleMenuBo;
import xin.v5ai.nb.domain.vo.SysRoleMenuVo;
import xin.v5ai.nb.domain.SysRoleMenu;
import xin.v5ai.nb.mapper.SysRoleMenuMapper;
import xin.v5ai.nb.service.ISysRoleMenuService;

import java.util.List;
import java.util.Map;
import java.util.Collection;

/**
 * 角色和菜单关联Service业务层处理
 *
 * @author zyw
 * @date 2026-08-26
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class SysRoleMenuServiceImpl implements ISysRoleMenuService {

    private final SysRoleMenuMapper baseMapper;

    /**
     * 查询角色和菜单关联
     *
     * @param roleId 主键
     * @return 角色和菜单关联
     */
    @Override
    public SysRoleMenuVo queryById(Long roleId){
        return baseMapper.selectVoById(roleId);
    }

    /**
     * 分页查询角色和菜单关联列表
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 角色和菜单关联分页列表
     */
    @Override
    public TableDataInfo queryPageList(SysRoleMenuBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper lqw = buildQueryWrapper(bo);
        Page result = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(result);
    }

    /**
     * 查询符合条件的角色和菜单关联列表
     *
     * @param bo 查询条件
     * @return 角色和菜单关联列表
     */
    @Override
    public List queryList(SysRoleMenuBo bo) {
        LambdaQueryWrapper lqw = buildQueryWrapper(bo);
        return baseMapper.selectVoList(lqw);
    }

    private LambdaQueryWrapper buildQueryWrapper(SysRoleMenuBo bo) {
        Map params = bo.getParams();
        LambdaQueryWrapper lqw = Wrappers.lambdaQuery();
        lqw.orderByAsc(SysRoleMenu::getRoleId);
        lqw.orderByAsc(SysRoleMenu::getMenuId);
        return lqw;
    }

    /**
     * 新增角色和菜单关联
     *
     * @param bo 角色和菜单关联
     * @return 是否新增成功
     */
    @Override
    public Boolean insertByBo(SysRoleMenuBo bo) {
        SysRoleMenu add = MapstructUtils.convert(bo, SysRoleMenu.class);
        validEntityBeforeSave(add);
        boolean flag = baseMapper.insert(add) > 0;
        if (flag) {
            bo.setRoleId(add.getRoleId());
        }
        return flag;
    }

    /**
     * 修改角色和菜单关联
     *
     * @param bo 角色和菜单关联
     * @return 是否修改成功
     */
    @Override
    public Boolean updateByBo(SysRoleMenuBo bo) {
        SysRoleMenu update = MapstructUtils.convert(bo, SysRoleMenu.class);
        validEntityBeforeSave(update);
        return baseMapper.updateById(update) > 0;
    }

    /**
     * 保存前的数据校验
     */
    private void validEntityBeforeSave(SysRoleMenu entity){
        //TODO 做一些数据校验,如唯一约束
    }

    /**
     * 校验并批量删除角色和菜单关联信息
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
