# 「引用展示」是纯展示开关，不碰检索与落库

新增 `v5ai_agent.show_citations BOOLEAN NOT NULL DEFAULT true`：Agent 上「聊天窗口是否展示引用」的开关，随发布快照下发门户，决定助手消息下方那个「引用（N 条）」折叠块**渲染不渲染**。它**只影响渲染**——RAG 检索本身不变（那由 `ragEnabled` / `ragCallMode` 管）、SSE `RETRIEVAL` 事件照常推送、引用照常随助手消息落进 `v5ai_message.metadata`、历史接口照常返回。默认 `true`，存量 Agent 零回归。作用面是**门户聊天窗口 + 管理端「预览与调试」**（调试面走 `resolveForDebug`，改完当场可见、不必先发布，这也正是验证开关效果的手段）；知识库「知识问答」tab 不属于任何 Agent、没有 `showCitations` 的来源，继续按组件默认值展示。**为什么不做成「连落库一起停」**：见下。

**Status**: accepted

**Considered Options**:
- **关闭时同时停止落库 / 停止推送 `RETRIEVAL`**：否决——引用是「这条回答当时依据了什么」的**快照**，而知识库允许改手工切片、删文档、重建索引，事后**无法重建**；一旦按开关停止落库，「关 → 开」后历史引用就永久缺失，开关变成**不可逆操作**。同时 ADR-0009 已写明 `v5ai_message.metadata` 当前只有一个写入方（整列覆盖），多一个写入方就必须改成读-改-写——为一个展示开关引入写入纪律，代价与收益不成比例（收益仅剩「历史读取每轮少 ≤8.5KB」）。
- **只做门户本地开关**（存 localStorage，同 `v5ai_chat_theme`）：否决——需求要的是 Agent 级配置；且本地开关是「用户想不想看」，Agent 级是「这个 Agent 对外展示什么」，两者是不同的产品决策。
- **Agent 级默认 + 门户用户可覆盖**：否决（本轮）——实现量最大（快照 + 本地覆盖 + 优先级），而本轮诉求只是「管理端能关掉这个展示」。
- **开关只作用于门户、不作用于管理端「预览与调试」**（本方案初稿的推荐）：否决——调试页要能**当场看到**开关效果，否则验证一次要「发布 → 刷新门户」两跳，成本高到没人会验。代价是「RAG 未命中」与「命中但未展示」在调试页不再可区分，用一句提示文案缓解（`showCitations=false` 时面板显示「该 Agent 已关闭引用展示」）。
- **连知识库「知识问答」一起关**：不做——那里没有 Agent 上下文，`showCitations` 无从取值。

**Consequences**:
- `show_citations` 随**发布快照**下发门户，`AgentDTO.showCitations()` 的两个便捷构造器默认 `true`、`AgentScopePublishedAgentResolver` 用 `getBool("showCitations", true)` 读：**存量快照缺该 key 时行为不变**，无需回填。
- `secondaryModelId` **不下发门户**（终端用户无需知道，也没有 UI 用得到）——已由 `ChatAuthControllerTest.secondaryModelIsNotPartOfThePortalContract` 用 `RecordComponent` 反射钉住门户契约的形状。
- **两个坑（与 `imageSupported` 同构）**：①门户读的是发布快照，改完**必须重新发布**；②bootstrap 每个页面加载**只取一次**，发布后终端用户需**刷新页面（或重登）**。均记入 `AGENTS.md` §8。
- 渲染侧 `ChatMessageBody.vue` 有**两份独立副本**（门户 `v5ai-ui-chat/`、管理端 `v5ai-ui/`），`showCitations` prop 的默认值在两处**必须都是 `true`**（未传即展示＝改动前行为），改动时两处同改；`v5ai-ui/src/views/knowledge-base/QaTab.vue` 刻意不传该 prop。
- 门户 Key 页的功能清单文案「知识库引用溯源」是 **Key 级**、开关是 **Agent 级**，粒度对不上：某 Agent 关掉引用展示后这条宣传文案对它不再成立。属文案层的小不一致，**本轮不改**，记录在案。
- 不新增审计与用量口径变化：开关不参与任何记账路径。
