package xin.v5ai.nb.infrastructure.runtime;

import org.springframework.stereotype.Repository;
import xin.v5ai.nb.runtime.AgentStateRepository;

@Repository
public class MyBatisAgentStateRepository implements AgentStateRepository {
    private final AgentStateGateway gateway;

    public MyBatisAgentStateRepository(AgentStateGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public void save(String conversationId, String stateJson) {
        var entity = new AgentStateEntity();
        entity.setConversationId(conversationId);
        entity.setStateJson(stateJson);
        gateway.upsert(entity);
    }

    @Override
    public String load(String conversationId) {
        var entity = gateway.selectByConversationId(conversationId);
        return entity == null ? null : entity.getStateJson();
    }
}
