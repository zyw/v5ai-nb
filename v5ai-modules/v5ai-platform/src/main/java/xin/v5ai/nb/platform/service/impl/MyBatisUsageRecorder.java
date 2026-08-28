//package xin.v5ai.nb.platform.service.impl;
//
//import org.springframework.stereotype.Component;
//import xin.v5ai.nb.common.core.platform.UsageRecord;
//import xin.v5ai.nb.common.core.platform.UsageRecorder;
//import xin.v5ai.nb.platform.domain.ModelUsage;
//import xin.v5ai.nb.platform.mapper.ModelUsageMapper;
//
//import java.time.OffsetDateTime;
//
//@Component
//public class MyBatisUsageRecorder implements UsageRecorder {
//    private final ModelUsageMapper mapper;
//
//    public MyBatisUsageRecorder(ModelUsageMapper mapper) {
//        this.mapper = mapper;
//    }
//
//    @Override
//    public void record(UsageRecord usage) {
//        var entity = new ModelUsage();
//        entity.setRunId(usage.runId());
//        entity.setAgentKey(usage.agentKey());
//        entity.setModelKey(usage.modelKey());
//        entity.setPromptTokens(usage.promptTokens());
//        entity.setCompletionTokens(usage.completionTokens());
//        entity.setTotalTokens(usage.totalTokens());
//        entity.setDurationMs(usage.durationMs());
//        entity.setStatus(usage.status());
//        entity.setCreatedAt(usage.createdAt() == null ? OffsetDateTime.now() : usage.createdAt());
//        mapper.insert(entity);
//    }
//}
