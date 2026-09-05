package xin.v5ai.nb.rag.core.config;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * RAG 检索与问答的页面配置参数（v5ai_knowledge_base.config 列，JSON 存储）。
 *
 * @author ZYW
 * @since 2026-08-30
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagConfigDO {

    /**
     * 检索参数（创建知识库时写入）
     */
    private SearchParams searchParams;

    /**
     * 生成答案时使用的对话模型参数（创建知识库时写入）
     */
    private ModelParams modelParams;

    /**
     * 文档切片策略（创建知识库时写入）
     */
    private ChunkParams chunkParams;

    /**
     * 文档解析引擎配置；为空时使用项目内置默认解析器。
     */
    private ParseParams parseParams;

    /**
     * 将 JSON 字符串解析为配置对象；空白输入返回 {@code null}。
     */
    public static RagConfigDO fromJson(String json) {
        return StrUtil.isBlank(json) ? null : JSONUtil.toBean(json, RagConfigDO.class);
    }

    /**
     * 序列化为 JSON 字符串；{@code null} 输入返回 {@code null}。
     */
    public static String toJson(RagConfigDO config) {
        return config == null ? null : JSONUtil.toJsonStr(config);
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParseParams {
        /**
         * 解析引擎：default 使用内置解析器，docling 使用外部 Docling Serve。
         */
        private String engine;
        /**
         * Docling 专属参数，仅 engine=docling 时生效。
         */
        private DoclingParams docling;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DoclingParams {
        /**
         * 是否启用 OCR，适用于扫描件或图片型 PDF。
         */
        private Boolean doOcr;
        /**
         * 是否启用表格结构识别。
         */
        private Boolean doTableStructure;
        /**
         * 图片导出模式，默认 embedded。
         */
        private String imageExportMode;
        /**
         * OCR 语言列表，例如 en、zh。
         */
        private List<String> ocrLang;
        /**
         * PDF 解析后端，随 Docling 服务版本调整。
         */
        private String pdfBackend;
        /**
         * 是否保存 Docling 提取出的图片到资源库。
         */
        private Boolean saveImages;
        /**
         * 单个文档最多保存的 Docling 图片数。
         */
        private Integer maxImageCount;
        /**
         * 单张 Docling 图片最大字节数。
         */
        private Long maxImageBytes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChunkParams {
        /**
         * length | delimiter | regex | smart
         */
        private String mode;
        /**
         * 单片段最大 token/字符量级（与 recursive splitter 一致）
         */
        private Integer maxChunkTokens;
        /**
         * 重叠部分的 token/字符量级
         */
        private Integer chunkOverlap;
        /**
         * mode=delimiter 时作为一级切分符
         */
        private String customDelimiter;
        /**
         * mode=regex 时的一级切分正则（Java Pattern）
         */
        private String chunkRegex;
        /**
         * mode=smart 时用于语义切片的对话模型配置 ID
         */
        private Long chunkModelId;
        /**
         * 是否合并短片段
         */
        private Boolean mergeShortSegments;
        /**
         * 是否对图片进行 OCR
         */
        private Boolean imageOcr;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SearchParams {
        /**
         * 检索结果数量
         */
        private Integer resultCount;
        /**
         * 是否启用检索结果重排序
         */
        private Boolean rerankEnabled;
        /**
         * 检索结果重排序模型 ID
         */
        private Long rerankModelId;
        /**
         * 检索结果重排序前进入重排序的文档数量
         */
        private Integer enterRerankCount;
        /**
         * 是否启用检索结果阈值过滤
         */
        private Boolean thresholdEnabled;
        /**
         * 检索结果阈值
         */
        private Double threshold;
        /**
         * 检索结果中稠密向量的权重
         */
        private Double denseWeight;
        /**
         * 检索结果中稀疏向量的权重
         */
        /**
         * 检索结果融合策略
         */
        private String fusionStrategy;
        /**
         * RRF 融合策略中的 k 值
         */
        private Integer rrfK;
        /**
         * 是否启用问题重写
         */
        private Boolean questionRewrite;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModelParams {
        /**
         * 生成答案的对话模型配置 ID
         */
        private Long modelId;
        /**
         * 生成答案时使用的文档片段数量
         */
        private Integer nearbySliceCount;
        /**
         * 生成答案时的提示信息
         */
        private String prompt;
    }
}
