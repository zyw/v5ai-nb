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
         * 解析引擎：default 使用内置解析器，docling/mineru 使用外部服务；外部服务失败时回退 default。
         */
        private String engine;
        /**
         * Docling 专属参数，仅 engine=docling 时生效。
         */
        private DoclingParams docling;
        /**
         * MinerU 专属参数，仅 engine=mineru 时生效。
         */
        private MineruParams mineru;
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
    public static class MineruParams {
        /**
         * OCR 语言列表，例如 {@code ch}、{@code korean}。
         */
        private List<String> langList;

        /**
         * MinerU 解析后端：{@code pipeline}、{@code vlm-engine}、{@code hybrid-engine}、
         * {@code vlm-http-client} 或 {@code hybrid-http-client}。
         */
        private String backend;

        /**
         * 解析工作量等级，支持 {@code medium} 或 {@code high}；主要对 hybrid 后端生效。
         */
        private String effort;

        /**
         * PDF 解析方式：{@code auto} 自动选择、{@code txt} 文本提取、{@code ocr} 强制 OCR。
         */
        private String parseMethod;

        /**
         * 是否启用公式识别。
         */
        private Boolean formulaEnable;

        /**
         * 是否启用表格结构识别。
         */
        private Boolean tableEnable;

        /**
         * 是否启用图片或图表分析。
         */
        private Boolean imageAnalysis;

        /**
         * 远程 VLM 或 hybrid 后端使用的模型服务地址。
         */
        private String serverUrl;

        /**
         * 是否返回 Markdown 结果。
         */
        private Boolean returnMd;

        /**
         * 是否返回中间结构化 JSON。
         */
        private Boolean returnMiddleJson;

        /**
         * 是否返回模型原始输出。
         */
        private Boolean returnModelOutput;

        /**
         * 是否返回内容列表结构。
         */
        private Boolean returnContentList;

        /**
         * 是否返回文档中的图片资源。
         */
        private Boolean returnImages;

        /**
         * 是否以 ZIP 格式返回结果包。
         */
        private Boolean responseFormatZip;

        /**
         * 是否返回原始上传文件。
         */
        private Boolean returnOriginalFile;

        /**
         * 是否由客户端生成输出结果。
         */
        private Boolean clientSideOutputGeneration;

        /**
         * 解析起始页码，默认由 MinerU 服务解释。
         */
        private Integer startPageId;

        /**
         * 解析结束页码，默认由 MinerU 服务解释。
         */
        private Integer endPageId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChunkParams {
        /**
         * 切片策略：length | delimiter | regex | smart
         */
        private String sliceStrategy;
        /**
         * 单片段最大字符数
         */
        private Integer maxChunkLength;
        /**
         * 重叠部分的 token/字符量级
         */
        private Integer chunkOverlap;
        /**
         * sliceStrategy=delimiter 时作为一级切分符
         */
        private String customDelimiter;
        /**
         * sliceStrategy=regex 时的一级切分正则（Java Pattern）
         */
        private String chunkRegex;
        /**
         * sliceStrategy=smart 时用于语义切片的对话模型配置 ID
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
