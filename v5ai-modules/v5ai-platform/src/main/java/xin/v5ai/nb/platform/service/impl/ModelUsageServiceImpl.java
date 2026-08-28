package xin.v5ai.nb.platform.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import xin.v5ai.nb.common.core.domain.PageResult;
import xin.v5ai.nb.common.mybatis.core.page.PageQuery;
import xin.v5ai.nb.common.mybatis.core.query.QueryBuilder;
import xin.v5ai.nb.platform.domain.ModelUsage;
import xin.v5ai.nb.platform.domain.bo.ModelUsageListBo;
import xin.v5ai.nb.platform.domain.vo.ModelUsageRecordVo;
import xin.v5ai.nb.platform.domain.vo.ModelUsageVo;
import xin.v5ai.nb.platform.mapper.ModelUsageMapper;
import xin.v5ai.nb.platform.service.IModelUsageService;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ModelUsageServiceImpl implements IModelUsageService {

    private final ModelUsageMapper mapper;

    @Override
    public PageResult<ModelUsageRecordVo> queryPageList(ModelUsageListBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<ModelUsage> lqw = buildQueryWrapper(bo);
        Page<ModelUsageVo> result = mapper.selectVoPage(pageQuery.build(), lqw);
        var records = getRecords(result);
        return PageResult.build(records, result.getTotal());
    }

    /**
     * 构造供应商列表查询条件。
     *
     * @param bo 供应商筛选条件
     * @return 包含 providerKey、name、providerType、enabled 等条件的查询包装器
     */
    private LambdaQueryWrapper<ModelUsage> buildQueryWrapper(ModelUsageListBo bo) {
        return QueryBuilder.lambda(ModelUsage.class)
                .eq(ObjectUtil.isNotNull(bo.getAgentKey()), ModelUsage::getAgentKey, bo.getAgentKey())
                .ge(ObjectUtil.isNotNull(bo.getForm()), ModelUsage::getCreatedAt, bo.getForm())
                .le(ObjectUtil.isNotNull(bo.getTo()), ModelUsage::getCreatedAt, bo.getTo())
                .orderByAsc(ModelUsage::getCreatedAt)
                .build();
    }

    private List<ModelUsageRecordVo> getRecords(Page<ModelUsageVo> result) {
        List<ModelUsageVo> entities = result.getRecords();
        return entities.stream().map(entity -> ModelUsageRecordVo.builder()
                .id(entity.getId())
                .runId(entity.getRunId())
                .agentKey(entity.getAgentKey())
                .modelKey(entity.getModelKey())
                .promptTokens(entity.getPromptTokens())
                .completionTokens(entity.getCompletionTokens())
                .durationMs(entity.getDurationMs())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .build()).toList();
    }
}
