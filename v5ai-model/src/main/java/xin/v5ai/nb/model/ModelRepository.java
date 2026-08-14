package xin.v5ai.nb.model;

import java.util.List;

public interface ModelRepository {
    void save(ModelConfig model);

    ModelConfig findById(Long id);

    void update(ModelConfig model);

    void delete(Long id);

    List<ModelConfig> list();
}
