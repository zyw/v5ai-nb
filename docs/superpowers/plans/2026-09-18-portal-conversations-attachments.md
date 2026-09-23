# 对话门户：附件上传与多模态、服务端会话列表、停止对话 —— 实施方案

## 1. 背景

独立对话门户（`v5ai-ui-chat`）目前只能做「拿 API Key 换 bootstrap → 挑一个已发布 Agent → 流式对话」，会话列表存在浏览器 localStorage，附件入口硬编码禁用。本次要把门户补成一个可用的对话产品：会话要能列出来、能改名、能归档；对话要能带图片；回答要能停。

代码现状（已逐条核实，不是推测）：

- 会话与运行**没有任何列表/改名/归档/取消端点**；`v5ai_conversation` 只有 `id/agent_key/user_id/created_at/updated_at`，且 **`user_id` 恒为 NULL**（`ensureConversation` 只写 id + agent_key）。
- `GET .../chat/conversations/{id}` 已存在，但 `findByConversationId` **没有 ORDER BY**，消息顺序无保证。
- run 状态是裸字符串 `RUNNING`/`COMPLETED`/`FAILED`，**没有 CANCELED**；`v5ai_run.completed_at` 列存在但**全仓没有任何 setter**，从不写入。
- 客户端断流（门户的 AbortController）→ Reactor cancel → `doOnComplete` 不执行 → **run 永久停在 RUNNING、助手回答不落库、用量照记**。即"停止"不是新功能，是修一个正在发生的 bug。
- 模型链路**全链路纯文本**：`ChatBo` → `AgentRunBo` → `SessionMessage.content(String)` → `Msg.textContent(String)` → `v5ai_message.content TEXT`，一处 ContentBlock 位置都没有。SDK 层（AgentScope 2.0.3）的 `ImageBlock`/`URLSource`/`Base64Source` 完备，openai 与 dashscope 适配器都已实现转换。
- 模型配置里 `capabilities`（含 `image`）与 `defaultVisionModel` **已被管理端 UI 写入，但 `AgentScopeModelFactory` 从不读取**；`ModelToolSupportResolver` 是空接口。
- `plm_resource` 已有 `biz_type`/`biz_id`/`mime_type`/`access_url`/`created_by`，`ATTACHMENT` 已在白名单；但上传只有管理端 multipart 入口，**API Key 侧不存在任何上传或资源读取入口**（唯一例外是头像代读）。资源读取当前只有 `platform:resource:query` 权限校验，**无任何归属校验**；上传**无文件头校验**、mime 直接采信客户端。
- 过滤器 `AgentApiKeysServletFilter` 只注册在 `/api/v1/agents/*`，且只从请求头取 Key；路径前缀 `auth` 已被占用为保留前缀（并有一条"Agent 恰好叫 auth"的回归测试）。

## 2. 已定决策

| # | 议题 | 定论 |
|---|---|---|
| Q1 | 交付范围 | 只有门户 `v5ai-ui-chat` + 后端运行接口；管理端 ChatDebug/PreviewPanel 不动 |
| Q2 | 契约严格性 | 按本项目约定：`apikey` 走 `Authorization` 请求头 |
| Q3 | 会话归属键 | **API Key**（`api_key_id`），不是用户 → ADR 0006 |
| Q4 | 存量本地会话 | 直接废弃 localStorage 列表 |
| Q5 | 归档语义 | 软状态 `archived_at`：默认隐藏、可取消归档、**归档后禁止继续对话** |
| Q6 | 停止语义 | 先断流 + 显式服务端取消（两者都要） |
| Q7 | 禁用 MCP/Skill | 接口支持 `disabledMcpServerIds`/`disabledSkillIds`，**门户不做 UI**（只能收窄） |
| Q8 | 会话跟踪ID | **不做**——与 `conversation.id` 语义重复；列表第三列直接返回 `conversationId`（门户列名建议「会话 ID」） |
| Q9 | 附件目标 | **真多模态**：图片打通到模型输入 |
| Q10 | 上传接口 | `POST /api/v1/agents/resource/upload`，Key 走请求头，参数走 **JSON body**；出参 `{id, accessUrl}`；`bizType` 服务端强制 `ATTACHMENT` |
| Q11 | 上传安全边界 | 文件头 magic bytes 校验、按 Key 限流 |
| Q12 | 非视觉模型 | 门户禁用上传入口 + 接口拒绝（400） |
| Q13 | 历史回放 | 图片参与历史回放 |
| Q14 | 持久化形状 | 新建关联表 `v5ai_message_attachment` |
| Q15 | 会话列表范围 | **限当前 Agent**：`/api/v1/agents/{agentKey}/chat/conversations` |
| Q16 | 改名与归档 | 共用一个 `PATCH`（body `{name?, archived?}`）；归档后继续对话 → 409 |
| Q17 | 停止与半截回答 | `POST .../chat/runs/{runId}/stop`（幂等）+ `doOnCancel` 兜底；**落库半截回答** |
| Q18 | 图片能力标记 | 判 `config.capabilities` 含 **`image`**（不是 `vision`） |
| Q19 | 附件数量 | 每次请求最多 **3 张新图** |
| Q20 | 附件读取鉴权 | **推导式**：资源可在"本 Key 的某条消息引用了它"时被读取；不给 `plm_resource` 加列 |
| Q21 | 体积与回放预算 | 单张 **5MB**；历史回放累计 **≤16MB**，超出部分退化为 `[图片]` 文字占位 |

## 3. 数据模型变更（迁移 `V38__portal_conversations_attachments.sql`）

> 编号说明：V37（已含 API Key 名称唯一索引）已应用到库且 checksum 与文件一致，历史表**没有 version 38 行**，因此 V38 可用。

```sql
ALTER TABLE v5ai_conversation ADD COLUMN IF NOT EXISTS api_key_id BIGINT;
ALTER TABLE v5ai_conversation ADD COLUMN IF NOT EXISTS name VARCHAR(100);
ALTER TABLE v5ai_conversation ADD COLUMN IF NOT EXISTS archived_at TIMESTAMPTZ;

COMMENT ON COLUMN v5ai_conversation.api_key_id IS '会话归属的 API Key（门户侧租户边界）；调试入口产生的会话为 NULL，不属于任何 Key';
COMMENT ON COLUMN v5ai_conversation.name IS '会话名称（调用方可改，1-100 字符）；NULL 表示未命名';
COMMENT ON COLUMN v5ai_conversation.archived_at IS '归档时间；非空表示已归档：列表默认隐藏且禁止继续对话';

CREATE INDEX IF NOT EXISTS idx_v5ai_conversation_api_key_agent ON v5ai_conversation (api_key_id, agent_key);

CREATE TABLE IF NOT EXISTS v5ai_message_attachment (
    id          BIGSERIAL PRIMARY KEY,
    message_id  BIGINT NOT NULL REFERENCES v5ai_message (id) ON DELETE CASCADE,
    resource_id BIGINT NOT NULL REFERENCES plm_resource (id) ON DELETE CASCADE,
    type        VARCHAR(32) NOT NULL,
    ordinal     INT NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_v5ai_message_attachment_msg_ord UNIQUE (message_id, ordinal)
);
CREATE INDEX IF NOT EXISTS idx_v5ai_message_attachment_resource ON v5ai_message_attachment (resource_id);
COMMENT ON TABLE v5ai_message_attachment IS '消息携带的附件（图片）：resource_id 指向 plm_resource 中的 ATTACHMENT 资源';
```

不需要 DDL 的部分：`v5ai_run.status` 是 `VARCHAR(30)`，`CANCELED` 直接可用；`completed_at` 列已存在。**`tracking_id` 不加**（Q8）。`plm_resource` 不加列（Q20）。

## 4. 接口契约

**保留前缀（只验 Key，无绑定、无配额）**

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/agents/resource/upload` | JSON body `{originalName, fileSize, content(base64), bizType?, bizId?}` → `R<{id, accessUrl}>`；`bizType` 强制 `ATTACHMENT`；解码后 ≤5MB；文件头必须 png/jpeg/webp；按 `apiKeyId` 限流；`accessUrl` = `/api/v1/agents/attachments/{id}` |
| GET | `/api/v1/agents/attachments/{resourceId}` | 返回图片字节（inline）；鉴权 = 本 Key 的某条消息引用了该资源；不命中统一 **404**（不泄露存在性） |

**Agent 分支（Key + 绑定 + 现有配额判定）**

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/agents/{agentKey}/chat/conversations` | 本 Key 本 Agent 的会话列表，`updated_at DESC`；`?archived=true` 查归档；返回 `conversationId/name/agentKey/userId/archived/createdAt/updatedAt/preview` |
| PATCH | `/api/v1/agents/{agentKey}/chat/conversations/{conversationId}` | body `{name?, archived?}`；改名 1–100 字符去首尾空白；归档/取消归档同一端点 |
| GET | `/api/v1/agents/{agentKey}/chat/conversations/{conversationId}` | 已有端点；**补 `ORDER BY created_at, id`**；每条消息增加 `attachments[]` |
| POST | `/api/v1/agents/{agentKey}/chat/stream` | body 增加 `attachments[]`、`disabledMcpServerIds[]`、`disabledSkillIds[]`；归档会话 → 409；非视觉模型带图 → 400 |
| POST | `/api/v1/agents/{agentKey}/chat` | 同上（一次性对话） |
| POST | `/api/v1/agents/{agentKey}/chat/conversations/{conversationId}/resume` | 同上（resume） |
| POST | `/api/v1/agents/{agentKey}/chat/runs/{runId}/stop` | 幂等（已完成/已取消返回 200）；`runId` 取 SSE 的 `id:` 字段 |

**已有端点扩展**

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/agents/auth/bootstrap` | 每个 agent 增加 `imageSupported`（按所绑 CHAT 模型 `config.capabilities` 含 `image` 计算），门户据此禁用上传入口 |

**请求体形状**

```json
{
  "conversationId": "由调用方生成并复用",
  "query": "用户消息",
  "webSearch": true,
  "attachments": [{ "type": "IMAGE", "resourceId": 123 }],
  "disabledMcpServerIds": [1, 2],
  "disabledSkillIds": [3]
}
```

## 5. 分阶段实施

> 前置：`ChatController` 的类级映射已由用户重构为 `@RequestMapping("/api/v1/agents")` + 方法级 `/{agentKey}/chat/...`（进行中）——否则新端点挂不进这个控制器。

**阶段 1 · 迁移 V38 + 修既有缺陷**
- 写 V38 迁移。
- `findByConversationId` 补 `ORDER BY created_at, id`。
- 新增 `RunStatus` 枚举（RUNNING/COMPLETED/FAILED/CANCELED）替换裸字符串；`markCompleted`/`markFailed` 补写 `completed_at`。
- 验证：`mvn -pl v5ai-modules/v5ai-runtime -am test`。

**阶段 2 · 附件上传与读取**
- 上传控制器（JSON body、base64 解码、文件头白名单、体积上限、`apiKeyId` 限流、强制 ATTACHMENT）。
- `IPlmResourceService` 增加带 creator 的重载（API Key 路径没有 Sa-Token 会话，`created_by` 必须显式传入）；现有签名保留并委托。
- 读取端点的推导式鉴权（`v5ai_message_attachment` → `v5ai_message` → `v5ai_conversation.api_key_id`）。
- 过滤器新增保留前缀 `resource`、`attachments`（只验 Key、跳过绑定与配额）；补"Agent 恰好叫 resource/attachments"的回归测试。
- 验证：`AgentApiKeysServletFilterTest`、新增上传/读取控制器测试。

**阶段 3 · 多模态贯通**
- `ChatBo`/`AgentRunBo`/`SessionMessage` 增加附件引用（`type` + `resourceId`）与 `with*` 方法（照 `webSearch` 样板）；`disabledMcpServerIds`/`disabledSkillIds` 同样只能收窄。
- 新增端口 `AttachmentContentProvider`（common-agentscope `core/service`，record `AttachmentContent(byte[] bytes, String mimeType)`），实现放 `v5ai-runtime` 委托 `ResourceContentPort`（common-agentscope 不依赖 v5ai-api，不能直接用该端口）。
- `ModelStreamTextExecutor.conversationMessages` 改为 `Msg.Builder.content(TextBlock + ImageBlock(Base64Source))`。
- 回放预算：按"最近优先"累计，超过 16MB 的图片退化为 `[图片]` 占位。
- 门控：`capabilities` 含 `image` 才允许带图；否则 400。**真正读取 `capabilities`**（今天 `AgentScopeModelFactory.generateOptions` 完全忽略它）。
- `AgentScopeRuntime.withMemory` 的去重比较 `request.query().equals(last.content())` 在多模态下会失效，改为按 role + 末条匹配。
- 验证：新增 executor 多模态测试（消息里出现 TextBlock + ImageBlock、预算截断生效）、`AgentRunBo` 收窄测试。

**阶段 4 · 会话列表 / 改名 / 归档 / bootstrap**
- `ConversationDTO` 增加 `apiKeyId`/`userId`；`ChatController` 从 `ApiKeyAuthAttributes` 取 API Key 上下文注入 `AgentRunBo`（**绝不接受请求体传入**）。
- `ensureConversation`：新建时写 `api_key_id`/`user_id`；**已存在时做归属断言**，`api_key_id` 不匹配 → 404（防止 Key B 猜到 sessionId 就写进来）。调试入口 `api_key_id` 为 NULL。
- `normalize` 补 `conversationId` 的格式与长度校验（现在 >36 字符直接 500）。
- 列表/改名/归档端点 + `archived_at` 拦截（409）。
- bootstrap 增加 `imageSupported`。
- 列表额外返回 `preview`（首条用户消息前若干字）：废弃 localStorage 后，未命名会话（`name` 为 NULL）需要展示兜底。
- 验证：新增 `ChatController` 测试（409、400、归属断言、列表形状、改名与归档）。

**阶段 5 · 停止对话**
- 每 `runId` 注册取消信号（`Sinks.Empty`），流上叠加 `takeUntilOther`；stop 端点触发。
- `doOnCancel` 兜底（关页面/断网走这条路）→ 标记 CANCELED、写 `completed_at`、落库半截回答。
- 状态迁移加条件（`WHERE status='RUNNING'`），防止完成覆盖取消。
- 取消时按已生成内容记用量（否则"点停止"成为免费额度漏洞）。
- 验证：`PersistingAgentRuntimeTest` 增加取消路径用例。

**阶段 6 · 门户前端 `v5ai-ui-chat`**
- `Composer.vue`：真实文件选择、多选上限 3、客户端预校验（png/jpeg/webp、≤5MB）、缩略图预览；`imageSupported` 为假时禁用入口。
- 上传 → 拿 `id` → 随消息提交。
- `stores/portal.ts`：废弃 localStorage 会话列表，改服务端列表（保留 API Key、当前 Agent、头像缓存）。
- `SessionList.vue`：服务端列表、行内改名、归档/取消归档、显示归档开关。
- `ChatView.vue`：停止按钮（abort + 调 stop 端点）、"已停止"标记、消息内附件缩略图（**fetch blob + objectURL**，`<img>` 带不了 Authorization 头，照现有头像的做法）。
- 验证：`npm run build`（vue-tsc + vite）。

**阶段 7 · 文档与验收**
- 新建 `docs/api/phase6.md`（对话门户 API 契约）。
- 更新 `AGENTS.md` §7（迁移清单 V38）/§8（门户）、`README.md`。
- 端到端手工验收清单（见 §7）。

## 6. 测试清单

| 层 | 用例 |
|---|---|
| 过滤器 | 保留前缀 `resource`/`attachments` 只验 Key、不查配额；agentKey 恰为 `resource`/`attachments` 的回归 |
| 上传 | 合法 PNG 通过；内容为文本的 `x.png` 被拒（文件头）；>5MB 被拒；无 Key 401；超频 429；`bizType` 被强制为 `ATTACHMENT` |
| 附件读取 | Key A 的资源被 Key B 读取 → 404；本 Key 已引用 → 200 |
| 会话 | `ensureConversation` 归属断言；改名边界（空/超长）；归档后对话 409；列表只含本 Key 本 Agent 且按 `updated_at` 倒序；消息顺序稳定 |
| 多模态 | 消息构造含 TextBlock + ImageBlock；回放预算截断转占位；非视觉模型带图 400 |
| 收窄 | `disabledMcpServerIds`/`disabledSkillIds` 生效且只能收窄 |
| 停止 | `doOnCancel` → CANCELED + `completed_at` + 半截回答落库；重复 stop 幂等 |

## 7. 验收标准

1. 上传合法 PNG 得到 `id`；内容为文本的 `.png` 被拒；>5MB 被拒；无 Key 401。
2. Key B 用 Key A 上传得到的 `resourceId` 读取 → 404。
3. 视觉模型下带图提问能答出图中内容（人工确认）；非视觉模型下门户无附件入口且接口 400。
4. 会话列表只含本 Key 本 Agent、按最近活跃倒序；改名后重载仍在；归档后默认不显示、继续对话 409、取消归档后可继续。
5. 点停止后 SSE 立即结束、run 状态为 `CANCELED`、`completed_at` 非空、已生成的部分回答已落库、紧接着的下一条对话正常。
6. `GET .../chat/conversations/{id}` 返回的消息顺序稳定（按 `created_at, id`）。

## 8. 风险与已知代价

- **进程内状态**：取消信号与上传限流都在单实例内存里（与既有 `InMemorySlidingWindowRateLimiter` 同一限制）——多实例部署时 stop 必须落到同一实例。
- **回放预算 16MB**：更早的图片会从上下文里消失，模型答不出"最开始那张图"。
- **单轮 3 张 × 5MB**：base64 后约 20MB 请求体，接近部分模型接口的单请求上限；已按 Q21 取 5MB 而非 10MB。
- `plm_resource.access_url` 仍是管理端路径 `/api/admin/resources/{id}/preview`，门户走新路径；不动 `access_url`，以免破坏 ADR 0001 的存量语义。
- **迁移教训**：V37 是在已应用之后被追加内容并修 checksum 的；今后改已应用迁移必须 `flyway repair`，新变更一律开新版本。

## 9. 范围外

- 管理端 ChatDebug / `PreviewPanel` 的图片入口保持现状（仍是禁用占位）。
- `defaultVisionModel`（OCR 兜底）不实现。
- 门户不暴露"禁用 MCP/Skill"UI。
- 附件只支持图片；不支持文档、音频、视频。
- 不迁移存量 localStorage 会话。

## 10. 相关文档

- ADR：`docs/adr/0006-api-key-scoped-conversations.md`
- 术语：`CONTEXT.md`（会话 / 归档 / 附件 / 停止 / 支持图片输入）
- API：`docs/api/phase6.md`（本方案阶段 7 产出）
