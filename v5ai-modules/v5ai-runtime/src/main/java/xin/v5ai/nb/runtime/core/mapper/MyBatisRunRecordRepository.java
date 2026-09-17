//package xin.v5ai.nb.runtime.core.mapper;
//
//import lombok.RequiredArgsConstructor;
//import org.springframework.stereotype.Component;
//import xin.v5ai.nb.common.agentscope.core.domain.bo.AgentRunBo;
//import xin.v5ai.nb.common.agentscope.core.service.RunRecordService;
//import xin.v5ai.nb.runtime.core.gateway.RuntimeRunGateway;
//import xin.v5ai.nb.runtime.core.domain.RuntimeRunEntity;
//
//@Component
//@RequiredArgsConstructor
//public class MyBatisRunRecordRepository implements RunRecordService {
//
//    private final RuntimeRunGateway gateway;
//
//    @Override
//    public void start(String runId, AgentRunBo request) {
//        var entity = new RuntimeRunEntity();
//        entity.setId(runId);
//        entity.setConversationId(request.conversationId());
//        entity.setAgentKey(request.agentKey());
//        entity.setStatus("RUNNING");
//        gateway.insertRun(entity);
//    }
//
//    @Override
//    public boolean complete(String runId) {
//        gateway.markCompleted(runId);
//    }
//
//    @Override
//    public boolean fail(String runId, String message) {
//        gateway.markFailed(runId, message);
//    }
//}
