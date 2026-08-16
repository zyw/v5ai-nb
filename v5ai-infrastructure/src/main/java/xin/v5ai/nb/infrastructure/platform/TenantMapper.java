package xin.v5ai.nb.infrastructure.platform;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface TenantMapper extends BaseMapper<TenantEntity> {
    default TenantEntity selectByKey(String tenantKey) {
        return selectOne(new LambdaQueryWrapper<TenantEntity>().eq(TenantEntity::getTenantKey, tenantKey));
    }

    default void disableTenant(Long id) {
        var entity = new TenantEntity();
        entity.setStatus("DISABLED");
        update(entity, new LambdaUpdateWrapper<TenantEntity>().eq(TenantEntity::getId, id));
    }

    default List<TenantEntity> selectAll() {
        return selectList(null);
    }
}
