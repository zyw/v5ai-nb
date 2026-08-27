package xin.v5ai.nb.platform.mapper;

import cn.hutool.core.convert.Convert;
import xin.v5ai.nb.common.mybatis.core.mapper.BaseMapperPlus;
import xin.v5ai.nb.platform.domain.PlmUserRole;
import xin.v5ai.nb.platform.domain.vo.PlmUserRoleVo;

import java.util.List;

/**
 * 用户和角色关联Mapper接口
 *
 * @author zyw
 * @date 2026-08-26
 */
public interface PlmUserRoleMapper extends BaseMapperPlus<PlmUserRole, PlmUserRoleVo> {
    /**
     * 根据角色ID查询关联的用户ID列表
     *
     * @param roleId 角色ID
     * @return 关联到指定角色的用户ID列表
     */
    default List<Long> selectUserIdsByRoleId(Long roleId) {
        return this.lambda()
                .select(PlmUserRole::getUserId)
                .eq(PlmUserRole::getRoleId, roleId)
                .objs(Convert::toLong);
    }
}