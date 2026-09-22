package xin.v5ai.nb.rag.core;

import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import xin.v5ai.nb.common.agentscope.core.ModelConfigRetrieve;
import xin.v5ai.nb.common.agentscope.core.domain.dto.ModelExtConfigAttrs;
import xin.v5ai.nb.common.agentscope.core.domain.dto.ModelRuntimeConfigDTO;
import xin.v5ai.nb.rag.domain.StoreInstance;
import xin.v5ai.nb.rag.mapper.StoreInstanceMapper;

/**
 * 向量维度能力解析：给定「嵌入模型 × 存储实例」计算各自的维度上限与可用上限。
 * <p>
 * - 模型维度：取自模型 config JSON（{@link ModelExtConfigAttrs#embeddingDimension} 为固定输出值或
 *   可调模型的上限；{@code dimensionAdjustable=true} 时还可调 {@code maxDimension}）。模型未声明维度时
 *   尝试在线探测（{@link EmbeddingClient#probeDimension}）一次；探测失败返回 {@code null}（不可判定）。
 * - 库维度：引擎类型默认上限（pgvector 2000 / Milvus 32768 / ES 2048），存储实例 config 可覆盖
 *   （{@code maxDimension}，如 pgvector 0.5+ 部署允许 16000）。
 * - 可用上限 = min(模型, 库)，供 KB 创建/编辑时权威校验与 UI 提示。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VectorDimensionService {

    /** StoreInstance.type 常量 */
    private static final int TYPE_PG_VECTOR = 1;
    private static final int TYPE_MILVUS = 2;
    private static final int TYPE_ELASTICSEARCH = 3;

    /** 引擎类型默认维度上限 */
    /**
     * pgvector 默认 2000：取「ivfflat 与旧版 hnsw 索引的 2000 维硬上限」的保守值。
     * 部署 pgvector 0.5+（列支持 16000 维）且 hnsw 索引支持高维时，可在存储实例
     * config 用 {@code maxDimension} 显式覆盖（如 16000）；覆盖后检索走 hnsw。
     */
    private static final int PG_VECTOR_DEFAULT_MAX = 2000;
    private static final int MILVUS_DEFAULT_MAX = 32768;
    private static final int ES_DEFAULT_MAX = 2048;

    private final ModelConfigRetrieve modelConfigRetrieve;
    private final StoreInstanceMapper storeInstanceMapper;
    private final EmbeddingClient embeddingClient;

    /**
     * 维度能力结果。
     *
     * @param modelMaxDimension    模型最大维度（固定输出值或可调上限；不可判定为 null）
     * @param storeMaxDimension    存储实例最大维度（引擎默认或实例覆盖）
     * @param effectiveMaxDimension 当前可用上限 = min(模型, 库)；任一侧缺失时取另一侧
     * @param dimensionAdjustable  模型是否支持 dimensions 参数降维
     */
    public record DimensionCapability(Integer modelMaxDimension, Integer storeMaxDimension,
                                      Integer effectiveMaxDimension, boolean dimensionAdjustable) {
    }

    /**
     * 解析「嵌入模型 × 向量库实例」的维度能力。
     *
     * @param embeddingModelId      嵌入模型 ID（可空：仅返回库上限）
     * @param vectorStoreInstanceId 向量存储实例 ID（可空：仅返回模型上限）
     */
    public DimensionCapability resolve(Long embeddingModelId, Long vectorStoreInstanceId) {
        Integer storeMax = vectorStoreInstanceId == null ? null : storeMaxDimension(vectorStoreInstanceId);
        Integer modelMax = null;
        boolean adjustable = false;
        if (embeddingModelId != null) {
            var model = modelConfigRetrieve.findRuntimeConfigByModelId(embeddingModelId);
            if (model != null) {
                var dims = modelDimensions(model);
                modelMax = dims.max();
                adjustable = dims.adjustable();
            } else {
                log.warn("嵌入模型 {} 不存在", embeddingModelId);
            }
        }
        return new DimensionCapability(modelMax, storeMax, effective(modelMax, storeMax), adjustable);
    }

    /**
     * 校验知识库冻结维度是否可用（KB 创建/编辑时调用，权威校验）。
     *
     * @param embeddingModelId      嵌入模型 ID
     * @param vectorStoreInstanceId 向量库实例 ID
     * @param dimensionOfVectorModel KB 冻结维度
     * @return null 表示可用；否则返回拒绝原因（供 ServiceException 展示）
     */
    public String validateDimension(Long embeddingModelId, Long vectorStoreInstanceId, Integer dimensionOfVectorModel) {
        if (dimensionOfVectorModel == null || dimensionOfVectorModel <= 0) {
            return "向量维度必须大于 0";
        }
        var capability = resolve(embeddingModelId, vectorStoreInstanceId);
        if (capability.storeMaxDimension() != null && dimensionOfVectorModel > capability.storeMaxDimension()) {
            return "向量维度 %d 超过该向量库上限 %d".formatted(
                    dimensionOfVectorModel, capability.storeMaxDimension());
        }
        // 模型维度可判定且不可调（固定输出）：冻结维度必须与模型输出一致，否则检索维度不匹配
        if (!capability.dimensionAdjustable() && capability.modelMaxDimension() != null
                && !dimensionOfVectorModel.equals(capability.modelMaxDimension())) {
            return "模型输出维度固定为 %d，向量维度必须一致".formatted(capability.modelMaxDimension());
        }
        // 可调模型：冻结维度不得超过模型上限
        if (capability.dimensionAdjustable() && capability.modelMaxDimension() != null
                && dimensionOfVectorModel > capability.modelMaxDimension()) {
            return "向量维度 %d 超过该模型可调上限 %d".formatted(
                    dimensionOfVectorModel, capability.modelMaxDimension());
        }
        return null;
    }

    // ---------- helpers ----------

    /**
     * 模型维度：config 声明优先；未声明则在线探测一次；两者都不可得返回 max=null。
     */
    private MaxDim modelDimensions(ModelRuntimeConfigDTO model) {
        ModelExtConfigAttrs config = model.config();
        boolean adjustable = config != null && Boolean.TRUE.equals(config.getDimensionAdjustable());
        Integer declared = config == null ? null : config.getEmbeddingDimension();
        if (adjustable && declared == null && config.getMaxDimension() != null) {
            declared = config.getMaxDimension();
        }
        if (declared != null && declared > 0) {
            return new MaxDim(declared, adjustable);
        }
        // 未声明：在线探测一次（仅固定维度模型可判定；可调模型无上限声明按不可判定处理）
        if (!adjustable) {
            Integer probed = embeddingClient.probeDimension(model.modelId());
            return new MaxDim(probed, false);
        }
        return new MaxDim(null, true);
    }

    /**
     * 模型最大维度与是否可调。
     */
    private record MaxDim(Integer max, boolean adjustable) {
    }

    private Integer storeMaxDimension(Long instanceId) {
        StoreInstance instance = storeInstanceMapper.selectById(instanceId);
        if (instance == null) {
            log.warn("向量库实例 {} 不存在", instanceId);
            return null;
        }
        Integer type = instance.getType();
        int engineDefault = switch (type == null ? -1 : type) {
            case TYPE_PG_VECTOR -> PG_VECTOR_DEFAULT_MAX;
            case TYPE_MILVUS -> MILVUS_DEFAULT_MAX;
            case TYPE_ELASTICSEARCH -> ES_DEFAULT_MAX;
            default -> 0;
        };
        if (engineDefault <= 0) {
            return null;
        }
        // 实例 config 可覆盖（maxDimension 键）
        Integer override = readConfigMaxDimension(instance.getConfig());
        return override != null && override > 0 ? override : engineDefault;
    }

    private Integer readConfigMaxDimension(String configJson) {
        if (StrUtil.isBlank(configJson)) {
            return null;
        }
        try {
            var parsed = cn.hutool.json.JSONUtil.parseObj(configJson);
            Object value = parsed.get("maxDimension");
            return value instanceof Number number ? number.intValue() : null;
        } catch (Exception exception) {
            return null;
        }
    }

    private static Integer effective(Integer modelMax, Integer storeMax) {
        if (modelMax == null) {
            return storeMax;
        }
        if (storeMax == null) {
            return modelMax;
        }
        return Math.min(modelMax, storeMax);
    }
}
