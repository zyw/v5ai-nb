# qwen-rerank 端点契约调研笔记（可丢弃）

> 调研日期：2026（当前）。来源为官方文档（阿里云百炼 Model Studio + QwenCloud/qianwen 平台）原文与 curl 示例。本文是 `01-qwen-rerank-endpoint-contract.md` 票的落地依据，仅供后续 RerankClient 契约参考。

## TL;DR

qwen rerank 有**两套接口形状**，核心差异是：**qwen3-rerank 走「扁平 OpenAI 兼容」形状（无 `input`/`parameters` 包裹），而 qwen3.7-text-rerank / qwen3-vl-rerank / gte-rerank-v2 走「DashScope 原生嵌套」形状（`input`/`parameters` 包裹、响应含 `output`）**。结果字段高度一致：`results[].index` + `results[].relevance_score`，分数 0.0~1.0，越高越相关（且是本请求内的相对分数，不可跨请求比较）。鉴权统一为 `Authorization: Bearer <API-KEY>`。

---

## 1. 三条「路」与 BaseURL

| 路 | baseURL | 说明 |
|---|---|---|
| A. DashScope 国际站（QwenCloud）原生 | `https://dashscope-intl.aliyuncs.com/api/v1` | 原生嵌套路径 `/api/v1/services/rerank/text-rerank/text-rerank` |
| B. DashScope 国际站（QwenCloud）OpenAI 兼容 | `https://dashscope-intl.aliyuncs.com/compatible-api/v1` | 路径 `/reranks` |
| C. 阿里云百炼 Model Studio（业务空间 WorkspaceId，国内 maas）原生 | `https://{WorkspaceId}.{region}.maas.aliyuncs.com/api/v1` | 原生嵌套路径 `/api/v1/services/rerank/text-rerank/text-rerank`；region 默认 `cn-beijing`，另有 `ap-southeast-1`、`us-east-1` |
| D. 阿里云百炼 Model Studio OpenAI 兼容 | `https://{WorkspaceId}.{region}.maas.aliyuncs.com/compatible-api/v1` | 路径 `/reranks` |

- 国际站（QwenCloud/qianwen 平台，`dashscope-intl.aliyuncs.com`）**两条路都挂了 `qwen3-rerank`**：原生端点用嵌套形状，OpenAI 兼容端点用扁平形状。
- 阿里云百炼（maas.aliyuncs.com）文档明确分流：**`qwen3-rerank` 只走 OpenAI 兼容 `/reranks`（扁平）**；`qwen3.7-text-rerank` / `qwen3-vl-rerank` / `gte-rerank-v2` 走原生 `/api/v1/services/rerank/text-rerank/text-rerank`（嵌套）。
- 经典 `https://dashscope.aliyuncs.com/api/v1`（非业务空间国际 DashScope）为更早形态，当前百炼文档已改用业务空间高位 `{WorkspaceId}.{region}.maas.aliyuncs.com`（待验：非业务空间 URL 是否仍可用）。

## 2. 鉴权

- Header 统一：`Authorization: Bearer <API-KEY>`（阿里云百炼 API Key，形如 `sk-...`）。
- Header `Content-Type: application/json`（必填）。
- 失败码示例：`InvalidApiKey` → `{"code":"InvalidApiKey","message":"Invalid API-key provided.",...}`。
- HTTP 状态码：`200` 成功、`400` 参数/超限（不截断）、`401` 鉴权失败、`429` 限流。

---

## 3. 形状 A — DashScope 原生嵌套（`input` / `parameters` / `output`）

### 模型：qwen3-rerank（国际站原生）、qwen3.7-text-rerank、gte-rerank-v2、qwen3-vl-rerank

端点：`POST {base}/api/v1/services/rerank/text-rerank/text-rerank`

请求体（`qwen3-rerank` 国际原生 / 其余三者）：

```json
{
  "model": "qwen3.7-text-rerank",
  "input": {
    "query": "什么是文本排序模型",
    "documents": ["...", "...", "..."]
  },
  "parameters": {
    "top_n": 2,
    "return_documents": true,
    "instruct": "Given a web search query, retrieve relevant passages that answer the query."
  }
}
```

字段（区分大小写，均为小写下划线）：

| 键 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `model` | string(enum) | 是 | `qwen3-rerank` / `qwen3.7-text-rerank` / `gte-rerank-v2` / `qwen3-vl-rerank` |
| `input` | object | 是 | 输入包裹 |
| `input.query` | string \| object | 是 | 查询文本；max 4,000 token。VL 模型可为对象 `{"text":...}`/`{"image":...}` |
| `input.documents` | array | 是 | 候选文档字符串数组；见下方条数上限。VL 模型每个元素可为 `{"text"/"image"/"video": ...}` |
| `parameters` | object | 否 | 可选参数包裹 |
| `parameters.top_n` | int | 否 | 返回 top N，`>=1`；默认返回全部；超过文档总数则返回全部 |
| `parameters.return_documents` | bool | 否 | 是否在结果中回原文，默认 `false`（`qwen3-rerank` 国际原生也支持；百炼原生仅 `gte-rerank-v2`/`qwen3-vl-rerank` 支持） |
| `parameters.instruct` | string | 否 | 自定义排序指令（英文）；仅 `qwen3.7-text-rerank`/`qwen3-rerank`/`qwen3-vl-rerank` 生效。默认 QA 检索指令 `"Given a web search query, retrieve relevant passages that answer the query."`；语义相似度指令 `"Retrieve semantically similar text."` |
| `parameters.fps` | float | 否 | 仅 `qwen3-vl-rerank`，视频抽帧比例 `[0,1]`，默认 `1.0` |

响应体：

```json
{
  "output": {
    "results": [
      {"document": {"text": "..."}, "index": 0, "relevance_score": 0.9334521178273196},
      {"document": {"text": "..."}, "index": 2, "relevance_score": 0.34100082626411193}
    ]
  },
  "usage": {"prompt_tokens": 79, "total_tokens": 79},
  "request_id": "85ba5752-1900-47d2-8896-23f99b13f6e1"
}
```

| 键 | 类型 | 说明 |
|---|---|---|
| `output` | object | 输出包裹（qwen3-rerank 扁平路径下无此层，见 §4） |
| `output.results[]` | array | 按 `relevance_score` 从高到低排序 |
| `output.results[].document` | object | 仅 `return_documents=true` 时返回；结构 `{"text": "..."}` |
| `output.results[].index` | int | 对应输入 `documents` 列表的原始下标 |
| `output.results[].relevance_score` | double | 0.0~1.0，越高越相关；本请求内相对分数，不可跨请求比较 |
| `usage.prompt_tokens` | int | 仅 `qwen3.7-text-rerank` 返回；输入 token 总数 |
| `usage.total_tokens` | int | 本次消耗 token 总数 |
| `request_id` | string | 请求唯一标识 |
| `code` / `message` | string | 仅失败时返回错误码/详情 |

---

## 4. 形状 B — OpenAI 兼容扁平（`/reranks`）

端点：`POST {base}/compatible-api/v1/reranks`（注意是 **`/reranks`（复数）**，不是 `/rerank`）

请求体（qwen3-rerank）：

```json
{
  "model": "qwen3-rerank",
  "query": "What is a rerank model?",
  "documents": ["...", "...", "..."],
  "top_n": 2,
  "instruct": "Given a web search query, retrieve relevant passages that answer the query."
}
```

| 键 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `model` | string(enum) | 是 | 仅 `qwen3-rerank` |
| `query` | string | 是 | max 4,000 token |
| `documents` | string[] | 是 | max 500 条 |
| `top_n` | int | 否 | `>=1`，默认全部 |
| `instruct` | string | 否 | 同 §3 |
| `return_documents` | — | 待验 | 国际站文档的 body 字段清单**未列出** `return_documents`（只有 model/query/documents/top_n/instruct），但响应 schema 又写 `results.document`「仅 return_documents=true 时返回」，存在文档自相矛盾；待验该端点是否接受此参数 |

响应体：

```json
{
  "id": "85ba5752-1900-47d2-8896-23f99b13f6e1",
  "object": "list",
  "model": "qwen3-rerank",
  "results": [
    {"document": {"text": "..."}, "index": 0, "relevance_score": 0.9334521178273196}
  ],
  "usage": {"total_tokens": 79}
}
```

| 键 | 类型 | 说明 |
|---|---|---|
| `id` | string | 请求唯一标识（等价于原生 `request_id`） |
| `object` | string | 恒为 `"list"` |
| `model` | string | 回声模型名 |
| `results[]` | array | 按 `relevance_score` 降序（顶层直接是 `results`，无 `output`/`request_id` 包裹） |
| `results[].document` | object | `{"text": "..."}`；仅 `return_documents=true`（待验，见上） |
| `results[].index` | int | 原始下标 |
| `results[].relevance_score` | number | 0.0~1.0，越高越相关；相对分数 |
| `usage.total_tokens` | int | 消耗 token |

---

## 5. 文档输入上限（模型概览合并表）

来源：阿里云百炼 [rerank](https://help.aliyun.com/zh/model-studio/rerank) 与 [text-rerank-api](https://help.aliyun.com/zh/model-studio/text-rerank-api) 的「模型概览」表。

| 模型 | 最大文档数 | 单条(Query/Doc)最大 Token | 单次请求最大输入 Token | 语种 | 备注 |
|---|---|---|---|---|---|
| `qwen3.7-text-rerank` | 500 | 30,000 | 120,000（建议） | 201 种 | 文本 |
| `qwen3-rerank` | 500 | 4,000 | 120,000 | 100+ | 文本 |
| `gte-rerank-v2` | 500 | 4,000 | 30,000 | 50+ | 文本 |
| `qwen3-vl-rerank` | 文本 100 / 图片 40 / 视频 4（按模态） | 8,000 | 120,000 | 33 种 | 多模态 |
| `gte-rerank`（v1） | 已下线（2026-05-30） | — | — | — | 停产，迁移到 `qwen3-rerank` |

关键术语（官方口径）：
- **单条最大 Token**：单条 Query 或 Document 超过该限制 → API 返回 **HTTP 400**，且**不截断**输入。
- **单次请求最大 Token** 计算公式 = `Query Token × 文档数量 + 所有文档 Token 总和`，不得超过上限。
- `top_n` > 文档总数时返回全部文档。

## 6. 分数/排序语义

- 结果统一按 `relevance_score` **从高到低**排序返回。
- `relevance_score` 取值范围 **0.0 ~ 1.0**，**越高越相关**。
- 官方明确：该分数是**当前请求内的相对分数**，用于本次请求内排序，**不可作为跨请求比较的绝对值**。
- 对 RerankClient 的含义：只用于候选池内重排（比较同一 request 内各候选的相对大小），不应持久化/跨请求比较分数。

## 7. 错误语义

- 成功：HTTP 200，响应体**不含** `code`/`message`。
- 失败：HTTP 非 200，响应体形如 `{"code": "<错误码>", "message": "<详情>", "request_id": "..."}`。
  - 已知错误码：`InvalidApiKey`（"Invalid API-key provided."）。
  - 常见 HTTP：`400`（参数错误/输入超限，不截断）、`401`（鉴权失败）、`429`（限流）。
  - 完整错误码枚举见阿里云 [错误码](https://help.aliyun.com/zh/model-studio/error-code) 与 [限流](https://help.aliyun.com/zh/model-studio/rate-limit)（本文未逐一展开，**待验**逐个码的确切语义）。

## 8. 来源

1. [阿里云百炼 · 重排序 Reranking API（概览）](https://help.aliyun.com/zh/model-studio/rerank)
2. [阿里云百炼 · 排序模型（Rerank）· 文本排序 API 参考（请求/响应/错误样例主来源）](https://help.aliyun.com/zh/model-studio/text-rerank-api)
3. [阿里云百炼（英文国际）Text rerank](https://www.alibabacloud.com/help/en/model-studio/text-rerank-api)
4. [QwenCloud · DashScope reranking（国际原生端点 + 模型概览）](https://docs.qwencloud.com/api-reference/rerank/dashscope-rerank)
5. [QwenCloud · OpenAI compatible reranking（`/compatible-api/v1/reranks`）](https://docs.qwencloud.com/api-reference/rerank/openai-rerank)
6. [千问平台 · OpenAI 兼容重排序（镜像）](https://platform.qianwenai.com/docs/api-reference/rerank/openai-rerank)
7. [千问平台 · DashScope 重排序（镜像）](https://platform.qianwenai.com/docs/api-reference/rerank/dashscope-rerank)
8. [阿里云百炼 · 错误码](https://help.aliyun.com/zh/model-studio/error-code)