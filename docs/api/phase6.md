# v5ai-nb Phase 6 API（对话门户：附件与多模态、会话管理、停止对话）

> 范围：独立对话门户 `v5ai-ui-chat` + 后端运行接口（`/api/v1/agents/**`）。
> 管理端调试入口（`/api/admin/agents/{agentKey}/chat/stream`）**不在本次范围内**，行为不变。
> 决策与术语见 `docs/adr/0006-api-key-scoped-conversations.md` 与 `CONTEXT.md`（会话 / 归档 / 附件 / 停止 / 支持图片输入）。

## 鉴权与过滤器口径

所有端点都在 `AgentApiKeysServletFilter` 之下，Key 走请求头 `Authorization: Bearer <api-key>`。
过滤器把请求分成三类（**新增的两族不带 agentKey**）：

| 路径族 | 校验内容 | 计模型配额 |
|---|---|---|
| `/api/v1/agents/auth/bootstrap` | 只验 Key | 否 |
| `/api/v1/agents/auth/{agentKey}/avatar` | Key + 该 Key 到 agentKey 的绑定 | 否 |
| `/api/v1/agents/resource/upload`、`/api/v1/agents/attachments/{id}` | 只验 Key（附件是 Key 维度的资源，与具体 Agent 无关） | 否 |
| `/api/v1/agents/{agentKey}/...` | Key + 绑定 + 每分钟限流 / 每日配额 | 是 |

保留路径按**精确形状**识别（精确匹配 `/resource/upload`、`/attachments/{纯数字 id}`），
因此名字恰好叫 `resource`、`attachments` 的 Agent 其运行路径 `/{agentKey}/chat/...` 不受影响。

### 错误码约定

响应统一为 `R` 包装（`{code, msg, data}`），业务错误通过 body 里的 `code` 表达
（与全平台一致，HTTP 状态仍是 200）：

| code | 场景 |
|---|---|
| 401 | 缺少/格式错误的 Bearer、Key 无效或停用（由过滤器直接返回 HTTP 401） |
| 403 | Key 有效但未绑定请求路径中的 Agent（过滤器直接返回 HTTP 403） |
| 429 | 按 Agent 的每分钟限流 / 每日配额超限（过滤器直接返回 HTTP 429）；附件上传按 Key 超频（body code 429） |
| 400 | 参数非法：附件类型/体积不合法、非视觉模型带图、会话 ID 非法、会话名为空或超长 |
| 404 | 会话/运行不存在，或**不属于当前 Key**（两者刻意同码，不泄露存在性） |
| 409 | 会话已归档，不能继续对话 |

## 门户初始化

### `GET /api/v1/agents/auth/bootstrap`

每个 Agent 增加 `imageSupported`：按所绑 CHAT 模型 `v5ai_model.config` 的 `capabilities` 是否含
`image` 计算（**不是** `vision`）。门户据此启用/禁用附件入口。

```json
{
  "code": 200,
  "data": {
    "keyName": "生产环境",
    "trackingId": "…",
    "ownerName": "张三",
    "agents": [
      {
        "agentKey": "demo",
        "name": "演示 Agent",
        "description": "…",
        "avatarUrl": "/api/v1/agents/auth/demo/avatar",
        "greeting": "你好",
        "presetQuestions": ["你能做什么？"],
        "webSearchEnabled": true,
        "imageSupported": true,
        "showCitations": true
      }
    ]
  }
}
```

## 附件

### `POST /api/v1/agents/resource/upload`

门户只持有 API Key，用不了管理端的 multipart 入口，因此走 JSON + base64。

```json
{
  "originalName": "photo.png",
  "fileSize": 12345,
  "content": "<base64，不含 data URI 前缀>",
  "bizType": "AVATAR",
  "bizId": 99
}
```

- `bizType` **由服务端强制为 `ATTACHMENT`**（请求里的值被忽略）；`bizId` 可选，仅作元数据。
- `content` 解码后必须 ≤ 5MB（`v5ai.chat.attachment.max-file-size-bytes`）；
  `fileSize` 仅用于解码前的快速拒绝，实际以解码结果为准。
- 类型**按文件头（magic bytes）判定**，只接受 PNG / JPEG / WebP：内容是文本的 `x.png` 会被拒。
- 按 `apiKeyId` 限流（`v5ai.chat.attachment.uploads-per-minute`，默认 20/分钟，进程内滑动窗口）。

响应：

```json
{ "code": 200, "data": { "id": 42, "accessUrl": "/api/v1/agents/attachments/42" } }
```

### `GET /api/v1/agents/attachments/{resourceId}`

inline 返回图片字节。鉴权是**推导式**的：只有「本 Key 的某条消息引用过该资源」才可读
（`v5ai_message_attachment → v5ai_message → v5ai_conversation.api_key_id`）。
不命中统一返回 **404**，不区分「不存在」与「不属于本 Key」。

## 会话

会话归属主体是 **API Key**：同一把 Key 的会话互相可见，不同 Key 之间隔离；
调试入口（Sa-Token）产生的会话 `api_key_id` 为 NULL，**不出现在门户列表里**，也不能被 Key 认领。

### `GET /api/v1/agents/{agentKey}/chat/conversations`

本 Key 本 Agent 的会话列表，按 `updated_at` 倒序。`?archived=true` 查已归档（默认查进行中）。

```json
{
  "code": 200,
  "data": [
    {
      "conversationId": "3f2c…",
      "name": "季度复盘",
      "agentKey": "demo",
      "userId": 7,
      "archived": false,
      "createdAt": "2026-09-18T10:00:00+08:00",
      "updatedAt": "2026-09-18T10:05:00+08:00"
    }
  ]
}
```

`name` **由服务端生成**：会话创建时取首条提问的首行（压缩空白后截到 100 字）落库，
首轮回答结束后再由模型改写成不超过 30 字的短标题（可用 `v5ai.chat.conversation-title.enabled=false` 关掉）。
因此列表只读 `v5ai_conversation` 一张表——过去那种「对每个会话再查一次消息表取首条提问做预览兜底」
的做法已移除（消息表一大，列表就成了门户最贵的接口）。

`name` 为 null 只出现在**提问为空**的会话（如只发了图片）：门户显示占位「新会话」。

### `PATCH /api/v1/agents/{agentKey}/chat/conversations/{conversationId}`

改名与归档共用一个端点，body 里出现哪个字段就执行哪个动作（都为 null 则空操作）：

```json
{ "name": "季度复盘", "archived": true }
```

- `name`：去首尾空白后 1–100 字符，否则 400。
- `archived`：true 归档、false 取消归档。归档后列表默认隐藏，且**继续对话返回 409**。

### `GET /api/v1/agents/{agentKey}/chat/conversations/{conversationId}`

会话历史，按 `created_at, id` 稳定排序（同一次运行的提问与回答可能同刻写入，故用主键兜底），**不含已作废的消息**。
每条消息带 `messageId`（「重新生成」的锚点）与 `attachments`，助手消息还带 `usage`（用量与用时；用户消息与 V39 之前落库的历史消息为 `null`）、`reasoning`（模型的思考过程；用户消息、没有思考的回答、以及关闭了 `v5ai.agentscope.reasoning-persist-enabled` 时为 `null`）与 `citations`（本条回答当时引用的切片；用户消息、没有检索的一轮、以及 V45 之前的回答为 `[]`）：

```json
{
  "code": 200,
  "data": [
    {
      "messageId": "2101304707712417794",
      "role": "USER",
      "content": "这张图里是什么？",
      "attachments": [
        { "type": "IMAGE", "resourceId": 42, "accessUrl": "/api/v1/agents/attachments/42" }
      ],
      "usage": null
    },
    {
      "messageId": "2101304707712417795",
      "role": "ASSISTANT",
      "content": "图里是…",
      "reasoning": null,
      "citations": [
        {
          "knowledgeBaseId": 5,
          "documentId": 13,
          "documentTitle": "DeepSeek从入门到精通(20250204).pdf",
          "chunkIndex": 20,
          "content": "……切片内容（落库时已按 2000 字符截断）……",
          "score": 0.2029792070388794
        }
      ],
      "attachments": [],
      "usage": { "promptTokens": 128, "completionTokens": 512, "totalTokens": 640, "durationMs": 3210 }
    }
  ]
}
```

`messageId` 是**字符串**：主键是 19 位雪花号，超出 JS 的 `Number.MAX_SAFE_INTEGER`（全局
`BigNumberSerializer` 对超范围 `Long` 一律输出字符串）。调用方必须**原样回传**——转成数字就会被
浏览器四舍五入成另一个 id（`…417794` → `…417800`），「重新生成」随后只能拿到 404「提问不存在」。
（其余小 id，如 `resourceId`、历史遗留的小主键，仍是数字。）

`accessUrl` 是给门户用的：`<img src>` 带不了 `Authorization` 头，门户需按该地址取 blob。

**关于 `citations`（V45 起）**：六个字段与实时流 `RETRIEVAL` 事件的载荷**逐字同构**（同一个
`CitationPayload` 编解码），门户因此复用实时那套解析器与渲染组件（见 CONTEXT.md「引用」）。
它是**快照**——存的是模型当时看到的那份内容（单条截断 2000 字符），不随切片被编辑、文档被删除或
索引重建而变化；一轮里检索到多次时按 `(knowledgeBaseId, documentId, chunkIndex)` 保序去重、
保留首次命中。它**只供回看，不回放给模型**（与 `reasoning` 同口径），也**不占用记忆窗口预算**
（该列已被 `HOT_PATH_EXCLUDED_COLUMNS` 显式剔除）。语义取舍见
`docs/adr/0009-citations-persisted-on-assistant-message.md`。

## 对话

`POST /api/v1/agents/{agentKey}/chat/stream`（SSE）、`POST …/chat`（一次性）、
`POST …/chat/conversations/{conversationId}/resume` 三者的请求体一致：

```json
{
  "conversationId": "由调用方生成并复用，1-36 位 [A-Za-z0-9._-]，为空则由后端生成",
  "query": "用户消息",
  "webSearch": true,
  "attachments": [{ "type": "IMAGE", "resourceId": 42 }],
  "disabledMcpServerIds": [1, 2],
  "disabledSkillIds": [3]
}
```

- `attachments`：每条消息最多 3 张（`max-per-message`）；所绑模型 `capabilities` 不含 `image` 时**整请求 400**，
  不会悄悄丢图。图片会作为图片块进入模型输入，历史回放同样带图。
- `disabledMcpServerIds` / `disabledSkillIds`：**只减不增**的收窄项，在解析阶段就排除
  （跳过建立 MCP 连接与读取 Skill 版本文件）。门户不暴露这两个 UI。
- `conversationId` 非法（超长或含 `[A-Za-z0-9._-]` 之外的字符）返回 400（过去会一路撞库变成 500）。
- 会话已归档 → 409；会话不属于本 Key → 404。

### 图片回放预算

历史回放中的图片按**最近优先**累计，总预算 16MB（`v5ai.chat.attachment.history-budget-bytes`）。
超出预算的图片不会去读字节，直接退化为 `[图片]` 文字占位 —— 更早的图片会从上下文里消失。

### 完成事件携带用量

```json
RUN_COMPLETED
{ "promptTokens": 128, "completionTokens": 512, "totalTokens": 640, "durationMs": 3210 }
```

对话流最后一个事件 `RUN_COMPLETED` 的载荷是本次运行的用量汇总（JSON），门户的「用量 / 用时」直接读它：

- token 以**模型回报的真实用量**为准：直连路径读 `ChatResponse.getUsage()`，带工具路径累加每次模型调用的 `ModelCallEndEvent.getUsage()`（工具循环会调用多轮，输入 token 是各轮提示词之和）。服务端没有回报、或本次运行在回报前就失败/被取消时，才回退为平台估算：CJK 按 1 字 1 token、其余 4 字符 1 token、图片按张计（`v5ai.agentscope.image-tokens-per-image`，默认 1024）；
  它与写进 `v5ai_model_usage`、扣减配额的**是同一份数字**：运行时在事件推送与落库之前只算一次，两边不会各估一套；
- `durationMs` 是服务端从 `RUN_STARTED` 到收尾的墙钟耗时（含 RAG 检索与工具调用）；
- 同一个形状也随助手消息落库（`v5ai_message` 的 `prompt_tokens`/`completion_tokens`/`duration_ms`），历史接口读它还原 `usage` —— 否则刷新页面后历史回答就没有这两个数字了；
- 被停止（`CANCELED`）的运行**没有** `RUN_COMPLETED` 事件、因而流里不带载荷，但它的半截回答照样带着用量与思考落库（停止不等于没消耗）；失败（`RUN_FAILED`）的运行两者都没有；
- 知识库问答调试链路（`/api/admin/knowledge-bases/{id}/qa/stream`）不带用量，载荷仍是空串。

### 历史窗口与会话摘要

发给模型的历史不是「这个会话的全部消息」，而是**窗口内逐条回放 + 窗口外摘要**：

- **窗口**（`v5ai.agentscope.history`，默认 `max-messages=40` / `max-chars=8000` / `min-messages=2` / `align-to-user-turn=true`）：
  按「最近优先」保留最近若干条，裁剪后首条一定对齐到用户提问。`enabled=false` 时退回「全量历史」的老行为，
  长会话会顶爆模型窗口，只建议排查问题时临时关闭。显式 `resume` 传入的历史**同样过这道窗口**。
- **摘要**（`v5ai.chat.conversation-summary`）：窗口之外的旧内容由后台异步压缩成一段文本
  （`v5ai_conversation_summary`，水位 `covered_until_message_id` 只增不减），下次运行随**系统提示**注入。
  触发是懒的：水位之后新增的窗口外消息达到 `min-new-messages`（默认 10）才再压一次，
  且有 `min-interval-seconds`（默认 30 秒）防抖。可以指定更便宜的 `model-id`，留空则跟随该 Agent 绑定的 CHAT 模型。

`RUN_STARTED` 的载荷因此是「本轮提问的消息 id + 本轮窗口统计」：

```json
RUN_STARTED
{ "userMessageId": "2101304707712417794",
  "context": { "keptMessages": 12, "droppedMessages": 28, "droppedChars": 15230,
               "keptChars": 4210, "trimmed": true } }
```

`userMessageId` 同样是**字符串**（载荷由服务端手拼 JSON，显式转字符串）：它会被原样回传给
`/regenerate`，一旦当成数字解析就会被四舍五入成另一个 id。
未开启记忆（`memoryEnabled=false`）时载荷仍是空串；开启但未发生裁剪时 `trimmed=false`。
平台内部还有一个不对外的事件 `MODEL_USAGE`（用量的真实值/估算值）：它在 SSE 里**不会出现**，
也不会写进 `v5ai_run_event`，只用于把用量从执行器传给持久化侧。

### `POST /api/v1/agents/{agentKey}/chat/conversations/{conversationId}/regenerate`

重新生成某一轮的回答。**这是服务端语义**：把该轮提问**之后**的消息全部标记为「作废」（软状态，见下），
然后**复用那条提问**重跑一次——提问行保持有效、不重新插入，所以历史里不会出现两条相同的提问，
作废的回答也不会再进入模型上下文。

```json
{ "fromMessageId": "2101304707712417794", "webSearch": false }
```

- `fromMessageId`：该轮**提问**的消息 id（历史消息里的 `messageId`，或本轮 `RUN_STARTED` 载荷的 `userMessageId`）。
  **按字符串传**（雪花主键 19 位，超出 JS 安全整数，数字形态会被浏览器四舍五入成另一个 id 而查无此消息）；
  服务端按字符串接收，数字形态的旧调用方仍可用，非数字则 400。
- 校验顺序是刻意的，**任何拒绝都发生在作废之前**：会话不属于本 Key → 404；会话已归档 → 409；
  锚点不存在 / 不属于该会话 / 不是用户消息 → 404；锚点那轮有图片但当前模型不支持图片 → 400。
- 通过后：作废锚点之后的全部消息（含被替换的回答与更晚的整轮问答），**并让覆盖到作废范围的会话摘要整条失效**
  （避免模型继续「记得」已被替换的内容），再以锚点的提问内容与附件重跑。
- 返回与 `/chat/stream` **完全相同的事件流**（含 `RUN_STARTED`/`RUN_COMPLETED` 的载荷）。
- **不幂等但可重复**：连点两次会再作废第一次的产物并重跑，最终仍只有一条可见回答。

#### 作废（superseded）

`v5ai_message.superseded_at` 非空即为「作废」。数据全部保留（可审计、可反悔、附件引用不断），
只是不再参与展示与记忆回放——这是**软**替换而不是删除，好处是「消息序列」与「已计费用量」始终对得上。

读取侧只在两处过滤：会话历史（`findByConversationId`，历史接口 / `resume` / 记忆回放共用）与会话列表的首条提问预览。
**附件读取的推导式鉴权刻意不过滤**——那是「这把 Key 是否处置过该资源」的归属判定，不是展示查询。

### 停止对话

`POST /api/v1/agents/{agentKey}/chat/runs/{runId}/stop`，`runId` 取自 SSE 的 `id:` 字段。

- **幂等**：已完成/已取消的运行同样返回 200。
- 本实例跑着这次运行时触发其取消信号，由流的取消路径统一收尾；找不到信号时直接把库里仍是
  `RUNNING` 的记录落定为 `CANCELED`。
- 归属校验：运行的会话必须属于本 Key，否则 404。
- 收尾口径：run 状态 → `CANCELED`、写入 `completed_at`、**落库已生成的部分回答**、
  按已生成内容记用量（否则「点停止」会成为免费额度漏洞）；已生成的**半截思考**同样随回答落库（V42 起），
刷新页面后仍能回看。`v5ai_run.error_message` 保持为空——
  停止是正常收尾，不是失败。
- 客户端断流（关页面/断网）走同一条取消路径（Reactor 取消 → `doOnCancel`），
  因此不会再出现「run 永远停在 RUNNING、回答不落库」的老问题。

## 配置项

| 配置 | 默认 | 说明 |
|---|---|---|
| `v5ai.chat.attachment.max-file-size-bytes` | `5242880`（5MB） | 单张附件体积上限 |
| `v5ai.chat.attachment.uploads-per-minute` | `20` | 每把 Key 每分钟的上传次数（进程内滑动窗口） |
| `v5ai.chat.attachment.max-per-message` | `3` | 每条消息允许携带的图片数量 |
| `v5ai.chat.attachment.history-budget-bytes` | `16777216`（16MB） | 历史回放图片的累计体积预算 |

## 数据库

迁移 `V38__portal_conversations_attachments.sql`：

- `v5ai_conversation` 增加 `api_key_id`（租户边界）、`name`、`archived_at`，
  以及索引 `(api_key_id, agent_key)`；
- 新建 `v5ai_message_attachment`（`message_id` / `resource_id` / `type` / `ordinal`，
  唯一约束 `(message_id, ordinal)`）。

`v5ai_run.status` 是 `VARCHAR(30)`，`CANCELED` 直接可用；`completed_at` 列早已存在，本次开始真正写入。

## 已知限制

- **进程内状态**：取消信号与上传限流都在单实例内存里（与既有 `InMemorySlidingWindowRateLimiter` 同一限制），
  多实例部署时 stop 必须落到跑着这次运行的那个实例。
- **回放预算 16MB**：更早的图片会从上下文里消失。
- **单轮 3 张 × 5MB**：base64 后约 20MB 请求体，接近部分模型接口的单请求上限。
- 附件只支持图片，不支持文档 / 音频 / 视频。
- `plm_resource.access_url` 仍是管理端路径 `/api/admin/resources/{id}/preview`，门户走新路径（不改存量语义）。
