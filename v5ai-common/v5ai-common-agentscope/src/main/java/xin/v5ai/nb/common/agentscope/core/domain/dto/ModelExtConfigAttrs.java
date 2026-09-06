package xin.v5ai.nb.common.agentscope.core.domain.dto;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 模型扩展配置属性（v5ai_model.config 列，JSON 存储）。
 * <p>
 * 按模型类型分组：
 * - 通用：timeoutMs, maxRetries
 * - CHAT：temperature, topP, topK, maxTokens, frequencyPenalty, presencePenalty, stopSequences, seed, responseFormat, stream, extraBody
 * - EMBEDDING：embeddingDimension, encodingFormat
 * - RERANKER：rerankPath
 *
 * @author ZYW
 * @since 2026-09-03
 */
@Data
public class ModelExtConfigAttrs {

    // ==================== 通用配置 ====================

    /**
     * 超时时间，单位毫秒
     */
    private Long timeoutMs;

    /**
     * 最大重试次数
     */
    private Integer maxRetries;

    // ==================== CHAT 对话模型 ====================

    /**
     * 大模型的温度参数，控制生成文本的随机性
     */
    private Double temperature;

    /**
     * 大模型的top_p参数，控制生成文本的多样性
     */
    private Double topP;

    /**
     * 大模型的top_k参数，控制生成文本的多样性
     */
    private Integer topK;

    /**
     * 大模型的max_tokens参数，控制生成文本的最大长度
     */
    private Integer maxTokens;

    /**
     * 大模型的frequency_penalty参数，控制生成文本中重复词汇的惩罚程度
     */
    private Double frequencyPenalty;

    /**
     * 大模型的presence_penalty参数，控制生成文本中重复概念的惩罚程度
     */
    private Double presencePenalty;

    /**
     * 大模型的stop_sequences参数，控制生成文本的停止序列
     */
    private List<String> stopSequences;

    /**
     * 大模型的seed参数，控制生成文本的随机种子
     */
    private Long seed;

    /**
     * 大模型的response_format参数，控制生成文本的响应格式
     */
    private String responseFormat;

    /**
     * 大模型的stream参数，控制生成文本的流式输出
     */
    private Boolean stream;

    /**
     * 大模型的capabilities参数，控制生成文本的能力
     */
    private List<String> capabilities;

    /**
     * 是否为默认视觉模型
     */
    private Boolean defaultVisionModel;

    /**
     * 大模型的extra_body参数，控制生成文本的额外请求体参数
     */
    private Map<String, Object> extraBody;

    // ==================== EMBEDDING 向量模型 ====================

    /**
     * 向量维度：固定输出维度的实际值；可调维度模型（dimensionAdjustable=true）为输出上限
     * （嵌入维度事实源，落 KB.dimensionOfVectorModel 的取值依据）。
     */
    private Integer embeddingDimension;

    /**
     * 是否支持可调维度（如 OpenAI text-embedding-3 的 dimensions 参数 / Matryoshka 类模型）。
     * true 时嵌入请求可带 {@code dimensions=<冻结维度>}，实际维度可小于 embeddingDimension。
     */
    private Boolean dimensionAdjustable;

    /**
     * 可调维度上限；缺省时取 {@link #embeddingDimension}。
     */
    private Integer maxDimension;

    /**
     * 向量编码格式
     */
    private String encodingFormat;

    // ==================== RERANKER 重排模型 ====================

    /**
     * 重排模型的rerank_path参数，控制重排模型的路径
     */
    private String rerankPath;

    /**
     * 将 JSON 字符串解析为配置对象；空白输入返回 {@code null}。
     */
    public static ModelExtConfigAttrs fromJson(String json) {
        return StrUtil.isBlank(json) ? null : JSONUtil.toBean(json, ModelExtConfigAttrs.class);
    }

    /**
     * 序列化为 JSON 字符串；{@code null} 输入返回 {@code null}。
     */
    public static String toJson(ModelExtConfigAttrs config) {
        return config == null ? null : JSONUtil.toJsonStr(config);
    }
}