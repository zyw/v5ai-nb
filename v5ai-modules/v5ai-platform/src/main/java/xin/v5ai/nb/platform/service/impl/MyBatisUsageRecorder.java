//package xin.v5ai.nb.platform.service.impl;
//
//import org.springframework.stereotype.Component;
//import xin.v5ai.nb.common.core.platform.UsageRecord;
//import xin.v5ai.nb.common.core.platform.UsageRecorder;
//import xin.v5ai.nb.platform.domain.V5aiModelUsage;
//import xin.v5ai.nb.platform.mapper.V5aiModelUsageMapper;
//
//import java.time.OffsetDateTime;
//
//@Component
//public class MyBatisUsageRecorder implements UsageRecorder {
//    private final V5aiModelUsageMapper mapper;
//
//    public MyBatisUsageRecorder(V5aiModelUsageMapper mapper) {
//        this.mapper = mapper;
//    }
//
//    @Override
//    public void record(UsageRecord usage) {
//        var entity = new V5aiModelUsage();
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
