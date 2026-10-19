# 从知识到行动：v5ai-nb 开源平台与白泽企业版，让 AI Agent 走进真实业务

一个能回答问题的 AI 应用，离真正可用还有多远？

它需要读懂企业资料、按场景选择工具、记住对话上下文，也需要让开发者知道：这次回答检索了什么、调用了什么、消耗了多少资源。如果 Agent 还要进入不同业务系统，执行位置和业务边界也必须清楚。

围绕这些问题，我们构建了 **v5ai-nb 开源版**和 **白泽（Baize）企业版**。前者提供可自部署的中心化 AI 应用平台；后者采用 Server–Client 架构，让 Agent Client 在业务进程或用户设备中执行任务。

## 一、v5ai-nb：从模型接入，到 Agent 应用落地

v5ai-nb 基于 Java 21、Spring Boot、AgentScope Java 构建。它把模型、知识库、Agent、MCP、Skill、工作流和运行记录放在同一个管理平台，并提供管理工作台、独立对话门户和 HTTP/SSE 运行接口。

![v5ai-nb 管理端总览：Agent、模型、知识库、Skill 与运行概况](https://ai.v5ai.xin/images/product/admin-dashboard.png)

*图：v5ai-nb 管理端总览*

### 01｜RAG：让回答有知识来源

企业知识通常散落在文档与网页里。v5ai-nb 支持上传 TXT、Markdown、PDF、DOCX 文档或导入 URL；异步任务完成解析、切片、向量化与索引。知识库可选择 pgvector、Milvus 或 Elasticsearch 作为向量存储实例，并结合关键词检索。

知识入库之后，可以先在知识库详情页查看原文和切片，调整返回数量、问题改写、重排及阈值，再直接测试检索与问答效果。这样，团队能先看清“找到了什么”，再决定“让 Agent 如何回答”。

RAG 提供两种调用方式：**强制调用**会在每轮运行前检索并注入上下文；**智能调用**把检索作为工具交给模型，由 Agent 判断何时检索。命中的知识片段还能随回答形成引用，便于回看来源。

![v5ai-nb 知识库详情：检索参数与知识问答调试](https://ai.v5ai.xin/images/product/rag-detail.png)

*图：知识库详情中的检索与问答调试*

### 02｜Agent：把模型、知识与工具组织成一个应用

在 v5ai-nb 中，一个 Agent 不只是提示词。它可以组合对话模型、系统提示词、知识库、MCP 工具、Skill、会话记忆和联网搜索，并配置头像、开场白与推荐问题。

管理端支持**草稿实时调试**：修改配置后即可在预览面板测试。对外运行则读取**已发布版本快照**，让正在使用的 Agent 不会因一次编辑而突然改变行为。模型凭据加密保存，对外通过 Agent API Key 接入，并受配额和限流约束。

对话门户提供流式回答、会话管理、停止生成、重新生成、知识引用和按模型能力开放的图片提问。历史窗口与会话摘要共同控制长对话上下文；运行事件、模型用量和工具调用也可供回看。

![v5ai-nb Agent 详情：模型与能力配置、右侧实时预览](https://ai.v5ai.xin/images/product/agent-detail.png)

*图：Agent 编辑与实时预览*

![v5ai-nb 对话门户：Agent 介绍、推荐问题与会话入口](https://ai.v5ai.xin/images/product/chat-home.png)

*图：独立的终端用户对话门户*

### 03｜Workflow：把 AI 判断与确定性步骤编排在一起

不是每项任务都适合只靠一次对话完成。v5ai-nb 的可视化工作流编辑器，已经可以在画布中连接**开始、Agent、条件、HTTP 请求、Python 脚本、JSON 转换、变量赋值、结束**等节点；通过属性面板配置输入、输出与分支，并进行定义校验、保存、预览运行和版本发布。

工作流把 Agent 的灵活判断与系统步骤放到一条可观察的执行路径上，运行记录可追踪到节点。**目前工作流的管理、编辑器与基础节点执行能力已落地，完整运行时闭环仍在持续完善。**Python 节点的生产使用还需要按部署要求完成独立 Runner 的隔离验收。

![v5ai-nb Workflow 编辑器：节点库、画布和属性面板](https://ai.v5ai.xin/images/product/workflow-editor.png)

*图：可视化 Workflow 编排*

### 更多能力，服务于真实运行

- **模型管理**：统一维护 Provider 与模型配置，支持连通性测试和凭据加密。
- **MCP**：注册 Streamable HTTP、SSE 或 Stdio Server，发现工具后绑定 Agent，配置工具权限并记录调用审计。
- **Skill**：上传或在线编辑 `SKILL.md` 文件包，发布、回滚版本，再注入 Agent 工作区。
- **调试与观测**：从回答、检索、工具事件到运行耗时和用量，逐步定位问题。
- **自部署与扩展**：模块化单体架构，业务库可选择 PostgreSQL 或 MySQL；管理端、对话门户与运行 API 分离。

![v5ai-nb Skill 编辑器：文件树、说明与参考资料](https://ai.v5ai.xin/images/product/skill-editor.png)

*图：在线编写与管理 Skill*

![v5ai-nb 调试工具：能力状态、对话与运行事件](https://ai.v5ai.xin/images/product/debug-tool.png)

*图：Agent 调试工具*

## 二、白泽企业版：让 Agent 在业务现场执行

当 Agent 需要进入现有服务、不同语言的技术栈，甚至用户自己的桌面环境，执行位置就变成架构的一部分。白泽采用 **Server–Client 模式**：Java Server 负责资源管理和运行调度；Agent Client 主动连接 Server，在所在进程执行 Agent Loop、调用模型与本地工具，再把过程和结果回传。

Server 与 Client 通过 **NARP v2 / gRPC 双向流**通信，承载连接握手、任务下发、事件回传、工具往返和取消。对话端通过 HTTP/SSE 看到流式进度。Server 统一管理模型与 Agent，以及 **MCP、Skill、RAG** 等资源和访问规则；RAG 的文档索引、检索留在 Server 侧，业务工具则可在 Client 所在的位置执行。

### 01｜多语言 Agent Client，进入不同技术栈

白泽目前提供 **Java、Python、Node.js Agent Client**。客户端可以作为独立进程运行，也可以接入业务应用，让 Agent 在业务系统附近调用已有能力。**Rust Agent Client 与 Golang Agent Client 仍在规划中。**桌面应用中的 Rust 目前承担 Tauri 宿主角色，桌面 Agent 执行由 Node sidecar 完成。

### 02｜一份兼容契约，约束三端模型行为

多语言支持不能只停留在“都能发请求”。白泽将模型适配规则放在 `conformance/model-adapters`，将思考处理与工具 Schema 的共享测试向量放在 `conformance/reasoning`、`conformance/tool-schema`：规则生成 Java、Python、Node.js 三端常量；同一批 fixture 和向量用于检查请求变换、事件、错误分类及策略行为。

这让跨语言适配有了共同的验证依据。它是**构建与测试阶段的一致性机制**，并不意味着所有模型供应商组合都已经完成真实环境验收。

### 03｜在业务进程中，给 Agent 一个可靠的入口

Agent Client 可以接入业务应用，并将受控的本地工具交给 Agent：查询订单、读取业务状态、调用已有服务等，仍由宿主系统执行实际逻辑。Server 下发本次可用的工具目录和授权约束，Client 按目录执行，避免把“模型想调用什么”直接等同于“系统允许做什么”。

Node.js 和 Python Client 已提供宿主工具注册入口；Java Client 当前通过 AgentScope Toolkit 中明确装配的工具集合接入业务能力。具体业务工具仍需按应用授权和运行策略配置。

### 04｜桌面 Agent Client，让对话回到当前设备

白泽的 AI Chat 同时提供 Web 与 Tauri 桌面应用。启用桌面本机执行后，桌面宿主启动 Node Agent Client sidecar。用户从这台桌面发起对话时，可依据**来源票据与路由规则**把任务锁定到当前连接的 Client，而不是随机交给另一台设备。

这为桌面侧的本地业务场景提供了路径。当前已实现受范围约束的只读文件工具，包括列目录、读取与搜索；桌面端的更多操作能力仍应遵循工具授权和部署边界。

## 三、从哪一版开始？

如果你希望自部署一套平台，围绕 **RAG、Agent 和 Workflow** 建立 AI 应用，再逐步接入 MCP 与 Skill，可以从 **v5ai-nb 开源版**开始。

如果你需要把 Agent 执行放进现有业务进程，用 **Java、Python、Node.js** 协同运行，或把对话路由到当前桌面设备，可以进一步了解 **白泽企业版**。

**一个让知识进入回答，一个让执行进入业务。**从可验证的知识检索、可调试的 Agent，到有明确执行位置的多语言客户端，我们希望让 AI 能力真正成为业务系统的一部分。

---

**了解更多**

- [v5ai-nb 开源版官网](https://ai.v5ai.xin/)
- [v5ai-nb 文档](https://ai-docs.v5ai.xin/)
- [v5ai-nb 源码](https://github.com/zyw/v5ai-nb)
- [白泽企业版官网](https://baize.v5ai.xin/)
- [白泽文档](https://baize-docs.v5ai.xin/)
