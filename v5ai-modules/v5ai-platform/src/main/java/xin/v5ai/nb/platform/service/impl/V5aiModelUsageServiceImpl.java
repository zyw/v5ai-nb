package xin.v5ai.nb.platform.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import xin.v5ai.nb.common.core.domain.PageResult;
import xin.v5ai.nb.common.core.utils.ObjectUtils;
import xin.v5ai.nb.common.mybatis.core.page.PageQuery;
import xin.v5ai.nb.common.mybatis.core.query.QueryBuilder;
import xin.v5ai.nb.model.api.ModelUsageService;
import xin.v5ai.nb.model.api.domain.ModelUsageDTO;
import xin.v5ai.nb.platform.domain.V5aiModelUsage;
import xin.v5ai.nb.platform.domain.bo.ModelUsageListBo;
import xin.v5ai.nb.platform.domain.vo.ModelUsagePageVo;
import xin.v5ai.nb.platform.domain.vo.V5aiModelUsageVo;
import xin.v5ai.nb.platform.mapper.V5aiModelUsageMapper;
import xin.v5ai.nb.platform.service.IV5aiModelUsageService;

import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class V5aiModelUsageServiceImpl implements IV5aiModelUsageService, ModelUsageService {

    private final V5aiModelUsageMapper mapper;

    @Override
    public PageResult<ModelUsagePageVo> queryPageList(ModelUsageListBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<V5aiModelUsage> lqw = buildQueryWrapper(bo);
        Page<V5aiModelUsageVo> result = mapper.selectVoPage(pageQuery.build(), lqw);
        var records = getRecords(result);
        return PageResult.build(records, result.getTotal());
    }

    /**
     * 构造模型用量查询条件。
     *
     * @param bo 用量筛选条件
     * @return 包含 agentKey、时间范围等条件的查询包装器
     */
    private LambdaQueryWrapper<V5aiModelUsage> buildQueryWrapper(ModelUsageListBo bo) {
        return QueryBuilder.lambda(V5aiModelUsage.class)
                .eq(ObjectUtil.isNotNull(bo.getAgentKey()), V5aiModelUsage::getAgentKey, bo.getAgentKey())
                .ge(ObjectUtil.isNotNull(bo.getFrom()), V5aiModelUsage::getCreatedAt, bo.getFrom())
                .le(ObjectUtil.isNotNull(bo.getTo()), V5aiModelUsage::getCreatedAt, bo.getTo())
                .orderByAsc(V5aiModelUsage::getCreatedAt)
                .build();
    }

    private List<ModelUsagePageVo> getRecords(Page<V5aiModelUsageVo> result) {
        List<V5aiModelUsageVo> entities = result.getRecords();
        return entities.stream().map(entity -> ModelUsagePageVo.builder()
                .id(entity.getId())
                .runId(entity.getRunId())
                .agentKey(entity.getAgentKey())
                .modelId(entity.getModelId())
                .modelKey(entity.getModelKey())
                .promptTokens(entity.getPromptTokens())
                .completionTokens(entity.getCompletionTokens())
                .durationMs(entity.getDurationMs())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .build()).toList();
    }

    @Override
    public boolean record(ModelUsageDTO usage) {
        var entity = new V5aiModelUsage();
        entity.setRunId(usage.runId());
        entity.setAgentKey(usage.agentKey());
        entity.setModelId(usage.modelId());
        entity.setModelKey(usage.modelKey());
        entity.setPromptTokens(usage.promptTokens());
        entity.setCompletionTokens(usage.completionTokens());
        entity.setTotalTokens(usage.totalTokens());
        entity.setDurationMs(usage.durationMs());
        entity.setStatus(usage.status());
        entity.setCreatedAt(ObjectUtils.defaultIfNull(usage.createdAt(), OffsetDateTime.now()));
        log.info("Recording model usage 模型使用量: {}", entity);
        return mapper.insert(entity) > 0;
    }
}
