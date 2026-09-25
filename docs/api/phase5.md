# v5ai-nb Phase 5 API（平台增强）

## 认证与 RBAC

管理 API（`/api/admin/**`）现在要求登录：

- `POST /api/auth/login` 返回令牌（`access_token`/`expire_in`/`client_id`）；
- **所有 `/api/admin/**` 需登录**（`/api/auth/login` 与验证码接口除外）；非 GET 写操作需 `ADMIN` 角色；
- 未登录返回 401，角色不足返回 403（JSON 错误体）。

### 登录（加密传输）

`/api/auth/login` 标注了 `@ApiEncrypt`，请求体必须加密（RSA+AES 混合，与 RuoYi-Vue-Plus 协议一致）：

1. 生成随机 16 位 AES 密钥；
2. 用 AES-128/ECB/PKCS5 加密 JSON 请求体，输出 base64 作为请求体；
3. AES 密钥先 base64 编码，再用 RSA 公钥（对应后端 `v5ai.api-decrypt.private-key`，即前端 `VITE_APP_RSA_PUBLIC_KEY`）加密，放入请求头 `encrypt-key`。

```http
POST /api/auth/login
Content-Type: application/json
encrypt-key: <RSA(base64(AES密钥))>

<AES(JSON)>

# 解密后的请求体：
{ "clientId": "e5cd7e4891bf95d1d19206ce24a7b32e", "grantType": "password", "username": "admin", "password": "admin", "uuid": "<滑块令牌，可选>" }
```

```json
// 响应（明文）
{ "code": 200, "msg": "操作成功", "data": { "access_token": "<jwt>", "expire_in": 1800, "refresh_token": "<uuid>", "refresh_expire_in": 604800, "client_id": "e5cd7e4891bf95d1d19206ce24a7b32e" } }
```

前端密钥配置见 `v5ai-ui/.env`（`VITE_APP_RSA_PUBLIC_KEY`/`VITE_APP_RSA_PRIVATE_KEY`），更换需与后端 `v5ai.api-decrypt` 一同更换。

### 刷新令牌

登录与刷新都会返回 `refresh_token`（UUID，Redis 存储，TTL `v5ai.auth.refresh-token-ttl` 默认 7 天）与 `refresh_expire_in`。访问令牌过期（401）时，用刷新令牌换取新令牌（轮换：旧刷新令牌立即失效）：

```http
POST /api/auth/refresh        # 同样需要 RSA+AES 加密（@ApiEncrypt）
encrypt-key: <RSA(base64(AES密钥))>

# 解密后请求体
{ "refreshToken": "<uuid>" }
```

- 成功：返回新的 `access_token` + `expire_in` + 轮换后的 `refresh_token` + `refresh_expire_in`；
- 失败（令牌无效/过期/客户端停用/用户停用）：`R.fail(401, "刷新令牌无效或已过期，请重新登录")`；
- 前端行为：任意接口返回 401 时自动调用 refresh 并重试一次，刷新失败才清除会话回登录页；
- `POST /api/auth/logout` 可携带 `{ "refreshToken": "..." }` 一并吊销刷新令牌。

### 滑块验证码（登录防爆破）

当某账号登录错误次数达到 `v5ai.captcha.slider-threshold`（默认 3，需小于 `user.password.maxRetryCount`=5）后，再次登录返回错误码 `1006`，需先通过滑块验证再携带 `uuid` 登录。

```http
GET /api/auth/captcha
# 响应 data：{ "uuid": "...", "background": "<PNG base64>", "puzzle": "<PNG base64>", "y": 62 }

POST /api/auth/captcha/verify
{ "uuid": "...", "x": 128 }   # x 为拼图块横向像素位置，误差 ±5px 内通过
```

缺口 x 坐标仅存于服务端 Redis（`captcha:slider:gap:{uuid}`，TTL 默认 2 分钟），令牌一次性；校验通过后标记 `captcha:slider:ok:{uuid}`，登录时消费。失败/过期分别返回「滑块验证失败，请重试」「验证码已过期，请刷新后重试」。

启动时若无任何用户，会自动创建默认管理员 `admin/admin`（生产环境务必修改密码）。

## 用户管理（RBAC）

```http
GET  /api/admin/users
POST /api/admin/users            # { username, password(≥4), displayName?, roles? } 默认 roles=["USER"]
PUT  /api/admin/users/{id}/roles # { roles: ["ADMIN","USER"] }
PUT  /api/admin/users/{id}/enabled # { enabled: true|false }
```

角色：`ADMIN`（平台管理员，可写操作）、`USER`（常规用户，只读）。

## 应用配额 / 限流 / 用量

```http
GET /api/admin/apps/{agentKey}/quota
PUT /api/admin/apps/{agentKey}/quota
{ "dailyModelCalls": 100, "dailyTokens": 50000, "ratePerMinute": 30 }   # 0 = 不限

GET /api/admin/apps/{agentKey}/usage/daily   # 当日调用次数 + Token
GET /api/admin/usage?agentKey=&from=&to=&pageNum=1&pageSize=10   # 用量明细（v5ai_model_usage）
```

`from`、`to` 为可选 ISO-8601 带时区日期时间（如 `2026-09-01T00:00:00+08:00`），按 `created_at >= from` 与 `created_at <= to` 包含边界过滤；任一边界可单独传入，并可与 `agentKey` 组合使用。

运行时 API（`/api/v1/agents/{agentKey}/chat/stream` 等）在 API Key 校验通过后：

- **限流**：按 agentKey 每分钟滑动窗口（配额 `ratePerMinute`，0 不限），超限返回 429；
- **每日配额**：调用次数 / Token 上限（0 不限），超限返回 429；
- **用量埋点**：每次运行完成/失败写 `v5ai_model_usage`（runId、agentKey、模型、token 估算、耗时、状态），并递增 `v5ai_app_usage` 当日计数。

Token 按字符数粗略估算（`字符数/4`），后续可对接真实 usage 数据。

## 审计日志

管理面关键操作自动记录（登录/登出、应用创建/禁用/发布、API Key 生成/删除、知识库/MCP/Skill 创建禁用发布回滚、用户/配额变更）：

```http
GET /api/admin/audit-logs?actor=&action=&limit=100
```

```json
[
  {
    "id": 1,
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

会话级 Agent 状态已持久化到 `v5ai_agent_state`（每次运行完成保存 agentKey + 最后回答），
会话历史可通过 `GET /api/v1/agents/{agentKey}/chat/conversations/{conversationId}` 查询。

## 资源存储（通用资源存储）

`plm_resource` 通用资源存储（迁移 V24）：上传文件的元数据（存储键/原始文件名/大小/MIME/存储类型/访问 URL/业务类型/关联业务ID/创建者）。存储后端由配置 `v5ai.storage.type` 决定（LOCAL=本地磁盘，默认；MINIO=MinIO），本地根目录 `v5ai.storage.local-dir`（默认 `./v5ai-upload`），MinIO 连接参数 `v5ai.minio.*`。删除会物理删除文件与记录；预览/下载均需登录，带 `platform:resource:query` 权限。

### 分页列表

文件名模糊、业务类型、创建时间区间（`params[beginTime]`/`params[endTime]`）：

```http
GET /api/admin/resources?pageNum=1&pageSize=10&originalName=报告&bizType=DOCUMENT&params[beginTime]=2026-08-01%2000:00:00&params[endTime]=2026-08-31%2023:59:59
Authorization: Bearer <token>
```

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "total": 1,
    "rows": [
      {
        "id": 1,
        "storageKey": "202608/1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d.pdf",
        "originalName": "产品报告.pdf",
        "fileSize": 20480,
        "mimeType": "application/pdf",
        "storageType": "LOCAL",
        "accessUrl": "/api/admin/resources/1/preview",
        "bizType": "DOCUMENT",
        "bizId": null,
        "createdBy": 1,
        "createDt": "2026-08-30 22:40:00",
        "updateDt": "2026-08-30 22:40:00"
      }
    ]
  }
}
```

### 详情

```http
GET /api/admin/resources/{id}
```

### 上传

```http
POST /api/admin/resources/upload        # multipart/form-data，需 platform:resource:add
file: <文件>
bizType: DOCUMENT        # 可选：GENERAL/AVATAR/ATTACHMENT/DOCUMENT，默认 GENERAL
bizId: 42                # 可选
```

存储类型不再由接口提供，由配置 `v5ai.storage.type`（`V5AI_STORAGE_TYPE` 环境变量，LOCAL/MINIO，默认 LOCAL）统一决定。

响应为资源 VO（`data` 同上），包含生成的 `id`、`storageKey`、`accessUrl`。

`accessUrl` 始终为应用内鉴权路径 `/api/admin/resources/{id}/preview`，与存储后端（LOCAL/MINIO）及 MinIO 桶策略无关；MinIO 桶设为私有同样可经该路径访问（由应用代理 + `platform:resource:query` 鉴权读取）。

### 修改元数据

```http
PUT /api/admin/resources                 # 需 platform:resource:edit
{ "id": 1, "bizType": "ATTACHMENT", "bizId": 7 }
```

### 批量删除

```http
DELETE /api/admin/resources/1,2,3        # 需 platform:resource:remove；物理删除文件与记录
```

### 预览 / 下载

```http
GET /api/admin/resources/{id}/preview    # inline 内容（图片/PDF 浏览器可直接查看）
GET /api/admin/resources/{id}/download   # attachment，Content-Disposition 携带原始文件名
```

浏览器无法为 `<img src>` 携带 `Authorization` 头，前端请使用 `fetch` + Blob 方式访问。
