package xin.v5ai.nb.platform.mapper;

import xin.v5ai.nb.common.mybatis.core.mapper.BaseMapperPlus;
import xin.v5ai.nb.platform.domain.PlmRoleMenu;
import xin.v5ai.nb.platform.domain.vo.PlmRoleMenuVo;

import java.util.Collection;

/**
 * 角色和菜单关联Mapper接口
 *
 * @author zyw
 * @date 2026-08-26
 */
public interface PlmRoleMenuMapper extends BaseMapperPlus<PlmRoleMenu, PlmRoleMenuVo> {

    /**
     * 根据菜单ID串删除关联关系
     *
     * @param menuIds 菜单ID串
     * @return 结果
     */
    default int deleteByMenuIds(Collection<Long> menuIds) {
        return this.lambda().in(PlmRoleMenu::getMenuId, menuIds).deleteCount();
    }

}