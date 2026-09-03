# Agent Model Adapters Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 `v5ai_model` + `v5ai_model_provider` 中的发布模型配置解析为 AgentScope Java `Model`，供 Phase 1 Runtime 调用真实模型。

**Architecture:** 在 `v5ai-runtime` 保持 `AgentModelResolver` 端口；在 `v5ai-infrastructure` 增加 MyBatis 模型配置查询、凭据 JSON 解密解析和 AgentScope provider-specific adapter factory。`PublishedModelAgentTextExecutor` 不关心 provider 细节，只依赖 `AgentModelResolver`。

**Tech Stack:** JDK 21, Spring Boot 4.1.0, MyBatis-Plus, Jackson, AgentScope Harness 2.0, `agentscope-extensions-model-openai`, `agentscope-extensions-model-dashscope`.

## Global Constraints

- 项目名称：`v5ai-nb`。
- Java 包名：`xin.v5ai.nb`。
- Maven 模块名使用 `v5ai-*`。
- 数据表名使用 `v5ai_*`。
- Runtime 只解析 CHAT 模型。
- `OPENAI` 和 `OPENAI_COMPATIBLE` 使用 OpenAI-compatible adapter。
- `DASHSCOPE` 使用 DashScope adapter。
- `EMBEDDING` / `RERANK` 用于 Runtime 时抛明确异常。
- API DTO 不暴露 AgentScope Java SDK 类型。
- 新增行为先写失败测试，再写实现。

---

## Task 1: AgentScope extension dependencies and SDK characterization

**Files:**
- Modify: `pom.xml`
- Modify: `v5ai-infrastructure/pom.xml`

**Interfaces:**
- Produces dependencies for `OpenAIChatModel` and `DashScopeChatModel`.

- [ ] Add AgentScope OpenAI and DashScope extension dependencies.
- [ ] Run a compile/test command to download dependencies.
- [ ] Use `javap` to confirm builder APIs before writing production adapter code.

## Task 2: Model configuration query port

**Files:**
- Create: `v5ai-infrastructure/src/main/java/xin/v5ai/nb/infrastructure/model/ModelRuntimeConfig.java`
- Create: `v5ai-infrastructure/src/main/java/xin/v5ai/nb/infrastructure/model/ModelRuntimeConfigGateway.java`
- Modify: `v5ai-infrastructure/src/main/java/xin/v5ai/nb/infrastructure/model/ModelConfigMapper.java`
- Test: `v5ai-infrastructure/src/test/java/xin/v5ai/nb/infrastructure/model/ModelRuntimeConfigGatewayTest.java`

**Interfaces:**
- Produces: `ModelRuntimeConfig findRuntimeConfigByModelId(Long modelId)` containing model id/key/type, provider key/type, and encrypted credentials.

- [ ] Write failing test for mapping model + provider runtime config.
- [ ] Implement mapper/gateway method using MyBatis annotation query.
- [ ] Run test and verify it passes.

## Task 3: Credential JSON parser

**Files:**
- Create: `v5ai-infrastructure/src/main/java/xin/v5ai/nb/infrastructure/runtime/AgentModelCredentialConfig.java`
- Test: `v5ai-infrastructure/src/test/java/xin/v5ai/nb/infrastructure/runtime/AgentModelCredentialConfigTest.java`

**Interfaces:**
- Produces parsed fields: `apiKey`, `baseUrl`, `temperature`, `maxTokens`.

- [ ] Write failing tests for complete JSON and missing optional fields.
- [ ] Implement Jackson-backed record/parser.
- [ ] Run test and verify it passes.

## Task 4: AgentScope model factory

**Files:**
- Create: `v5ai-infrastructure/src/main/java/xin/v5ai/nb/infrastructure/runtime/AgentScopeModelFactory.java`
- Test: `v5ai-infrastructure/src/test/java/xin/v5ai/nb/infrastructure/runtime/AgentScopeModelFactoryTest.java`

**Interfaces:**
- Produces: `Model create(ModelRuntimeConfig config, String plaintextCredentials)`.
- Throws `IllegalArgumentException` for non-CHAT models and unsupported provider types.

- [ ] Write failing tests for OpenAI-compatible, DashScope, unsupported type, and non-chat model.
- [ ] Implement factory using confirmed AgentScope builders.
- [ ] Run test and verify it passes.

## Task 5: MyBatis AgentModelResolver

**Files:**
- Create: `v5ai-infrastructure/src/main/java/xin/v5ai/nb/infrastructure/runtime/MyBatisAgentModelResolver.java`
- Modify: `v5ai-infrastructure/src/main/java/xin/v5ai/nb/infrastructure/runtime/RuntimeConfiguration.java`
- Test: `v5ai-infrastructure/src/test/java/xin/v5ai/nb/infrastructure/runtime/MyBatisAgentModelResolverTest.java`

**Interfaces:**
- Consumes: `ModelConfigRetrieve`, `CredentialCipher`, `AgentScopeModelFactory`.
- Produces: `AgentModelResolver` bean.

- [ ] Write failing resolver test that decrypts credentials and delegates to factory.
- [ ] Implement resolver.
- [ ] Wire it as a Spring bean so `PublishedModelAgentTextExecutor` becomes active automatically.
- [ ] Run resolver/runtime tests and verify they pass.

## Task 6: Verification and docs

**Files:**
- Modify: `docs/phase1-verification-update.md`
- Modify: `docs/api/phase1.md`

**Interfaces:**
- Produces verification evidence and credential JSON documentation.

- [ ] Run targeted adapter tests.
- [ ] Run full backend Maven `clean verify`.
- [ ] Update docs with supported providers and credential JSON schema.
