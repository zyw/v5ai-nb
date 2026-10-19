# 知识库与 RAG

知识库将文档变成 Agent 可检索的片段。上传或导入后，异步 Worker 完成解析、切片、Embedding 和索引；检索命中可随回答保存并展示为引用。

## 准备知识库

1. 配置一个 `EMBEDDING` 模型与可用的向量存储实例。
2. 新建知识库，选择嵌入模型、存储实例和解析/切片策略。
3. 上传 TXT、Markdown、PDF、DOCX 文档，或导入 URL。
4. 在任务列表等待索引完成；失败任务可重试或重新解析。
5. 在详情页查看原始文档、切片、知识检索和知识问答，再绑定到 Agent。

向量索引与业务库中的切片正文分开保存。存储实例可使用 pgvector、Milvus 或 Elasticsearch；MySQL 业务库部署时，向量检索仍走独立存储实例。

## 检索与问答调试

知识库详情页提供检索参数和知识问答面板。可以调节返回数量、问题改写、重排和阈值过滤，查看命中效果后再交给 Agent 使用。

![知识库详情：检索参数与知识问答](/images/rag-detail.png)

## Agent 如何调用 RAG

| 模式 | 行为 | 适合场景 |
|---|---|---|
| 强制调用 | 每轮运行前检索并把结果注入上下文 | 回答始终应参考内部资料 |
| 智能调用 | 注册 `rag_search` 工具，由模型自主决定检索与次数 | 问题有时与知识库无关 |

智能调用时，系统提示会列出 Agent 可用的知识库；工具调用仅能访问已绑定的知识库。检索结果可通过 `RETRIEVAL` 事件与助手消息引用回传。Agent 的**引用展示**开关只影响前端是否画出引用块，不改变检索和持久化。

## 常用管理 API

```http
POST /api/admin/knowledge-bases
POST /api/admin/knowledge-bases/{kbId}/documents
POST /api/admin/knowledge-bases/{kbId}/documents/url
GET  /api/admin/knowledge-bases/{kbId}/tasks
POST /api/admin/knowledge-bases/{kbId}/retrieve
POST /api/admin/knowledge-bases/{kbId}/chat/stream
POST /api/admin/agents/{agentKey}/knowledge-bindings
```

解析器、切片与检索细节见仓库 `docs/api/phase2.md`、`docs/adr/0004-document-chunker-per-rule-strategy.md`、`docs/adr/0005-rag-call-mode.md`。
