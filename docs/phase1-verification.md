# Phase 1 Verification

## Commands

```bash
java -version
mvn -Dmaven.repo.local=/private/tmp/v5ai-m2 clean verify -Dsurefire.failIfNoSpecifiedTests=false
cd v5ai-ui
npm install
npm run build
git status --short
```

Expected:

- Java reports JDK 21.
- Maven exits with code 0 on Spring Boot 4.1.0.
- The frontend build exits with code 0.
- Git status is clean after committed changes.

## Verified Scope

- Maven reactor uses `v5ai-*` module names.
- Java packages are rooted under `xin.v5ai.nb`.
- Phase 1 migration tables use the `v5ai_` prefix.
- Sa-Token JWT login service is covered by tests.
- Model credentials are encrypted before persistence.
- Infrastructure AES credential cipher uses AES-GCM with randomized IV.
- Runtime rejects unpublished Applications through `PublishedApplicationResolver`.
- Agent events are mapped to platform-owned `RuntimeRunEventDTO`.
- SSE chat endpoint returns typed server-sent events.
- Runtime endpoint rejects missing or invalid Agent API Keys.
- User and assistant messages plus run lifecycle are persisted through runtime ports and MyBatis adapters.
- `v5ai-ui` provides the first management console skeleton for login, models, Applications, API Keys and chat debugging.

## Known Phase 1 Limitations

- The default local Agent executor is a deterministic placeholder behind `AgentTextExecutor`.
- Agent and provider CRUD APIs are not complete yet.
- The development Agent repository is in-memory for local startup.
- RAG, MCP, Skill and Workflow modules are intentionally out of Phase 1.
