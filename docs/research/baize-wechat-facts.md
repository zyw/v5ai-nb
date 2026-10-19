# 白泽企业版微信公众号文章事实笔记

> 核对日期：2026-10-19。以 `/Users/jingyuan-mb01/javaspace/baize` 当前源码和同仓库 `baize-docs`、`baize-website` 为一手依据。公开入口：[白泽官网](https://baize.v5ai.xin/) · [白泽文档](https://baize-docs.v5ai.xin/)。这两个线上域名在本次环境中无法解析，未能直接核验已部署页面；下述结论依据对应站点的仓库源文件及实现源码，发布前宜再核对线上版本。

## 六项能力逐项核实

1. **Server–Client 与 gRPC 双向流：已实现。** Java Server 在管理/控制面负责配置、准入、调度；Agent Client 主动建立一条 NARP v2 gRPC 双向流，在自身进程内运行 Agent Loop、调用模型并执行工具，任务和事件沿该流往返。不是 Server 反向访问 Client 的 HTTP 服务。`/Users/jingyuan-mb01/javaspace/baize/README.md` §系统结构；`/Users/jingyuan-mb01/javaspace/baize/baize-ai/baize-common/baize-common-agent-runtime/src/main/proto/agent_runtime_v2.proto` L28–36；[架构指南](https://baize-docs.v5ai.xin/zh/architecture)。
2. **Java Server 统一管理资源、MCP、Skill、RAG、Agent：已实现。** Server 管理模型和存储资源、Agent 与发布版本、知识库及索引、MCP 服务/权限、Skill 包/版本/绑定；运行时也负责路由、会话与执行状态。RAG 的解析、索引与检索在 Server；MCP 工具由 Server 发现并授权，但实际 MCP 连接/调用可由所选 Client 执行；Skill 正文可按需读取。避免写成“所有能力都在 Server 计算”。`/Users/jingyuan-mb01/javaspace/baize/README.md` §功能概览；`/Users/jingyuan-mb01/javaspace/baize/baize-docs/zh/architecture.md` §资源所在位置；[RAG](https://baize-docs.v5ai.xin/zh/rag) · [MCP](https://baize-docs.v5ai.xin/zh/mcp) · [Skill](https://baize-docs.v5ai.xin/zh/skills) · [Agent](https://baize-docs.v5ai.xin/zh/agents)。
3. **多语言 Agent Client：Java、Python、Node.js 已有实现；Rust 与 Golang 仍属规划。** Java、Node.js、Python 均有 SDK/启动工程，运行时分别采用 AgentScope、Pi Agent、LangChain/LangGraph 路线。桌面 Tauri 的 Rust Host 不等于独立 Rust NARP Agent Client；不要声称 Rust/Go Client 已发布。`/Users/jingyuan-mb01/javaspace/baize/README.md` §仓库目录；`/Users/jingyuan-mb01/javaspace/baize/baize-website/PRODUCT.md` L13–18；`/Users/jingyuan-mb01/javaspace/baize/baize-docs/zh/desktop.md` L8–10；[Java](https://baize-docs.v5ai.xin/zh/java-client) · [Python](https://baize-docs.v5ai.xin/zh/python-client) · [Node](https://baize-docs.v5ai.xin/zh/node-client)。
4. **跨语言模型适配一致性：共享规格、生成物与一致性测试已落地。** `conformance/model-adapters` 管模型厂商兼容规则、错误分类、Provider/Adapter 清单和请求/事件 fixture；生成器产出 Java/Node/Python 常量。`conformance/reasoning` 与 `conformance/tool-schema` 是共享测试向量，供三端各自实现的策略/规范化器验证，**并不经同一生成器生成运行时代码**。宣传建议说“以一份共享契约生成部分适配规则，并用统一测试向量验证三端行为”；不要说“所有适配代码一键生成”或“已经消除全部模型差异”。`/Users/jingyuan-mb01/javaspace/baize/conformance/README.md` §0–2；`/Users/jingyuan-mb01/javaspace/baize/baize-docs/zh/conformance.md` L3–20, L40；[一致性契约](https://baize-docs.v5ai.xin/zh/conformance)。
5. **嵌入业务与客户端本地工具：可实现，但 Java 扩展路径有边界。** Client 可以部署在业务进程或靠近业务系统，Client-owned Tool 将业务能力暴露给 Agent；Server 下发工具目录并授权，所选 Client 在本地执行。Node 和 Python 提供注册表/API 注入业务处理函数；Java 当前固定 Toolkit 里并无与前两端对等的通用 `register_tool` API，接入新业务工具需要扩展 Toolkit 装配及 Server 目录投影，或通过 MCP。宣传可写“可把客户端嵌入业务环境，以受控工具连接业务能力”，避免承诺三种语言都只需一行注册。`/Users/jingyuan-mb01/javaspace/baize/baize-docs/zh/business-tools.md` L3–21, L24–101；`/Users/jingyuan-mb01/javaspace/baize/baize-sdk/nodejs/packages/agent-client/src/tool/client-tool-registry.ts` L18；`/Users/jingyuan-mb01/javaspace/baize/baize-sdk/python/packages/agent-client/baize_agent_client/tools.py` L16；[业务工具指南](https://baize-docs.v5ai.xin/zh/business-tools)。
6. **Desktop Agent Client 与来源路由：实现了基于当前桌面实例的本机执行路径。** Tauri/Rust Host 启动 Node sidecar，后者作为 Agent Client 建立 NARP 连接；桌面对话携带短时来源票据，Server 校验来源连接、Agent 桌面执行许可及节点可用性，并将这一轮精确派发回该实例。默认关闭；本机离线时不会自动转派别的桌面或服务端。跨平台安装包不能仅凭构建脚本宣称已全面验证。`/Users/jingyuan-mb01/javaspace/baize/baize-docs/zh/desktop.md` L3–22, L124–137；`/Users/jingyuan-mb01/javaspace/baize/baize-ai/baize-modules/baize-agent-server/src/main/java/xin/v5ai/baize/agent/server/controller/AgentConversationTurnController.java` L138–143, L178–199；[桌面执行指南](https://baize-docs.v5ai.xin/zh/desktop)。

## 适合文章延伸的企业版场景

- **一个控制面，多个执行位置**：同一个 Agent 配置可由不同的客户端节点承接；应用标识、节点状态、路由策略和桌面来源共同决定执行位置。`/Users/jingyuan-mb01/javaspace/baize/baize-docs/zh/architecture.md` §用应用组织客户端；`/Users/jingyuan-mb01/javaspace/baize/baize-docs/zh/agents.md` §排查没有可用客户端。
- **RAG 可独立检索验证**：文档上传/URL 导入、解析/切片、向量或关键词检索、可选重排与知识问答；Agent 可选择智能调用或强制预检索。`/Users/jingyuan-mb01/javaspace/baize/baize-docs/zh/rag.md` 全文。
- **MCP 权限与审计**：ALLOW/APPROVE/DENY 三态；Server 管目录与审批，Client 执行受准入的调用。`/Users/jingyuan-mb01/javaspace/baize/baize-docs/zh/mcp.md` §工具权限。
- **Skill 版本与按需读取**：ZIP 或在线编辑，草稿/发布/回滚，运行中按需读取 `SKILL.md` 和参考文件。`/Users/jingyuan-mb01/javaspace/baize/baize-docs/zh/skills.md` §认识 Skill、§发布。

## 来源标注建议

微信正文中可将“白泽官网、白泽文档”作为阅读入口，技术细节若需支撑则链接到上文对应的文档页。官网站点是否与当前源码完全同步，本次网络环境无法核实。
