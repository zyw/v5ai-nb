package xin.v5ai.nb.platform.mapper;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import xin.v5ai.nb.platform.domain.Role;

import java.time.Instant;
import java.util.List;

@Mapper
public interface RoleMapper extends BaseMapper<Role> {
    default List<Role> selectAll() {
        return selectList(null);
    }

    default int updateMeta(String roleKey, String roleName, String description, String dataScope) {
        return update(null, new LambdaUpdateWrapper<Role>()
                .eq(Role::getRoleKey, roleKey)
                .set(Role::getRoleName, roleName)
                .set(Role::getDescription, description)
                .set(Role::getDataScope, dataScope)
                .set(Role::getUpdatedAt, Instant.now()));
    }
}
