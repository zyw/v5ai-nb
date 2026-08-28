//package xin.v5ai.nb.platform.mapper;
//
//import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
//import com.github.yulichang.base.MPJBaseMapper;
//import org.apache.ibatis.annotations.Mapper;
//import xin.v5ai.nb.common.mybatis.core.mapper.BaseMapperPlus;
//import xin.v5ai.nb.common.mybatis.core.query.QueryBuilder;
//import xin.v5ai.nb.platform.domain.Role;
//import xin.v5ai.nb.platform.domain.UserRole;
//import xin.v5ai.nb.platform.domain.vo.RoleVo;
//
//import java.time.Instant;
//import java.util.List;
//
//@Mapper
//public interface RoleMapper extends BaseMapperPlus<Role, RoleVo>, MPJBaseMapper<Role> {
//
//    default List<Role> selectAll() {
//        return selectList(null);
//    }
//
//    default int updateMeta(String roleKey, String roleName, String description, String dataScope) {
//        return update(null, new LambdaUpdateWrapper<Role>()
//                .eq(Role::getRoleKey, roleKey)
//                .set(Role::getRoleName, roleName)
//                .set(Role::getDescription, description)
//                .set(Role::getDataScope, dataScope)
//                .set(Role::getUpdatedAt, Instant.now()));
//    }
//
//    /**
//     * 根据用户ID查询角色
//     *
//     * @param userId 用户ID
//     * @return 角色列表
//     */
//    default List<RoleVo> selectRolesByUserId(Long userId) {
//        return this.selectJoinList(RoleVo.class, QueryBuilder.lambdaJoin("r", Role.class)
//                .select(Role::getRoleName, Role::getRoleKey,
//                        Role::getBuiltin, Role::getDataScope)
//                .leftJoin(UserRole.class, "sur", UserRole::getRoleKey, Role::getRoleKey)
//                .eq("sur", UserRole::getUserId, userId)
//                .build());
//    }
//}
