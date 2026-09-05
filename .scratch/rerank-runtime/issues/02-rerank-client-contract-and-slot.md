# RerankClient 契约与候选池槽位语义

Type: grilling
Status: resolved

## Question

定义 rerank 运行时端口（RerankClient）与 `RetrievalContextBuilder` 的接入槽，候选池语义（先检索 N>topK 个候选，rerank 后截 topK，N 由 `enterRerankCount` 控）。需决策：(1) RerankClient 契约签名（输入 query + 候选列表 → 输出重排 + 分数）；(2) 落在哪个模块/包（v5ai-rag 端口 vs v5ai-runtime）；(3) 槽位在「merge 之后、formatContext 之前」重排，还是替代 merge；(4) 无 rerank 模型/失败时静默退回原序。

## Answer

**RerankClient 契约**（端口，`v5ai-rag/core`，镜像 `EmbeddingClient`）

```java
package xin.v5ai.nb.rag.core;

import xin.v5ai.nb.rag.core.store.VectorStore;
import java.util.List;

/** 重排端口：候选池语义，对已召回候选按相关度重排并截取结果数。 */
public interface RerankClient {
    List<VectorStore.RetrievalHit> rerank(String query, List<VectorStore.RetrievalHit> candidates, int topK);
}
```

- 实现 `QwenRerankClient` 同在 `v5ai-rag/core`：HttpClient 调 qwen rerank（形状见 01 号票），取 `candidates[].content()` 作 `documents`，映射 `results[].index` 回重排。
- `RetrievalHit` 不可变：重排只置换列表顺序；`score` 保持向量/关键词原值，**不覆写**，rerank 分数随用随弃。

**候选池槽位**（`RetrievalContextBuilder`）

- 拆两个量：召回数 `recall`（rerank 关 = `DEFAULT_TOP_K(4)`，开 = `enterRerankCount`，N 由 03 解析）与结果数 `resultTopK = DEFAULT_TOP_K(4)`。
- 链：`vectorSearch`/`keywordSearch`(召回 `recall`) → `merge`（去重、按 `score` 降序、**截 `recall` 而非 `topK`**）→（有 resolved RERANK 模型时）`rerankClient.rerank(query, candidates, resultTopK)` → `formatContext`。
- 失败降级：无 resolved 模型 / 抛异常 → 静默退回 `merge` 原序截 `resultTopK`，不阻断。

**与 03 的边界**：本票只定「给定 resolved 模型 + N 时的行为」；开关/模型/N 的解析归 03。`merge` 由「截 topK」改「截 recall」是候选池语义的必然副作用。