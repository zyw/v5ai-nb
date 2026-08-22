package xin.v5ai.nb.platform.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import xin.v5ai.nb.platform.domain.Tenant;

import java.util.List;

@Mapper
public interface TenantMapper extends BaseMapper<Tenant> {
    default Tenant selectByKey(String tenantKey) {
        return selectOne(new LambdaQueryWrapper<Tenant>().eq(Tenant::getTenantKey, tenantKey));
    }

    default void disableTenant(Long id) {
        var entity = new Tenant();
        entity.setStatus("DISABLED");
        update(entity, new LambdaUpdateWrapper<Tenant>().eq(Tenant::getId, id));
    }

    default List<Tenant> selectAll() {
        return selectList(null);
    }
}
