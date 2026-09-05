# qwen-rerank 端点契约调研

Type: research
Status: resolved

## Question

确定 qwen-rerank 端点的确切请求/响应契约，作为 RerankClient 契约与 qwen 实现的落地依据。至少覆盖：DashScope `text-rerank`、ModelStudio（百炼）的 rerank 端点、以及 qwen 是否提供 OpenAI 兼容的 `/rerank`。输出：鉴权方式、base URL、请求体字段（`model`/`query`/`documents`/`top_n`/`return_documents`）、响应体字段（`results[index,relevance_score]` 或等价）、文档输入上限、错误语义。

## Answer

qwen rerank 有两套接口形状（统一 `Authorization: Bearer <API-KEY>`）：

- **A. DashScope 原生嵌套**（`POST {base}/api/v1/services/rerank/text-rerank/text-rerank`）：请求 = `{model, input:{query, documents[]}, parameters:{top_n?, return_documents?, instruct?, fps?}}`；响应 = `{output:{results:[{index, relevance_score, document?{text}}]}, usage:{prompt_tokens?, total_tokens}, request_id}`。base：国际 `https://dashscope-intl.aliyuncs.com/api/v1`，百炼 `https://{WorkspaceId}.{region}.maas.aliyuncs.com/api/v1`（region 默认 `cn-beijing`；`ap-southeast-1`/`us-east-1`）。
- **B. OpenAI 兼容扁平**（`POST {base}/compatible-api/v1/reranks`，注意复数是 `/reranks`）：请求 = `{model, query, documents[], top_n?, instruct?}`（无 `input`/`parameters`）；响应 = `{id, object:"list", model, results:[{index, relevance_score, document?{text}}], usage:{total_tokens}}`（无 `output`）。base：`https://dashscope-intl.aliyuncs.com/compatible-api/v1` 或 `https://{WorkspaceId}.{region}.maas.aliyuncs.com/compatible-api/v1`。
- **模型×端点分流**：`qwen3-rerank` 在国际站两条路都有；阿里云百炼只走 OpenAI 兼容扁平路径。`qwen3.7-text-rerank`/`gte-rerank-v2`/`qwen3-vl-rerank` 走原生嵌套路径。`gte-rerank`(v1) 已下线（2026-05-30）。
- **字段名**：全小写下划线，`model`/`query`/`documents`/`top_n`/`return_documents`/`instruct`/`fps`（请求），`index`/`relevance_score`/`document.text`/`usage.total_tokens`/`request_id`（响应）。`model`、`query`、`documents` 必填；`top_n`/`return_documents`/`instruct` 可选。`return_documents` 默认 `false`。
- **文档上限**：文本模型每请求最多 **500** 条文档；单条 Query/Doc 上限 `qwen3-rerank`/`gte-rerank-v2`=4,000 token、`qwen3.7-text-rerank`=30,000、`qwen3-vl-rerank`=8,000；单次请求总 token = `queryTokens×N + ΣdocTokens`，不超 120,000（gte-rerank-v2 为 30,000）。超限返回 HTTP 400（不截断）。
- **分数语义**：`relevance_score` ∈ 0.0~1.0，**越高越相关**；结果为降序；官方明确是**当前请求内相对分数，不可跨请求比较**。错误：HTTP 200 成功（无 `code`/`message`）；失败返回 `{code, message, request_id}`（如 `InvalidApiKey`）；400/401/429。
- **待验**：#B 是否接受 `return_documents` 参数（国际文档 body 清单不含它，但响应 schema 又提「return_documents=true 才回 document」）；完整错误码枚举。

详见 [qwen-rerank-endpoint-contract.md](../research/qwen-rerank-endpoint-contract.md)