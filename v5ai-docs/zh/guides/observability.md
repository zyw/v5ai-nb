# 调试与观测

管理端的调试工具使用 Agent 当前编辑配置，适合发布前验证。正式运行则记录 Run、消息、事件和模型用量，便于定位回答过程与调用成本。

![调试工具：上下文、对话与事件时间线](/images/debug-tool.png)

## 草稿实时调试

在 **调试工具** 选择 Agent 后，可以检查能力状态、发送问题并查看运行事件。草稿和已发布 Agent 都可调试；禁用的 Agent 会被拒绝。调试入口使用管理端登录凭证，不需要 Agent API Key，也不经过门户侧的 Key 限流与配额。

知识库详情还有单独的检索与问答调试，适合先确认切片、召回与重排效果，再测试整条 Agent 链路。

## 事件和用量

典型事件顺序为：

```text
RUN_STARTED → MODEL_CALL → (TEXT_DELTA | TOOL_CALL → TOOL_RESULT | RETRIEVAL)*
            → MESSAGE_COMPLETED → RUN_COMPLETED
```

失败时出现 `RUN_FAILED`；工具需授权时可出现 `PERMISSION_REQUIRED`。完成事件携带本轮用量与耗时。平台优先使用模型回报的 token 数；缺少真实用量时使用估算值，消息、完成事件与配额记录保持同一口径。

长会话先按历史窗口裁剪，再用异步滚动摘要覆盖窗口外内容。模型的思考过程可随助手消息保存供回看，但不会重新进入模型上下文。

## 运营视角

总览、用量、配额和审计页面可帮助管理员查看 Agent 运行与管理操作。MCP 工具调用另有按 Server 或 Run 查询的审计记录。

![管理端总览](/images/admin-dashboard.png)

更细的事件结构见仓库 `README.md` 和 `docs/api/phase5.md`、`docs/api/phase6.md`。
