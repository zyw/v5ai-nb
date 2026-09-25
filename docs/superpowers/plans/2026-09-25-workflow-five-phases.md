# Workflow Five Phases Implementation Plan

> **For agentic workers:** Implement sequentially in this session, following this plan and the linked design.

**Goal:** Deliver workflow editor, validation/versioning, execution nodes, safe Python runner integration, and external workflow run APIs across phases A–E.

**Architecture:** Keep the platform-owned JSON graph and `v5ai-workflow` module. Replace hard-coded execution dispatch with registered node executors, keep Agent execution on `AgentRuntime`, persist immutable versions and run snapshots, and expose external runs through existing platform authentication/quota boundaries.

**Tech Stack:** Existing JDK 21, Spring Boot, MyBatis-Plus, Flyway, AgentScope Java, Vue 3, TypeScript, Vue Flow, Naive UI. No new third-party Java libraries.

**Spec:** `workflow实现设计文档.md`

## Global Constraints

- Do not add a third-party Java workflow engine or new Java dependency.
- Preserve v1 workflow definition compatibility and existing published-run behavior.
- Use Flyway V51 onward, with PostgreSQL and MySQL migrations.
- HTTP calls require SSRF controls and secret redaction.
- Python code must run only in an isolated Runner; fail closed if unavailable.
- External execution uses published snapshots and existing API-key quota/rate-limit controls.

## Review Focus

- Legacy v1 definitions remain readable and editable after migration.
- Conditional edges select exactly one branch; malformed handles are rejected.
- Repeated/concurrent updates cannot silently overwrite workflow drafts.
- HTTP cannot reach loopback/private/metadata destinations after DNS or redirects.
- Draft test runs remain reproducible after later edits; Python execution fails closed.

---

### Task 1: Workflow definition and node execution core

**Files:** `v5ai-modules/v5ai-workflow/src/main/java/xin/v5ai/nb/workflow/core/**`, workflow core tests.

- Add schema-versioned definition, positions, typed ports, structured diagnostics, variable namespaces, executor interface and registry.
- Move START/AGENT/CONDITION/END behavior into independent executors and replace recursive traversal with iterative DAG scheduling.
- Preserve existing definitions and AgentRuntime integration.
- Exercise legacy parse, duplicate IDs, condition handles, unreachable nodes, typed template references, executor registration, and branch execution with focused tests.

### Task 2: Versioned persistence and run lifecycle

**Files:** workflow domain/mappers/services/XML; platform Flyway PostgreSQL and MySQL migrations from V51.

- Add immutable workflow versions, draft revision, draft-test run source/revision/definition snapshot, node attempts/duration/error metadata.
- Add validation and restore services, compare-and-update draft semantics, paginated runs, short state-transition transactions.
- Preserve compatibility fields and existing API response shapes where possible.

### Task 3: Admin API and execution nodes

**Files:** `WorkflowController`, BO/VO, executor implementations, runtime configuration.

- Add validate, versions, restore, draft-test, run detail and cancellation API contracts.
- Implement HTTP execution with allow-list/deny-network checks, redirect revalidation, timeout/size caps, secret references and redacted output.
- Implement Python executor contract against a separately configured Runner; fail closed until the Runner endpoint is configured and healthy.
- Add VARIABLE and structured error semantics.

### Task 4: Visual editor and run debugging

**Files:** `v5ai-ui/src/views/WorkflowEditorView.vue`, workflow components, API client and related styles.

- Persist node positions, add drag palette, typed node cards/inspectors, edge validation, undo/redo, auto-layout and diagnostics.
- Add explicit draft-test vs published-run choice, validation panel, version history and node-level run inspection.
- Keep Vue Flow and existing UI libraries; add no dependencies.

### Task 5: External API, limits and end-to-end contracts

**Files:** workflow runtime/controller, `v5ai-api`/platform filters where required, UI/API docs, migrations and tests.

- Expose API-key protected published workflow run endpoint through existing authentication, quota, rate-limit and audit mechanisms.
- Persist caller identity and usage correlation; add cancellation and event polling where compatible with existing runtime conventions.
- Verify builds and inspect the complete diff. Do not claim an isolated Python sandbox is operational unless a concrete Runner implementation and deployment configuration are present.

