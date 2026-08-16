package xin.v5ai.nb.infrastructure.platform;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLogEntity> {
    default List<AuditLogEntity> search(String actor, String action, int limit) {
        var wrapper = new LambdaQueryWrapper<AuditLogEntity>()
                .eq(actor != null && !actor.isBlank(), AuditLogEntity::getActor, actor)
                .eq(action != null && !action.isBlank(), AuditLogEntity::getAction, action)
                .orderByDesc(AuditLogEntity::getId)
                .last("LIMIT " + Math.min(Math.max(limit, 1), 500));
        return selectList(wrapper);
    }
}
