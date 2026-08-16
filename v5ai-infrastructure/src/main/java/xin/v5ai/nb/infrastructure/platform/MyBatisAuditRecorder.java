package xin.v5ai.nb.infrastructure.platform;

import org.springframework.stereotype.Component;
import xin.v5ai.nb.common.platform.AuditLog;
import xin.v5ai.nb.common.platform.AuditRecorder;

import java.time.Instant;
import java.util.List;

@Component
public class MyBatisAuditRecorder implements AuditRecorder {
    private final AuditLogMapper mapper;

    public MyBatisAuditRecorder(AuditLogMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void record(Long tenantId, String actor, String action,
                       String targetType, String targetId, String detail, String ip) {
        var entity = new AuditLogEntity();
        entity.setTenantId(tenantId);
        entity.setActor(actor);
        entity.setAction(action);
        entity.setTargetType(targetType);
        entity.setTargetId(targetId);
        entity.setDetail(detail);
        entity.setIp(ip);
        entity.setCreatedAt(Instant.now());
        mapper.insert(entity);
    }

    @Override
    public List<AuditLog> search(String actor, String action, int limit) {
        return mapper.search(actor, action, limit).stream().map(this::toDomain).toList();
    }

    private AuditLog toDomain(AuditLogEntity entity) {
        return new AuditLog(entity.getId(), entity.getTenantId(), entity.getActor(), entity.getAction(),
                entity.getTargetType(), entity.getTargetId(), entity.getDetail(), entity.getIp(),
                entity.getCreatedAt());
    }
}
