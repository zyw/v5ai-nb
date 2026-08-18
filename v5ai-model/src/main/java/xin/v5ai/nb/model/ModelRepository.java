package xin.v5ai.nb.model;

import java.util.List;

public interface ModelRepository {
    void save(ModelConfig model);

    ModelConfig findById(Long id);

    void update(ModelConfig model);

    void delete(Long id);

    List<ModelConfig> list();

    /**
     * 模型下拉选项轻量视图。默认基于全量列表推导，持久化实现可覆盖为投影查询以降低大表开销。
     */
    default List<ModelOption> listOptions() {
        return list().stream()
                .map(model -> new ModelOption(model.id(), model.providerKey(), model.modelKey(), model.type()))
                .toList();
    }
}
