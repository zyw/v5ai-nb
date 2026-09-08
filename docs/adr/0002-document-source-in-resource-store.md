# 知识库文档源文件统一存资源存储（plm_resource）

`v5ai_knowledge_document.content`（BYTEA）曾直接落库上传文件的字节、URL 导入的抓取文本，导致「资源存储」页看不到知识库文档文件，且知识库导入绕过了通用资源存储（`plm_resource`）的上传链与 `v5ai.storage.type`（LOCAL/MinIO）存储后端。

我们决定：知识库文档（本地上传与 URL 导入）的源文件**只写入通用资源存储**（`biz_type=DOCUMENT`，`biz_id=文档 id`），文档行以 `resource_id` 指向该资源，`content` 列对新增数据不再写入、仅作存量兜底读取。Worker 解析/重新解析、文档预览/下载统一走「有 `resource_id` 读资源，否则读 BYTEA」的字节源决策。

**Status**: accepted

**Considered Options**:
- 双写（资源 + BYTEA 都写）：改动最小但同一文件存两份，且两处易漂移 —— 否决。
- 单源 + 存量迁移：一致性最好，但迁移与读链路改造面大，与「开发阶段存量不迁移」的既定决策冲突 —— 否决（存量不迁移，BYTEA 兜底即可）。

**Consequences**:
- 模块解耦：rag 不依赖 platform，通过 common-core 的 `ResourceContentPort` 端口（platform 以 `plm_resource` 实现）读写字节。
- 删除联动：删除文档同步删除其关联资源（幂等、失败仅告警不阻断）；资源页手动删除 DOCUMENT 资源后，文档仍在但预览/下载/重新解析不可用（字节源缺失，解析任务置失败并注明「源文件已不存在」）。
- `content`（BYTEA）列保留但不写入，历史数据无需迁移。