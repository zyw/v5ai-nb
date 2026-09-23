# 文档切片器按切片策略拆分实现类并统一配置字段命名

原 `DocumentChunker` 只有一个实现 `FixedSizeDocumentChunker`，四种切片方式（按长度/分隔符/正则/智能）靠一个 `switch(mode)` 在同一类内完成；同时配置字段名与实现不符（`mode` 是通用词、`maxChunkTokens` 实际按字符数切分）。新增切分规则会持续膨胀该 switch，智能切片的 LLM 调用逻辑也难以与纯文本切分隔离。

我们决定：按切片策略拆分——每个策略一个实现类（`Length/Delimiter/Regex/SmartDocumentChunker`），实现 `ChunkingStrategy`（声明 `supportedMode()`）；共享的「定长切分 + 短段合并 + 流水线编排」抽成 `FixedLengthSplitter`/`ChunkMerger`/`ChunkPipeline` 组件；`DocumentChunkerRegistry`（`@Primary`，实现 `DocumentChunker`）按 `ChunkMode` 路由，注入方零感知。配置字段随之更名：`chunkParams.mode` → `sliceStrategy`、`maxChunkTokens` → `maxChunkLength`（旧数据不兼容，人工处理）。

**Status**: accepted

**Considered Options**:
- 维持单类 + switch(mode)：改动最小，但新增规则膨胀 switch、智能切片 LLM 逻辑与纯文本切分耦合 —— 否决。
- 拆「一级切分策略」+ 共享流水线，不按 mode 一对一建类：职责边界更细，但类数量与注册关系更复杂，且与 `DocumentParserRegistry`「每类型一实现类」的既有样板不一致 —— 否决。
- 按切片策略每规则一个实现类 + 共享组件 + 注册中心路由（本方案）：对齐解析器先例、新增规则只加一个类、智能切片预留独立落点 —— 采纳。

**Consequences**:
- `config.chunkParams` JSON 键变更（`mode`→`sliceStrategy`、`maxChunkTokens`→`maxChunkLength`），存量知识库配置需人工改写；不提供兼容层。
- 新增切片策略只需新增一个 `ChunkingStrategy` 实现类（含 `supportedMode()`），注册中心与 worker 零改动。
- `SmartDocumentChunker` 当前为占位（回退段落切分，与按长度切片的一级切分相同），LLM 语义切分接入点已预留（`chunkModelId`）。
- 行为冻结：四种策略的切分结果与原实现逐字节一致，由各策略类测试 + 注册中心路由测试锁定。
