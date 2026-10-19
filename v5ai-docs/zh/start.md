# 快速上手

用一条最短路径理解 v5ai-nb：启动平台、添加模型、创建并发布 Agent，然后在对话门户中提问。平台由一个 Spring Boot 后端、管理端 `v5ai-ui` 和独立对话门户 `v5ai-ui-chat` 组成。

## 开始前准备

本地开发需要 JDK 21、Maven 3.9+、Node.js 22+、PostgreSQL 14+（含 pgvector）和 Redis 6+。业务数据库也可选 MySQL 8.0.17+；此时向量检索需使用独立的存储实例。完整部署步骤见[本地开发部署](/zh/deploy/local)。

::: tip 想先了解产品？
可以先阅读[平台概念与架构](/zh/concepts)。已有运行环境时，直接从下面的第 2 步开始。
:::

## 1 · 启动后端和管理端

在仓库根目录准备 PostgreSQL、Redis 与环境变量，然后构建启动：

```bash
mvn -pl v5ai-starter -am package -DskipTests
java -jar v5ai-starter/target/v5ai-starter-0.1.0-SNAPSHOT.jar
```

另开终端启动管理端：

```bash
cd v5ai-ui
npm install
npm run dev
```

开发配置默认在 `http://localhost:80` 打开管理端，后端监听 `8080`。管理端默认种子账号是 `admin` / `admin`，仅用于首次本地登录；部署到其他环境后应立即修改。

::: warning 登录接口加密
管理端登录会对请求体加密，直接用裸 `curl` 调用默认配置下的 `/api/auth/login` 会被拒绝。按正常流程使用管理端页面登录；接口细节见 [API 与鉴权](/zh/reference/api)。
:::

## 2 · 添加供应商和模型

打开 **模型管理**，先创建供应商，再创建一个 `CHAT` 模型。填写模型标识、适配器、基础地址与凭据，保存后执行 **连通性测试**。凭据由后端加密存储，页面不应回显明文。

如果后续要使用知识库，再配置一个 `EMBEDDING` 模型以及向量存储实例。模型类型、默认模型与停用规则见[模型与供应商](/zh/guides/models)。

## 3 · 创建并发布 Agent

打开 **Agent 管理**，创建 Agent，选定对话模型，填写名称、系统提示词和对外展示内容。按业务需要开启知识库、MCP、Skill、记忆或联网搜索。先在管理端预览与调试，再点击 **发布**。

线上 API 读取发布时的配置快照；只保存草稿不会改变线上行为。如何配置与重新发布，见[创建与发布 Agent](/zh/guides/agents)。

![Agent 编辑页：配置与预览并排](/images/agent-detail.png)

## 4 · 创建 API Key 并进入对话门户

在管理端为已发布 Agent 创建并绑定 API Key。Key 的明文仅在创建时展示，请立即妥善保存。启动门户：

```bash
cd v5ai-ui-chat
npm install
npm run dev
```

开发模式默认打开 `http://localhost:5174`。输入 API Key 后即可选择 Agent、发起会话；门户会从服务端读取会话列表。完整行为见[对话门户](/zh/guides/chat-portal)。

## 接下来

1. 用[知识库与 RAG](/zh/guides/knowledge)为回答接入企业资料。
2. 用[MCP](/zh/guides/mcp)和[Skill](/zh/guides/skills)扩展 Agent 的工具与操作经验。
3. 在[调试与观测](/zh/guides/observability)中查看事件、用量和审计记录。

以上步骤依据当前仓库源码和 `docs/deploy/dev.md`、`docs/api/phase1.md` 至 `phase6.md` 整理。
