//package xin.v5ai.nb.runtime.core.mapper;
//
//import lombok.RequiredArgsConstructor;
//import org.springframework.stereotype.Repository;
//import xin.v5ai.nb.common.agentscope.core.service.AgentStateService;
//import xin.v5ai.nb.runtime.core.domain.AgentStateEntity;
//import xin.v5ai.nb.runtime.core.gateway.AgentStateGateway;
//
//@Repository
//@RequiredArgsConstructor
//public class MyBatisAgentStateRepository implements AgentStateService {
//
//    private final AgentStateGateway gateway;
//
//    @Override
//    public void save(String conversationId, String stateJson) {
//        var entity = new AgentStateEntity();
//        entity.setConversationId(conversationId);
//        entity.setStateJson(stateJson);
//        gateway.upsert(entity);
//    }
//
//    @Override
//    public String load(String conversationId) {
//        var entity = gateway.selectByConversationId(conversationId);
//        return entity == null ? null : entity.getStateJson();
//    }
//}
