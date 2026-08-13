# v5ai-nb

基于 JDK 21、Spring Boot、AgentScope Java、MyBatis-Plus 和 Sa-Token + JWT 构建的中心化 AI 应用平台。

当前处于第一阶段：项目骨架、认证、模型配置、Application 发布、运行时边界、SSE 对话入口、会话/run 持久化基础和管理前端骨架已经建立。

## 技术基线

- JDK 21
- Spring Boot 4.1.0 / WebFlux
- MyBatis-Plus
- Flyway
- Sa-Token + JWT
- BCrypt
- AgentScope Java 运行时边界

## 模块

- `v5ai-common`: 公共响应、异常、分页和 API Key 校验端口
- `v5ai-domain`: 预留领域共享模块
- `v5ai-model`: 模型配置与凭证加密前置服务
- `v5ai-application`: Application 草稿/发布/已发布解析核心
- `v5ai-runtime`: Agent 运行时边界、事件模型、会话/run 持久化装饰器
- `v5ai-infrastructure`: MyBatis-Plus、Sa-Token、BCrypt、AES-GCM、运行时持久化适配
- `v5ai-api`: 登录接口、Application API Key 过滤器、对话/SSE API
- `v5ai-starter`: Spring Boot 启动模块
- `v5ai-ui`: Vue 3 + Vite + TypeScript + Naive UI 管理前端

## 本地验证

```bash
mvn -Dmaven.repo.local=/private/tmp/v5ai-m2 clean verify -Dsurefire.failIfNoSpecifiedTests=false
cd v5ai-ui
npm install
npm run build
```

当前 Phase 1 默认提供开发期 `demo` Application 和占位 Agent 响应，方便先验证 Dify 风格中心化调用链路。真实 AgentScope SDK 适配、模型 Provider 后台 CRUD、RAG/MCP/Skill/Workflow 将在后续阶段接入。

更多说明见：

- `docs/api/phase1.md`
- `docs/phase1-verification.md`
