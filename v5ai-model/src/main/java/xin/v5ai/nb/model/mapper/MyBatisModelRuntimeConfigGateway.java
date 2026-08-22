package xin.v5ai.nb.model.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Repository;
import xin.v5ai.nb.model.domain.V5aiModel;
import xin.v5ai.nb.model.domain.V5aiModelProvider;
import xin.v5ai.nb.model.runtime.config.ModelRuntimeConfig;

/**
 * 基于 {@link V5aiModelMapper} / {@link V5aiModelProviderMapper} 的运行时配置网关实现，
 * 供连接测试与运行时模型解析读取模型 + 供应商投影。
 *
 * @author ZYW
 * @since 2026-08-20
 */
@Repository
public class MyBatisModelRuntimeConfigGateway implements ModelRuntimeConfigGateway {

    private final V5aiModelMapper modelMapper;
    private final V5aiModelProviderMapper providerMapper;

    public MyBatisModelRuntimeConfigGateway(V5aiModelMapper modelMapper, V5aiModelProviderMapper providerMapper) {
        this.modelMapper = modelMapper;
        this.providerMapper = providerMapper;
    }

    @Override
    public ModelRuntimeConfig findRuntimeConfigByModelId(Long modelId) {
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
    public ModelRuntimeConfig findFirstEmbeddingModel() {
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

    private static ModelRuntimeConfig toRuntimeConfig(V5aiModel model, V5aiModelProvider provider) {
        return new ModelRuntimeConfig(
                model.getId(),
                model.getModelKey(),
                model.getModelType(),
                provider.getId(),
                provider.getProviderKey(),
                provider.getProviderType(),
                model.getCredentialsCiphertext());
    }
}
