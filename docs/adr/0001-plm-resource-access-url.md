# access_url 为稳定逻辑引用，不存具体对象定位符

`plm_resource.access_url` 曾按存储后端二态：LOCAL 存应用内鉴权下载路径，MINIO 存桶内裸 URL（`endpoint/bucket/key`）。MinIO 桶设为私有时裸 URL 直接 403，且 `access_url` 同时扮演「稳定引用」与「具体定位符」两个概念。我们决定：`access_url` 一律为应用内鉴权路径 `/api/admin/resources/{id}/preview`，与存储后端及桶策略无关，经应用代理 + Sa-Token 鉴权（`platform:resource:query`）读取。

**Status**: accepted

**Considered Options**:
- 预签名 URL（presigned）：短时效，无法持久化过期值，需改为「存 storageKey + 读取时生成」，为尚无外部消费方的场景引入时效复杂度 —— 否决。
- 桶公开直链：违背「资源默认私有」的安全立场 —— 否决。
- 维持双态行为：私有桶下 MINIO 路径失效 —— 根因，排除。

**Consequences**: 文件字节经应用代理读出（接受带宽代价）；删除 `buildAccessUrl` 的 MINIO 裸 URL 分支；存量 MINIO 行不迁移（模型 Logo 可重传，`provider.icon_url` 已有拷贝无法一并修正）；确有外部直链需求时另立独立接口，不把第二语义塞进本字段。
