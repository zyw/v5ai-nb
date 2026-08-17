package xin.v5ai.nb.infrastructure.platform;

import org.springframework.stereotype.Component;
import xin.v5ai.nb.common.core.platform.UsageRecord;
import xin.v5ai.nb.common.core.platform.UsageRecorder;

import java.time.Instant;

@Component
public class MyBatisUsageRecorder implements UsageRecorder {
    private final ModelUsageMapper mapper;

    public MyBatisUsageRecorder(ModelUsageMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void record(UsageRecord usage) {
        var entity = new ModelUsageEntity();
        entity.setRunId(usage.runId());
        entity.setAgentKey(usage.agentKey());
        entity.setModelKey(usage.modelKey());
        entity.setPromptTokens(usage.promptTokens());
        entity.setCompletionTokens(usage.completionTokens());
        entity.setTotalTokens(usage.totalTokens());
        entity.setDurationMs(usage.durationMs());
        entity.setStatus(usage.status());
        entity.setCreatedAt(usage.createdAt() == null ? Instant.now() : usage.createdAt());
        mapper.insert(entity);
    }
}
