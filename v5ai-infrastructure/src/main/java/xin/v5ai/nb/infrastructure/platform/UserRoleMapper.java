package xin.v5ai.nb.infrastructure.platform;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface UserRoleMapper extends BaseMapper<UserRoleEntity> {
    default void insertRoles(Long userId, List<String> roleKeys) {
        for (String roleKey : roleKeys) {
            var entity = new UserRoleEntity();
            entity.setUserId(userId);
            entity.setRoleKey(roleKey);
            insert(entity);
        }
    }

    default void deleteByUser(Long userId) {
        delete(new LambdaQueryWrapper<UserRoleEntity>().eq(UserRoleEntity::getUserId, userId));
    }

    default List<String> selectRolesByUser(Long userId) {
        return selectList(new LambdaQueryWrapper<UserRoleEntity>()
                        .eq(UserRoleEntity::getUserId, userId))
                .stream().map(UserRoleEntity::getRoleKey).toList();
    }

    default void deleteByRole(String roleKey) {
        delete(new LambdaUpdateWrapper<UserRoleEntity>().eq(UserRoleEntity::getRoleKey, roleKey));
    }

    default long countByRole(String roleKey) {
        return selectCount(new LambdaQueryWrapper<UserRoleEntity>().eq(UserRoleEntity::getRoleKey, roleKey));
    }
}
