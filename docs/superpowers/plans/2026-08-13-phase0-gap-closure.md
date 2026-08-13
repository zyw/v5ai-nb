# Phase 0 Gap Closure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 补齐 v5ai-nb Phase 0 项目骨架缺口，使基础设施配置、认证、统一错误模型和 AgentScope 最小调用边界具备可启动、可测试的闭环。

**Architecture:** 保持现有多模块分层：`v5ai-api` 暴露 Web/API 错误处理，`v5ai-infrastructure` 承载 PostgreSQL/Flyway/Redisson/MinIO/认证持久化适配，`v5ai-runtime` 只定义 AgentScope 运行边界。Phase 0 不引入 Server-Agent 架构，也不推进 Phase 1 的完整管理 API。

**Tech Stack:** JDK 21, Maven multi-module, Spring Boot 4.1.0 BOM import, MyBatis-Plus, PostgreSQL, Flyway, Redisson, MinIO, Sa-Token + JWT, AgentScope Java Harness boundary.

## Global Constraints

- 项目名称：`v5ai-nb`。
- 包名：`xin.v5ai.nb`。
- 模块名前缀：`v5ai-*`。
- 数据表名前缀：`v5ai_*`。
- Spring Boot 版本：`4.1.0`，使用 `spring-boot-dependencies` BOM import。
- JDK：21。
- ORM：MyBatis-Plus。
- 认证：Sa-Token + JWT。
- Redis 客户端：Redisson。

---

### Task 1: Infrastructure dependency and startup configuration

**Files:**
- Modify: `pom.xml`
- Modify: `v5ai-infrastructure/pom.xml`
- Modify: `v5ai-starter/pom.xml`
- Create: `v5ai-starter/src/main/resources/application.yml`
- Modify: `v5ai-starter/src/test/java/xin/v5ai/nb/starter/V5aiApplicationContextTest.java`

**Interfaces:**
- Produces: runtime dependencies for PostgreSQL, Redisson, MinIO, Flyway configuration.
- Produces: Boot properties under `spring.datasource`, `spring.flyway`, `v5ai.minio`, `v5ai.redisson`.

- [ ] Add a failing context/property test that requires datasource, Flyway, Redisson, and MinIO properties.
- [ ] Run the starter test and verify it fails because properties/classes are missing.
- [ ] Add Maven dependencies and starter `application.yml`.
- [ ] Run the starter test and verify it passes.

### Task 2: Redisson and MinIO client beans

**Files:**
- Create: `v5ai-infrastructure/src/main/java/xin/v5ai/nb/infrastructure/redis/RedissonProperties.java`
- Create: `v5ai-infrastructure/src/main/java/xin/v5ai/nb/infrastructure/redis/RedissonConfiguration.java`
- Create: `v5ai-infrastructure/src/main/java/xin/v5ai/nb/infrastructure/storage/MinioProperties.java`
- Create: `v5ai-infrastructure/src/main/java/xin/v5ai/nb/infrastructure/storage/MinioConfiguration.java`
- Test: `v5ai-infrastructure/src/test/java/xin/v5ai/nb/infrastructure/redis/RedissonConfigurationTest.java`
- Test: `v5ai-infrastructure/src/test/java/xin/v5ai/nb/infrastructure/storage/MinioConfigurationTest.java`

**Interfaces:**
- Produces: `RedissonClient redissonClient(RedissonProperties properties)`.
- Produces: `MinioClient minioClient(MinioProperties properties)`.

- [ ] Add failing tests for property binding and bean construction.
- [ ] Run tests and verify they fail because classes are missing.
- [ ] Implement properties and configuration beans.
- [ ] Run tests and verify they pass.

### Task 3: Database-backed user credential store

**Files:**
- Create: `v5ai-infrastructure/src/main/java/xin/v5ai/nb/infrastructure/auth/UserEntity.java`
- Create: `v5ai-infrastructure/src/main/java/xin/v5ai/nb/infrastructure/auth/UserMapper.java`
- Create: `v5ai-infrastructure/src/main/java/xin/v5ai/nb/infrastructure/auth/MyBatisUserCredentialStore.java`
- Test: `v5ai-infrastructure/src/test/java/xin/v5ai/nb/infrastructure/auth/MyBatisUserCredentialStoreTest.java`

**Interfaces:**
- Consumes: `xin.v5ai.nb.common.auth.UserCredentialStore`.
- Produces: `String findPasswordHash(String username)` backed by `v5ai_user.password_hash`, returning `null` for missing or disabled users.

- [ ] Add failing tests for active user, disabled user, and missing user.
- [ ] Run tests and verify they fail because implementation is missing.
- [ ] Implement MyBatis entity, mapper, gateway, and store.
- [ ] Run tests and verify they pass.

### Task 4: Unified Web error model

**Files:**
- Create: `v5ai-api/src/main/java/xin/v5ai/nb/api/error/GlobalExceptionHandler.java`
- Test: `v5ai-api/src/test/java/xin/v5ai/nb/api/error/GlobalExceptionHandlerTest.java`

**Interfaces:**
- Consumes: `ApiResponse.failure(ErrorCode, String)`.
- Produces: JSON error bodies for `V5aiException`, `IllegalArgumentException`, and unknown exceptions.

- [ ] Add failing tests that call the handler directly and assert status and response payload.
- [ ] Run tests and verify they fail because the handler is missing.
- [ ] Implement the handler.
- [ ] Run tests and verify they pass.

### Task 5: AgentScope Harness minimal adapter boundary

**Files:**
- Modify: `pom.xml`
- Modify: `v5ai-runtime/pom.xml`
- Create or modify: runtime adapter classes as needed under `v5ai-runtime/src/main/java/xin/v5ai/nb/runtime`
- Test: `v5ai-runtime/src/test/java/xin/v5ai/nb/runtime/AgentScopeHarnessExecutorTest.java`

**Interfaces:**
- Consumes: `AgentTextExecutor`.
- Produces: an AgentScope Harness backed executor boundary that can be wired when SDK classes are present.

- [ ] Verify official AgentScope Java Maven coordinates before adding dependency.
- [ ] Add a failing test around the adapter contract.
- [ ] Run test and verify it fails because adapter is missing.
- [ ] Implement the minimum adapter boundary.
- [ ] Run runtime tests and verify they pass.

### Task 6: Full verification and Phase 0 checklist

**Files:**
- Modify: `docs/phase1-verification.md` or create a Phase 0 verification note if clearer.

**Interfaces:**
- Produces: final verification evidence for Phase 0.

- [ ] Run targeted module tests.
- [ ] Run full backend Maven verification.
- [ ] Inspect git diff for unrelated changes.
- [ ] Report completed and remaining Phase 0/Phase 1 status with command evidence.
