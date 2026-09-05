package xin.v5ai.nb.model.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
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
@Service
@RequiredArgsConstructor
public class ModelConfigRetrieveImpl implements ModelConfigRetrieve {

    private final V5aiModelMapper modelMapper;
    private final V5aiModelProviderMapper providerMapper;

    @Override
    public ModelRuntimeConfigDTO findRuntimeConfigByModelId(Long modelId) {
        var model = modelMapper.selectById(modelId);
        if (model == null) {
            return null;
        }
        var provider = providerMapper.selectById(model.getProviderId());
        if (provider == null) {
            return null;
        }
        return toRuntimeConfig(model, provider);
    }

    @Override
    public ModelRuntimeConfigDTO findFirstEmbeddingModel() {
        var model = modelMapper.selectOne(new LambdaQueryWrapper<V5aiModel>()
                .eq(V5aiModel::getModelType, "EMBEDDING")
                .eq(V5aiModel::getEnabled, true)
                .orderByAsc(V5aiModel::getId)
                .last("LIMIT 1"));
        if (model == null) {
            return null;
        }
        var provider = providerMapper.selectById(model.getProviderId());
        if (provider == null) {
            return null;
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
