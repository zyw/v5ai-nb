# Phase 1 Verification

## Commands

```bash
java -version
mvn -Dmaven.repo.local=/private/tmp/v5ai-m2 clean verify -Dsurefire.failIfNoSpecifiedTests=false
git status --short
```

Expected:

- Java reports JDK 21.
- Maven exits with code 0.
- Git status is clean after committed changes.

## Verified Scope

- Maven reactor uses `v5ai-*` module names.
- Java packages are rooted under `xin.v5ai.nb`.
- Phase 1 migration tables use the `v5ai_` prefix.
- Sa-Token JWT login service is covered by tests.
- Model credentials are encrypted before persistence.
- Infrastructure AES credential cipher uses AES-GCM with randomized IV.
- Runtime rejects unpublished Applications through `PublishedApplicationResolver`.
- Agent events are mapped to platform-owned `RuntimeEvent`.
- SSE chat endpoint returns typed server-sent events.
- Runtime endpoint rejects missing or invalid Application API Keys.
- User and assistant messages plus run lifecycle are persisted through runtime ports and MyBatis adapters.

## Known Phase 1 Limitations

- The default local Agent executor is a deterministic placeholder behind `AgentTextExecutor`.
- Application and provider CRUD APIs are not complete yet.
- The development Application repository is in-memory for local startup.
- RAG, MCP, Skill and Workflow modules are intentionally out of Phase 1.
