# 源码与设计文档索引

本网站按“先做成什么，再理解为什么”的顺序编排。需要核对精确字段、错误码或设计取舍时，可以从下面的源码和仓库文档继续追踪。

## 从功能找源码

| 功能 | 入口 |
|---|---|
| 模型与供应商 | [`v5ai-model`](https://github.com/zyw/v5ai-nb/tree/main/v5ai-modules/v5ai-model) |
| Agent 管理与发布 | [`v5ai-agent`](https://github.com/zyw/v5ai-nb/tree/main/v5ai-modules/v5ai-agent) |
| 对话、会话与运行事件 | [`v5ai-runtime`](https://github.com/zyw/v5ai-nb/tree/main/v5ai-modules/v5ai-runtime) |
| 知识库与文档索引 | [`v5ai-rag`](https://github.com/zyw/v5ai-nb/tree/main/v5ai-modules/v5ai-rag)、[`v5ai-worker`](https://github.com/zyw/v5ai-nb/tree/main/v5ai-modules/v5ai-worker) |
| MCP 与 Skill | [`v5ai-mcp`](https://github.com/zyw/v5ai-nb/tree/main/v5ai-modules/v5ai-mcp)、[`v5ai-skill`](https://github.com/zyw/v5ai-nb/tree/main/v5ai-modules/v5ai-skill) |
| Workflow | [`v5ai-workflow`](https://github.com/zyw/v5ai-nb/tree/main/v5ai-modules/v5ai-workflow) |
| 认证、配额、迁移 | [`v5ai-platform`](https://github.com/zyw/v5ai-nb/tree/main/v5ai-modules/v5ai-platform) |
| 前端 | [`v5ai-ui`](https://github.com/zyw/v5ai-nb/tree/main/v5ai-ui)、[`v5ai-ui-chat`](https://github.com/zyw/v5ai-nb/tree/main/v5ai-ui-chat) |

## 从问题找文档

| 主题 | 仓库文档 |
|---|---|
| 整体设计与架构决策 | [`README.md`](https://github.com/zyw/v5ai-nb/blob/main/README.md)、[初始设计](https://github.com/zyw/v5ai-nb/blob/main/docs/superpowers/specs/2026-08-12-v5ai-nb-design.md)、[`docs/adr/`](https://github.com/zyw/v5ai-nb/tree/main/docs/adr) |
| 管理和运行 API | [`docs/api/phase1.md` 至 `phase6.md`](https://github.com/zyw/v5ai-nb/tree/main/docs/api) |
| 数据库与迁移 | [当前 schema](https://github.com/zyw/v5ai-nb/blob/main/docs/db/schema.md)、[迁移目录](https://github.com/zyw/v5ai-nb/tree/main/v5ai-modules/v5ai-platform/src/main/resources/db/migration) |
| 本地与 Compose 部署 | [开发部署](https://github.com/zyw/v5ai-nb/blob/main/docs/deploy/dev.md)、[仓库 README](https://github.com/zyw/v5ai-nb/blob/main/README.md) |
| Workflow 和 Python Runner | [工作流实现设计](https://github.com/zyw/v5ai-nb/blob/main/docs/workflow%E5%AE%9E%E7%8E%B0%E8%AE%BE%E8%AE%A1%E6%96%87%E6%A1%A3.md)、[Runner 部署](https://github.com/zyw/v5ai-nb/blob/main/docs/deploy/workflow-python-runner.md) |
| 完整配置 | [配置详情](https://github.com/zyw/v5ai-nb/blob/main/v5ai-starter/src/main/resources/v5ai-nb-%E9%85%8D%E7%BD%AE%E6%96%87%E4%BB%B6%E8%AF%A6%E6%83%85.yml)、[`.env.example`](https://github.com/zyw/v5ai-nb/blob/main/.env.example) |

早期设计和 Phase 文档保留当时的计划或契约。遇到差异时，以当前 Controller、Service、配置文件和迁移为准。
