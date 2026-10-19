# 常见问题

按现象从连接、配置和发布状态三处排查。详细日志与环境差异见仓库 `docs/deploy/dev.md`。

## 管理端无法登录

检查后端和 Redis 是否可用、是否连续输错触发滑块验证码，以及前端 RSA 公钥与后端解密私钥是否匹配。默认 `/api/auth/login` 要求加密请求体，直接发送明文 JSON 会得到“请求非法”。

## 门户登录后看不到 Agent

确认 Agent 已发布、API Key 未禁用并绑定了该 Agent。门户的 bootstrap 只返回当前 Key 有权访问的 Agent；更改绑定或发布状态后刷新门户。

## 调试有效，线上回答却没变化

管理端调试读取实时编辑行；线上读取发布快照。保存 Agent 后还需重新发布，门户已打开页面也需刷新或重登。知识库、MCP、Skill 绑定列表虽实时解析，相应开关仍来自发布快照。

## 知识库没有命中

先确认文档任务已完成、切片可见、嵌入模型与存储实例维度匹配，再在知识库详情的 **知识检索** 和 **知识问答** 调试。若 Agent 使用智能调用 RAG，模型可能选择不检索；需要每轮检索时改用强制调用。

## MCP 工具不可用

按“测试连接 → 发现工具 → 设置权限 → 绑定 Agent → 启用 MCP 开关”逐步核对。权限为 `DENY` 的工具不会注册；Stdio 部署还需检查命令白名单。

## Python 节点运行失败

核对 Runner URL 与 Token、执行镜像、独立 daemon 的 gVisor runtime 和网络连通性。Runner 默认失败关闭，不会退回本地 Python。具体错误码与现场验收见仓库 `docs/deploy/workflow-python-runner.md`。

## 配额或限流返回 429

检查 Agent 的 `ratePerMinute`、`dailyModelCalls` 和 `dailyTokens`，以及当前 Key 绑定与用量。附件上传还有单独的按 Key 限流。

## 修改配置后启动失败

确认 `V5AI_CREDENTIAL_CIPHER_KEY` 为 32 字节，数据库方言与 JDBC URL 成组切换；检查 Flyway 日志。不要直接改已应用的迁移脚本，新表或列应新增迁移版本。
