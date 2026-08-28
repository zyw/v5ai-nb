//package xin.v5ai.nb.platform.mapper;
//
//import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
//import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
//import com.baomidou.mybatisplus.core.mapper.BaseMapper;
//import org.apache.ibatis.annotations.Mapper;
//import xin.v5ai.nb.platform.domain.UserRole;
//
//import java.util.List;
//
//@Mapper
//public interface UserRoleMapper extends BaseMapper<UserRole> {
//    default void insertRoles(Long userId, List<String> roleKeys) {
//        for (String roleKey : roleKeys) {
//            var entity = new UserRole();
//            entity.setUserId(userId);
//            entity.setRoleKey(roleKey);
//            insert(entity);
//        }
//    }
//
//    default void deleteByUser(Long userId) {
//        delete(new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, userId));
//    }
//
//    default List<String> selectRolesByUser(Long userId) {
//        return selectList(new LambdaQueryWrapper<UserRole>()
//                        .eq(UserRole::getUserId, userId))
//                .stream().map(UserRole::getRoleKey).toList();
//    }
//
//    default void deleteByRole(String roleKey) {
//        delete(new LambdaUpdateWrapper<UserRole>().eq(UserRole::getRoleKey, roleKey));
//    }
//
//    default long countByRole(String roleKey) {
//        return selectCount(new LambdaQueryWrapper<UserRole>().eq(UserRole::getRoleKey, roleKey));
//    }
//}
