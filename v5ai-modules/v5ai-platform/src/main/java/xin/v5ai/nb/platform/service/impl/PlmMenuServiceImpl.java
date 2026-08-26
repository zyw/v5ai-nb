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
import xin.v5ai.nb.domain.bo.SysMenuBo;
import xin.v5ai.nb.domain.vo.SysMenuVo;
import xin.v5ai.nb.domain.SysMenu;
import xin.v5ai.nb.mapper.SysMenuMapper;
import xin.v5ai.nb.service.ISysMenuService;

import java.util.List;
import java.util.Map;
import java.util.Collection;

/**
 * 菜单权限Service业务层处理
 *
 * @author zyw
 * @date 2026-08-26
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class SysMenuServiceImpl implements ISysMenuService {

    private final SysMenuMapper baseMapper;

    /**
     * 查询菜单权限
     *
     * @param menuId 主键
     * @return 菜单权限
     */
    @Override
    public SysMenuVo queryById(Long menuId){
        return baseMapper.selectVoById(menuId);
    }

    /**
     * 分页查询菜单权限列表
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 菜单权限分页列表
     */
    @Override
    public TableDataInfo queryPageList(SysMenuBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper lqw = buildQueryWrapper(bo);
        Page result = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(result);
    }

    /**
     * 查询符合条件的菜单权限列表
     *
     * @param bo 查询条件
     * @return 菜单权限列表
     */
    @Override
    public List queryList(SysMenuBo bo) {
        LambdaQueryWrapper lqw = buildQueryWrapper(bo);
        return baseMapper.selectVoList(lqw);
    }

    private LambdaQueryWrapper buildQueryWrapper(SysMenuBo bo) {
        Map params = bo.getParams();
        LambdaQueryWrapper lqw = Wrappers.lambdaQuery();
        lqw.orderByAsc(SysMenu::getMenuId);
        lqw.like(bo.getMenuName() != null, SysMenu::getMenuName, bo.getMenuName());
        lqw.eq(bo.getParentId() != null, SysMenu::getParentId, bo.getParentId());
        lqw.eq(bo.getOrderNum() != null, SysMenu::getOrderNum, bo.getOrderNum());
        lqw.eq(bo.getPath() != null, SysMenu::getPath, bo.getPath());
        lqw.eq(bo.getComponent() != null, SysMenu::getComponent, bo.getComponent());
        lqw.eq(bo.getQueryParam() != null, SysMenu::getQueryParam, bo.getQueryParam());
        lqw.eq(bo.getIsFrame() != null, SysMenu::getIsFrame, bo.getIsFrame());
        lqw.eq(bo.getIsCache() != null, SysMenu::getIsCache, bo.getIsCache());
        lqw.eq(bo.getMenuType() != null, SysMenu::getMenuType, bo.getMenuType());
        lqw.eq(bo.getVisible() != null, SysMenu::getVisible, bo.getVisible());
        lqw.eq(bo.getStatus() != null, SysMenu::getStatus, bo.getStatus());
        lqw.eq(bo.getPerms() != null, SysMenu::getPerms, bo.getPerms());
        lqw.eq(bo.getIcon() != null, SysMenu::getIcon, bo.getIcon());
        return lqw;
    }

    /**
     * 新增菜单权限
     *
     * @param bo 菜单权限
     * @return 是否新增成功
     */
    @Override
    public Boolean insertByBo(SysMenuBo bo) {
        SysMenu add = MapstructUtils.convert(bo, SysMenu.class);
        validEntityBeforeSave(add);
        boolean flag = baseMapper.insert(add) > 0;
        if (flag) {
            bo.setMenuId(add.getMenuId());
        }
        return flag;
    }

    /**
     * 修改菜单权限
     *
     * @param bo 菜单权限
     * @return 是否修改成功
     */
    @Override
    public Boolean updateByBo(SysMenuBo bo) {
        SysMenu update = MapstructUtils.convert(bo, SysMenu.class);
        validEntityBeforeSave(update);
        return baseMapper.updateById(update) > 0;
    }

    /**
     * 保存前的数据校验
     */
    private void validEntityBeforeSave(SysMenu entity){
        //TODO 做一些数据校验,如唯一约束
    }

    /**
     * 校验并批量删除菜单权限信息
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
