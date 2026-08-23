package xin.v5ai.nb.runtime.core.mapper;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import xin.v5ai.nb.common.agentscope.core.domain.RuntimeEvent;
import xin.v5ai.nb.common.agentscope.core.service.RunEventService;
import xin.v5ai.nb.runtime.core.gateway.RuntimeRunEventGateway;
import xin.v5ai.nb.runtime.core.domain.RuntimeRunEventEntity;

@Repository
@RequiredArgsConstructor
public class MyBatisRunEventRepository implements RunEventService {

    private final RuntimeRunEventGateway gateway;

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
