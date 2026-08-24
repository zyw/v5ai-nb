package xin.v5ai.nb.platform.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import xin.v5ai.nb.common.mybatis.core.mapper.BaseMapperPlus;
import xin.v5ai.nb.platform.domain.AuditLog;
import xin.v5ai.nb.platform.domain.vo.AuditLogVo;

import java.util.List;


public interface AuditLogMapper extends BaseMapperPlus<AuditLog, AuditLogVo> {
//    default List<AuditLogDTO> search(String actor, String action, int limit) {
//        var wrapper = new LambdaQueryWrapper<AuditLogDTO>()
//                .eq(actor != null && !actor.isBlank(), AuditLogDTO::getActor, actor)
//                .eq(action != null && !action.isBlank(), AuditLogDTO::getAction, action)
//                .orderByDesc(AuditLogDTO::getId)
//                .last("LIMIT " + Math.min(Math.max(limit, 1), 500));
//        return selectList(wrapper);
//    }
}
