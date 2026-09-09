# Issue tracker: OneDev Issues（REST API）

本仓库的 issue 托管在自托管 OneDev 实例 `https://onedev.v5ai.xin` 的 `v5ai-nb` 项目（远端
`https://onedev.v5ai.xin/v5ai-nb`）。工程 skills（to-tickets / triage / to-spec / wayfinder 等）
通过 OneDev REST API 读写 issue，**不使用** `gh` / `glab` / 本地 Markdown。

## 认证

- 在 OneDev 账户下创建个人访问令牌（Account → Access Tokens），授予 issue 相关的读写权限。
- 所有请求带请求头 `Authorization: Bearer <token>`。
- Token 通过环境变量注入（如 `V5AI_ONEDEV_TOKEN`），**禁止写入仓库、文档或 issue 正文**。

## 端点

权威来源是本实例的 Swagger UI：`https://onedev.v5ai.xin/swagger-ui`（旧版本可能是
`/swagger-ui.html`）。下列为常用端点，路径与请求/响应 schema 以 Swagger 为准：

| 操作 | 方法与路径 | 说明 |
| --- | --- | --- |
| 创建 issue | `POST /api/issues` | 请求体含 `projectPath`（`v5ai-nb`）、`title`、`description` 等 |
| 读取 issue | `GET /api/issues/{id}` | `id` 形如 `v5ai-nb#123` |
| 查询 issue | `GET /api/issues?query=…&count=…&offset=…` | `query` 用 OneDev 查询语法，如 `state: open`、`title~"…"` |
| 追加评论 | `POST /api/issues/{id}/comments` | 请求体 `{"content": "…"}` |
| 状态转换 | `POST /api/issues/{id}/transitions/{name}` | 按项目 issue 状态机执行转换；路径/字段随版本略有差异，以 Swagger 为准 |

其他能力（里程碑、指派、标签、自定义字段、附件）见 Swagger 与
[onedev REST API](https://deepwiki.com/theonedev/onedev/6.3-rest-api)。

## 引用格式

issue 在文字中引用为 `v5ai-nb#<编号>`（如 `v5ai-nb#12`），链接形如
`https://onedev.v5ai.xin/v5ai-nb/~issues/<编号>`。

## 工作流约定（skills 遵守）

- **「发布到 issue tracker」**：`POST /api/issues` 建单，把返回的 issue 编号写回正文/输出。
- **「取回相关 ticket」**：给定编号用 `GET /api/issues/{id}`；未给编号先用 `GET /api/issues?query=…`
  检索（如 `project: "v5ai-nb"`、`title~"关键词"`），再读取命中项。
- **triage 状态**：以 OneDev issue 的状态机（state / transitions）表达；`docs/agents/triage-labels.md`
  中的角色词汇映射到对应的 OneDev 状态/标签。
- **历史存档**：切到 OneDev 之前的本地 issue 保留在 `.scratch/<feature-slug>/issues/`，只读存档，不再新建。
