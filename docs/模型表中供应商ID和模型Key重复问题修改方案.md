# 模型表中供应商ID和模型Key重复问题修改方案

这项需求已经从“删除一个约束”扩展为模型身份、默认选择、用量追踪和 RAG 回退策略的联合调整，属于一次跨模块设计变更。本轮只给方案，暂不修改代码。

## 一、目标模型语义

模型记录的唯一身份改为：

```text
v5ai_model.id
```

`provider_id + model_key` 不再代表平台内唯一模型，只代表“上游供应商下的模型名称”。

因此允许存在：

```text
provider_id = 1, model_key = llama3, base_url = http://ollama-a:11434
provider_id = 1, model_key = llama3, base_url = http://ollama-b:11434
```

两条记录通过不同的 `id`、凭据或 `base_url` 区分。

所有内部引用继续使用 `model_id`，不改为 `model_key`。

---

## 二、模型下拉选项

当前：

```java
providerId/modelKey (modelType)
```

改为：

```java
modelId/modelName (modelType)
```

例如：

```text
23/llama3 (CHAT)
24/llama3 (CHAT)
```

修改点：

- [`V5aiModelServiceImpl.java`](</Users/jingyuan-mb01/javaspace/im/agent-sys/v5ai-nb/v5ai-modules/v5ai-model/src/main/java/xin/v5ai/nb/model/service/impl/V5aiModelServiceImpl.java>)
- `models/options` API 的 label 生成逻辑
- 相关前端模型选择器无需改 value，仍使用模型 ID
- 模型列表页建议继续单独展示 `provider`、`modelKey`、`baseUrl`，方便区分多个 Ollama 实例

建议标签实际格式为：

```java
model.getId() + "/" + model.getModelName() + " (" + model.getModelType() + ")"
```

如果 `modelName` 为空，再回退到 `modelKey`。

---

## 三、模型唯一约束和索引

新增迁移，例如 V45：

```sql
ALTER TABLE v5ai_model
DROP CONSTRAINT IF EXISTS v5ai_model_provider_id_model_key_key;

CREATE INDEX IF NOT EXISTS idx_v5ai_model_provider_key
    ON v5ai_model(provider_id, model_key);
```

原因：

- 删除唯一约束后，原来的唯一索引也会消失
- 后续如果按供应商和模型 key 筛选，仍需要普通索引
- 不修改已经应用的 V1，符合当前迁移历史冻结规则

同时更新：

- `docs/db/schema.md`
- `V43__schema_comments.sql` 中“唯一组合”的错误描述
- `V5aiModel.java` 的类注释
- 相关 API 文档中“唯一模型”的表述

---

## 四、用量表增加 `model_id`

当前用量记录只保存：

```text
model_key
```

改为同时保存：

```text
model_id
model_key
```

其中：

- `model_id`：真正用于区分平台模型配置
- `model_key`：保留作为当时上游模型名快照，方便历史可读性
- 后续查询、展示、聚合以 `model_id` 为准
- 如果模型后来被删除，`model_id` 仍保留为历史 ID，不建议加数据库外键

### 数据库

新增迁移：

```sql
ALTER TABLE v5ai_model_usage
ADD COLUMN model_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_v5ai_model_usage_model
    ON v5ai_model_usage(model_id, created_at);
```

`model_id` 应允许为空，因为历史用量无法可靠回填：

- 旧用量表没有 provider ID
- 只凭 `model_key` 无法在未来重复模型场景下确定原始模型
- 只能让历史数据保持 `model_id = NULL`

### API 和运行时链路

需要修改：

- `ModelUsageDTO` 增加 `Long modelId`
- `V5aiModelUsage` 增加 `modelId`
- `V5aiModelUsageVo` / `ModelUsagePageVo` 增加 `modelId`
- `V5aiModelUsageServiceImpl.record()` 写入 `modelId`
- 运行时从 `MODEL_CALL` 事件中传递模型 ID

当前 `MODEL_CALL` 只携带模型名称：

```java
new AgentTextEvent.ModelCall(model.getModelName())
```

建议改为：

```java
new AgentTextEvent.ModelCall(modelId, modelName)
```

需要同步修改：

- `AgentTextEvent.ModelCall`
- `ModelStreamTextExecutor`
- `AgentScopeHarnessExecutor`
- `AgentScopeRuntime`
- `RuntimeRunEventDTO`
- `PersistingAgentRuntime`

运行时记账时保存：

```text
modelId = 本次实际调用的模型配置 ID
modelKey = 本次模型的上游 model key
```

这样即使两个 Ollama 配置都是 `llama3`，用量也能准确分开。

---

## 五、统一默认模型选择规则

建议把规则明确为“按模型类型选择”：

```text
给定 modelType：

1. 查找该类型中 is_default = true 且 enabled = true 的模型
2. 如果没有，查找该类型中 enabled = true 的最早模型
3. 如果仍没有，抛出明确的模型配置异常
```

也就是说：

```text
CHAT       使用 CHAT 默认模型，否则 CHAT 最早启用模型
EMBEDDING  使用 EMBEDDING 默认模型，否则 EMBEDDING 最早启用模型
RERANK     使用 RERANK 默认模型，否则 RERANK 最早启用模型
```

默认模型目前已经被业务代码限制为“同类型最多一个”，重复 `model_key` 不会改变这个规则。

### 建议新增统一接口

将当前：

```java
ModelRuntimeConfigDTO findFirstEmbeddingModel();
```

改为：

```java
ModelRuntimeConfigDTO findDefaultOrFirstEnabledModel(String modelType);
```

或使用枚举：

```java
ModelRuntimeConfigDTO findDefaultOrFirstEnabledModel(ModelType modelType);
```

查询顺序必须显式写出：

```java
.eq(modelType)
.eq(enabled, true)
.orderByDesc(isDefault)
.orderByAsc(id)
.last("LIMIT 1")
```

如果查询不到，抛出类似：

```text
未配置可用的 EMBEDDING 模型，请先配置并启用一个模型
```

而不是返回 `null`。

---

## 六、目前发现的不符合规则的位置

### 1. `ModelConfigRetrieveImpl.findFirstEmbeddingModel`

当前逻辑：

```java
.eq(modelType, "EMBEDDING")
.eq(enabled, true)
.orderByAsc(id)
```

问题：

- 没有优先默认模型
- 没有模型时返回 `null`
- 只实现了 EMBEDDING 类型

这是后端最主要的违规点。

修改为统一的 `findDefaultOrFirstEnabledModel(modelType)`。

---

### 2. `OpenAiCompatibleEmbeddingClient.resolveModel`

当前逻辑：

```java
embeddingModelId == null
    ? configRetrieve.findFirstEmbeddingModel()
    : configRetrieve.findRuntimeConfigByModelId(embeddingModelId)
```

它间接继承了错误的“只取最早启用模型”规则。

修改为：

```java
embeddingModelId == null
    ? configRetrieve.findDefaultOrFirstEnabledModel("EMBEDDING")
    : configRetrieve.findRuntimeConfigByModelId(embeddingModelId)
```

这里还有一个需要明确的行为变化：

当前没有模型时会回退到本地 HashEmbedding。你的新规则是“都没有就报错”，因此建议改为：

```text
没有默认模型，也没有启用模型 → 抛出模型配置异常
```

也就是移除 EMBEDDING 的隐式本地 Hash 回退，或者把 Hash 回退改成显式开发配置，例如：

```yaml
v5ai.rag.local-embedding-fallback-enabled: false
```

生产环境默认关闭。

---

### 3. `KnowledgeBaseDetailView.vue`

当前聊天模型初始化是：

```ts
modelParams.modelId = chatModels.value[0].value
```

它没有主动查找 `isDefault`。

应改为：

```ts
modelParams.modelId =
  chatModels.value.find(model => model.isDefault)?.value ??
  chatModels.value[0]?.value
```

更稳妥的做法是后端 options 接口本身也按：

```text
默认模型优先，之后按 ID 升序
```

返回，这样前端取第一项也符合规则。但前端仍建议显式 `find(isDefault)`，避免未来接口排序变化。

---

### 4. 已经符合规则的地方

以下逻辑已经显式优先 `isDefault`：

- `AgentsView.vue`
- `AgentEditView.vue`
- `SkillEditorView.vue`

这些地方主要需要配合新的模型 label，不需要改变选择算法。

---

### 5. 可选模型路径

`KnowledgeRetrievalServiceImpl.resolveRewriteModelId()` 当前只取：

```text
请求中的 modelId
→ 知识库配置中的 modelId
→ null
```

它是“问题改写”这个可选增强能力，不是主检索模型选择。

建议暂时保持：

```text
显式配置优先，没有配置则跳过问题改写
```

不要让“没有 CHAT 模型”导致普通知识库检索整体失败。

如果你希望所有 CHAT 模型调用也严格走默认模型回退，则需要改为：

```text
请求 modelId
→ 知识库配置 modelId
→ CHAT 默认模型
→ CHAT 最早启用模型
→ 没有则报错
```

这一点会改变当前“问题改写失败时回退原问题”的容错语义，建议单独确认。

---

## 七、建议的修改顺序

### 第一阶段：数据库和模型身份

- 删除 `provider_id + model_key` 唯一约束
- 增加普通索引
- 增加 `v5ai_model_usage.model_id`
- 更新 schema、注释和实体说明

### 第二阶段：用量链路

- `ModelUsageDTO` 增加 `modelId`
- `MODEL_CALL` 携带模型 ID
- `PersistingAgentRuntime` 记录模型 ID
- 用量查询和展示使用 `model_id`
- 保留 `model_key` 作为快照字段

### 第三阶段：统一模型选择

- 将 `findFirstEmbeddingModel()` 改成按类型的默认优先查询
- EMBEDDING、CHAT、RERANK 统一使用同一规则
- 没有可用模型时抛出明确异常
- 修改 RAG Embedding 的回退逻辑
- 修改知识库页面取默认聊天模型的逻辑

### 第四阶段：前端可辨识性

- 模型 options label 改成 `modelId/modelName(modelType)`
- 模型管理列表增加或突出 `id`、`baseUrl`
- 验证多个相同 provider/modelKey 的 Ollama 配置可以分别绑定和运行

### 第五阶段：测试

重点增加：

- 同一 provider、同一 modelKey 可以插入多条模型记录
- 两个相同 key 的模型按不同 ID 查询配置互不串线
- 默认模型优先于更早创建的普通模型
- 默认模型停用后回退到最早启用模型
- 没有默认且没有启用模型时抛错
- CHAT、EMBEDDING、RERANK 三种类型分别验证
- 用量记录包含正确 `model_id`
- 两个相同 `model_key` 的用量可以分别查询和聚合
- 历史用量 `model_id = NULL` 仍能正常展示

我建议采用上述方案，尤其是保留 `model_key` 作为快照、以 `model_id` 作为唯一平台身份。需要你确认的一点只有：**是否要彻底关闭“没有 EMBEDDING 模型时的本地 HashEmbedding 回退”，改为直接报错？**


# 我的答复
改为直接报错，其他的按照方案开发就可以了，1-5个阶段，一次性改完中间不要停