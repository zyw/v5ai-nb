package xin.v5ai.nb.runtime.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import xin.v5ai.nb.common.agentscope.core.domain.bo.AgentRunBo;
import xin.v5ai.nb.runtime.core.enums.RunStatus;
import xin.v5ai.nb.runtime.core.service.RunRecordService;
import xin.v5ai.nb.runtime.domain.RuntimeRun;
import xin.v5ai.nb.runtime.mapper.RuntimeRunMapper;
import xin.v5ai.nb.runtime.service.IRuntimeRunService;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RuntimeRunServiceImpl implements IRuntimeRunService, RunRecordService {

    private final RuntimeRunMapper baseMapper;

    @Override
    public boolean start(String runId, AgentRunBo request) {
        var entity = new RuntimeRun();
        entity.setId(runId);
        entity.setConversationId(request.conversationId());
        entity.setAgentKey(request.agentKey());
        entity.setStatus(RunStatus.RUNNING.name());
        return baseMapper.insert(entity) > 0;
    }

    @Override
    public boolean complete(String runId) {
        return baseMapper.markCompleted(runId);
    }

    @Override
    public boolean fail(String runId, String message) {
        return baseMapper.markFailed(runId, message);
    }

    @Override
    public boolean cancel(String runId) {
        return baseMapper.markCanceled(runId);
    }

    @Override
    public Optional<String> findConversationId(String runId) {
        var run = baseMapper.selectById(runId);
        return Optional.ofNullable(run).map(RuntimeRun::getConversationId);
    }
}
