# Phase 0 Verification

验证时间：2026-08-13

## 范围

Phase 0 本轮补齐以下缺口：

- PostgreSQL driver 与正式 datasource 配置。
- Flyway 正式启动配置与 `v5ai_flyway_schema_history` 表名。
- Redisson 配置属性与 `RedissonClient` Bean。
- MinIO 配置属性与 `MinioClient` Bean。
- Sa-Token + JWT 认证的数据库用户凭证读取实现。
- 统一 Web 错误模型。
- AgentScope Java Harness 最小调用适配边界。

## 验证命令

```bash
mvn -Dmaven.repo.local=/private/tmp/v5ai-m2 \
  -pl v5ai-infrastructure -am \
  -Dtest=RedissonConfigurationTest,MinioConfigurationTest,MyBatisUserCredentialStoreTest \
  -Dsurefire.failIfNoSpecifiedTests=false test -q

mvn -Dmaven.repo.local=/private/tmp/v5ai-m2 \
  -pl v5ai-api -am \
  -Dtest=GlobalExceptionHandlerTest,V5aiLoginServiceTest \
  -Dsurefire.failIfNoSpecifiedTests=false test -q

mvn -Dmaven.repo.local=/private/tmp/v5ai-m2 \
  -pl v5ai-runtime -am \
  -Dtest=AgentScopeHarnessExecutorTest,AgentScopeRuntimeTest \
  -Dsurefire.failIfNoSpecifiedTests=false test -q

mvn -Dmaven.repo.local=/private/tmp/v5ai-m2 clean verify \
  -Dsurefire.failIfNoSpecifiedTests=false -q
```

## 结果

- 目标测试通过。
- 后端完整 Maven `clean verify` 通过。
- AgentScope Harness 测试使用内存 `Model` 经过 `HarnessAgent.builder().model(...).build().call(...)` 调用链返回文本。

## 备注

- Phase 0 仅完成 AgentScope 最小调用边界；真实模型 Provider/Model 到 AgentScope Model 的解析属于 Phase 1。
- `AgentScopeRuntimeConfiguration` 在存在 AgentScope `Model` Bean 时启用 `AgentScopeHarnessExecutor`，否则保留开发占位响应，避免 Phase 0 本地启动强依赖真实模型服务。
