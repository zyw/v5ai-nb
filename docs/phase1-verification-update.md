# Phase 1 Verification Update

验证时间：2026-08-13

## 本轮完成范围

- Provider 管理：
  - `POST /api/admin/providers`
  - `GET /api/admin/providers`
  - MyBatis 持久化到 `v5ai_model_provider`

- Model 管理：
  - `POST /api/admin/models`
  - `GET /api/admin/models`
  - 凭据经 `CredentialCipher` 加密后保存到 `v5ai_model`

- Agent 管理与发布：
  - `POST /api/admin/agents`
  - `GET /api/admin/agents`
  - `POST /api/admin/agents/{agentKey}/publish`
  - 发布时生成 `v5ai_application_version.snapshot_json`
  - Runtime resolver 只接受 `PUBLISHED` Agent

- Runtime 持久化：
  - 自动确保 `v5ai_conversation`
  - 保存 user/assistant message 到 `v5ai_message`
  - 保存 run 到 `v5ai_run`
  - 保存 runtime event 到 `v5ai_run_event`

- AgentScope Runtime 边界：
  - 新增 `AgentModelResolver`
  - 新增 `PublishedModelAgentTextExecutor`
  - Runtime wiring 优先使用 `AgentModelResolver`，其次单个 AgentScope `Model` Bean，最后使用开发占位 executor

- AgentScope 模型适配器：
  - `OPENAI` / `OPENAI_COMPATIBLE` 解析为 AgentScope `OpenAIChatModel`
  - `DASHSCOPE` 解析为 AgentScope `DashScopeChatModel`
  - `v5ai_model.credentials_ciphertext` 经 `CredentialCipher.decrypt` 后解析为模型凭据 JSON
  - `EMBEDDING` / `RERANK` 用于 Agent Runtime 时抛明确异常

## 验证命令

```bash
mvn -Dmaven.repo.local=/private/tmp/v5ai-m2 clean verify \
  -Dsurefire.failIfNoSpecifiedTests=false -q
```

## 结果

- 后端完整 Maven `clean verify` 通过，exit code 0。
- 本轮新增/更新的模型、应用、运行时、基础设施和 API 测试均纳入完整验证。
- 模型适配器目标测试通过：`ModelRuntimeConfigGatewayTest`、`AgentModelCredentialConfigTest`、`AgentScopeModelFactoryTest`、`MyBatisAgentModelResolverTest`、`PublishedModelAgentTextExecutorTest`、`AesCredentialCipherTest`。

## Phase 1 仍未包含

- RAG 管理。
- MCP 管理。
- Skill 管理。
- Workflow 编排。
- Provider 管理 UI 中对不同 provider credential schema 的表单化校验。
