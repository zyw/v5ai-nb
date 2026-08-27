package xin.v5ai.nb.platform.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.yulichang.base.MPJBaseMapper;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import org.apache.ibatis.annotations.Param;
import xin.v5ai.nb.common.mybatis.annotation.DataColumn;
import xin.v5ai.nb.common.mybatis.annotation.DataPermission;
import xin.v5ai.nb.common.mybatis.core.mapper.BaseMapperPlus;
import xin.v5ai.nb.common.mybatis.core.query.QueryBuilder;
import xin.v5ai.nb.platform.domain.PlmRole;
import xin.v5ai.nb.platform.domain.PlmUser;
import xin.v5ai.nb.platform.domain.PlmUserRole;
import xin.v5ai.nb.platform.domain.bo.PlmUserBo;
import xin.v5ai.nb.platform.domain.vo.PlmUserVo;

import java.util.List;

/**
 * 用户信息Mapper接口
 *
 * @author zyw
 * @date 2026-08-26
 */
public interface PlmUserMapper extends BaseMapperPlus<PlmUser, PlmUserVo>, MPJBaseMapper<PlmUser> {
    /**
     * 分页查询用户列表，并进行数据权限控制
     *
     * @param page         分页参数
     * @param queryWrapper 查询条件
     * @return 分页的用户信息
     */
    @DataPermission({
            @DataColumn(key = "userName", value = "created_by")
    })
    default Page<PlmUserVo> selectPageUserList(Page<PlmUser> page, Wrapper<PlmUser> queryWrapper) {
        return this.selectVoPage(page, queryWrapper);
    }

    /**
     * 查询用户列表，并进行数据权限控制
     *
     * @param queryWrapper 查询条件
     * @return 用户信息集合
     */
    @DataPermission({
            @DataColumn(key = "userName", value = "created_by")
    })
    default List<PlmUserVo> selectUserList(Wrapper<PlmUser> queryWrapper) {
        return this.selectVoList(queryWrapper);
    }

    /**
     * 根据条件分页查询已配用户角色列表
     *
     * @param page 分页信息
     * @param user 查询条件
     * @return 用户信息集合信息
     */
    @DataPermission({
            @DataColumn(key = "userName", value = "u.created_by")
    })
    default Page<PlmUserVo> selectAllocatedList(Page<PlmUserVo> page, PlmUserBo user) {
        MPJLambdaWrapper<PlmUser> wrapper = this.buildUserRoleJoinWrapper(user)
                .eq(user.getId() != null, "r", PlmRole::getId, user.getRoleId())
                .orderByAsc("u", PlmUser::getId);
        return this.selectJoinPage(page, PlmUserVo.class, wrapper);
    }

    /**
     * 根据条件分页查询未分配用户角色列表
     *
     * @param page    分页信息
     * @param user    查询条件
     * @param userIds 未分配用户角色的用户ID列表
     * @return 用户信息集合信息
     */
    @DataPermission({
            @DataColumn(key = "userName", value = "u.created_by")
    })
    default Page<PlmUserVo> selectUnallocatedList(Page<PlmUserVo> page, PlmUserBo user, List<Long> userIds) {
        MPJLambdaWrapper<PlmUser> wrapper = this.buildUserRoleJoinWrapper(user)
                .notIn(userIds != null && !userIds.isEmpty(), "u", PlmUser::getId, userIds)
                .orderByAsc("u", PlmUser::getId);
        return this.selectJoinPage(page, PlmUserVo.class, wrapper);
    }

    /**
     * 根据用户ID统计用户数量
     *
     * @param userId 用户ID
     * @return 用户数量
     */
    @DataPermission({
            @DataColumn(key = "userName", value = "created_by")
    })
    default long countUserById(Long userId) {
        return lambda().eq(PlmUser::getId, userId).count();
    }

    /**
     * 根据条件更新用户数据
     *
     * @param user          要更新的用户实体
     * @param updateWrapper 更新条件封装器
     * @return 更新操作影响的行数
     */
    @Override
    @DataPermission({
            @DataColumn(key = "userName", value = "created_by")
    })
    int update(@Param(Constants.ENTITY) PlmUser user, @Param(Constants.WRAPPER) Wrapper<PlmUser> updateWrapper);

    /**
     * 根据用户ID更新用户数据
     *
     * @param user 要更新的用户实体
     * @return 更新操作影响的行数
     */
    @Override
    @DataPermission({
            @DataColumn(key = "userName", value = "created_by")
    })
    int updateById(@Param(Constants.ENTITY) PlmUser user);

    /**
     * 构建用户与角色关联查询条件
     *
     * @param user 查询条件
     * @return 用户角色关联查询包装器
     */
    default MPJLambdaWrapper<PlmUser> buildUserRoleJoinWrapper(PlmUserBo user) {
        return QueryBuilder.lambdaJoin("u", PlmUser.class)
                .distinct()
                .selectAll(PlmUser.class)
                .leftJoin(PlmUserRole.class, "sur", PlmUserRole::getUserId, PlmUser::getId)
                .leftJoin(PlmRole.class, "r", PlmRole::getId, PlmUserRole::getRoleId)
                .likeIfText("u", PlmUser::getUserName, user.getUserName())
                .eqIfText("u", PlmUser::getStatus, user.getStatus())
                .likeIfText("u", PlmUser::getPhoneNumber, user.getPhoneNumber())
                .build();
    }
}