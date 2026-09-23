# 引用随助手消息落库，但不回放给模型

门户在实时流里能折叠展示「引用」的切片（SSE `RETRIEVAL` 事件，载荷与知识库问答同构），但这些切片**回看不到**：它们只被写进 `v5ai_run_event`（运行事件流水，端口里只有 `save`、没有任何查询端），而门户历史接口只返回 `role/content/reasoning/attachments/usage/messageId`，`v5ai_message` 上也没有落点——引用因此成了唯一一个「实时有、回看无」的素材（同类问题在思考上已由 ADR-0007 解决）。本决定把引用**落到所属助手消息**上：复用 V42 预留给扩展语义的保留列 `v5ai_message.metadata`，形状为 `{"citations":[…]}`，里面存的是与当时 SSE 载荷**逐字一致**的快照（单条内容照旧按 2000 字符截断）。历史接口把这组引用 typed 返回，门户回看与当时实时所见一致；引用**不回放给模型**——不进上下文（组装历史时只取 role/content/attachments，结构上就进不去）、不占历史窗口预算、也不进会话摘要。

**Status**: accepted

**Considered Options**:
- **维持实时专用**：否决——同一性质的「回看素材」（思考）已经能回看，引用不该是本仓库里唯一的例外。
- **消息只记 `run_id`，引用在读取时从 `v5ai_run_event` 取**：不重复存储，还能顺带拿到工具调用轨迹。否决——`v5ai_run_event` 的自我定位是「SSE 事件的流水副本」而不是展示源；消息表没有 `run_id`，两者只能靠 `RUN_STARTED` 载荷里的 `userMessageId` 反推；而且表里还躺着 25 行旧格式（载荷是 `[Knowledge Base Context]…` 而非 JSON）的 RETRIEVAL 记录。
- **新增专用列 `v5ai_message.citations`**：与 `reasoning` 对称、语义单一。否决——`metadata` 正是为「以后要加」预留的扩展槽（见 V42 迁移注释），消费它省掉一次 schema 变更；代价只是写入纪律（见下）。
- **只存切片标识，读取时回查切片内容**：省体积。否决——引用的语义是「这条回答当时依据了什么」，而知识库允许编辑手工切片、删除文档、重建索引；回查会让历史引用在几周后自己变样或消失。
- **回填已有数据**：否决——关联要靠解析 JSON 反推，收益只是几轮旧问答好看；`NULL` 的语义与 `reasoning` 保持一致（= 本迁移之前没有记录）。

**Consequences**:
- `v5ai_message.metadata TEXT`（可空）= `{"citations":[{knowledgeBaseId,documentId,documentTitle,chunkIndex,content,score}, …]}`；`NULL` = 这一轮没有引用（未启用 RAG / 无命中 / V45 之前的数据）。
- **写入纪律**：当前只有助手消息落库一处写入该列，采取**整列覆盖**；将来出现第二个写入方时必须改成读-改-写，否则会抹掉引用。
- 同一条切片在一次运行里被检索到多次时，按 `(knowledgeBaseId, documentId, chunkIndex)` **保留首次命中**（连同它的相似度与检索顺序）；实时侧三个前端（门户 `ChatView`、管理端 `PreviewPanel`、知识库 `QaTab`）同样由「后者覆盖」改为「累积去重」，保证刷新前后看到的一致。
- **记忆窗口与摘要的取数必须把该列一并剔除**（`HOT_PATH_EXCLUDED_COLUMNS`）：少写一个字符串不会有任何编译或运行时报错，只会让每一轮运行悄悄多读每消息约 8.5KB。
- 本轮**不**给载荷补切片的稳定标识 `chunk_id`：引用维持与知识库问答「逐字同构」的不变量（见 `CONTEXT.md`「引用」）；将来要支持「点击跳转到切片」时再两侧同时加字段。
- 门户历史接口因此每轮多 ≤8.5KB（`INJECT_TOP_K = 4` × 单条 2000 字符上限），与正文同量级，接受。
- 本 ADR 消费掉 ADR-0007 最后一条遗留：`v5ai_message.metadata` 不再是「语义待定、无写入方与读取方」的列（V45 迁移同步更新列注释）。
