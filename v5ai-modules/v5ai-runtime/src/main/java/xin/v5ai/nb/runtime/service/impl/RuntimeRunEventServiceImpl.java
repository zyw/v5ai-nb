package xin.v5ai.nb.runtime.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import xin.v5ai.nb.common.agentscope.core.domain.dto.RuntimeRunEventDTO;
import xin.v5ai.nb.runtime.core.service.RunEventService;
import xin.v5ai.nb.runtime.domain.RuntimeRunEvent;
import xin.v5ai.nb.runtime.mapper.RuntimeRunEventMapper;
import xin.v5ai.nb.runtime.service.IRuntimeRunEventService;

@Service
@RequiredArgsConstructor
public class RuntimeRunEventServiceImpl implements IRuntimeRunEventService, RunEventService {

    private final RuntimeRunEventMapper baseMapper;

    @Override
    public boolean save(RuntimeRunEventDTO event) {
        var entity = new RuntimeRunEvent();
        entity.setRunId(event.runId());
        entity.setEventType(event.type().name());
        entity.setPayload(event.payload());
        entity.setCreatedAt(event.createdAt());
        return baseMapper.insert(entity) > 0;
    }
}
