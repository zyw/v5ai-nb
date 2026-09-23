# RAG Parser Engines Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Implement the `default`/Docling/MinerU document parsing engines in `v5ai-nb`, including structured results, service adapters, Worker integration, validation, UI configuration, persistence, and tests.

**Architecture:** Keep the existing built-in PDFBox/POI/Jsoup parsers behind a `default` engine. Add a structured parsing port and Spring-registered engine registry; Docling and MinerU are HTTP adapters. The Worker always invokes the selected engine through a fallback coordinator: external failure logs `WARN` and retries the same source with `default`; only default failure marks the document/task failed.

**Tech Stack:** Java 21, Spring Boot 4.1, MyBatis-Plus, Hutool HTTP/JSON, JUnit 5/AssertJ/Mockito, Vue 3 + TypeScript + Naive UI, Flyway PostgreSQL migrations.

**Spec:** `docs/RAG解析引擎Docling和MinerU集成方案.md`

## Global Constraints

- `default` must remain available and is the mandatory fallback for Docling and MinerU failures.
- External parser failures must emit `WARN` logs containing document ID, knowledge base ID, engine, and reason without secrets, raw files, or complete response bodies.
- MinerU must follow the attached OpenAPI contract: `/health`, `/file_parse`, `/tasks`, `/tasks/{task_id}`, `/tasks/{task_id}/result`; do not invent `/v1/uploads`, `tier`, `outputFormat`, or mandatory Bearer auth.
- Service URLs, credentials, and optional headers are deployment configuration, never knowledge-base JSON or UI-submitted fields.
- Keep changes scoped to the requested RAG parsing capability and preserve existing indexing idempotency.
- Follow repository Java conventions; no public method with more than three parameters unless it uses a request/context object.

## Review Focus

- External service unavailable: fallback to default, one `WARN`, task continues.
- External result is HTTP-success but empty/invalid: fallback to default.
- Default parser failure after fallback: task/document fail with sanitized error.
- MinerU response schema is intentionally open: support JSON/ZIP content types and preserve unknown fields.
- Existing knowledge-base configs without `parseParams`, or with `engine=docling`, remain readable and default-compatible.

### Task 1: Establish structured parser contracts and configuration

**Files:**
- Modify: `v5ai-modules/v5ai-rag/src/main/java/xin/v5ai/nb/rag/core/config/RagConfigDO.java`
- Modify: `v5ai-modules/v5ai-rag/src/main/java/xin/v5ai/nb/rag/core/parser/DocumentParser.java`
- Create: `v5ai-modules/v5ai-rag/src/main/java/xin/v5ai/nb/rag/core/parser/ParseRequest.java`
- Create: `v5ai-modules/v5ai-rag/src/main/java/xin/v5ai/nb/rag/core/parser/ParsedDocument.java`
- Create: `v5ai-modules/v5ai-rag/src/main/java/xin/v5ai/nb/rag/core/parser/DocumentParseEngine.java`
- Create: `v5ai-modules/v5ai-rag/src/main/java/xin/v5ai/nb/rag/core/parser/BuiltinDocumentParseEngine.java`
- Create: `v5ai-modules/v5ai-rag/src/test/java/xin/v5ai/nb/rag/core/parser/ParsedDocumentTest.java`
- Create: `v5ai-modules/v5ai-rag/src/test/java/xin/v5ai/nb/rag/core/config/RagConfigDOTest.java`

**Steps:**

- [ ] Write tests for default-compatible config parsing, MinerU parameter round-trip, structured result defaults, and text/markdown selection.
- [ ] Run the focused tests and verify they fail because the new contracts/config fields do not exist.
- [ ] Implement records/DTOs and `RagConfigDO` Docling/MinerU fields from the spec; keep existing parser compatibility methods.
- [ ] Wrap the existing `DocumentParserRegistry` as the built-in default engine without changing its file-type behavior.
- [ ] Run focused tests and the existing parser tests.

### Task 2: Add engine registry and fallback coordinator

**Files:**
- Modify: `v5ai-modules/v5ai-rag/src/main/java/xin/v5ai/nb/rag/core/parser/DocumentParserRegistry.java`
- Create: `v5ai-modules/v5ai-rag/src/main/java/xin/v5ai/nb/rag/core/parser/DocumentParseEngineRegistry.java`
- Create: `v5ai-modules/v5ai-rag/src/main/java/xin/v5ai/nb/rag/core/parser/FallbackDocumentParseEngine.java`
- Create: `v5ai-modules/v5ai-rag/src/test/java/xin/v5ai/nb/rag/core/parser/DocumentParseEngineRegistryTest.java`
- Create: `v5ai-modules/v5ai-rag/src/test/java/xin/v5ai/nb/rag/core/parser/FallbackDocumentParseEngineTest.java`

**Steps:**

- [ ] Write failing tests for default resolution, case-insensitive engine names, disabled/unavailable external engine, empty result, warning context, and default failure propagation.
- [ ] Run the tests and verify the expected missing-type/behavior failures.
- [ ] Implement Spring engine registration and explicit injected default engine.
- [ ] Implement fallback around external parse exceptions and empty normalized content; avoid retrying the same external engine.
- [ ] Run focused tests and parser module tests.

### Task 3: Implement Docling HTTP adapter

**Files:**
- Create: `v5ai-modules/v5ai-rag/src/main/java/xin/v5ai/nb/rag/core/parser/docling/DoclingParserProperties.java`
- Create: `v5ai-modules/v5ai-rag/src/main/java/xin/v5ai/nb/rag/core/parser/docling/DoclingParseEngine.java`
- Create: `v5ai-modules/v5ai-rag/src/main/java/xin/v5ai/nb/rag/core/parser/docling/DoclingResponseNormalizer.java`
- Create: `v5ai-modules/v5ai-rag/src/test/java/xin/v5ai/nb/rag/core/parser/docling/DoclingResponseNormalizerTest.java`
- Create: `v5ai-modules/v5ai-rag/src/test/java/xin/v5ai/nb/rag/core/parser/docling/DoclingParseEngineTest.java`
- Modify: `v5ai-modules/v5ai-rag/pom.xml` only if an existing HTTP dependency is insufficient.

**Steps:**

- [ ] Write failing tests for multipart fields, optional API key, async task id/status/result, structured JSON preservation, invalid response, and HTTP failure.
- [ ] Run focused tests and verify failure.
- [ ] Implement health check, semaphore/connection-safe async submit, polling deadline, result fetch, and Docling option mapping.
- [ ] Normalize markdown/text/images/locators/diagnostics without embedding base64 in chunk text.
- [ ] Run focused tests with MockWebServer/WireMock-equivalent local test tooling already available in the repository.

### Task 4: Implement MinerU HTTP adapter from attached OpenAPI

**Files:**
- Create: `v5ai-modules/v5ai-rag/src/main/java/xin/v5ai/nb/rag/core/parser/mineru/MineruParserProperties.java`
- Create: `v5ai-modules/v5ai-rag/src/main/java/xin/v5ai/nb/rag/core/parser/mineru/MineruParseEngine.java`
- Create: `v5ai-modules/v5ai-rag/src/main/java/xin/v5ai/nb/rag/core/parser/mineru/MineruResponseNormalizer.java`
- Create: `v5ai-modules/v5ai-rag/src/test/java/xin/v5ai/nb/rag/core/parser/mineru/MineruResponseNormalizerTest.java`
- Create: `v5ai-modules/v5ai-rag/src/test/java/xin/v5ai/nb/rag/core/parser/mineru/MineruParseEngineTest.java`

**Steps:**

- [ ] Write failing tests for `/health`, `/tasks` multipart field mapping, open task-id response extraction, status terminal mapping, JSON result, ZIP result, timeout, and optional headers.
- [ ] Run focused tests and verify failure.
- [ ] Implement the adapter using only the attached endpoint/field contract; retain unknown JSON fields in document diagnostics.
- [ ] Implement content-type dispatch for JSON/ZIP and clean up temporary artifacts.
- [ ] Run focused tests and parser module tests.

### Task 5: Wire Worker indexing and persistence

**Files:**
- Modify: `v5ai-modules/v5ai-worker/src/main/java/xin/v5ai/nb/worker/KnowledgeIndexingService.java`
- Modify: `v5ai-modules/v5ai-rag/src/main/java/xin/v5ai/nb/rag/domain/KnowledgeDocument.java`
- Modify: `v5ai-modules/v5ai-rag/src/main/java/xin/v5ai/nb/rag/domain/KnowledgeChunk.java`
- Modify: `v5ai-modules/v5ai-rag/src/main/resources/mapper/KnowledgeDocumentMapper.xml` if result metadata needs explicit mapping.
- Create/Modify: `v5ai-modules/v5ai-worker/src/test/java/xin/v5ai/nb/worker/KnowledgeIndexingServiceTest.java`

**Steps:**

- [ ] Write failing Worker tests for engine selection, loading knowledge-base config before parse, external fallback warning, parsed text/parse time persistence, and default-only failure.
- [ ] Run focused tests and verify failure.
- [ ] Replace direct `DocumentParser.parse(bytes,type)` call with structured request + fallback coordinator.
- [ ] Save parsed text, parse time, engine/fallback diagnostics, then chunk/embed/store exactly once.
- [ ] Ensure empty default result fails cleanly and existing vector/search idempotency remains unchanged.
- [ ] Run Worker and RAG tests.

### Task 6: Add validation, service configuration, and migration for parser assets/diagnostics

**Files:**
- Modify: `v5ai-modules/v5ai-rag/src/main/java/xin/v5ai/nb/rag/service/impl/KnowledgeBaseServiceImpl.java`
- Modify: `v5ai-modules/v5ai-rag/src/main/java/xin/v5ai/nb/rag/domain/bo/KnowledgeBaseBo.java` only if validation input requires it.
- Modify: `v5ai-modules/v5ai-platform/src/main/resources/db/migration/README.md`
- Create: `v5ai-modules/v5ai-platform/src/main/resources/db/migration/V44__knowledge_document_parser_assets.sql` if schema-backed assets/diagnostics are implemented.
- Modify: `v5ai-modules/v5ai-rag/src/test/java/xin/v5ai/nb/rag/service/impl/KnowledgeBaseServiceImplTest.java`

**Steps:**

- [ ] Write failing validation tests for default/docling/mineru, MinerU enum/range constraints, legacy null config, and service fields not accepted from KB JSON.
- [ ] Run tests and verify failure.
- [ ] Implement validation and deployment-only property binding.
- [ ] Add only the migration fields required by the final structured result/asset implementation; preserve existing migration numbering.
- [ ] Run service tests and migration/schema checks.

### Task 7: Complete management API and UI configuration

**Files:**
- Modify: `v5ai-modules/v5ai-rag/src/main/java/xin/v5ai/nb/rag/controller/KnowledgeBaseController.java` only if API response/validation needs explicit behavior.
- Modify: `v5ai-ui/src/api/client.ts`
- Modify: `v5ai-ui/src/views/KnowledgeBasesView.vue`
- Modify: `v5ai-ui/src/views/knowledge-base/KnowledgeBaseDetailView.vue` only if parse diagnostics/status are shown there.
- Modify: `docs/api/phase2.md`

**Steps:**

- [ ] Write/update frontend type/build tests or type-level checks for three engine values and parameter payloads.
- [ ] Implement engine selector and conditional Docling/MinerU fields; keep service URLs/credentials out of forms.
- [ ] Show selected engine and fallback diagnostics/status without exposing secrets.
- [ ] Update API documentation and reparse semantics.
- [ ] Run UI typecheck/build and API tests.

### Task 8: Integration verification and documentation

**Files:**
- Modify: `docs/RAG解析引擎Docling和MinerU集成方案.md` only for implementation-status corrections.
- Create/Modify: deployment/config examples as needed.

**Steps:**

- [ ] Run the full Maven test suite with tests enabled and the UI typecheck/build.
- [ ] Run static checks, inspect `git diff --check`, and verify no secrets/raw documents are logged.
- [ ] Exercise adapter contract tests using fake HTTP services only; do not require real Docling/MinerU debugging services.
- [ ] Run repository change-impact checks required by `AGENTS.md` before any commit.
- [ ] Perform a final self-review against the spec and record any deferred non-blocking items.
