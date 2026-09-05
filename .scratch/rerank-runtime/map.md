# RERANK 运行时接入

## Destination

把 `qwen-rerank`（按知识库配置）接入运行时检索链路的「可选 Rerank」槽位。终点 = 一份可交接的实现 spec：RerankClient 契约、候选池重排语义、配置解析（KB → 模型 → 凭据/rerankPath → client）、失败降级，以及 KB 表单的 rerank UI。范围只回 Rerank 节点。

## Notes

- 领域：RAG 运行时检索（`v5ai-rag`：`RetrievalContextBuilder`、`EmbeddingClient`、`VectorStore`）。参考 `docs/superpowers/specs/2026-08-12-v5ai-nb-design.md` 运行时检索链路「可选 Rerank」。
- 会话建议技能：`grilling` + `domain-modeling`（决策类票）。
- 既定立场（发起人已敲定，勿重复辩论）：现在接入；按知识库配置；候选池语义（`enterRerankCount`）；失败静默降级为不重排；供应商 = qwen-rerank（真实端点在手）。
- 配置事实源（03 已收敛）：开关 `rerankEnabled` = `config.searchParams.rerankEnabled`（RagConfigDO JSON）；模型 = `v5ai_knowledge_base.rerank_model_id` 列；候选数 N = 后端常量 `DEFAULT_RERANK_CANDIDATES=20`；`searchParams.rerankModelId`（JSON）作废忽略。
- 现状：01/02/03/05 均已 resolved，**实现 spec 已可交接**；仅剩 [04 qwen-rerank 真机验配](issues/04-qwen-rerank-validation.md) 属实现落地后的验收（需真实端点 + key），非规划期内可解。

## Decisions so far

<!-- 一行一条已关闭的决策，链接到票 -->
- [qwen-rerank 端点契约调研](issues/01-qwen-rerank-endpoint-contract.md): qwen rerank 有 DashScope 原生嵌套（`/api/v1/services/rerank/text-rerank`，`input`/`parameters`/`output`）与 OpenAI 兼容扁平（`/compatible-api/v1/reranks`，顶层 `model/query/documents/top_n`）两套；结果均 `results[].index + relevance_score`（0~1 越高越相关，本请求内相对分），每请求最多 500 文档，`gte-rerank`(v1) 已下线。
- [RerankClient 契约与候选池槽位语义](issues/02-rerank-client-contract-and-slot.md): 端口 + 实现均在 `v5ai-rag/core`（镜像 `EmbeddingClient`），最小重排契约 `rerank(query, candidates, topK)`；候选池语义 = 召回 `recall`(=`enterRerankCount`) > 结果 `topK`(=4)；`score` 不覆写，失败静默退回原序。
- [Rerank 配置解析路径](issues/03-rerank-config-resolution.md): 开关 = `config.searchParams.rerankEnabled`(JSON)，模型 = `rerank_model_id` 列，N = 常量 20；解析回退链 = 绑定模型(isDefault 优先→第一个 enabled→降级)；`ModelRuntimeConfigDTO` 增补 `config`，`rerankPath`/凭据镜像 embedding 下传。
- [KB 表单 Rerank UI](issues/05-kb-rerank-form-ui.md): 开关→`config.searchParams.rerankEnabled`、下拉→`rerank_model_id` 列（可选=默认）；不暴露 `enterRerankCount`；`client.ts` 零改动，仅 `KnowledgeBasesView.vue` 加字段/加载/映射/控件。

## Not yet specified

<!-- fog：超时/成本/限流（含 500 文档与总 token 上限）；RETRIEVAL 事件是否携带 rerank 分数 -->

- rerank 请求超时 / 成本 / 限流（含跨 KB 合并候选池的 500 文档与总 token 上限，见 01）
- `RETRIEVAL` 事件与引用是否暴露 rerank 分数

## Out of scope

<!-- 本图之外：SearchParams 其余 dormant 字段，Rerank 的兄弟节点 -->

- `threshold` / `thresholdEnabled`（相似度阈值过滤）
- `denseWeight` / `fusionStrategy` / `rrfK`（稠密/稀疏融合）
- `questionRewrite`（查询改写）