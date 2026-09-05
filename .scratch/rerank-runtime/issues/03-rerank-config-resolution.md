# Rerank 配置解析路径

Type: grilling
Status: resolved

## Question

收敛 rerank 配置的解析路径：`v5ai_knowledge_base.rerank_model_id`（列）与 `RagConfigDO.SearchParams.rerankEnabled/rerankModelId/enterRerankCount`（JSON）两处双栖，需定单一事实源与回退链。需决策：(1) 开关 + 模型读哪一处（去重/合并）；(2) KB 开 rerank 未绑模型时回退「默认 RERANK 模型（isDefault）」；(3) 复用 `ModelConfigRetrieve` 增加按 KB 取 RERANK 模型的解析；(4) `rerankPath`/凭据如何下传到 client。

## Answer

**单一事实源（收敛）**

- 开关：`rerankEnabled` = `RagConfigDO.fromJson(kb.config).searchParams.rerankEnabled`（JSON，默认 false）。`searchParams.rerankModelId`（JSON）与列重复，作废、不读取，避免三栖漂移。
- 模型：`v5ai_knowledge_base.rerank_model_id` 列（唯一活路径，现有 BO/VO/service 已通）。
- 候选数：后端常量 `DEFAULT_RERANK_CANDIDATES = 20`（挂 `RetrievalContextBuilder`），不配 UI、不读 `searchParams`。

**解析回退链（运行时）**

1. `rerankEnabled == false` → 不重排（现状路径）。
2. `rerankEnabled == true`：
   - `rerank_model_id != null` → `configRetrieve.findRuntimeConfigByModelId(id)`；需 `modelType == "RERANK"`，否则（脏数据/已删）视为关、降级不重排。
   - `rerank_model_id == null` → 回退默认：`type=RERANK && enabled`，`ORDER BY is_default DESC, id ASC LIMIT 1`（isDefault 优先，否则第一个 enabled）；无 → 降级不重排。

**端口变更**

- `ModelConfigRetrieve` 新增 `findDefaultOrFirstRerankModel()`（上述 `ORDER BY is_default DESC, id ASC`、`type=RERANK`、`enabled`、LIMIT 1，实现落在 `ModelConfigRetrieveImpl`）。绑定模型走既有 `findRuntimeConfigByModelId`，不另开方法。
- `ModelRuntimeConfigDTO` 增补 `config`（模型原始 config JSON）字段，`toRuntimeConfig` 从 `model.getConfig()` 注入。

**rerankPath / 凭据下传**

- 镜像 `OpenAiCompatibleEmbeddingClient`：`QwenRerankClient` 接收 `ModelRuntimeConfigDTO` → `CredentialCipher.decrypt(credentialsCiphertext)` → `AgentModelCredentialConfig.parse`（apiKey/baseUrl）；`rerankPath` = `ModelExtConfigAttrs.fromJson(config).rerankPath`，与 baseUrl 组合出 01 号票那两套形状的端点路径。