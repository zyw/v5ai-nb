package xin.v5ai.nb.infrastructure.platform;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.Instant;
import java.util.List;

@Mapper
public interface RoleMapper extends BaseMapper<RoleEntity> {
    default List<RoleEntity> selectAll() {
        return selectList(null);
    }

    default int updateMeta(String roleKey, String name, String description) {
        return update(null, new LambdaUpdateWrapper<RoleEntity>()
                .eq(RoleEntity::getRoleKey, roleKey)
                .set(RoleEntity::getName, name)
                .set(RoleEntity::getDescription, description)
                .set(RoleEntity::getUpdatedAt, Instant.now()));
    }
}
