package xin.v5ai.nb.platform.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import xin.v5ai.nb.platform.domain.AuditLog;

import java.util.List;

@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {
    default List<AuditLog> search(String actor, String action, int limit) {
        var wrapper = new LambdaQueryWrapper<AuditLog>()
                .eq(actor != null && !actor.isBlank(), AuditLog::getActor, actor)
                .eq(action != null && !action.isBlank(), AuditLog::getAction, action)
                .orderByDesc(AuditLog::getId)
                .last("LIMIT " + Math.min(Math.max(limit, 1), 500));
        return selectList(wrapper);
    }
}
