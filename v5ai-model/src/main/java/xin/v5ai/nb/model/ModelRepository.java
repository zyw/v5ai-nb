package xin.v5ai.nb.model;

import java.util.List;

public interface ModelRepository {
    void save(ModelConfig model);

    List<ModelConfig> list();
}
