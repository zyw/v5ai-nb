# Agent 次要模型：两级回退链，全局摘要模型配置退役

平台有两处「不面向用户、但确实要调用模型」的内部活儿：**会话标题改写**（首轮回答落库后异步跑一次）与**会话摘要压缩**（历史窗口之外的旧内容滚动压成一段文本）。此前它们取模型的方式各不相同——标题**写死**用该 Agent 绑定的对话模型、摘要则先看一处**全局配置** `v5ai.chat.conversation-summary.model-id`、为空才回退 Agent 模型。本决定给 Agent 加一个**可选**的**次要模型**（`v5ai_agent.secondary_model_id BIGINT NULL`），两处共用、由同一个解析器 `AuxiliaryModel.resolve` 取值：**次要模型 → 绑定的对话模型**两级回退，**没有第三层**；那处全局配置随之**下线**（属性字段、`application.yml.template` 注释、README 说明一并移除）。留空的 Agent 行为与改动前完全一致，无需回填存量数据。这样做的直接理由是**这两处调用都静默**——它们 fire-and-forget、失败只留一条 debug 日志、不记用量、不落事件，一旦模型配错（类型不对、被禁用、被删除），表现是「标题默默不改写、摘要默默不更新」，没有任何报错。把模型来源收敛到**一个离 Agent 最近、保存时就能校验**的字段上，是让这类故障从「运行期静默」前移到「保存期报错」的唯一手段。

**Status**: accepted

**Considered Options**:
- **保留全局 `conversation-summary.model-id` 作为回退链的中间层**（本方案初稿的推荐）：否决——它与「Agent 级次要模型」职责重叠且语义更弱（全局一刀切、无法按 Agent 区分），保留会让「这个 Agent 的摘要用了哪个模型」取决于一处与 Agent 相距很远的配置，多一层推理成本却换不到能力。
- **只加全局配置**（扩展 `conversation-summary.model-id`，或按 `会话名称命名设计方案.md` §9.1 新增 `conversation-title.model-id`）：否决——满足不了「每个 Agent 各配各的」。各 Agent 的知识库、语种、语气都不同，共用一个小模型未必都合适。
- **拆成「标题模型」+「压缩模型」两个字段**：否决——两者诉求相同（轻量、非主对话的内部调用），拆开徒增表单与快照复杂度；真需要分化时再加第三个字段即可。
- **允许指向 `EMBEDDING` / `RERANK` 模型**：否决——`AgentScopeModelFactory` 对模型类型硬校验 CHAT，违规会在 `chatText` 里抛异常，而两个 Writer 都是静默失败路径，表现为「没有报错但没有效果」。故保存时即校验「存在 + CHAT 类型 + 已启用」，走跨模块端口 `ModelCatalogPort.isEnabledChatModel`（`AgentServiceImpl` 以 `ObjectProvider` 注入：端口缺席时**跳过**校验而非抛异常，纯单测与裁剪部署都不必凭空造一个模型目录）。
- **次要模型实时读（不走发布快照）**：否决（本轮）——会破坏「线上行为 = 发布时的内容」这一既有不变量，且与 `modelId` 的处理方式不一致。**代价**是换次要模型必须重新发布才对门户生效（管理端调试走 `resolveForDebug` 用实时编辑行，不受此限）。
- **用 `@TableField(updateStrategy = ALWAYS)` 实现「清除」**：否决——`disable()` / `publish()` 都是**部分实体**更新，`ALWAYS` 会让它们把该列一并抹成 NULL。清除改走单独一条 `AgentMapper#clearSecondaryModel`（`LambdaUpdateWrapper.set(col, null)`），仅在提交的 `secondaryModelId` 为 `null` 时执行。
- **给标题生成补一个模型审计列**（摘要侧已有 `v5ai_conversation_summary.model_id`）：否决（本轮）——维持标题「不记用量、不落表」的现有口径，靠 debug 日志归因。
- **两份设计文档里的旧设想**：`同会话短期记忆实现方案.md` §11.2 提出的「Agent 级小模型，标题与摘要共用」即本次落地；`会话名称命名设计方案.md` §9.1 设想的 `conversation-title.model-id` 全局项**作废**。

**Consequences**:
- `v5ai_agent.secondary_model_id BIGINT NULL`（V46），**不建外键**——与 `embedding_model_id` / `rerank_model_id` / `v5ai_conversation_summary.model_id` 等近期新增的模型引用列一致；引用拦截由应用层 `verifyNotReferenced` 负责，能给出「被 N 个 Agent 使用，无法禁用」的友好文案，而裸外键约束只会抛原始约束错误。
- **`secondaryModelId == null` 表示「清除」，与 Agent 其它字段的「null 即不改」语义不同**。理由是它是本表单里唯一**可清除**的模型字段：若也按「null 即不改」，用户选了次要模型后就再也回不到「复用对话模型」。安全性建立在两个前提上——① 前端提交的是**整个表单**、该字段恒在；② `updateAgent` 在全仓库只有 `AgentController` 一个调用方。**改这个调用方必须保持整体提交**，否则会误清该字段。
- 该字段随**发布快照**固化（快照 JSON 里是 `"secondaryModelId"`，用 `jsonLong(Long)` 输出 `null` 或数字，不走 `%d` 的位置参数拼 `null` 字面量）。存量快照缺该 key → 解析得 `null` → 落到回退链，**无需回填历史快照**（与 ADR-0005 处理 `ragCallMode` 缺省同一手法）。
- 两个 Writer 都补上 `modelId == null` 的空值保护，**标题侧是本次新加的**：改动前主模型为空时它会在 `chatText(null, …)` 抛异常被 `catch` 吞掉。解析器同时提供 `AuxiliaryModel.isSecondary(agent)`，供两处 debug 日志区分「用了次要模型还是对话模型」——这两条链路不记用量、不落事件，日志是唯一的归因手段。
- **`V5aiModelMapper.countAgentUsage` 必须同时覆盖 `model_id` 与 `secondary_model_id`**：只被当作次要模型引用的模型若漏拦，就能被删除/禁用，随后该 Agent 的标题与摘要静默失败。该 SQL 是资源文件里的字面量，**当前没有自动化测试兜底**（单测里 Mapper 是 mock，仓库也无读资源文件做字符串断言的先例），交付前须人工验证一次拦截（见 `docs/次要模型与引用展示开关实现方案.md` §8.2）。
- **对存量部署可见的行为变更（唯一一处）**：全局 `v5ai.chat.conversation-summary.model-id` 已下线；设过该项的部署，其摘要调用会从那个模型切到「各 Agent 的次要模型（未配置则对话模型）」。发布说明需写明。
- 管理端表单给出「改动需重新发布后对门户生效（右侧调试面板即时生效）」的提示，与 `imageSupported` 是同一个坑（见 `AGENTS.md` §8）。
