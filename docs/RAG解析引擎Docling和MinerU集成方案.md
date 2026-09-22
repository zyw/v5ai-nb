# RAG 文档解析引擎接入设计：Docling 与 MinerU

> 状态：首期功能已实现（外部服务联调与真实服务压测待部署后完成）
>
> 适用版本：`v5ai-nb` 当前 RAG/Worker 架构（2026-09）
>
> 本文不是简单的依赖升级说明，而是把 Docling、MinerU 接入现有“上传/URL → 解析 → 切片 → 向量化 → 入库”链路所需的代码、配置、数据和运维改造一次说明清楚。

## 1. 结论与推荐方案

推荐将 Docling 和 MinerU 作为独立的 Python 解析服务，通过 HTTP 接入 Java Worker；不要把 Python 运行时、模型权重和 GPU 依赖直接塞进 `v5ai-rag` Maven 模块。

原因：

1. 当前 Worker 的职责边界已经是同步执行一条索引任务，最适合增加一个“解析端口 + HTTP 适配器”，而不是改变整个任务调度模型。
2. Docling 官方提供 `docling-serve` REST API，支持 multipart 文件上传、同步/异步转换、任务轮询和 Markdown/JSON 等结果；当前附件 `openapi.json` 对应的 MinerU Router 提供同步 `/file_parse` 和异步 `/tasks` 两种入口，异步流程是 multipart 提交、轮询 `/tasks/{task_id}`、再读取 `/tasks/{task_id}/result`。
3. 两个引擎都包含模型、OCR、表格/版面分析和 GPU 资源，独立服务更容易单独扩容、隔离故障、按 CPU/GPU 部署，并避免 Java 进程受 Python native 依赖影响。
4. 当前 `DocumentParser` 只返回 `String`，无法承载图片、页码、表格、解析诊断和引用定位信息。接入外部引擎时应先升级为结构化 `ParsedDocument`，再把其中的规范化文本交给现有切片/Embedding 链路。

推荐默认策略：

- `default`：保持现有 PDFBox/POI/Jsoup 行为，兼容存量知识库，并作为所有外部引擎的强制兜底路径。
- `docling`：优先用于 PDF、扫描 PDF、版面复杂、表格较多的文档。
- `mineru`：优先用于中文 PDF、复杂版面、需要稳定 Markdown/结构化内容和页级定位的文档。
- 首期统一以 Markdown/纯文本作为 RAG 的切片输入；图片和页级定位先保存为解析元数据，后续再扩展为可检索的多模态资源。

**兜底原则**：`default` 必须始终可用。知识库选择 `docling` 或 `mineru` 时，如果服务未启用、健康检查失败、连接/认证/超时、任务失败、响应解析失败或结果为空，解析器必须记录包含文档 ID、知识库 ID、目标引擎和原因的 `WARN` 日志，然后自动使用 `default` 重新解析。外部引擎失败不应直接把文档置为失败，也不应把同一个不可用的外部服务重复重试到耗尽任务重试次数；只有 `default` 解析也失败时，才按现有规则将任务置为失败。

## 2. 当前代码现状与缺口

### 2.1 已有扩展点

当前解析链路如下：

```text
KnowledgeTaskScheduler
  -> KnowledgeIndexingService.processTask
     -> KnowledgeDocumentContentStore.loadContent
     -> DocumentParser.parse(byte[], DocumentFileType)
     -> DocumentChunker.chunk(String, ChunkingOptions)
     -> EmbeddingClient.embed
     -> KnowledgeChunk + VectorStore
```

关键代码位置：

- 解析端口：[DocumentParser.java](/Users/jingyuan-mb01/javaspace/im/agent-sys/v5ai-nb/v5ai-modules/v5ai-rag/src/main/java/xin/v5ai/nb/rag/core/parser/DocumentParser.java)
- 现有注册表：[DocumentParserRegistry.java](/Users/jingyuan-mb01/javaspace/im/agent-sys/v5ai-nb/v5ai-modules/v5ai-rag/src/main/java/xin/v5ai/nb/rag/core/parser/DocumentParserRegistry.java)
- 内置 PDF 解析：[PdfBoxDocumentParser.java](/Users/jingyuan-mb01/javaspace/im/agent-sys/v5ai-nb/v5ai-modules/v5ai-rag/src/main/java/xin/v5ai/nb/rag/core/parser/PdfBoxDocumentParser.java)
- Worker：[KnowledgeIndexingService.java](/Users/jingyuan-mb01/javaspace/im/agent-sys/v5ai-nb/v5ai-modules/v5ai-worker/src/main/java/xin/v5ai/nb/worker/KnowledgeIndexingService.java)
- 知识库解析配置：[RagConfigDO.java](/Users/jingyuan-mb01/javaspace/im/agent-sys/v5ai-nb/v5ai-modules/v5ai-rag/src/main/java/xin/v5ai/nb/rag/core/config/RagConfigDO.java)
- 前端配置表单：[KnowledgeBasesView.vue](/Users/jingyuan-mb01/javaspace/im/agent-sys/v5ai-nb/v5ai-ui/src/views/KnowledgeBasesView.vue)

### 2.2 当前 Docling 配置只是“契约雏形”

`RagConfigDO.ParseParams` 已包含：

```java
private String engine;
private DoclingParams docling;
```

前端也已经可以选择 `default`/`docling`，并提交 OCR、表格识别、图片保存等字段。但后端注册表仍然在构造函数中硬编码：

```java
parsers.put(DocumentFileType.PDF, new PdfBoxDocumentParser());
parsers.put(DocumentFileType.DOCX, new DocxDocumentParser());
```

Worker 调用时也没有传入知识库配置，只传了文件类型：

```java
var text = documentParser.parse(content,
        DocumentFileType.valueOf(document.getFileType()));
```

因此当前选择 `engine=docling` 实际不会改变解析行为；MinerU 则尚未出现在 Java 配置、前端类型或解析器中。

### 2.3 Snail AI 中 Docling 的现有实现

`snail-ai` 已经实现了一条可复用的 Docling 接入链路，代码事实如下：

1. `DoclingParser` 通过 `DocumentParseRequest` 接收输入流、文件名、文件类型和知识库配置；先调用 `DoclingClient.isAvailable()` 访问 `/health`，再把输入流写入带扩展名的临时文件，避免 multipart 上传时丢失原始文件类型。
2. `DoclingClient.convert()` 使用本地 `Semaphore` 限制并发，固定调用异步接口 `POST /v1/convert/file/async`，同时提交 `to_formats=md` 和 `to_formats=json`；随后轮询 `GET /v1/status/poll/{task_id}`，成功后调用 `GET /v1/result/{task_id}`。
3. 请求参数由 `DoclingConvertOptions.fromConfig()` 从 `parseParams.docling` 映射，当前实际发送 `do_ocr`、`do_table_structure`、`image_export_mode`、重复的 `ocr_lang` 和 `pdf_backend`。服务级配置由 `DoclingProperties` 管理，包括启用开关、地址、整体超时、轮询、并发和图片大小/数量限制。
4. `DoclingResponseParser` 读取 `document.md_content` 和 `document.json_content`；对 JSON 中 `body.children` 的 `#/texts/*`、`#/groups/*`、`#/tables/*`、`#/pictures/*` 引用递归展开，重建标题、段落、表格和图片元素，同时提取图片 base64、页码、图注和统计信息。`DoclingMarkdownSanitizer` 会移除 Markdown 内嵌 base64 图片并压缩多余空行。
5. `DocumentPipeline` 在解析后、切片前执行 `DoclingImageOcrApplier`：优先复用 Docling 已有文字，再按配置调用 PaddleOCR，必要时才使用视觉模型兜底；切片/向量双写成功后，按数量和字节上限保存图片资源，并写入图片关联记录。
6. Docling 是增强能力而不是硬依赖：`DocumentPipeline.parseDocument()` 捕获 Docling 异常后重新加载原文，明确取得内置 parser，避免 fallback 再次选回 Docling；Snail AI 的现有行为已经体现了“记录告警并回退内置解析器”的方向。v5ai-nb 应把同样的回退规则扩展到 Docling 和 MinerU，最终任务仍可完成，但外部引擎的统计和结构化结果为空。

对应实现：

- [DoclingParser.java](/Users/jingyuan-mb01/javaspace/im/agent-sys/snail-ai/snail-ai-server/snail-ai-server-features/snail-ai-feature-rag/src/main/java/com/aizuda/snail/ai/features/rag/strategy/parser/DoclingParser.java)
- [DoclingClient.java](/Users/jingyuan-mb01/javaspace/im/agent-sys/snail-ai/snail-ai-server/snail-ai-server-features/snail-ai-feature-rag/src/main/java/com/aizuda/snail/ai/features/rag/docling/DoclingClient.java)
- [DoclingResponseParser.java](/Users/jingyuan-mb01/javaspace/im/agent-sys/snail-ai/snail-ai-server/snail-ai-server-features/snail-ai-feature-rag/src/main/java/com/aizuda/snail/ai/features/rag/docling/DoclingResponseParser.java)
- [DocumentPipeline.java](/Users/jingyuan-mb01/javaspace/im/agent-sys/snail-ai/snail-ai-server/snail-ai-server-features/snail-ai-feature-rag/src/main/java/com/aizuda/snail/ai/features/rag/pipeline/DocumentPipeline.java)

需要吸收但不能照搬的经验：v5ai-nb 的 Worker 目前使用同步方法 `DocumentParser.parse(byte[], DocumentFileType)`，且在解析后才加载知识库；应先加载知识库配置，再进入结构化解析端口。Snail 的图片资源和 OCR 处理可作为目标实现参考，但不应把 Snail 的表结构、API 或源码直接复制到 v5ai-nb。

### 2.4 当前模型无法表达外部解析结果

`DocumentParser.parse` 返回 `String`，这会丢失：

- 页码、块坐标和章节层级；
- 表格是否保留结构；
- 图片二进制、图片引用和替代文本；
- 引擎诊断、耗时、部分成功/失败状态；
- Docling 的 `json_content` 或 MinerU 的 `middle_json`/`content_list`（是否返回由附件参数控制）；
- 后续引用回链所需的页/块 locator。

所以不能只增加两个 `HttpClient` 类然后继续返回一个字符串；应先把解析结果抽象出来。

## 3. 官方能力与接入约束

### 3.1 Docling

Docling 官方文档说明其支持 PDF、DOCX、PPTX、XLSX、HTML、图片等多种输入，并可导出 Markdown、HTML、JSON 等格式；它还支持 OCR、表格结构、图片导出和本地/隔离环境运行。[Docling 总览](https://docling-project.github.io/docling/)

Docling 服务端 `docling-serve` 是 FastAPI HTTP 服务，当前文档列出的关键接口为：

- `POST /v1/convert/file`：multipart 文件同步转换；
- `POST /v1/convert/file/async`：提交异步转换任务；
- `GET /v1/status/poll/{task_id}`：轮询状态；
- `GET /v1/result/{task_id}`：取得结果；
- 官方 REST API 支持通过 `X-Api-Key` 发送。[Docling REST API](https://docling-project.github.io/docling/usage/api_server/rest_api/) 但当前 Snail AI 的 `DoclingClient` 没有设置该 header，v5ai-nb 接入时应把它作为可选的服务级认证配置补齐。

Snail AI 当前适配器实际发送 `do_ocr`、`do_table_structure`、`ocr_lang`、`image_export_mode`、`pdf_backend` 和 `to_formats=md,json`；`force_ocr`、`table_mode` 等可以作为后续版本透传字段，但不能在当前实现说明中当作已接入能力。单文件 JSON 响应中，`document.md_content`、`json_content`、`html_content`、`text_content` 只有在请求对应输出格式时才会填充；适配器仍应保留未知状态和服务端错误，不能仅用 HTTP 200 判断解析完全成功。

**实现约束**：适配器必须以运行时服务 `/docs` 的 OpenAPI schema 为最终契约。文档中的 `docling-serve` 版本会变化，配置中的 `pdfBackend`、`tableMode` 等字段应透传但不能写死某个未来版本的枚举。

### 3.2 MinerU（以附件 `openapi.json` 为准）

附件是一个 OpenAPI 3.1.0 文档，标题为 `FastAPI`、版本 `0.1.0`。它描述的是一个带 Router 的 MinerU HTTP 服务，当前契约只有以下路径：

| 方法 | 路径 | 行为 |
|---|---|---|
| `GET` | `/health` | 健康检查，OpenAPI 只声明 HTTP 200，响应体未定义 |
| `POST` | `/file_parse` | 同步解析 multipart 文件，等待上游完成后在本次响应返回结果 |
| `POST` | `/tasks` | 异步提交 multipart 文件，成功响应为 HTTP 202，返回体 schema 未定义 |
| `GET` | `/tasks/{task_id}` | 查询 Router task 状态，响应体 schema 未定义 |
| `GET` | `/tasks/{task_id}/result` | 获取异步任务结果，响应体 schema 未定义 |

`/file_parse` 和 `/tasks` 使用同一个 multipart 请求模型：必填 `files`（文件数组），并支持以下参数：

| 参数 | 类型/默认值 | 说明 |
|---|---|---|
| `lang_list` | 枚举数组，默认 `ch` | OCR 语言；附件枚举包含 `ch`、`ch_server`、`korean`、`ta`、`te`、`ka`、`th`、`el`、`arabic`、`east_slavic`、`cyrillic`、`devanagari` |
| `backend` | `hybrid-engine` | `pipeline`、`vlm-engine`、`hybrid-engine`、`vlm-http-client`、`hybrid-http-client` |
| `effort` | `medium` | 仅 hybrid 后端适用，支持 `medium`/`high`；`high` 才启用图像/图表分析 |
| `parse_method` | `auto` | PDF 解析方法；`auto`、`txt`、`ocr` |
| `formula_enable` | `true` | 是否解析公式 |
| `table_enable` | `true` | 是否解析表格 |
| `image_analysis` | `true` | VLM/hybrid 后端是否进行图像/图表分析 |
| `server_url` | `null` | 仅远程 VLM/hybrid 后端使用的 OpenAI-compatible 服务地址 |
| `return_md` | `true` | 是否返回 Markdown |
| `return_middle_json` | `false` | 是否返回 middle JSON |
| `return_model_output` | `false` | 是否返回模型输出 JSON |
| `return_content_list` | `false` | 是否返回 content list JSON |
| `return_images` | `false` | 是否返回提取出的图片 |
| `response_format_zip` | `false` | 是否返回 ZIP 而不是 JSON |
| `return_original_file` | `false` | 仅 `response_format_zip=true` 时有效 |
| `client_side_output_generation` | `false` | 是否由客户端延迟生成最终 Markdown/content list |
| `start_page_id` / `end_page_id` | `0` / `99999` | PDF 页码范围，起始页从 0 开始 |

因此，当前附件**没有声明** `/v1/uploads`、`tier`、`outputFormat`、`structured_content` 参数、`Authorization: Bearer` 认证或固定的结果 JSON 字段。适配器不能依据这些未出现在附件契约中的内容编写固定请求；认证若由部署层增加，应作为可选 header 配置，不能在本方案中宣称为接口必需项。由于状态和结果响应 schema 也是空对象，必须通过实际服务样例或服务端补充 schema 确认 task id 字段、终态字段、Markdown/middle JSON 字段及 ZIP 下载方式。

**实现约束**：首期使用异步 `/tasks`，实现“multipart 提交 → 轮询 `/tasks/{task_id}` → 获取 `/tasks/{task_id}/result`”；同步 `/file_parse` 作为小文件或连通性验证的可选路径。客户端应保留未知响应字段，不能把未定义的状态值硬编码成唯一成功/失败枚举；若结果为 ZIP 或二进制，按 `Content-Type` 分流处理。

## 4. 目标架构

### 4.1 解析端口

新增结构化端口，建议放在 `v5ai-rag`：

```java
public interface DocumentParseEngine {
    ParsedDocument parse(ParseRequest request);

    String engine();
}
```

`ParseRequest` 至少包含：

```java
public record ParseRequest(
        byte[] content,
        String filename,
        DocumentFileType fileType,
        Long documentId,
        Long knowledgeBaseId,
        RagConfigDO.ParseParams params,
        CancellationToken cancellationToken) {}
```

`ParsedDocument` 建议包含：

```java
public record ParsedDocument(
        String engine,
        String text,
        String markdown,
        String structuredJson,
        List<ParsedImage> images,
        List<ParsedLocator> locators,
        ParseDiagnostics diagnostics) {}
```

首期可以让 `text`/`markdown` 有值、`images`/`locators` 为空；但类型必须预留，避免第二次重构端口。

建议的子类型：

```java
public record ParsedImage(
        String name,
        String mimeType,
        byte[] bytes,
        String altText,
        Integer pageNumber,
        String locator) {}

public record ParsedLocator(
        int startOffset,
        int endOffset,
        Integer pageNumber,
        String blockType,
        String sourceId) {}

public record ParseDiagnostics(
        String status,
        long elapsedMillis,
        List<String> warnings,
        Map<String, Object> raw) {}
```

### 4.2 解析注册表

用 Spring Bean 注册，不再在 `DocumentParserRegistry` 构造函数中 `new` 实现：

```java
@Component
public class DocumentParseEngineRegistry {
    private final Map<String, DocumentParseEngine> engines;

    public DocumentParseEngineRegistry(List<DocumentParseEngine> candidates) {
        this.engines = candidates.stream().collect(Collectors.toUnmodifiableMap(
                DocumentParseEngine::engine, Function.identity()));
    }

    public DocumentParseEngine resolve(String configuredEngine) {
        var name = StringUtils.hasText(configuredEngine) ? configuredEngine : "default";
        var engine = engines.get(name.toLowerCase(Locale.ROOT));
        if (engine == null) {
            throw new ServiceException("不支持的文档解析引擎: " + name);
        }
        return engine;
    }
}
```

内置引擎包一层 `BuiltinDocumentParseEngine`，内部继续复用现有 PDFBox/POI/Jsoup parser；Docling 和 MinerU 各自实现 HTTP adapter。

解析器注册表必须提供显式的兜底入口：

```java
public ParsedDocument parseWithFallback(ParseRequest request) {
    var configured = resolve(request.params().getEngine());
    if (configured.engine().equals("default")) {
        return defaultEngine.parse(request);
    }
    try {
        return configured.parse(request);
    } catch (Exception exception) {
        log.warn("External document parser unavailable, fallback to default. "
                + "engine={}, documentId={}, reason={}",
                configured.engine(), request.documentId(), exception.getMessage());
        return defaultEngine.parse(request);
    }
}
```

这里的 `defaultEngine` 必须是明确注入的内置解析器，不能再次通过知识库的 `engine` 配置解析；日志使用 `WARN` 级别且不得输出 API Key、原始文件内容或完整响应体。

### 4.3 Worker 调整

`KnowledgeIndexingService` 需要先加载知识库，再解析；当前代码是先解析、后查询知识库，因此必须调整顺序：

```text
load task/document
  -> load knowledgeBase
  -> parseParams = knowledgeBase.config.parseParams
  -> engineRegistry.resolve(parseParams.engine)
  -> ParsedDocument parsed = engine.parse(request)
  -> document.parsedText = parsed.text/markdown
  -> persist parser diagnostics/images
  -> chunk(parsed.text/markdown)
  -> embed/store
```

解析文本建议优先级：

1. `ParsedDocument.text` 非空时使用 `text`；
2. 否则使用 `ParsedDocument.markdown`；
3. 外部引擎结果为空时先记录 `WARN` 并调用 `defaultEngine`；
4. `defaultEngine` 结果也为空时任务失败，错误信息包含引擎状态和服务端 errors。

首期不要把 Markdown 的图片 base64 直接送入切片和 Embedding。图片应转成稳定的资源引用或占位符，例如：

```text
![figure-1](resource://document/123/image/1)
```

并在切片 metadata 中保留 `pageNumber`、`locator` 和 `resourceId`。

## 5. 配置契约设计

### 5.1 知识库级 JSON

当前 `v5ai_knowledge_base.config` 已经是 JSON，新增字段无需数据库迁移。建议将 `RagConfigDO.ParseParams` 扩展为：

```java
public class ParseParams {
    private String engine; // default | docling | mineru
    private DoclingParams docling;
    private MineruParams mineru;
}

public class MineruParams {
    private List<String> langList;       // 默认 [ch]
    private String backend;              // pipeline | vlm-engine | hybrid-engine | vlm-http-client | hybrid-http-client
    private String effort;               // medium | high
    private String parseMethod;          // auto | txt | ocr
    private Boolean formulaEnable;
    private Boolean tableEnable;
    private Boolean imageAnalysis;
    private String serverUrl;            // 仅远程 VLM/hybrid 后端
    private Boolean returnMd;
    private Boolean returnMiddleJson;
    private Boolean returnModelOutput;
    private Boolean returnContentList;
    private Boolean returnImages;
    private Boolean responseFormatZip;
    private Boolean returnOriginalFile;
    private Boolean clientSideOutputGeneration;
    private Integer startPageId;
    private Integer endPageId;
}
```

Docling 参数建议把已存在的 `doOcr`、`doTableStructure` 保留，同时补充与官方 REST 参数对齐的字段：

```java
public class DoclingParams {
    private Boolean doOcr;
    private Boolean doTableStructure;
    private String imageExportMode; // placeholder | embedded | referenced
    private List<String> ocrLang;
    private String pdfBackend;
    private Boolean saveImages;
    private Integer maxImageCount;
    private Long maxImageBytes;
}
```

示例：

```json
{
  "chunkParams": {
    "sliceStrategy": "length",
    "maxChunkLength": 1800,
    "chunkOverlap": 150,
    "mergeShortSegments": true
  },
  "parseParams": {
    "engine": "mineru",
    "mineru": {
      "langList": ["ch"],
      "backend": "hybrid-engine",
      "effort": "medium",
      "parseMethod": "auto",
      "formulaEnable": true,
      "tableEnable": true,
      "imageAnalysis": true,
      "returnMd": true,
      "returnMiddleJson": true,
      "returnImages": false,
      "responseFormatZip": false,
      "startPageId": 0,
      "endPageId": 99999
    }
  }
}
```

### 5.2 服务级配置

服务 URL 和 API Key 不应放在知识库 JSON 中，也不应从前端提交。建议在 `application.yml`/环境变量中配置：

```yaml
v5ai:
  rag:
    parser:
      default-engine: default
      docling:
        enabled: true
        base-url: ${V5AI_DOCLING_URL:http://docling-serve:5001}
        api-key: ${V5AI_DOCLING_API_KEY:}
        connect-timeout: 5s
        read-timeout: 10m
        poll-interval: 2s
        poll-timeout: 30m
      mineru:
        enabled: true
        base-url: ${V5AI_MINERU_URL:http://mineru:8000}
        headers: {}
        connect-timeout: 5s
        read-timeout: 10m
        poll-interval: 3s
        poll-timeout: 30m
```

启动时应校验：

- engine 被选中但未启用时，任务处理阶段记录 `WARN` 并回退 `default`；管理端可以额外显示配置告警，但不能因为外部服务未启用而阻断索引任务；
- URL 必须是 `http`/`https`，禁止从知识库配置动态指定任意内网地址，避免 SSRF；
- Docling API Key 或 MinerU 可选 header 的值日志中必须脱敏；
- 服务健康检查失败不能导致应用启动失败，但在管理端应可见状态。

## 6. 两个适配器的实现细节

### 6.1 DoclingAdapter

建议实现步骤：

1. 根据 `DocumentFileType` 与文件名构造 multipart `files`。
2. 当前按 Snail AI 的实际实现请求 `POST {baseUrl}/v1/convert/file/async`；同步接口可作为后续优化，不应在首期同时维护两套响应协议。
3. headers 设置 `X-Api-Key`（仅配置了 key 时），不要把 key 写进 URL。
4. `to_formats=md,text,json` 中只请求实际需要的格式；首期至少请求 `md` 和 `json`。
5. 将 `doOcr`、`doTableStructure`、`ocrLang`、`imageExportMode`、`pdfBackend` 映射到 Docling options；未来新增字段必须先以服务 `/docs` schema 和契约测试为准。
6. async 返回后轮询 `/v1/status/poll/{taskId}`，成功后 GET `/v1/result/{taskId}`。
7. 当前 Snail AI 客户端按 `success`/`failure` 处理状态；v5ai-nb 应额外保留未知/部分成功状态和服务端错误，避免把服务升级后的状态误判为成功。
8. 从 `md_content`/`text_content` 得到 RAG 文本，从 `json_content` 保存结构化诊断或定位信息。

上述任一步骤出现服务不可用、请求异常、任务失败、响应无法解析或空结果，都由统一的 `parseWithFallback()` 捕获；记录 `WARN` 后重新调用 `defaultEngine`，不能由 `DoclingAdapter` 自己再次选择 Docling。

注意：Docling 文档当前标明 `docling-serve` 版本会随服务发布变化；生产环境应固定镜像版本，并在适配器集成测试中读取实际 `/docs` schema。

### 6.2 MineruAdapter

按附件契约实现：

1. 先调用 `GET {baseUrl}/health`；健康检查只作为可观测性，不应让应用启动失败。
2. 默认调用 `POST {baseUrl}/tasks`，使用 `multipart/form-data` 上传一个文件，并把 `MineruParams` 映射为同名 snake_case 字段：`lang_list`、`backend`、`effort`、`parse_method`、各类 `return_*` 和页码范围。
3. 从 HTTP 202 的 JSON 响应中提取 task id；由于 OpenAPI 没有定义响应 schema，适配器必须兼容服务实际返回的字段，并在字段缺失时给出可诊断错误。
4. 轮询 `GET {baseUrl}/tasks/{task_id}`，只根据实际响应识别完成、失败、取消等终态；不能假定与 Docling 使用相同的 `task_status` 字段。
5. 完成后调用 `GET {baseUrl}/tasks/{task_id}/result`。按 `Content-Type` 处理 JSON 或 ZIP；JSON 中优先取 Markdown，按配置保存 middle JSON/content list/model output，未知字段写入文档级 raw metadata。
6. 小文件可选调用 `POST {baseUrl}/file_parse`，但不应让同步请求承担大文件的 Worker 线程占用；同步接口的响应 schema 同样需要通过真实服务样例确认。
7. 除非部署后的服务文档明确要求，否则不自动添加 Bearer 认证；若实际部署要求认证，使用服务级可选 header 注入并禁止记录密钥。上述任一步骤失败时由统一 fallback 层记录 `WARN` 并调用 `defaultEngine`。

附件没有定义 MinerU 的 `tier`；配置页应展示 `backend`、`effort` 和 `imageAnalysis` 的资源/质量成本，不能继续使用 `standard`/`advanced` 作为后端参数。

## 7. 数据落库与图片处理

### 7.1 解析文本

Worker 当前有 `KnowledgeDocument.parsedText` 字段，但 `KnowledgeIndexingService` 只计算 `text`，没有将解析文本写回 `parsed_text`。接入新引擎时应补上：

```java
document.setParsedText(parsed.text());
document.setParseTime(parseTime);
documentService.save(document);
```

建议在解析成功、切片成功之后保存 `parsedText`；这样切片失败仍可诊断解析结果，但若不希望保存大文本，应配置最大长度或改用对象存储。

### 7.2 图片

当前源文档通过 `resourceId` 关联 `plm_resource`，解析图片不应复用源文件字段。推荐新增资源关联表，而不是向 `v5ai_knowledge_document` 添加单个图片 ID：

```sql
CREATE TABLE v5ai_knowledge_document_asset (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL,
    asset_type VARCHAR(32) NOT NULL, -- IMAGE / TABLE_RENDER / JSON
    name VARCHAR(255) NOT NULL,
    mime_type VARCHAR(128),
    resource_id BIGINT NOT NULL,
    page_number INTEGER,
    locator VARCHAR(512),
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

如果首期只做文本 RAG，建议 `saveImages=false`，并明确在 UI 上说明“图片不会单独进入知识库检索”。启用图片保存后必须同时完成：

- 解析结果中图片提取与大小/数量限制；
- 资源存储与权限校验；
- 文档删除时级联删除资产资源；
- 切片 metadata 的图片引用；
- 门户/调试页面鉴权读取图片。

### 7.3 引用定位

Docling 的 JSON 和 MinerU 的 structured/middle JSON 都可能包含页级或块级结构。不要把引擎原始 JSON 直接塞进每个 chunk；推荐保存文档级 JSON，chunk 只保留：

```json
{
  "documentId": 123,
  "chunkIndex": 4,
  "engine": "mineru",
  "pageStart": 7,
  "pageEnd": 8,
  "locators": ["page:7:block:12"]
}
```

这样向量库 metadata 不会膨胀，同时可以在引用展示时回查文档解析结果。

## 8. API、UI 与兼容策略

### 8.1 后端校验

在 `KnowledgeBaseServiceImpl.validKnowledgeBaseBeforeSave` 中新增：

- engine 只允许 `default`、`docling`、`mineru`；
- `docling` 时校验 Docling 参数范围；
- `mineru` 时校验附件中定义的 `backend`、`effort`、`parseMethod`、语言枚举、页码范围及各 `return_*` 组合；
- 未提供 `parseParams` 时回退 `default`；
- 已有 `engine=docling` 存量配置必须继续可读；
- 不要在保存知识库时强制调用外部解析服务，连通性检查应是独立管理操作。

### 8.2 API 文档

更新 `docs/api/phase2.md`：

- `parseParams.engine` 增加 `mineru`；
- 增加 `mineru` 参数示例；
- 说明 Docling 服务地址/API Key 以及 MinerU 可选 headers 由部署配置提供；
- 说明改解析引擎不会自动重建已有文档，需调用文档重新解析接口；
- 明确外部引擎任务失败时先记录 `WARN` 并使用 `default` 解析；只有 `default` 也失败时，文档状态才为 `4-处理失败`，错误写入 `errorMessage`；建议在解析诊断中记录 `fallbackFrom` 和原因。

### 8.3 UI

当前 `KnowledgeBasesView.vue` 只显示“内置解析器/Docling 引擎”，应扩展为三项：

- 内置解析器；
- Docling；
- MinerU。

UI 应根据引擎显示不同字段：

- Docling：OCR、表格模式、图片输出、PDF backend；
- MinerU：backend、effort、parseMethod、语言、公式/表格/图像分析、返回格式和页码范围。

服务 URL、API Key、GPU 模型状态不要由知识库表单展示或修改；管理员可在系统设置/健康检查页看到“已启用、不可达、认证失败”等脱敏状态。

## 9. 失败、重试和幂等

当前任务最大重试次数由 `KnowledgeTask` 控制，文档状态为：`0 待处理 → 1 解析中 → 2 处理中 → 3 完成 / 4 失败`。外部引擎接入后建议保持这个状态机，不要把远端 job 状态直接写入业务状态。外部引擎不可用时，回退 `default` 并继续当前任务，不进入失败状态。

必须区分：

- 网络连接超时、服务不可达、401/403：记录 `WARN`，本次任务直接回退 `default`；不要在同一任务内重复调用不可用外部服务；
- 429/服务繁忙：可记录 `WARN` 后回退 `default`，是否在后续独立任务重试由平台任务策略决定；
- 不支持的文件格式：不可重试；
- 解析结果为空：记录 `WARN` 并回退 `default`；
- 远端 job poll 超时：记录 task id 后回退 `default`；task id 仅用于诊断或后续清理，不应阻塞当前索引任务。

每次重试前要清理当前文档上一次生成的 chunk/vector；当前代码在写入新向量前已经执行按文档删除，适配器本身不应重复写向量。远端服务若支持幂等键，应使用 `documentId + contentHash + attempt`，避免 HTTP 重试产生多个不可追踪 job。

## 10. 测试计划

### 10.1 单元测试

新增：

- `DocumentParseEngineRegistryTest`：默认引擎、未知引擎、大小写和禁用引擎；
- `DocumentParseFallbackTest`：Docling/MinerU 未启用、健康检查失败、HTTP 异常、超时、空结果时均记录告警并调用 default；default 失败时才抛出异常；
- `DoclingAdapterTest`：multipart/options、API Key、async、success/failure、空结果；
- `MineruAdapterTest`：multipart 参数、可选 headers、job poll、JSON/ZIP 结果、超时和取消；
- `ParsedDocumentNormalizerTest`：text/markdown 优先级、图片占位符、locator metadata；
- `KnowledgeBaseServiceImplTest`：三个 engine 配置校验和非法 MinerU backend/effort/parseMethod/语言/页码范围；
- `KnowledgeIndexingServiceTest`：按知识库选择引擎，并保存 `parsedText`/parseTime。

外部 HTTP 测试应使用 MockWebServer/WireMock，不访问真实 GPU 服务；另提供一组可选 profile 的真实服务契约测试。

### 10.2 端到端验收矩阵

| 文件 | default | Docling | MinerU | 重点 |
|---|---:|---:|---:|---|
| 文本型 PDF | ✓ | ✓ | ✓ | 文本、页码、切片数量 |
| 扫描 PDF | 可能为空 | ✓ OCR | ✓ OCR | OCR 开关、耗时、错误 |
| 中文复杂 PDF | 一般 | ✓ | ✓ | 标题顺序、表格、引用定位 |
| DOCX/XLSX/PPTX | ✓ | ✓ | ✓/Flash | 结构化文本不丢失 |
| HTML/CSV | ✓ | ✓ | ✓/Flash | 编码、脚本过滤、表格 |
| 超大文件 | 受 JVM 内存影响 | async | job poll | 超时、取消、重试 |

每个成功用例都要验证：

1. 文档最终状态为 `3`；
2. `parsed_text` 非空；
3. chunk、业务行、向量行数量一致；
4. 重跑不会产生重复向量；
5. 检索能返回对应 chunk；
6. 失败用例的 `errorMessage` 不包含 API Key 或完整原始文档内容。

## 11. 实施顺序

建议拆成以下提交，便于回滚和审查：

1. **解析模型重构**：新增 `ParsedDocument`、`ParseRequest`、`DocumentParseEngine`，把内置 parser 包装为 default engine。
2. **Worker 接入配置选择**：从知识库读取 `parseParams`，按 engine 选择，保存 `parsedText` 和诊断。
3. **Docling adapter**：配置属性、HTTP client、sync/async、单元测试和 Docker/部署说明。
4. **MinerU adapter**：`/tasks` multipart 提交、task 状态轮询、结果 JSON/ZIP 分流、附件字段映射和单元测试。
5. **API/UI**：校验和 MinerU 表单；增加解析服务健康检查与错误提示。

### 11.1 当前代码落地情况

首期代码已经完成以下内容：

- `DocumentParseEngine`、`ParseRequest`、`ParsedDocument`、注册表和统一协调器；`default` 由内置 PDFBox/POI/Jsoup 解析器包装而成。
- `DoclingParseEngine`：健康检查、multipart 异步提交、状态轮询、结果读取、Docling 选项映射和可选 `X-Api-Key`。
- `MineruParseEngine`：严格按附件 `openapi.json` 使用 `/health`、`/tasks`、`/tasks/{task_id}` 和 `/tasks/{task_id}/result`，支持 multipart 字段映射以及 JSON/ZIP 结果分流。
- Worker 已按知识库 `parseParams.engine` 选择解析器，并将解析文本、实际引擎、结构化诊断和耗时写回文档；外部引擎未启用、不可用、异常、超时、空结果或响应解析失败时，统一记录 `WARN` 并调用 `default`。
- 知识库后端校验已覆盖 `default/docling/mineru`、MinerU backend/effort/parseMethod/语言和页码范围；管理端已增加 MinerU 配置表单。
- 管理端提供 `GET /api/admin/parser-engines/health`，展示 default、Docling、MinerU 的启用与健康状态；健康检查会携带部署配置中的可选认证信息，但不会返回或记录密钥。
- 默认外部服务开关为关闭，服务地址和认证信息只允许通过应用配置/环境变量提供，不进入知识库 JSON。

真实 Docling/MinerU 服务的接口联调、图片资源持久化和 OCR/多模态增强属于部署后的调试与后续迭代，不影响首期文本解析、切片和兜底链路。
6. **统一兜底与诊断**：外部引擎异常的 `WARN` 日志、`fallbackFrom`/原因记录、default 回退测试和监控指标。
7. **图片/引用增强**：资产表、资源权限、chunk locator；这一步不要和首期文本解析强绑定。
8. **文档与验收**：更新 phase2 API、部署文档、真实服务契约测试和回滚说明。

## 12. 部署建议

### Docling

- 固定 `docling-serve` 镜像版本；
- OCR/表格解析单独设置并发上限；
- 生产优先使用 async API；
- `/docs` 仅内网开放；
- API Key 通过环境变量注入；
- GPU 服务和 Java Worker 分开部署，Worker 只访问内部服务地址。

### MinerU

- 根据业务选择 `backend` 与 `effort`；`vlm-*`/`hybrid-*` 的 GPU、模型和内存成本要单独评估；
- 高质量 PDF 解析需要按实际部署的 MinerU runtime/模型要求准备 CPU、GPU、内存；
- 对 `/tasks` 提交、状态轮询和结果获取分别设置超时；
- 生产部署前通过实际服务响应确认 task id、终态和结果字段；OpenAPI 当前没有声明认证方式，不能预设 Bearer header；
- 远程 MinerU 服务收到的是源文件字节，部署网络和数据合规边界必须明确。

## 13. 参考资料

- [Docling 官方总览](https://docling-project.github.io/docling/)
- [Docling API server](https://docling-project.github.io/docling/usage/api_server/)
- [Docling REST API](https://docling-project.github.io/docling/usage/api_server/rest_api/)
- [Docling 支持格式](https://docling-project.github.io/docling/usage/supported_formats/)
- [MinerU 官方总览](https://opendatalab.github.io/MinerU/)
- [MinerU V1 HTTP API](https://opendatalab.github.io/MinerU/usage/http_api/)
- [MinerU Python SDK](https://opendatalab.github.io/MinerU/usage/sdk_api/)
- [MinerU Tiers and Runtimes](https://opendatalab.github.io/MinerU/usage/tiers/)
- [MinerU Output Formats and Result Contract](https://opendatalab.github.io/MinerU/reference/output_files/)
