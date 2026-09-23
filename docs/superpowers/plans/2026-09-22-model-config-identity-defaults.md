# Model Configuration Identity and Default Selection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Support duplicate provider/model-key configurations, ID-based usage attribution, and default-first model selection with hard errors when no model is available.

**Architecture:** Keep model references ID-based. Add model ID to internal model-call metadata and usage records, while retaining model key as a display/history snapshot. Centralize fallback selection in `ModelConfigRetrieve` by model type.

**Tech Stack:** Java 21, Spring Boot, MyBatis-Plus, PostgreSQL/Flyway, JUnit 5/Mockito, Vue 3/TypeScript.

**Spec:** `docs/superpowers/specs/2026-09-22-model-config-identity-defaults-design.md`

## Global Constraints

- Do not modify applied migration history; add a new incremental migration.
- `v5ai_model.id` is the authoritative model identity.
- New usage records must use `model_id` for attribution; `model_key` remains a snapshot.
- Selection order is enabled default, then enabled lowest ID, then a configuration error.
- No implicit local HashEmbedding fallback when no EMBEDDING model exists.

## Review Focus

- Two rows with identical provider/model key resolve independently by ID: test runtime config retrieval.
- Default is selected over an older enabled non-default row: test every model type selector.
- No available model fails clearly: test EMBEDDING and generic selector callers.
- Usage attribution survives duplicate keys: test model ID propagation and persistence.
- Existing nullable historical usage remains readable: test mapping/display compatibility.

### Task 1: Schema and model option identity

**Files:**
- Create: `v5ai-modules/v5ai-platform/src/main/resources/db/migration/V48__model_identity_comments.sql`
- Modify: model option service, model entity comments, schema docs, model usage domain/VO
- Test: model service option tests and migration-oriented assertions where available

- [ ] Add migration dropping the old unique constraint, adding a non-unique provider/key index, adding nullable usage `model_id`, and indexing usage by model ID.
- [ ] Change option labels to `modelId/modelName (modelType)` and order enabled options by default first then ID.
- [ ] Update model usage persistence/domain projections with nullable `modelId`.
- [ ] Add tests for duplicate option labels being ID-distinguishable and default-first ordering.

### Task 2: Unified default-first model selection

**Files:**
- Modify: `ModelConfigRetrieve.java`, `ModelConfigRetrieveImpl.java`, embedding client, knowledge-base UI initialization
- Test: model retrieval and embedding client tests

- [ ] Replace embedding-only first-enabled lookup with a type-parameterized default-first selector.
- [ ] Throw a clear configuration exception when no enabled model exists.
- [x] Remove the implicit HashEmbedding fallback from the missing-model path.
- [ ] Update the knowledge-base chat model initializer to explicitly prefer `isDefault`.
- [ ] Add tests for CHAT, EMBEDDING, and RERANK selection and no-model failures.

### Task 3: Model ID propagation into usage

**Files:**
- Modify: `AgentTextEvent`, runtime event conversion, both model executors, `PersistingAgentRuntime`, `ModelUsageDTO`, usage service
- Test: runtime persistence and usage service tests

- [ ] Carry model ID with internal model-call metadata while preserving the external model-name payload.
- [ ] Track the model ID in `PersistingAgentRuntime` for completed, failed, canceled, and no-start edge paths.
- [ ] Persist both model ID and model key.
- [ ] Add tests proving duplicate keys remain separate by ID.

### Task 4: Frontend identity and documentation

**Files:**
- Modify: `ModelsView.vue`, model option consumers, API types, docs/schema comments

- [ ] Make all model selectors show the new ID-based label.
- [ ] Ensure explicit default-first selection is used where a consumer initializes from the first option.
- [ ] Update API/schema documentation to describe ID identity and duplicate configurations.

### Task 5: Verification

**Files:** all changed files

- [ ] Run focused model/runtime/RAG tests.
- [ ] Run backend `mvn -Dmaven.repo.local=/private/tmp/v5ai-m2 clean verify -Dmaven.test.skip=false -Dsurefire.failIfNoSpecifiedTests=false`.
- [ ] Run affected frontend build(s).
- [ ] Review diff, migration ordering, and working-tree status.
