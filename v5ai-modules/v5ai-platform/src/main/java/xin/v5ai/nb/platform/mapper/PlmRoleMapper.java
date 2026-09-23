package xin.v5ai.nb.platform.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.yulichang.base.MPJBaseMapper;
import org.apache.ibatis.annotations.Param;
import xin.v5ai.nb.common.mybatis.annotation.DataColumn;
import xin.v5ai.nb.common.mybatis.annotation.DataPermission;
import xin.v5ai.nb.common.mybatis.core.mapper.BaseMapperPlus;
import xin.v5ai.nb.common.mybatis.core.query.QueryBuilder;
import xin.v5ai.nb.platform.domain.PlmRole;
import xin.v5ai.nb.platform.domain.PlmUserRole;
import xin.v5ai.nb.platform.domain.vo.PlmRoleVo;

import java.util.Collection;
import java.util.List;

/**
 * 角色信息Mapper接口
 *
 * @author zyw
 * @date 2026-08-26
 */
public interface PlmRoleMapper extends BaseMapperPlus<PlmRole, PlmRoleVo>, MPJBaseMapper<PlmRole> {
    /**
     * 分页查询角色列表
     *
     * @param page         分页对象
     * @param queryWrapper 查询条件
     * @return 包含角色信息的分页结果
     */
    @DataPermission({
            @DataColumn(key = "userName", value = "create_by")
    })
    default Page<PlmRoleVo> selectPageRoleList(@Param("page") Page<PlmRole> page, @Param(Constants.WRAPPER) Wrapper<PlmRole> queryWrapper) {
        return this.selectVoPage(page, queryWrapper);
    }

    /**
     * 根据条件查询角色数据
     *
     * @param queryWrapper 查询条件
     * @return 角色数据集合信息
     */
    @DataPermission({
            @DataColumn(key = "userName", value = "create_by")
    })
    default List<PlmRoleVo> selectRoleList(@Param(Constants.WRAPPER) Wrapper<PlmRole> queryWrapper) {
        return this.selectVoList(queryWrapper);
    }

    /**
     * 根据角色ID集合查询角色数量
     *
     * @param roleIds 角色ID列表
     * @return 匹配的角色数量
     */
    @DataPermission({
            @DataColumn(key = "userName", value = "create_by")
    })
    default long selectRoleCount(Collection<Long> roleIds) {
        return this.lambda().in(PlmRole::getId, roleIds).count();
    }

    /**
     * 根据角色ID查询角色信息
     *
     * @param roleId 角色ID
     * @return 对应的角色信息
     */
    @DataPermission({
            @DataColumn(key = "userName", value = "create_by")
    })
    default PlmRoleVo selectRoleById(Long roleId) {
        return this.selectVoById(roleId);
    }

    /**
     * 根据用户ID查询角色
     *
     * @param userId 用户ID
     * @return 角色列表
     */
    default List<PlmRoleVo> selectRolesByUserId(Long userId) {
        return this.selectJoinList(PlmRoleVo.class, QueryBuilder.lambdaJoin("r", PlmRole.class)
                .select(PlmRole::getId, PlmRole::getRoleName, PlmRole::getRoleKey,
                        PlmRole::getRoleSort, PlmRole::getDataScope, PlmRole::getStatus)
                .leftJoin(PlmUserRole.class, "sur", PlmUserRole::getRoleId, PlmRole::getId)
                .eq("sur", PlmUserRole::getUserId, userId)
                .build());
    }
}