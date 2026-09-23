# v5ai-nb Phase 1 Foundation and Agent Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first runnable `v5ai-nb` platform slice with JDK 21, MyBatis-Plus, Sa-Token + JWT, dynamic model configuration, published Applications, AgentScope execution, SSE streaming, and persisted conversations.

**Architecture:** Create a new modular Maven project under `v5ai-nb`, independent from `snail-ai`. Keep domain contracts separate from infrastructure implementations, expose all AgentScope execution through `v5ai-runtime`, and use a single Spring Boot application with an asynchronous boundary reserved for later phases. Runtime requests resolve only published Agent versions.

**Tech Stack:** JDK 21, Spring Boot 4.x, Spring WebFlux, Project Reactor, AgentScope Java 2.0, MyBatis-Plus, PostgreSQL, Flyway, Sa-Token with JWT, BCrypt, Jackson, JUnit 5, Mockito, and Testcontainers where available.

## Global Constraints

- Project name is `v5ai-nb`.
- Java root package is `xin.v5ai.nb`.
- Maven modules use the `v5ai-*` prefix.
- Database tables use the `v5ai_` prefix.
- The new project must not modify or depend on `snail-ai` source code, modules, database tables, or APIs.
- Do not add Server-Agent, gRPC, client-node registration, or routing.
- JDK version is 21.
- ORM is MyBatis-Plus.
- Authentication is Sa-Token with JWT session mode.
- Use TDD: every production behavior task starts with a failing test and records the red result before implementation.
- Do not expose AgentScope implementation classes from API DTOs.
- Runtime reads only published Agent versions.
- No RAG, MCP, Skill, Workflow, or frontend implementation in Phase 1; create only extension interfaces needed by the runtime boundary.

---

## File Map

```text
v5ai-nb/
├── pom.xml
├── README.md
├── .gitignore
├── v5ai-common/
├── v5ai-domain/
├── v5ai-infrastructure/
├── v5ai-model/
├── v5ai-runtime/
├── v5ai-agent/
├── v5ai-api/
└── v5ai-starter/
```

The phase intentionally keeps `v5ai-rag`, `v5ai-mcp`, `v5ai-skill`, and `v5ai-worker` out of the first build until their contracts are needed. The parent POM and package layout must leave those names available for later modules.

## Task 1: Create the Maven project skeleton

**Files:**
- Create: `pom.xml`
- Create: `v5ai-common/pom.xml`
- Create: `v5ai-domain/pom.xml`
- Create: `v5ai-infrastructure/pom.xml`
- Create: `v5ai-model/pom.xml`
- Create: `v5ai-runtime/pom.xml`
- Create: `v5ai-agent/pom.xml`
- Create: `v5ai-api/pom.xml`
- Create: `v5ai-starter/pom.xml`
- Create: `README.md`
- Create: `.gitignore`
- Test: `v5ai-starter/src/test/java/xin/v5ai/nb/starter/V5aiApplicationContextTest.java`

**Interfaces:**
- Produces the Maven reactor and Spring Boot entry point `xin.v5ai.nb.starter.V5aiApplication`.
- Parent properties expose `java.version=21`, `mybatis-plus.version`, `sa-token.version`, and the AgentScope version in one place.

- [ ] **Step 1: Write the failing context test**

```java
@SpringBootTest
class V5aiApplicationContextTest {
    @Test
    void applicationContextLoads() {
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw -pl v5ai-starter -am test -Dtest=V5aiApplicationContextTest`

Expected: FAIL because the Maven modules and `V5aiApplication` do not exist.

- [ ] **Step 3: Add the parent POM, modules, and minimal Spring Boot application**

The parent must set compiler release 21, UTF-8 encoding, Spring Boot dependency management, and module dependency order. The starter must contain:

```java
package xin.v5ai.nb.starter;

@SpringBootApplication
public class V5aiApplication {
    public static void main(String[] args) {
        SpringApplication.run(V5aiApplication.class, args);
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./mvnw -pl v5ai-starter -am test -Dtest=V5aiApplicationContextTest`

Expected: PASS with exit code 0.

- [ ] **Step 5: Commit**

```bash
git add pom.xml v5ai-*/pom.xml v5ai-starter/src README.md .gitignore
git commit -m "feat: 创建 v5ai-nb 项目骨架"
```

## Task 2: Add common contracts and database migration baseline

**Files:**
- Create: `../../../v5ai-common/v5ai-common-core/src/main/java/xin/v5ai/nb/common/core/api/ApiResponse.java`
- Create: `../../../v5ai-common/v5ai-common-core/src/main/java/xin/v5ai/nb/common/core/exception/V5aiException.java`
- Create: `../../../v5ai-common/v5ai-common-core/src/main/java/xin/v5ai/nb/common/core/exception/ErrorCode.java`
- Create: `../../../v5ai-common/v5ai-common-core/src/main/java/xin/v5ai/nb/common/core/page/PageQuery.java`
- Create: `v5ai-infrastructure/src/main/resources/db/migration/V1__phase1_schema.sql`
- Create: `v5ai-infrastructure/src/test/java/xin/v5ai/nb/infrastructure/schema/TablePrefixTest.java`

**Interfaces:**
- `ApiResponse<T>` exposes `success(T data)` and `failure(ErrorCode code, String message)`.
- `PageQuery` exposes `page` and `size` with bounds of page >= 1 and 1 <= size <= 100.
- Migration creates only `v5ai_*` tables required by Phase 1.

- [ ] **Step 1: Write failing tests for response and table naming**

```java
@Test
void pageSizeIsBounded() {
    assertThatThrownBy(() -> new PageQuery(1, 101)).isInstanceOf(IllegalArgumentException.class);
}

@Test
void phaseOneTablesUseV5aiPrefix() {
    assertThat(migrationSql).contains("CREATE TABLE v5ai_model_provider");
    assertThat(migrationSql).contains("CREATE TABLE v5ai_application");
    assertThat(migrationSql).contains("CREATE TABLE v5ai_application_version");
    assertThat(migrationSql).contains("CREATE TABLE v5ai_conversation");
    assertThat(migrationSql).contains("CREATE TABLE v5ai_message");
}
```

- [ ] **Step 2: Run the tests and verify the expected failures**

Run: `./mvnw -pl v5ai-infrastructure -am test -Dtest=TablePrefixTest`

Expected: FAIL because the contracts and migration do not exist.

- [ ] **Step 3: Implement the common contracts and V1 schema**

The Phase 1 schema must include:

```text
v5ai_model_provider
v5ai_model
v5ai_application
v5ai_application_version
v5ai_application_model
v5ai_conversation
v5ai_message
v5ai_agent_state
v5ai_run
v5ai_run_event
v5ai_user
v5ai_api_key
```

Use UUID or BIGINT consistently across all tables, add foreign keys for Phase 1 relations, include `tenant_id` on tenant-scoped rows, and store timestamps as `TIMESTAMPTZ`. Do not store plaintext API keys.

- [ ] **Step 4: Run the tests and migration validation**

Run: `./mvnw -pl v5ai-infrastructure -am test -Dtest=TablePrefixTest`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add v5ai-common v5ai-infrastructure/src/main/resources/db/migration v5ai-infrastructure/src/test
git commit -m "feat: 增加公共契约和一期数据库结构"
```

## Task 3: Implement Sa-Token + JWT authentication

**Files:**
- Create: `v5ai-infrastructure/src/main/java/xin/v5ai/nb/infrastructure/security/SaTokenConfiguration.java`
- Create: `v5ai-infrastructure/src/main/java/xin/v5ai/nb/infrastructure/security/V5aiLoginService.java`
- Create: `v5ai-api/src/main/java/xin/v5ai/nb/api/auth/AuthController.java`
- Create: `v5ai-api/src/main/java/xin/v5ai/nb/api/auth/LoginRequest.java`
- Create: `v5ai-api/src/main/java/xin/v5ai/nb/api/auth/LoginResponse.java`
- Create: `v5ai-api/src/test/java/xin/v5ai/nb/api/auth/AuthControllerTest.java`

**Interfaces:**
- `V5aiLoginService.login(LoginRequest request)` returns a Sa-Token JWT token.
- `AuthController` exposes `POST /api/auth/login` and `POST /api/auth/logout`.
- Protected admin endpoints require a valid login token; runtime App API Key authentication is separate and is not implemented as user login.

- [ ] **Step 1: Write failing authentication tests**

```java
@WebFluxTest(AuthController.class)
class AuthControllerTest {
    @Test
    void loginReturnsJwtTokenForValidCredentials() {
        // submit valid credentials and assert tokenType=Bearer and non-empty token
    }

    @Test
    void protectedEndpointRejectsMissingToken() {
        // call a protected endpoint without token and assert HTTP 401
    }
}
```

- [ ] **Step 2: Run the tests and verify they fail**

Run: `./mvnw -pl v5ai-api -am test -Dtest=AuthControllerTest`

Expected: FAIL because Sa-Token configuration and controller do not exist.

- [ ] **Step 3: Implement authentication**

Configure Sa-Token JWT mode, use BCrypt password verification, define a request filter/interceptor for `/api/admin/**`, and return a stable error body for unauthorized requests. Credentials may use a seeded development admin only through configuration; production must read users from the database.

- [ ] **Step 4: Run the tests and verify they pass**

Run: `./mvnw -pl v5ai-api -am test -Dtest=AuthControllerTest`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add v5ai-infrastructure/src/main/java v5ai-api/src/main/java v5ai-api/src/test
git commit -m "feat: 增加 Sa-Token JWT 认证"
```

## Task 4: Add MyBatis-Plus model and provider management

**Files:**
- Create: `v5ai-domain/src/main/java/xin/v5ai/nb/model/ModelProvider.java`
- Create: `v5ai-domain/src/main/java/xin/v5ai/nb/model/ModelConfig.java`
- Create: `v5ai-domain/src/main/java/xin/v5ai/nb/model/ModelProviderRepository.java`
- Create: `v5ai-domain/src/main/java/xin/v5ai/nb/model/ModelConfigRepository.java`
- Create: `v5ai-model/src/main/java/xin/v5ai/nb/model/ModelManagementService.java`
- Create: `v5ai-infrastructure/src/main/java/xin/v5ai/nb/infrastructure/model/ModelProviderMapper.java`
- Create: `v5ai-infrastructure/src/main/java/xin/v5ai/nb/infrastructure/model/ModelConfigMapper.java`
- Create: `v5ai-api/src/main/java/xin/v5ai/nb/api/model/ModelController.java`
- Create: `v5ai-api/src/test/java/xin/v5ai/nb/api/model/ModelControllerTest.java`

**Interfaces:**
- `ModelManagementService.createProvider(CreateProviderCommand)` creates a provider.
- `ModelManagementService.createModel(CreateModelCommand)` creates a model with encrypted credentials.
- `ModelManagementService.listModels(PageQuery)` returns model summaries.
- MyBatis-Plus mappers target `v5ai_model_provider` and `v5ai_model`.

- [ ] **Step 1: Write failing service tests**

```java
@Test
void modelCredentialsAreEncryptedBeforePersistence() {
    service.createModel(commandWithApiKey("secret-key"));
    verify(repository).save(argThat(model -> !model.credentials().equals("secret-key")));
}
```

- [ ] **Step 2: Run the tests and verify failure**

Run: `./mvnw -pl v5ai-model -am test -Dtest=ModelManagementServiceTest`

Expected: FAIL because the service and domain objects do not exist.

- [ ] **Step 3: Implement domain objects, MyBatis-Plus persistence, service, and admin endpoints**

Use explicit status/type enums instead of magic values. Add validation for provider key uniqueness, model type, endpoint URL, and required credential fields. Keep encryption behind `CredentialCipher` so tests can use a deterministic test key.

- [ ] **Step 4: Run unit and API tests**

Run: `./mvnw -pl v5ai-model,v5ai-api -am test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add v5ai-domain/src/main/java v5ai-model v5ai-infrastructure/src/main/java v5ai-api/src/main/java
git commit -m "feat: 增加模型 Provider 和配置管理"
```

## Task 5: Implement Agent draft and publish lifecycle

**Files:**
- Create: `v5ai-domain/src/main/java/xin/v5ai/nb/application/Agent.java`
- Create: `v5ai-domain/src/main/java/xin/v5ai/nb/application/ApplicationVersion.java`
- Create: `v5ai-agent/src/main/java/xin/v5ai/nb/application/ApplicationManagementService.java`
- Create: `v5ai-agent/src/main/java/xin/v5ai/nb/application/ApplicationPublishValidator.java`
- Create: `v5ai-infrastructure/src/main/java/xin/v5ai/nb/infrastructure/application/ApplicationMapper.java`
- Create: `v5ai-infrastructure/src/main/java/xin/v5ai/nb/infrastructure/application/ApplicationVersionMapper.java`
- Create: `v5ai-api/src/main/java/xin/v5ai/nb/api/application/ApplicationController.java`
- Create: `v5ai-api/src/test/java/xin/v5ai/nb/api/application/ApplicationControllerTest.java`

**Interfaces:**
- `ApplicationManagementService.create(CreateApplicationCommand)` creates a draft.
- `ApplicationManagementService.updateDraft(UpdateApplicationCommand)` changes only the draft.
- `ApplicationManagementService.publish(ApplicationId)` validates and publishes an immutable version.
- `PublishedApplicationResolver.resolve(String agentKey)` returns only the published version.

- [ ] **Step 1: Write failing lifecycle tests**

```java
@Test
void publishingInvalidApplicationIsRejected() {
    assertThatThrownBy(() -> service.publish(applicationIdWithoutModel()))
        .isInstanceOf(ApplicationPublishException.class);
}

@Test
void runtimeResolverNeverReturnsDraft() {
    assertThat(resolver.resolve("demo").status()).isEqualTo(ApplicationStatus.PUBLISHED);
}
```

- [ ] **Step 2: Run the tests and verify failure**

Run: `./mvnw -pl v5ai-agent -am test -Dtest=ApplicationManagementServiceTest,PublishedApplicationResolverTest`

Expected: FAIL because lifecycle services do not exist.

- [ ] **Step 3: Implement Agent lifecycle and persistence**

Agent updates write draft configuration. Publishing creates an immutable version number, validates that the selected model exists and is enabled, and atomically updates the published version pointer. Resolver queries must include `status=PUBLISHED`.

- [ ] **Step 4: Run the tests and verify they pass**

Run: `./mvnw -pl v5ai-agent -am test -Dtest=ApplicationManagementServiceTest,PublishedApplicationResolverTest`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add v5ai-domain/src/main/java v5ai-agent v5ai-infrastructure/src/main/java v5ai-api/src/main/java
git commit -m "feat: 增加 Agent 草稿发布生命周期"
```

## Task 6: Build the AgentScope runtime boundary

**Files:**
- Create: `v5ai-runtime/src/main/java/xin/v5ai/nb/runtime/AgentRuntime.java`
- Create: `v5ai-runtime/src/main/java/xin/v5ai/nb/runtime/AgentScopeRuntime.java`
- Create: `v5ai-runtime/src/main/java/xin/v5ai/nb/runtime/AgentFactory.java`
- Create: `v5ai-runtime/src/main/java/xin/v5ai/nb/runtime/RuntimeEvent.java`
- Create: `v5ai-runtime/src/main/java/xin/v5ai/nb/runtime/AgentRunRequest.java`
- Create: `v5ai-runtime/src/main/java/xin/v5ai/nb/runtime/AgentRunResult.java`
- Create: `v5ai-runtime/src/test/java/xin/v5ai/nb/runtime/AgentScopeRuntimeTest.java`

**Interfaces:**
- `AgentRuntime.stream(AgentRunRequest)` returns `Flux<RuntimeEvent>`.
- `AgentRuntime.call(AgentRunRequest)` returns `Mono<AgentRunResult>`.
- `AgentFactory.create(PublishedApplication application, RuntimeContext context)` returns the configured AgentScope agent.
- `RuntimeRunEventDTO` is platform-owned and contains `runId`, `type`, `payload`, and timestamp.

- [ ] **Step 1: Write failing runtime tests**

```java
@Test
void streamConvertsAgentTextToPlatformTextDelta() {
    StepVerifier.create(runtime.stream(request("hello")))
        .expectNextMatches(event -> event.type() == RuntimeEventType.TEXT_DELTA)
        .verifyComplete();
}

@Test
void runtimeRejectsUnpublishedApplication() {
    StepVerifier.create(runtime.stream(requestForDraftApplication()))
        .expectError(ApplicationNotPublishedException.class)
        .verify();
}
```

- [ ] **Step 2: Run the tests and verify failure**

Run: `./mvnw -pl v5ai-runtime -am test -Dtest=AgentScopeRuntimeTest`

Expected: FAIL because AgentScope runtime adapters do not exist.

- [ ] **Step 3: Implement the minimal AgentScope adapter**

Resolve the published Agent, create the configured `ChatModel`, construct a basic AgentScope Agent, invoke it with the request message, and map AgentScope events into `RuntimeRunEventDTO`. Keep RAG, MCP, Skill, and permission resolvers represented by interfaces returning empty implementations in Phase 1.

- [ ] **Step 4: Run the runtime tests**

Run: `./mvnw -pl v5ai-runtime -am test -Dtest=AgentScopeRuntimeTest`

Expected: PASS using a deterministic fake ChatModel or local test model; no network API key is required.

- [ ] **Step 5: Commit**

```bash
git add v5ai-runtime
git commit -m "feat: 增加 AgentScope 运行时边界"
```

## Task 7: Add conversation and run persistence

**Files:**
- Create: `v5ai-domain/src/main/java/xin/v5ai/nb/conversation/Conversation.java`
- Create: `v5ai-domain/src/main/java/xin/v5ai/nb/conversation/Message.java`
- Create: `v5ai-domain/src/main/java/xin/v5ai/nb/runtime/RunRecord.java`
- Create: `v5ai-domain/src/main/java/xin/v5ai/nb/conversation/ConversationRepository.java`
- Create: `v5ai-domain/src/main/java/xin/v5ai/nb/conversation/MessageRepository.java`
- Create: `v5ai-domain/src/main/java/xin/v5ai/nb/runtime/RunRecordRepository.java`
- Create: `v5ai-infrastructure/src/main/java/xin/v5ai/nb/infrastructure/conversation/*Mapper.java`
- Create: `v5ai-runtime/src/main/java/xin/v5ai/nb/runtime/PersistingAgentRuntime.java`
- Create: `v5ai-runtime/src/test/java/xin/v5ai/nb/runtime/PersistingAgentRuntimeTest.java`

**Interfaces:**
- `PersistingAgentRuntime.stream(request)` creates or loads a conversation, persists the user message, persists run start and terminal status, and persists the assistant message on completion.
- Persisted records include `tenantId`, `userId`, `agentKey`, `conversationId`, `runId`, status, error code, and timestamps.

- [ ] **Step 1: Write failing persistence tests**

```java
@Test
void completedRunPersistsUserAndAssistantMessages() {
    StepVerifier.create(runtime.stream(request("hello")).then()).verifyComplete();
    verify(messageRepository).save(argThat(message -> message.role() == MessageRole.USER));
    verify(messageRepository).save(argThat(message -> message.role() == MessageRole.ASSISTANT));
    verify(runRecordRepository).markCompleted(any());
}
```

- [ ] **Step 2: Run the tests and verify failure**

Run: `./mvnw -pl v5ai-runtime -am test -Dtest=PersistingAgentRuntimeTest`

Expected: FAIL because persistence adapters do not exist.

- [ ] **Step 3: Implement persistence decorator and MyBatis-Plus mappers**

Use a decorator around `AgentRuntime`; do not mix SQL statements into AgentScope event conversion. Persist large event payloads with a bounded summary in the Phase 1 schema, and preserve the full assistant response in `v5ai_message`.

- [ ] **Step 4: Run persistence tests**

Run: `./mvnw -pl v5ai-runtime -am test -Dtest=PersistingAgentRuntimeTest`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add v5ai-domain/src/main/java v5ai-infrastructure/src/main/java v5ai-runtime
git commit -m "feat: 持久化会话消息和 Agent 运行记录"
```

## Task 8: Expose admin and streaming runtime APIs

**Files:**
- Create: `v5ai-api/src/main/java/xin/v5ai/nb/api/runtime/ChatRequest.java`
- Create: `v5ai-api/src/main/java/xin/v5ai/nb/api/runtime/ChatController.java`
- Create: `v5ai-api/src/main/java/xin/v5ai/nb/api/runtime/RuntimeEventSseMapper.java`
- Create: `v5ai-api/src/main/java/xin/v5ai/nb/api/apiKey/ApplicationApiKeyAuthenticationFilter.java`
- Create: `v5ai-api/src/test/java/xin/v5ai/nb/api/runtime/ChatControllerTest.java`
- Create: `v5ai-starter/src/main/resources/application.yml`
- Create: `v5ai-starter/src/main/resources/application-dev.yml`
- Create: `.env.example`

**Interfaces:**
- `POST /api/v1/agents/{agentKey}/chat/stream` accepts `{ "conversationId": "...", "query": "..." }` and returns `text/event-stream`.
- `POST /api/v1/agents/{agentKey}/chat` returns the completed `AgentRunVo`.
- Admin model and Agent endpoints require Sa-Token JWT.
- Runtime endpoints require an Agent API Key resolved by `agentKey`.

- [ ] **Step 1: Write failing WebFlux API tests**

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ChatControllerTest {
    @Test
    void streamEndpointReturnsTypedSseEvents() {
        // call with a valid app key and assert content type text/event-stream
        // assert events include run_started, text_delta, and run_completed
    }

    @Test
    void streamEndpointRejectsInvalidAppKey() {
        // assert HTTP 401 or 403 and no AgentRuntime invocation
    }
}
```

- [ ] **Step 2: Run the tests and verify failure**

Run: `./mvnw -pl v5ai-api,v5ai-starter -am test -Dtest=ChatControllerTest`

Expected: FAIL because the controller, API-key filter, and runtime configuration do not exist.

- [ ] **Step 3: Implement the streaming and completion endpoints**

Use `Flux<ServerSentEvent<RuntimeEvent>>` for streaming. Validate `agentKey`, query length, conversation ownership, and request content before calling `PersistingAgentRuntime`. Map runtime failures to terminal SSE error events and a stable JSON error response for non-streaming calls. Never log API keys or model credentials.

- [ ] **Step 4: Run all Phase 1 tests**

Run: `./mvnw clean verify`

Expected: PASS with no test failures. If PostgreSQL/Testcontainers is unavailable, the migration integration test must be profile-gated and the unit/API suite must still pass without external services.

- [ ] **Step 5: Commit**

```bash
git add v5ai-api v5ai-starter/src .env.example
git commit -m "feat: 增加 Agent 对话和 SSE 运行接口"
```

## Task 9: Phase 1 verification and documentation

**Files:**
- Modify: `README.md`
- Create: `docs/phase1-verification.md`
- Create: `docs/api/phase1.md`

- [ ] **Step 1: Document local setup**

Document JDK 21, PostgreSQL with pgvector, Redis, environment variables, Flyway startup, development admin login, model configuration, Agent publish, and SSE curl example.

- [ ] **Step 2: Run the complete verification matrix**

Run:

```bash
java -version
./mvnw clean verify
./mvnw -pl v5ai-starter spring-boot:run
```

Verify manually with a deterministic test model or configured OpenAI-compatible endpoint:

```bash
curl -N -X POST http://localhost:8080/api/v1/agents/demo/chat/stream \
  -H 'Authorization: Bearer <application-api-key>' \
  -H 'Content-Type: application/json' \
  -d '{"query":"你好"}'
```

Expected: SSE events include `run_started`, at least one response event, and `run_completed`; database contains corresponding `v5ai_conversation`, `v5ai_message`, and `v5ai_run` rows.

- [ ] **Step 3: Run repository and secret checks**

Run: `git status --short` and `rg -n "sk-|api[_-]?key|password" --glob '!target/**' --glob '!.env' .`

Expected: no real credentials committed and only documented placeholder values remain.

- [ ] **Step 4: Commit documentation**

```bash
git add README.md docs
git commit -m "docs: 增加 v5ai-nb 一期运行文档"
```

## Completion Checklist

- [ ] `java -version` reports JDK 21.
- [ ] Maven reactor uses only `v5ai-*` module names.
- [ ] Root package is `xin.v5ai.nb`.
- [ ] All Phase 1 tables start with `v5ai_`.
- [ ] Sa-Token JWT login and protected admin endpoint tests pass.
- [ ] Model credentials are encrypted before persistence.
- [ ] Agent cannot publish without an enabled model.
- [ ] Runtime rejects drafts and disabled Applications.
- [ ] AgentScope events are mapped to platform-owned events.
- [ ] SSE endpoint requires Agent API Key.
- [ ] Conversations, messages, runs, and terminal events are persisted.
- [ ] `./mvnw clean verify` passes.
- [ ] No Server-Agent, gRPC, RAG, MCP, Skill, or Workflow code has been added beyond Phase 1 extension interfaces.
