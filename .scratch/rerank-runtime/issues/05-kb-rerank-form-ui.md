# KB 表单 Rerank UI

Type: grilling
Status: resolved
Blocked by: 03

## Question

在 KB 编辑表单（`v5ai-ui/src/views/KnowledgeBasesView.vue`）加入 Rerank 配置：开关（rerankEnabled）与 RERANK 模型下拉（仅 `type=RERANK` 的模型）。需决策：(1) 控件与字段映射（对齐 03 收敛后的单一事实源）；(2) 开关默认值（关）；(3) 是否透出 `enterRerankCount` 候选数（还是用后端默认）。

## Answer

无剩余 HITL 决策——本票三条（映射 / 默认值 / `enterRerankCount`）均已由 03 预先定死；直接落 spec。

**字段映射（对齐 03）**

- 「重排序」开关 `form.rerankEnabled` → `config.searchParams.rerankEnabled`（JSON，默认 false）。
- 「RERANK 模型」下拉 `form.rerankModelId` → `rerank_model_id` 列（`CreateKnowledgeBaseRequest.rerankModelId`）。
- 下拉**可选**（留空 = 默认）：空传 `undefined`，后端走 03 回退链（isDefault → 第一个 enabled → 降级）。
- `enterRerankCount` 不暴露（后端常量 `DEFAULT_RERANK_CANDIDATES = 20`）。

**改动点（仅 `v5ai-ui`，`client.ts` 零改动）**

1. `client.ts`：无需改——`RagSearchParams.rerankEnabled`、`CreateKnowledgeBaseRequest.rerankModelId`、`KnowledgeBaseResponse.rerankModelId` 均已存在。
2. `KnowledgeBasesView.vue`：
   - `form` 增 `rerankEnabled: false`、`rerankModelId: null`；`resetForm()` 复位。
   - `loadFormOptions()` 增第 5 路 `listModelOptions(adminToken, 'RERANK')` → `rerankModels`。
   - `openEdit()`：`form.rerankEnabled = !!kb.config?.searchParams?.rerankEnabled`；`form.rerankModelId = kb.rerankModelId ?? null`。
   - `buildRequest()`：`rerankModelId: form.rerankEnabled ? form.rerankModelId ?? undefined : undefined`；`config` 增平级 `searchParams: { rerankEnabled: form.rerankEnabled }`（与 chunkParams/parseParams 并列）。
   - 模板：在「混合检索」下方加「重排序」开关（复制 `n-switch` + `v-if` 下拉模式）；开关 ON 显示 RERANK 模型下拉，placeholder「不选则用默认 RERANK 模型」；`rerankModels` 为空时开关禁用，hint「请先在模型管理配置 RERANK 模型」。