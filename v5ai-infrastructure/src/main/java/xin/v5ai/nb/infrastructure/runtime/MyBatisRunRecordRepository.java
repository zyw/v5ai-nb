package xin.v5ai.nb.infrastructure.runtime;

import org.springframework.stereotype.Component;
import xin.v5ai.nb.runtime.AgentRunRequest;
import xin.v5ai.nb.runtime.RunRecordRepository;

@Component
public class MyBatisRunRecordRepository implements RunRecordRepository {
    private final RuntimeRunGateway gateway;

    public MyBatisRunRecordRepository(RuntimeRunGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public void start(String runId, AgentRunRequest request) {
        var entity = new RuntimeRunEntity();
        entity.setId(runId);
        entity.setConversationId(request.conversationId());
        entity.setAgentKey(request.agentKey());
        entity.setStatus("RUNNING");
        gateway.insertRun(entity);
    }

    @Override
    public void complete(String runId) {
        gateway.markCompleted(runId);
    }

    @Override
    public void fail(String runId, String message) {
        gateway.markFailed(runId, message);
    }
}
