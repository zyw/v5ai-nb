# 模型与供应商

模型是 Agent 的基础资源。先创建 Provider，再配置具体模型；同一平台可管理多种供应商和 `CHAT`、`EMBEDDING`、`RERANK` 类型模型。

## 配置顺序

1. 在 **模型管理** 新建 Provider，设置 `providerKey`、名称和启用状态。
2. 新建模型，选择 Provider、模型类型和协议适配器，填写模型标识、基础地址及凭据。
3. 保存后点击 **测试连接**。对话、嵌入和重排模型使用各自的最小请求测试。
4. 需要时标记该类型的默认模型；同一类型最多一个默认。

凭据在服务端以 AES-GCM 加密。模型编辑页不回传密文；更新模型时不提交新凭据会保留原值。`baseUrl` 是 API 基础地址，具体模型标识由 `modelKey` 决定。

## 模型类型

| 类型 | 用途 |
|---|---|
| `CHAT` | Agent 对话、调试、标题和摘要等模型调用 |
| `EMBEDDING` | 文档切片向量化和知识库检索 |
| `RERANK` | 对检索候选结果重新排序 |

知识库绑定嵌入模型时应核对向量维度与存储实例维度。模型被 Agent 或知识库引用时，停用或删除会被拦截；次要模型的引用也在校验范围内。

## Agent 的主模型与次要模型

每个 Agent 绑定一个对话模型。可选的**次要模型**用于会话短标题和滚动摘要；未配置时回退到该 Agent 的对话模型。更换 Agent 绑定模型后要重新发布，门户才会读取新快照。

模型的 `config.capabilities` 可包含 `image`，用于判定门户是否开放图片附件入口。给原模型添加图片能力后，门户刷新或重登可读取新能力；如果给 Agent 换了模型，还必须先重新发布。

## 常用管理 API

```http
GET  /api/admin/providers
POST /api/admin/providers
GET  /api/admin/models
POST /api/admin/models
GET  /api/admin/models/options
POST /api/admin/models/{id}/test
PUT  /api/admin/models/{id}/enabled
PUT  /api/admin/models/{id}/default
```

详细字段和错误口径见仓库 `docs/api/phase1.md`，实际路由定义在 `v5ai-modules/v5ai-model` 的 Controller 中。
