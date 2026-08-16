# v5ai-nb Phase 5 API（平台增强）

## 认证与 RBAC

管理 API（`/api/admin/**`）现在要求登录：

- `POST /api/auth/login` 返回令牌 + 当前用户信息（userId/username/tenantId/roles）；
- `GET /api/auth/me` 返回当前登录用户信息；
- **所有 `/api/admin/**` 需登录**（`/api/auth/login` 除外）；非 GET 写操作需 `ADMIN` 角色；
- 未登录返回 401，角色不足返回 403（JSON 错误体）。

```http
POST /api/auth/login
{ "username": "admin", "password": "admin" }

{
  "tokenType": "Bearer",
  "token": "<jwt>",
  "userId": 1,
  "username": "admin",
  "displayName": "Platform Admin",
  "tenantId": null,
  "roles": ["ADMIN"]
}
```

启动时若无任何用户，会自动创建默认管理员 `admin/admin`（生产环境务必修改密码）。

## 租户管理

```http
POST /api/admin/tenants          { "tenantKey": "acme", "name": "Acme Corp" }
GET  /api/admin/tenants
DELETE /api/admin/tenants/{id}   # 禁用
```

## 用户管理（RBAC）

```http
GET  /api/admin/users
POST /api/admin/users            # { username, password(≥4), displayName?, tenantId?, roles? } 默认 roles=["USER"]
PUT  /api/admin/users/{id}/roles # { roles: ["ADMIN","USER"] }
PUT  /api/admin/users/{id}/enabled # { enabled: true|false }
```

角色：`ADMIN`（平台管理员，可写操作）、`USER`（常规用户，只读）。

## 应用配额 / 限流 / 用量

```http
GET /api/admin/apps/{appKey}/quota
PUT /api/admin/apps/{appKey}/quota
{ "dailyModelCalls": 100, "dailyTokens": 50000, "ratePerMinute": 30 }   # 0 = 不限

GET /api/admin/apps/{appKey}/usage/daily   # 当日调用次数 + Token
GET /api/admin/usage?appKey=&from=&to=&limit=   # 用量明细（v5ai_model_usage）
```

运行时 API（`/api/v1/apps/{appKey}/chat/stream` 等）在 API Key 校验通过后：

- **限流**：按 appKey 每分钟滑动窗口（配额 `ratePerMinute`，0 不限），超限返回 429；
- **每日配额**：调用次数 / Token 上限（0 不限），超限返回 429；
- **用量埋点**：每次运行完成/失败写 `v5ai_model_usage`（runId、appKey、模型、token 估算、耗时、状态），并递增 `v5ai_app_usage` 当日计数。

Token 按字符数粗略估算（`字符数/4`），后续可对接真实 usage 数据。

## 审计日志

管理面关键操作自动记录（登录/登出、应用创建/禁用/发布、API Key 生成/删除、知识库/MCP/Skill 创建禁用发布回滚、租户/用户/配额变更）：

```http
GET /api/admin/audit-logs?actor=&action=&limit=100
```

```json
[
  {
    "id": 1,
    "tenantId": null,
    "actor": "admin",
    "action": "CREATE",
    "targetType": "application",
    "targetId": "demo",
    "detail": "create application",
    "ip": null,
    "createdAt": "..."
  }
]
```

## 可观测性

```http
GET /api/health                    # { status, database, service } 数据库探活
GET /api/admin/stats/overview      # 应用/模型/知识库/Skill/运行总数/今日运行/今日调用/今日 Token
```

## AgentState 持久化

会话级 Agent 状态已持久化到 `v5ai_agent_state`（每次运行完成保存 appKey + 最后回答），
会话历史可通过 `GET /api/v1/apps/{appKey}/chat/conversations/{conversationId}` 查询。
