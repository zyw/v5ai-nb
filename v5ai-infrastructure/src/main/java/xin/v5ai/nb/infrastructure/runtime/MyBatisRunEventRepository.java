package xin.v5ai.nb.infrastructure.runtime;

import org.springframework.stereotype.Repository;
import xin.v5ai.nb.common.agentscope.core.domain.RuntimeEvent;
import xin.v5ai.nb.common.agentscope.core.service.RunEventService;

@Repository
public class MyBatisRunEventRepository implements RunEventService {
    private final RuntimeRunEventGateway gateway;

    public MyBatisRunEventRepository(RuntimeRunEventGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public void save(RuntimeEvent event) {
        var entity = new RuntimeRunEventEntity();
        entity.setRunId(event.runId());
        entity.setEventType(event.type().name());
        entity.setPayload(event.payload());
        entity.setCreatedAt(event.createdAt());
        gateway.insertRunEvent(entity);
    }
}
