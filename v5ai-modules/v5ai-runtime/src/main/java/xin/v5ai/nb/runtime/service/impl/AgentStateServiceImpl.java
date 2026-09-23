package xin.v5ai.nb.runtime.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import xin.v5ai.nb.runtime.core.service.AgentStateService;
import xin.v5ai.nb.runtime.domain.AgentState;
import xin.v5ai.nb.runtime.mapper.AgentStateMapper;
import xin.v5ai.nb.runtime.service.IAgentStateService;

@Service
@RequiredArgsConstructor
public class AgentStateServiceImpl implements IAgentStateService, AgentStateService {

    private final AgentStateMapper baseMapper;

    @Override
    public boolean save(String conversationId, String stateJson) {
        var entity = new AgentState();
        entity.setConversationId(conversationId);
        entity.setStateJson(stateJson);
        return baseMapper.upsert(entity) > 0;

    }

    @Override
    public String load(String conversationId) {
        var entity = baseMapper.selectByConversationId(conversationId);
        return entity == null ? null : entity.getStateJson();
    }
}
