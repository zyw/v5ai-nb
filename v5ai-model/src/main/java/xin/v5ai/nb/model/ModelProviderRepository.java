package xin.v5ai.nb.model;

import xin.v5ai.nb.common.mybatis.core.mapper.BaseMapperPlus;

import java.util.List;

public interface ModelProviderRepository extends BaseMapperPlus<ModelProvider, ModelProvider> {
    void save(ModelProvider provider);

    ModelProvider findById(Long id);

    ModelProvider findByKey(String providerKey);

    void update(ModelProvider provider);

    void delete(String providerKey);

    List<ModelProvider> list();
}
