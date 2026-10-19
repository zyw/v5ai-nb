# 创建与发布 Agent

Agent 组合模型、提示词、能力开关和对外展示信息。管理端编辑的是实时配置；线上运行使用发布版本快照。

## 创建一个 Agent

1. 打开 **Agent 管理**，创建 Agent，填写名称、描述与唯一的 `agentKey`。
2. 选择对话模型，按需设置次要模型和系统提示词。
3. 配置头像、开场白与推荐问题，作为对话门户首页内容。
4. 根据场景启用知识库、MCP、Skill、记忆和联网搜索。对应资源需分别完成绑定。
5. 在右侧 **预览与调试** 发送问题；确认配置后保存并发布。

![Agent 编辑页：基础信息、模型和实时预览](/images/agent-detail.png)

## 能力开关

| 开关 | 运行时效果 |
|---|---|
| RAG | 强制检索：每轮先检索；智能调用：注册 `rag_search` 工具，由模型决定是否调用 |
| MCP | 加载已绑定、启用且未被 `DENY` 的工具 |
| Skill | 加载已绑定 Skill 的当前发布版本 |
| 记忆 | 自动注入同会话历史，受条数和字符预算限制 |
| 联网搜索 | 注册 Tavily 搜索工具；服务器需配置 `V5AI_TAVILY_API_KEY` |

未启用的能力不会在运行时装配。`showCitations` 只控制引用块是否显示，不会关闭实际检索或引用记录。

## 发布与生效时机

发布会把模型、系统提示词、能力开关等配置写入 `v5ai_agent_version.snapshot_json`。线上 `/api/v1/agents/{agentKey}/...` 读取此快照；管理端 `/api/admin/agents/{agentKey}/chat/stream` 读取当前编辑行，因此草稿也可调试。

::: important 修改后要重新发布
模型、次要模型、系统提示词、引用展示等 Agent 字段在保存后不会立即影响线上。重新发布后，已打开的对话门户页面还需刷新或重登，才能重新获取 bootstrap 信息。
:::

知识库、MCP、Skill 的**绑定列表**没有固化在发布快照中，运行时按 `agentKey` 读取；但相应能力开关来自发布快照，关闭时绑定也不会加载。

## 对外调用

为已发布 Agent 创建并绑定 API Key 后，可调用[运行 API](/zh/reference/api)或使用[对话门户](/zh/guides/chat-portal)。API Key 以 BCrypt 校验，明文只在创建时展示。

实现依据：`AgentController`、`PublishedAgentResolver`、`AgentScopeRuntime`，以及仓库 `docs/api/phase1.md` 与 `docs/adr/0010-agent-secondary-model.md`。
