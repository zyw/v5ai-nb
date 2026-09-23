# Phase 5 Verification（平台增强）

验证时间：2026-08-16

## 命令

```bash
set JAVA_HOME=D:\softwares\jdk\bellsoft-jdk-21.0.8+12
mvn clean verify -Dsurefire.failIfNoSpecifiedTests=false
cd v5ai-ui && npx vue-tsc --noEmit
```

结果：后端 `verify` exit 0（新增 21 个测试，全部通过），前端 `vue-tsc --noEmit` exit 0。

## 本轮完成范围（Phase 5 平台增强）

### RBAC

- `V7__platform_enhancements.sql`：`v5ai_role`（种子 admin/user）、`v5ai_user_role`、
  `v5ai_user` 增加 display_name。
- `v5ai-common` 领域：`UserAccount`（含角色）、`RoleKey`、`UserAccountStore`。
- 基础设施：`MyBatisUserAccountStore`；启动 `PlatformDataInitializer`
  无用户时创建默认管理员 admin/admin（H2 等无表环境容错跳过）。
- **管理 API 认证（关键安全修复）**：Sa-Token 拦截器要求 `/api/admin/**` 登录，
  非 GET 写操作要求 ADMIN 角色；未登录 401 / 无权限 403 JSON；登录/`/api/auth/me` 返回
  用户/角色；角色写入 Sa-Token session 供拦截使用。

### API Key 增强（限流 + 配额挂接）

- `ApplicationApiKeyServletFilter` 在密钥校验后按 agentKey 检查每分钟限流与每日配额，超限 429；
- 配额配置 `v5ai_app_quota`（每日调用次数 / 每日 Token / 每分钟限流，0=不限）；
- `InMemorySlidingWindowRateLimiter`（进程内滑动窗口，多实例需换 Redis）。

### 用量

- `v5ai_model_usage` 明细（runId/agentKey/modelKey/prompt/completion/total tokens/duration/status）；
- `PersistingAgentRuntime` 运行完成/失败埋点（token 按字符数/4 估算），同时递增
  `v5ai_app_usage` 当日计数（配额记账）；修复了失败路径重复记账缺陷（onErrorResume 同步 runId）；
- 查询 API：`GET /api/admin/usage`、`GET /api/admin/apps/{agentKey}/usage/daily`。

### 审计

- `v5ai_audit_log` + `AuditLogRecorderServiceImpl`；埋点：登录/登出、应用创建/禁用/发布、
  API Key 生成/删除、知识库/MCP/Skill 创建/禁用/发布/回滚、用户/配额变更；
- 查询 API：`GET /api/admin/audit-logs?actor=&action=&limit=`。

### AgentState 持久化

- 既有 `v5ai_agent_state` 保存/加载闭环保留（每轮运行保存 agentKey + 最后回答），
  会话历史恢复 API 可用；本轮补充文档说明。

### 可观测性

- `GET /api/health`（进程 + DB 探活）；
- `GET /api/admin/stats/overview`（应用/模型/知识库/Skill/运行/今日用量汇总，JdbcTemplate 聚合）。

### Admin API + 前端

- 新 Controller：UserController、AppQuotaController、UsageController、
  AuditController、StatsController、HealthController；`AuthController` 增加 `/me`。
- 前端：`PlatformView`（用户/配额）、`ObservabilityView`（总览统计/用量明细/审计日志）、
  router 与菜单注册（Phase 5 标记）；`client.ts` 补齐全部 API。

## 说明 / 限制

- 限流器为进程内实现（单实例）；多实例部署需替换为 Redis 实现。
- Token 用量按字符数估算（中文约 1 字符 ≈ 1 token 的实际偏差），未对接模型真实 usage 字段。
- 配额/用量按 agentKey 维度（与现有模型一致）。
- 默认管理员 `admin/admin` 由启动器自动创建，生产环境应通过用户 API 修改密码或停用。
- 管理 API 现在强制登录：存量前端调用已带 token；未登录访问将收到 401。
