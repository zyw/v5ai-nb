# Phase 1 Agent Model Runtime Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 完成 Phase 1 的 Provider/Model 管理、Agent 管理与发布、基础对话/SSE、会话与消息持久化、RunEvent 持久化，以及发布模型到 AgentScope Runtime 的解析边界。

**Architecture:** 沿用现有 `v5ai-*` 多模块：`v5ai-model` 负责 Provider/Model 业务服务，`v5ai-agent` 负责 Agent 和发布版本，`v5ai-runtime` 负责运行时端口和事件，`v5ai-infrastructure` 提供 MyBatis/AgentScope 适配，`v5ai-api` 暴露 Admin 与 Runtime API。运行时只读取已发布 Agent 版本，API DTO 不暴露 AgentScope SDK 类型。

**Tech Stack:** JDK 21, Spring Boot 4.1.0, MyBatis-Plus, PostgreSQL, Flyway, Sa-Token + JWT, Redisson, MinIO, AgentScope Java Harness 2.0, Reactor, JUnit 5.

## Global Constraints

- 项目名称：`v5ai-nb`。
- Java 包名：`xin.v5ai.nb`。
- Maven 模块名使用 `v5ai-*`。
- 数据表名使用 `v5ai_*`。
- JDK 使用 21。
- ORM 使用 MyBatis-Plus。
- 认证使用 Sa-Token + JWT。
- Redis 使用 Redisson。
- 不使用 Server-Agent 架构，使用 Dify 架构。
- Phase 1 不实现 RAG、MCP、Skill、Workflow。
- Runtime 只能读取已发布 Agent 版本。
- API DTO 不暴露 AgentScope Java SDK 类型。
- 所有新增行为先写失败测试，再写实现。

---

## Task 1: Provider/Model management domain and API

**Files:**
- Create: `v5ai-model/src/main/java/xin/v5ai/nb/model/CreateProviderCommand.java`
- Create: `v5ai-model/src/main/java/xin/v5ai/nb/model/ModelProvider.java`
- Create: `v5ai-model/src/main/java/xin/v5ai/nb/model/ModelProviderRepository.java`
- Modify: `v5ai-model/src/main/java/xin/v5ai/nb/model/ModelManagementService.java`
- Create: `v5ai-api/src/main/java/xin/v5ai/nb/api/model/ModelProviderController.java`
- Create: `v5ai-api/src/main/java/xin/v5ai/nb/api/model/ModelController.java`
- Test: `v5ai-model/src/test/java/xin/v5ai/nb/model/ModelManagementServiceTest.java`
- Test: `v5ai-api/src/test/java/xin/v5ai/nb/api/model/ModelProviderControllerTest.java`

**Interfaces:**
- Produces: `ModelManagementService.createProvider(CreateProviderCommand): ModelProvider`
- Produces: `ModelManagementService.createModel(CreateModelCommand): ModelConfig`
- Produces: admin endpoints `POST /api/admin/providers`, `GET /api/admin/providers`, `POST /api/admin/models`, `GET /api/admin/models`

- [ ] Write failing service tests for creating a provider, rejecting duplicate provider keys, and creating a model with encrypted credentials.
- [ ] Run the model service tests and verify missing methods/classes fail.
- [ ] Implement the domain records, repository ports, and service behavior.
- [ ] Write failing controller tests for provider/model create/list endpoints.
- [ ] Implement request/response DTOs and controllers.
- [ ] Run model/api tests and verify they pass.

## Task 2: Provider/Model MyBatis persistence

**Files:**
- Create: `v5ai-infrastructure/src/main/java/xin/v5ai/nb/infrastructure/model/ModelProviderEntity.java`
- Create: `v5ai-infrastructure/src/main/java/xin/v5ai/nb/infrastructure/model/ModelProviderMapper.java`
- Create: `v5ai-infrastructure/src/main/java/xin/v5ai/nb/infrastructure/model/MyBatisModelProviderRepository.java`
- Modify: `v5ai-infrastructure/src/main/java/xin/v5ai/nb/infrastructure/model/ModelConfigMapper.java`
- Modify: `v5ai-infrastructure/src/main/java/xin/v5ai/nb/infrastructure/model/MyBatisModelRepository.java`
- Test: `v5ai-infrastructure/src/test/java/xin/v5ai/nb/infrastructure/model/MyBatisModelProviderRepositoryTest.java`

**Interfaces:**
- Consumes: `ModelProviderMapper`
- Consumes: `ModelMapper`
- Produces: MyBatis-backed provider/model repositories targeting `v5ai_model_provider` and `v5ai_model`

- [ ] Write failing tests for provider save/list/findByKey and provider-key-to-id resolution.
- [ ] Run tests and verify persistence adapters are missing/incomplete.
- [ ] Implement entities, mappers, repositories, and provider id resolution through `v5ai_model_provider`.
- [ ] Run infrastructure model tests and verify they pass.

## Task 3: Agent CRUD and published version persistence

**Files:**
- Create: `v5ai-agent/src/main/java/xin/v5ai/nb/application/CreateApplicationCommand.java`
- Create: `v5ai-agent/src/main/java/xin/v5ai/nb/application/PublishApplicationCommand.java`
- Modify: `v5ai-agent/src/main/java/xin/v5ai/nb/application/ApplicationManagementService.java`
- Create: `v5ai-api/src/main/java/xin/v5ai/nb/api/application/ApplicationController.java`
- Create: `v5ai-infrastructure/src/main/java/xin/v5ai/nb/infrastructure/application/ApplicationEntity.java`
- Create: `v5ai-infrastructure/src/main/java/xin/v5ai/nb/infrastructure/application/ApplicationVersionEntity.java`
- Create: `v5ai-infrastructure/src/main/java/xin/v5ai/nb/infrastructure/application/ApplicationMapper.java`
- Create: `v5ai-infrastructure/src/main/java/xin/v5ai/nb/infrastructure/application/ApplicationVersionMapper.java`
- Modify: `v5ai-infrastructure/src/main/java/xin/v5ai/nb/infrastructure/application/ApplicationRuntimeConfiguration.java`
- Test: `v5ai-agent/src/test/java/xin/v5ai/nb/application/ApplicationManagementServiceTest.java`
- Test: `v5ai-infrastructure/src/test/java/xin/v5ai/nb/infrastructure/application/MyBatisApplicationRepositoryTest.java`

**Interfaces:**
- Produces: `createApplication`, `listApplications`, `publishApplication`
- Produces: immutable `v5ai_application_version.snapshot_json`
- Produces: resolver that returns only `PUBLISHED` applications

- [ ] Write failing service tests for creating app, publishing only with enabled model, and rejecting unpublished runtime resolution.
- [ ] Implement application commands and service methods.
- [ ] Write failing persistence tests for saving app and published version snapshot.
- [ ] Implement MyBatis application repositories and replace in-memory runtime repository when DB beans exist.
- [ ] Run application tests and verify they pass.

## Task 4: Conversation, Message, Run, and RunEvent persistence completion

**Files:**
- Create: `v5ai-runtime/src/main/java/xin/v5ai/nb/runtime/Conversation.java`
- Create: `v5ai-runtime/src/main/java/xin/v5ai/nb/runtime/ConversationRepository.java`
- Create: `v5ai-runtime/src/main/java/xin/v5ai/nb/runtime/RunEventRepository.java`
- Modify: `v5ai-runtime/src/main/java/xin/v5ai/nb/runtime/PersistingAgentRuntime.java`
- Create: `v5ai-infrastructure/src/main/java/xin/v5ai/nb/infrastructure/runtime/RuntimeConversationEntity.java`
- Create: `v5ai-infrastructure/src/main/java/xin/v5ai/nb/infrastructure/runtime/RuntimeConversationMapper.java`
- Create: `v5ai-infrastructure/src/main/java/xin/v5ai/nb/infrastructure/runtime/MyBatisConversationRepository.java`
- Create: `v5ai-infrastructure/src/main/java/xin/v5ai/nb/infrastructure/runtime/RuntimeRunEventEntity.java`
- Create: `v5ai-infrastructure/src/main/java/xin/v5ai/nb/infrastructure/runtime/RuntimeRunEventMapper.java`
- Create: `v5ai-infrastructure/src/main/java/xin/v5ai/nb/infrastructure/runtime/MyBatisRunEventRepository.java`
- Test: `v5ai-runtime/src/test/java/xin/v5ai/nb/runtime/PersistingAgentRuntimeTest.java`
- Test: `v5ai-infrastructure/src/test/java/xin/v5ai/nb/infrastructure/runtime/MyBatisRuntimePersistenceTest.java`

**Interfaces:**
- Produces: conversation create/load when request conversation id is missing or new.
- Produces: persisted user/assistant messages.
- Produces: persisted `RUN_STARTED`, `TEXT_DELTA`, `RUN_COMPLETED` events.

- [ ] Write failing runtime tests that prove conversation rows and run events are persisted.
- [ ] Update `PersistingAgentRuntime` to call conversation and run event repositories.
- [ ] Write failing infrastructure tests for conversation/run event mapper payloads.
- [ ] Implement MyBatis repositories.
- [ ] Run runtime/infrastructure tests and verify they pass.

## Task 5: Published model to AgentScope runtime resolver

**Files:**
- Create: `v5ai-runtime/src/main/java/xin/v5ai/nb/runtime/AgentModelResolver.java`
- Modify: `v5ai-infrastructure/src/main/java/xin/v5ai/nb/infrastructure/runtime/RuntimeConfiguration.java`
- Create: `v5ai-infrastructure/src/main/java/xin/v5ai/nb/infrastructure/runtime/PublishedModelAgentTextExecutor.java`
- Test: `v5ai-infrastructure/src/test/java/xin/v5ai/nb/infrastructure/runtime/PublishedModelAgentTextExecutorTest.java`

**Interfaces:**
- Consumes: published `Agent.modelId`
- Produces: `AgentTextExecutor` that resolves a model per published application, then delegates to `AgentScopeHarnessExecutor`.

- [ ] Write failing tests for resolving model per application and falling back to development placeholder only when no resolver/model exists.
- [ ] Implement `AgentModelResolver` and executor wrapper.
- [ ] Wire runtime configuration with clear precedence: resolver-backed AgentScope executor first, single Model bean second, placeholder last.
- [ ] Run runtime configuration tests and verify they pass.

## Task 6: Verification and documentation

**Files:**
- Create: `docs/phase1-verification.md`
- Modify: `docs/api/phase1.md`

**Interfaces:**
- Produces: verification record for Phase 1 scope.

- [ ] Run targeted tests for model, application, runtime, API, and infrastructure modules.
- [ ] Run full backend `mvn -Dmaven.repo.local=/private/tmp/v5ai-m2 clean verify -Dsurefire.failIfNoSpecifiedTests=false -q`.
- [ ] Update docs with implemented endpoint list and remaining non-Phase-1 exclusions.
- [ ] Inspect git status and report unrelated files.
