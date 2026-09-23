package xin.v5ai.nb.rag.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xin.v5ai.nb.common.agentscope.core.ModelConfigRetrieve;
import xin.v5ai.nb.common.agentscope.core.domain.dto.ModelExtConfigAttrs;
import xin.v5ai.nb.common.agentscope.core.domain.dto.ModelRuntimeConfigDTO;
import xin.v5ai.nb.rag.domain.StoreInstance;
import xin.v5ai.nb.rag.mapper.StoreInstanceMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link VectorDimensionService} 单测：模型维度（声明/可调/探测）、引擎类型默认上限与实例覆盖、
 * effective=min 语义、KB 冻结维度权威校验。
 */
class VectorDimensionServiceTest {

    private ModelConfigRetrieve modelRetrieve;
    private StoreInstanceMapper storeMapper;
    private EmbeddingClient embeddingClient;
    private VectorDimensionService service;

    @BeforeEach
    void setUp() {
        modelRetrieve = mock(ModelConfigRetrieve.class);
        storeMapper = mock(StoreInstanceMapper.class);
        embeddingClient = mock(EmbeddingClient.class);
        service = new VectorDimensionService(modelRetrieve, storeMapper, embeddingClient);
    }

    private static ModelRuntimeConfigDTO model(Long id, ModelExtConfigAttrs config) {
        return new ModelRuntimeConfigDTO(id, "m" + id, "EMBEDDING", 1L, "p", "openai-compatible",
                "cipher", config);
    }

    private static ModelExtConfigAttrs fixed(int dim) {
        var config = new ModelExtConfigAttrs();
        config.setEmbeddingDimension(dim);
        return config;
    }

    private static ModelExtConfigAttrs adjustable(int dim, Integer max) {
        var config = new ModelExtConfigAttrs();
        config.setEmbeddingDimension(dim);
        config.setDimensionAdjustable(true);
        config.setMaxDimension(max);
        return config;
    }

    private StoreInstance store(Long id, int type, String config) {
        var store = new StoreInstance();
        store.setId(id);
        store.setType(type);
        store.setConfig(config);
        return store;
    }

    @Test
    void fixedModelWithPgUsesMin() {
        when(modelRetrieve.findRuntimeConfigByModelId(1L)).thenReturn(model(1L, fixed(1024)));
        when(storeMapper.selectById(2L)).thenReturn(store(2L, 1, null));

        var capability = service.resolve(1L, 2L);

        assertThat(capability.modelMaxDimension()).isEqualTo(1024);
        assertThat(capability.storeMaxDimension()).isEqualTo(2000); // pgvector 引擎默认
        assertThat(capability.effectiveMaxDimension()).isEqualTo(1024);
        assertThat(capability.dimensionAdjustable()).isFalse();
    }

    @Test
    void adjustableModelWithPgCapsAtMin() {
        when(modelRetrieve.findRuntimeConfigByModelId(1L)).thenReturn(model(1L, adjustable(2048, 2048)));
        when(storeMapper.selectById(2L)).thenReturn(store(2L, 1, null));

        var capability = service.resolve(1L, 2L);

        assertThat(capability.modelMaxDimension()).isEqualTo(2048);
        assertThat(capability.effectiveMaxDimension()).isEqualTo(2000); // min(2048, pg 2000)
        assertThat(capability.dimensionAdjustable()).isTrue();
    }

    @Test
    void storeConfigOverrideRaisesPgCap() {
        when(modelRetrieve.findRuntimeConfigByModelId(1L)).thenReturn(model(1L, fixed(16000)));
        // pgvector 0.5+ 部署通过实例 config 覆盖上限
        when(storeMapper.selectById(2L)).thenReturn(store(2L, 1, "{\"maxDimension\":16000}"));

        var capability = service.resolve(1L, 2L);

        assertThat(capability.storeMaxDimension()).isEqualTo(16000);
        assertThat(capability.effectiveMaxDimension()).isEqualTo(16000);
    }

    @Test
    void undeclaredFixedModelFallsBackToProbe() {
        when(modelRetrieve.findRuntimeConfigByModelId(1L)).thenReturn(model(1L, new ModelExtConfigAttrs()));
        when(storeMapper.selectById(2L)).thenReturn(store(2L, 1, null));
        when(embeddingClient.probeDimension(1L)).thenReturn(768);

        var capability = service.resolve(1L, 2L);

        assertThat(capability.modelMaxDimension()).isEqualTo(768);
        assertThat(capability.dimensionAdjustable()).isFalse();
    }

    @Test
    void validateDimensionRejectsAboveStoreCap() {
        when(modelRetrieve.findRuntimeConfigByModelId(1L)).thenReturn(model(1L, fixed(1024)));
        when(storeMapper.selectById(2L)).thenReturn(store(2L, 3, null)); // ES 默认 2048

        assertThat(service.validateDimension(1L, 2L, 4096)).contains("上限");
        assertThat(service.validateDimension(1L, 2L, 1024)).isNull();
    }

    @Test
    void validateDimensionRejectsFixedModelMismatch() {
        // 固定输出 1536 的模型：冻结维度必须一致，否则检索维度不匹配
        when(modelRetrieve.findRuntimeConfigByModelId(1L)).thenReturn(model(1L, fixed(1536)));
        when(storeMapper.selectById(2L)).thenReturn(store(2L, 1, null)); // pg 2000

        assertThat(service.validateDimension(1L, 2L, 1024)).contains("固定");
        assertThat(service.validateDimension(1L, 2L, 1536)).isNull();
    }

    @Test
    void validateDimensionAllowsAdjustableBelowModelMax() {
        when(modelRetrieve.findRuntimeConfigByModelId(1L)).thenReturn(model(1L, adjustable(2048, 2048)));
        when(storeMapper.selectById(2L)).thenReturn(store(2L, 1, null)); // pg 2000

        assertThat(service.validateDimension(1L, 2L, 1024)).isNull();
        assertThat(service.validateDimension(1L, 2L, 2500)).contains("上限");
    }

    @Test
    void resolveWithoutInstanceReturnsModelOnly() {
        when(modelRetrieve.findRuntimeConfigByModelId(1L)).thenReturn(model(1L, fixed(1024)));

        var capability = service.resolve(1L, null);

        assertThat(capability.modelMaxDimension()).isEqualTo(1024);
        assertThat(capability.storeMaxDimension()).isNull();
        assertThat(capability.effectiveMaxDimension()).isEqualTo(1024);
    }

    @Test
    void resolveWithoutModelReturnsStoreOnly() {
        when(storeMapper.selectById(2L)).thenReturn(store(2L, 2, null)); // Milvus 32768

        var capability = service.resolve(null, 2L);

        assertThat(capability.modelMaxDimension()).isNull();
        assertThat(capability.storeMaxDimension()).isEqualTo(32768);
        assertThat(capability.effectiveMaxDimension()).isEqualTo(32768);
    }
}
