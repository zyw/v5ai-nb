# Issue tracker: OneDev Issues（REST API）

本仓库的 issue 托管在自托管 OneDev 实例 `https://onedev.v5ai.xin` 的 `v5ai-nb` 项目（远端
`https://onedev.v5ai.xin/v5ai-nb`，项目 id=41）。工程 skills（to-tickets / triage / to-spec / wayfinder 等）
通过 OneDev REST API 读写 issue，**不使用** `gh` / `glab` / 本地 Markdown。

> ⚠️ **API 基础路径是 `/~api/`（带 `~`），不是 `/api/`** —— 本实例（OneDev 12）把 REST API 挂在
> OneDev 的 `/~` 路由命名空间下（与 `/~help`、`/~login` 同级）。`/api/*` 会落到 Web 兜底并返回
> `/~login` 重定向页，无法访问。已实测确认（2026-09-08）。

## 认证

- 在 OneDev 账户下创建个人访问令牌（Account → Access Tokens），授予 issue 相关角色
  （本项目实测：令牌选 Issue Manager / Issue Reporter 角色即可读写；令牌所属账号需是
  `v5ai-nb` 项目成员，否则请求被当作匿名处理）。
- 所有请求带请求头 `Authorization: Bearer <token>`，推荐同时带 `Accept: application/json`。
- Token 通过环境变量注入（如 `V5AI_ONEDEV_TOKEN`），**禁止写入仓库、文档或 issue 正文**。

## 端点

基础地址：`https://onedev.v5ai.xin/~api`。下列为已实测可用或文档声明的常用端点
（页内 API 文档：`https://onedev.v5ai.xin/~help/api`，需 Web 登录查看）：

| 操作 | 方法与路径 | 说明 |
| --- | --- | --- |
| 列出项目 | `GET /~api/projects?offset=0&count=100` | 返回项目数组（含 `id`、`path`、`issueManagement`）；实测可用 |
| 列出 issue | `GET /~api/issues?offset=0&count=N` | 返回 issue 数组；实测可用 |
| 创建 issue | `POST /~api/issues` | 请求体为 `IssueOpenData`：`{"projectId": 41, "title": "…", "description": "…", "confidential": false, …}`（字段：`ownEstimatedTime`/`projectId`/`title`/`fields`/`iterationIds`/`description`/`confidential`；**注意是 `projectId` 数字而非 `projectPath`**）；响应为 issue 编号数字；实测可用 |
| 读取 issue | `GET /~api/issues/{id}` | 返回 issue JSON（`id`、`number`、`state`、`title`、`description` 等）；`id` 为数字，实测可用 |
| 追加评论 | `POST /~api/issues/{issueId}/comments` | ⚠️ **本实例实测不可用**：`GET` 返回 200，`POST` 返回 405 Method Not Allowed（2026-09-16 复现）；`PUT /~api/issues/{id}/description` 同样 405。需要跨单关联时，直接在正文里写 `#<编号>`（OneDev 会渲染成链接并在被引用单上生成反向引用），不要依赖评论。 |
| 状态转换 | `POST /~api/issues/{issueId}/transitions/{name}` | 按项目 issue 状态机执行转换（路径以文档为准） |

项目列表与 issue 列表的「集合端点」支持 OneDev Smart Query（`query` 参数，ANTLR 语法）；
权威 schema 一律以 `/~help/api`（或 OneDev 文档）为准。

## 引用格式

issue 在文字中引用为 `v5ai-nb#<编号>`（如 `v5ai-nb#12`），链接形如
`https://onedev.v5ai.xin/v5ai-nb/~issues/<编号>`。

## 工作流约定（skills 遵守）

- **「发布到 issue tracker」**：`POST /~api/issues`（体：`projectId` + `title` + `description`）建单，
  响应即 issue 编号，写回正文/输出。
- **「取回相关 ticket」**：给定编号用 `GET /~api/issues/{id}`；未给编号先用
  `GET /~api/issues?query=…&count=…` 检索，再读取命中项。
- **triage 状态**：以 OneDev issue 的状态机（state / transitions）表达；`docs/agents/triage-labels.md`
  中的角色词汇映射到对应的 OneDev 状态/标签。
- **历史存档**：切到 OneDev 之前的本地 issue 保留在 `.scratch/<feature-slug>/issues/`，只读存档，不再新建。
