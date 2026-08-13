package xin.v5ai.nb.infrastructure.model;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ModelConfigMapper extends BaseMapper<ModelConfigEntity>, ModelConfigGateway, ModelRuntimeConfigGateway {
    @Override
    default void insertModel(ModelConfigEntity entity) {
        insert(entity);
    }

    @Override
    default List<ModelConfigEntity> selectModels() {
        return selectList(null);
    }

    @Override
    @Select("""
            SELECT
                m.id AS model_id,
                m.model_key AS model_key,
                m.model_type AS model_type,
                p.id AS provider_id,
                p.provider_key AS provider_key,
                p.provider_type AS provider_type,
                m.credentials_ciphertext AS credentials_ciphertext
            FROM v5ai_model m
            JOIN v5ai_model_provider p ON p.id = m.provider_id
            WHERE m.id = #{modelId}
            """)
    ModelRuntimeConfig findRuntimeConfigByModelId(@Param("modelId") Long modelId);
}
