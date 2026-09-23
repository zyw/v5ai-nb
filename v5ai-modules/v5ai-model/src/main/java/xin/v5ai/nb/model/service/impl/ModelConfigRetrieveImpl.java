package xin.v5ai.nb.model.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import xin.v5ai.nb.common.agentscope.core.ModelConfigRetrieve;
import xin.v5ai.nb.common.agentscope.core.domain.dto.ModelExtConfigAttrs;
import xin.v5ai.nb.common.agentscope.core.domain.dto.ModelRuntimeConfigDTO;
import xin.v5ai.nb.model.domain.V5aiModel;
import xin.v5ai.nb.model.domain.V5aiModelProvider;
import xin.v5ai.nb.model.mapper.V5aiModelMapper;
import xin.v5ai.nb.model.mapper.V5aiModelProviderMapper;

/**
 * 基于 {@link V5aiModelMapper} / {@link V5aiModelProviderMapper} 的运行时配置网关实现，
 * 供连接测试与运行时模型解析读取模型 + 供应商投影。
 *
 * @author ZYW
 * @since 2026-08-20
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModelConfigRetrieveImpl implements ModelConfigRetrieve {

    private final V5aiModelMapper modelMapper;
    private final V5aiModelProviderMapper providerMapper;

    @Override
    public ModelRuntimeConfigDTO findRuntimeConfigByModelId(Long modelId) {
        var model = modelMapper.selectById(modelId);
        if (model == null) {
            log.warn("模型没有找到，ID: {}", modelId);
            return null;
        }
        var provider = providerMapper.selectById(model.getProviderId());
        if (provider == null) {
            log.warn("模型供应商没有找到, ID: {}", model.getProviderId());
            return null;
        }
        return toRuntimeConfig(model, provider);
    }

    @Override
    public ModelRuntimeConfigDTO findDefaultOrFirstEnabledModel(String modelType) {
        var model = modelMapper.selectOne(new LambdaQueryWrapper<V5aiModel>()
                .eq(V5aiModel::getModelType, modelType)
                .eq(V5aiModel::getEnabled, true)
                .orderByDesc(V5aiModel::getIsDefault)
                .orderByAsc(V5aiModel::getId)
                .last("LIMIT 1"));
        if (model == null) {
            throw new IllegalStateException("未配置可用的 " + modelType + " 模型");
        }
        var provider = providerMapper.selectById(model.getProviderId());
        if (provider == null) {
            throw new IllegalStateException("模型 " + model.getId() + " 的供应商不存在");
        }
        return toRuntimeConfig(model, provider);
    }

    private static ModelRuntimeConfigDTO toRuntimeConfig(V5aiModel model, V5aiModelProvider provider) {
        return new ModelRuntimeConfigDTO(
                model.getId(),
                model.getModelKey(),
                model.getModelType(),
                provider.getId(),
                provider.getProviderKey(),
                model.getAdapterKey(),
                model.getCredentialsCiphertext(),
                ModelExtConfigAttrs.fromJson(model.getConfig()));
    }
}
