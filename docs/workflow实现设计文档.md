# Agent 工作流实现设计文档

> 状态：设计稿  
> 日期：2026-09-25  
> 适用范围：`v5ai-workflow` 后端模块、管理端工作流编辑器，以及后续工作流运行 API  
> 约束：不新增第三方 Java 编排框架或依赖；复用项目现有 Spring、MyBatis-Plus、AgentScope、Vue Flow 和 Naive UI。

## 1. 目标与背景

本设计将现有工作流 MVP 演进为由管理员通过画布配置、保存、校验、发布、测试和追踪运行的 Agent 工作流能力。工作流用于组合平台 Agent 与确定性的系统执行器，运行结果、节点输入输出和错误可以在管理端回看。

设计以用户提供的工作流 UI 图为交互参考：左侧节点分类与搜索、中间画布、右侧节点配置、顶部工作流操作、底部画布操作和运行调试。UI 图中的 HTTP 请求和 Python 脚本属于可编排的**执行器节点**。它们不是整个工作流引擎，也不是第三方 LiteFlow 的执行器 API。后端通过平台自己的 `WorkflowNodeExecutor` 扩展点实现每种节点。

### 1.1 目标

- 保留现有 `v5ai-workflow` 模块、工作流 JSON 定义和发布快照方向。
- 支持通过可视化画布编排 Agent、控制节点与执行器节点。
- 发布前检查结构、连线、节点配置、变量引用和依赖资源。
- 每次运行锁定发布版本，并保存工作流级、节点级执行记录。
- 在运行调试视图中定位执行路径、节点输入输出和失败原因。
- 通过明确边界，为后续异步 Worker、暂停恢复和外部运行 API 留出扩展点。

### 1.2 非目标

第一阶段不实现 BPMN，不将 LiteFlow、Temporal、Flowable 等作为运行引擎，不支持管理员提交任意 Java 类。第一阶段不承诺跨服务重启恢复、长时间人工等待、分布式并行调度和任意 Python 代码的进程内执行。此类能力需在对应阶段单独定义运行和安全模型。

## 2. 当前实现基线

当前代码已经提供下列能力：

- `../v5ai-modules/v5ai-workflow` 是独立 Maven 模块，并由 `v5ai-starter` 引入。
- `WorkflowDefinition` 保存 `nodes` 和 `edges`；节点类型为 `START`、`AGENT`、`CONDITION`、`END`。
- `v5ai_workflow` 保存草稿定义、已发布定义、状态和当前发布版本。
- 发布时调用 `WorkflowDefinitionValidator`，校验恰好一个开始节点、至少一个结束节点、边引用合法和无环。
- `WorkflowEngine` 从已发布定义开始执行，Agent 节点复用 `AgentRuntime`，条件节点使用白名单运算符，未走分支的节点写为 `SKIPPED`。
- `v5ai_workflow_run` 和 `v5ai_workflow_node_run` 保存运行与节点级记录。
- 管理 API 已有工作流 CRUD、发布、运行和运行记录查询。
- 前端 `WorkflowEditorView.vue` 使用 Vue Flow，具有四类节点、简化节点配置、保存草稿、发布和测试运行。

当前与设计图之间的主要差距：节点位置未持久化；节点只能点击添加；校验结果没有结构化呈现；执行逻辑集中在 `WorkflowEngine` 的类型分支中；条件分支和节点配置没有统一的类型级校验；节点运行记录主要在完成后写入；暂不支持 HTTP、Python、人工等待、循环和运行取消；运行记录查询没有完整分页及事件时间线。

## 3. 架构决策

### 3.1 执行引擎

继续使用项目自研引擎，逐步把 `WorkflowEngine` 整理为校验、调度、执行器分派、上下文管理和持久化协调。节点具体逻辑通过 `WorkflowNodeExecutor` 注册，不在引擎里持续堆积 `switch` 分支。

```text
WorkflowController
  └── IWorkflowService
        ├── WorkflowDefinitionValidator
        ├── 发布快照与版本管理
        └── WorkflowEngine
              ├── WorkflowExecutionContext
              ├── WorkflowScheduler
              ├── WorkflowNodeExecutorRegistry
              │     ├── StartNodeExecutor
              │     ├── AgentNodeExecutor
              │     ├── ConditionNodeExecutor
              │     ├── HttpNodeExecutor
              │     ├── PythonNodeExecutor
              │     ├── VariableNodeExecutor
              │     └── EndNodeExecutor
              └── WorkflowRunRepository
```

所有新增执行能力只依赖项目当前已有的基础设施。新增节点不能绕过工作流运行记录、权限、发布版本和统一变量上下文。

### 3.2 术语映射

| 产品/UI 概念 | 后端概念 | 说明 |
|---|---|---|
| 工作流画布 | `WorkflowDefinition` | 节点、边、布局和工作流输入输出的版本化定义 |
| 节点类型 | `WorkflowNodeType` | 如 `AGENT`、`HTTP`、`PYTHON`、`CONDITION` |
| 执行器节点 | 一种可配置节点类型 | HTTP 请求、Python 脚本等；决定可配置字段和端口 |
| 节点执行器 | `WorkflowNodeExecutor` 实现 | Java 端处理该类型节点的执行代码 |
| 工作流引擎 | `WorkflowEngine` | 读取已发布定义、调度节点并汇总运行状态 |
| 自定义执行器 | 服务端注册的可信扩展 | 由开发侧实现和部署；管理端只能选择已注册类型，不能在线提交 Java 代码 |

### 3.3 执行策略

初期采用单次请求内顺序执行，条件分支选中一条路径，失败后结束本次运行。执行入口和执行器接口独立于调度实现，使同步调度未来可被异步 Worker 替换。

任何同步执行入口必须设置最大节点数、总运行时长、单节点超时和输入输出体积上限。达到上限时以明确失败码收尾，不得继续执行或无限递归。

## 4. 领域模型与定义格式

### 4.1 工作流定义

定义格式增加 `schemaVersion`、工作流输入输出与节点位置，保留现有 `nodes`、`edges` 结构。位置由前端保存，后端执行时忽略。

```json
{
  "schemaVersion": 2,
  "inputs": [
    { "name": "orderId", "label": "订单号", "type": "string", "required": true }
  ],
  "nodes": [
    {
      "id": "start_order",
      "type": "START",
      "name": "接收订单",
      "position": { "x": 120, "y": 180 },
      "config": { "variables": [] }
    },
    {
      "id": "check_inventory",
      "type": "HTTP",
      "name": "查询库存",
      "position": { "x": 420, "y": 180 },
      "config": {
        "method": "POST",
        "url": "https://inventory.example/api/check",
        "headers": [],
        "body": { "orderId": "{{inputs.orderId}}" },
        "timeoutMs": 10000,
        "retry": { "maxAttempts": 1, "backoffMs": 0 },
        "outputVar": "inventory"
      }
    }
  ],
  "edges": [
    { "id": "e1", "source": "start_order", "target": "check_inventory", "sourceHandle": "out" }
  ],
  "outputs": [
    { "name": "result", "value": "{{inventory}}" }
  ]
}
```

兼容要求：缺少 `schemaVersion` 的当前存量定义按 v1 读取；迁移为 v2 时保留 node/edge ID、配置与原发布版本语义。只有保存草稿或显式迁移时才写入新格式，不能因读取旧定义而改写数据库。

### 4.2 变量作用域

统一变量命名空间：

- `inputs.*`：工作流调用输入，只读。
- `nodes.<nodeId>.*`：节点结构化输出，只读；避免不同节点写入同名变量时互相覆盖。
- `vars.*`：工作流显式变量赋值节点维护的可变变量。
- `system.*`：平台运行元信息，只读，例如 `runId`、`workflowKey`、`workflowVersion`。

迁移期兼容当前直接使用 `{{变量名}}` 的写法：解析器先查 `vars`，再查节点输出别名，再查 `inputs`；新定义与编辑器生成的模板必须使用显式命名空间。表达式解析仅支持模板变量引用和既有白名单比较操作符，不允许执行 SpEL、JavaScript、Shell 或任意表达式代码。

所有变量都以 JSON 值承载，尽量保留数字、布尔、数组和对象类型。模板作为整值引用时保留 JSON 类型；嵌入普通文本时才做安全字符串化。未定义变量应在发布校验中报错；运行时缺失则返回包含节点 ID 和引用路径的错误。

### 4.3 工作流、发布版本和运行

- `Workflow` 是可编辑的工作流资源，含草稿和当前发布指针。
- `WorkflowVersion` 是不可变的发布快照；一次运行固定引用一个版本。
- `WorkflowRun` 表示一次执行，保存输入、输出、状态和时间。
- `WorkflowNodeRun` 表示该运行中的一个节点执行尝试，保存输入/输出摘要、状态、耗时、错误和尝试次数。
- `WorkflowRunEvent` 是可选的追加式运行事件，供调试界面按时间线回放。

工作流版本不能依赖当前工作流行中的 `publishedDefinition` 覆盖来充当完整版本历史。为支持历史查看与回滚，建议新增版本表；现有 `published_definition` 暂作为兼容字段和当前发布缓存，迁移期保持一致。

## 5. 节点类型设计

### 5.1 第一批节点

| 类型 | 类别 | 配置和行为 |
|---|---|---|
| `START` | 基础 | 唯一入口；定义入参名称、说明、类型、必填、默认值 |
| `END` | 基础 | 定义工作流返回值；允许多个结束节点，但同一次执行只能到达一条终止路径，若未来允许多个分支汇聚需另行定义 |
| `AGENT` | AI | 选择已发布 Agent，映射输入/Prompt，配置输出提取、超时和有限重试 |
| `CONDITION` | 控制 | 布尔条件；true/false 两个出口，使用类型安全的比较器 |
| `HTTP` | 执行器 | 发起受策略约束的 HTTP 请求，解析 JSON 响应并输出变量 |
| `PYTHON` | 执行器 | 将脚本、输入交给隔离的外部 Python Runner；Runner 未配置时节点不可发布/不可执行 |
| `VARIABLE` | 数据 | 在 `vars` 命名空间设置、合并或删除变量 |

### 5.2 后续节点

在第一阶段稳定后再增加：多条件分支、循环、并行/汇聚、子工作流、人工审核、用户询问、延时和检查点。循环必须由显式循环体和迭代上限表示，不能通过普通图边形成任意环；DAG 校验继续拒绝非循环节点形成的环。并行必须定义数据冲突策略与汇聚规则，不能直接把 `WHEN` 或线程池语义暴露成隐式行为。

### 5.3 Agent 节点

Agent 节点复用项目现有 `AgentRuntime` 和已发布 Agent 解析规则，不接入 LiteFlow Agent 或另一套会话/工具/模型配置体系。

配置建议：

```json
{
  "agentKey": "order-risk-agent",
  "prompt": "分析订单 {{inputs.orderId}}，库存信息：{{nodes.check_inventory.body}}",
  "outputMode": "text",
  "outputVar": "riskAnalysis",
  "timeoutMs": 120000,
  "retry": { "maxAttempts": 1, "backoffMs": 0 }
}
```

第一版输出以文本为主；结构化输出需依赖 Agent/模型现有能力并验证结果 JSON，无法解析时节点失败，不能静默返回伪结构化数据。Agent 节点记录 Agent Key、Agent Run ID（可获得时）、Prompt 模板版本、输入变量、回答摘要和用量引用。工作流用量汇总由 AgentRuntime 已有用量口径提供，避免重复估算。

### 5.4 HTTP 请求执行器

节点配置包括：请求方法、URL 模板、Query、Headers、Body、连接/读取超时、有限重试、成功状态码范围、响应解析方式、输出变量和允许的响应字段路径。

安全约束：

- 只允许 `http` / `https`，生产推荐强制 HTTPS。
- 通过可配置目标主机白名单控制目的地址；阻止 loopback、链路本地、私网、云元数据地址及 DNS 解析后落入禁用网段的请求。
- 禁止自动跟随跨域重定向，或在每次重定向后重新执行完整目标校验。
- 禁止由工作流配置任意代理、TLS 忽略校验或连接到 Unix Socket。
- Header 凭据应引用平台密钥/凭据，不明文保存到工作流定义；读回和运行详情均脱敏。
- 限制请求/响应字节数、并发数和运行时间；日志不记录 Authorization、Cookie 与敏感 Body。
- 默认不自动重试非幂等请求。只有显式确认幂等或配置幂等键后才能重试 POST/PATCH。

### 5.5 Python 脚本执行器

Python 是高风险执行器。不得在 Spring Boot 主进程内执行管理员提交的 Python，也不得用仅靠超时的 `ProcessBuilder` 视为安全沙箱。

可发布的 Python 节点必须调用隔离 Runner：单独操作系统身份或容器、独立工作目录、CPU/内存/进程数/运行时间限制、默认禁网、只挂载本次输入输出目录、无应用密钥和宿主机敏感目录、脚本和产物大小限制。Runner 的部署配置属于平台基础设施；未部署或健康检查失败时，编辑器显示节点不可用，发布校验阻止发布包含该节点的定义。

首期实现中，`PythonNodeExecutor` 只定义契约和不可用时的明确错误；上线执行能力以前述 Runner 安全验收为前提。允许配置依赖的能力后置，首版仅限预置运行环境，禁止用户自行 pip 安装。

## 6. 后端模块设计

### 6.1 核心接口

```java
public interface WorkflowNodeExecutor {
    WorkflowNodeType type();

    NodeExecutionResult execute(
        WorkflowNode node,
        WorkflowExecutionContext context
    );
}
```

`WorkflowNodeExecutorRegistry` 在应用启动时收集 Spring Bean，校验类型唯一并提供 `type -> executor` 查询。未注册节点类型不能通过校验或执行。节点执行器负责该节点的配置解析、输入映射、外部调用和输出生成，不负责更新工作流整体状态。

`WorkflowExecutionContext` 最少包括：

```java
public final class WorkflowExecutionContext {
    private final String runId;
    private final String workflowKey;
    private final long workflowVersion;
    private final Map<String, Object> inputs;
    private final Map<String, Object> variables;
    private final Map<String, NodeOutput> nodeOutputs;
    private final WorkflowRunControl control;
}
```

Context 的变量写入通过方法完成，验证 JSON 可序列化、大小限制、只读命名空间和节点输出隔离；执行器不能直接替换共享 Map。

### 6.2 组件职责

- `WorkflowDefinitionParser`：解析并兼容旧 schema。
- `WorkflowDefinitionValidator`：结构、端口、变量、节点配置及依赖校验，返回结构化诊断列表。
- `WorkflowEngine`：载入已发布快照并启动一次运行。
- `WorkflowScheduler`：计算可执行路径，执行节点、分支和状态转移。
- `WorkflowNodeExecutorRegistry`：节点类型分派。
- `WorkflowRunService`：建立运行记录、逐节点状态更新、最终成功/失败收尾。
- `WorkflowExecutionContext`：维护本次执行作用域与数据。
- `WorkflowRunRepository`：运行记录持久化边界。
- `WorkflowVersionService`：创建不可变发布版本、查询历史及回滚草稿。
- `WorkflowSecretResolver`：按运行身份解析凭据，禁止将明文凭据写入定义/事件。

模块仍自包含于 `v5ai-workflow`；跨模块调用 Agent 只依赖 `v5ai-common-agentscope` 的 `AgentRuntime` 契约，不依赖 Agent 模块内部实现。

### 6.3 调度与控制流

第一阶段维持单线程顺序调度：

1. 加载运行开始时固定的发布版本。
2. 验证输入并初始化只读 `inputs` 与系统变量。
3. 写入 `WorkflowRun=RUNNING`。
4. 写入开始节点 `RUNNING`，执行后更新为 `SUCCEEDED`。
5. 按边和出口 ID 找到后继节点；普通节点必须恰有一条有效后继路径，条件节点必须选择且仅选择一个出口。
6. 每个节点执行前保存 RUNNING，完成后原子更新节点结果；未走到的合法分支标记 `SKIPPED`。
7. 到达结束节点时构造输出，运行标记 `SUCCEEDED`。
8. 任一节点错误时停止新节点，失败节点标记 `FAILED`，未执行节点按策略标记 `SKIPPED`，工作流标记 `FAILED`。

避免当前递归访问带来的深图栈溢出，调度器使用显式队列/栈和访问集合；对 DAG 保证节点最多执行一次。运行起始时保存 `workflowVersion`，并从版本快照取定义，后续发布不会改变运行中的定义。

### 6.4 失败与重试

- 错误分为配置错误、输入错误、外部服务错误、超时、执行器不可用、取消和内部错误，并提供稳定错误码与可展示消息。
- 重试由调度/运行服务统一管理，节点执行器仅报告可重试性；不在节点内部无限重试。
- 默认最大尝试 1 次（即不重试）；配置上限由服务端限制。使用指数退避时设定最大延迟与总运行时长上限。
- 只对明确幂等操作自动重试。Agent 重试可能重复产生外部工具副作用，默认关闭自动重试。
- 捕获异常时保存脱敏错误摘要和受控诊断信息，不向管理端返回堆栈、凭据或内部网络详情。
- 运行取消在第一阶段不作为承诺能力；数据模型和 `WorkflowRunControl` 预留取消信号。实现取消后，新状态为 `CANCELED`，应停止尚未开始节点并尽力中断可中断的外部调用。

## 7. 发布、版本与并发编辑

### 7.1 状态

工作流资源维持 `DRAFT`、`PUBLISHED`、`DISABLED`。前端另显示“已发布版本存在未发布草稿”这一派生状态，不增加持久化状态枚举。

- 创建产生空草稿。
- 编辑只更新草稿，不改变当前发布版本。
- 发布先执行完整校验；校验失败不改变现行版本。
- 发布成功创建下一个单调递增版本，快照不可变并更新当前发布指针。
- 禁用阻止新运行，不改变历史版本与已有运行记录。
- 恢复历史版本时，将选定版本复制为新草稿，发布后生成新版本，版本号不回退。

### 7.2 并发编辑

工作流定义更新使用 `revision` 或 `updatedAt` 作为乐观锁版本。请求必须带 `expectedRevision`；版本不匹配返回冲突错误，并提示客户端重新加载/合并。禁止静默覆盖另一编辑者刚保存的画布。

### 7.3 保存策略

编辑器提供显式保存，并在交互稳定后增加防抖自动保存。自动保存失败需保留本地编辑状态、提示未保存标记和重试入口。发布前强制完成保存，发布请求引用已保存的 revision，防止发布旧定义。

## 8. 数据库设计

当前 workflow schema 已存在于 PostgreSQL V12 历史迁移和 MySQL V1 基线中。后续迁移按项目约定从 **V51** 开始；不得修改历史迁移。迁移必须同时提供 PostgreSQL 与 MySQL 版本，或在 `common/` 使用两方言兼容 SQL。

### 8.1 推荐变更

新增 `v5ai_workflow_version`：

| 列 | 含义 |
|---|---|
| `id` | 主键 |
| `workflow_key` | 工作流 Key |
| `version` | 单工作流内递增版本 |
| `definition` | 完整不可变 JSON 快照 |
| `schema_version` | 定义 schema 版本 |
| `change_summary` | 发布说明，可空 |
| `published_by` | 发布人标识 |
| `published_at` | 发布时间 |
| `created_at` | 创建时间 |

唯一约束：`(workflow_key, version)`。对版本读取建立 `(workflow_key, version desc)` 查询索引。

扩展 `v5ai_workflow`：

- `draft_revision`：草稿乐观锁修订号。
- 若现有更新时间可可靠满足并发控制，也可先使用 `updated_at`，避免无必要列；设计实现时确认 ORM 时间精度与更新规则后选定一种方式。

扩展 `v5ai_workflow_run`：

- `source`：`PUBLISHED` 或 `DRAFT_TEST`。
- `draft_revision`：草稿测试时记录所测 revision，发布运行时为空。
- `definition_snapshot`：草稿测试时保存本次实际执行的完整定义快照；发布运行从 `v5ai_workflow_version` 按版本读取，无需重复保存定义。
- 草稿测试的 `workflow_version` 为空，不能伪装成当前发布版本。

扩展 `v5ai_workflow_node_run`：

- `attempt`、`duration_ms`、`error_code`、`executor_type`、`agent_run_id`。
- 输入输出继续使用 JSON 文本列，写入前按配置脱敏并限长。
- 如前端确实需要逐步事件流，再新增 `v5ai_workflow_run_event`；否则第一阶段通过节点状态轮询，避免过早增加事件表。

### 8.2 事务边界

- 草稿更新：单工作流行事务，校验 JSON 可解析；完整语义校验可由单独 validate API 和发布事务执行。
- 发布：校验、插入不可变版本、更新发布指针在同一事务中完成。
- 运行：运行记录、节点状态和外部调用不放在一个长数据库事务里。每个状态转换为短事务，避免模型/HTTP 调用期间持有连接和锁。
- 工作流节点执行尝试以 `(run_id, node_id, attempt)` 标识；初期无重试时仍写 attempt=1，便于后续扩展。

## 9. 管理 API 契约

保留已有 `/api/admin/workflows` 路径，按能力增加以下接口。API 具体 DTO 和分页格式遵循当前 `R`、`PageResult`、`PageQuery`。

| 方法 | 路径 | 用途 |
|---|---|---|
| `GET` | `/api/admin/workflows` | 分页列表、按名称/Key/状态筛选 |
| `POST` | `/api/admin/workflows` | 创建工作流 |
| `GET` | `/api/admin/workflows/{key}` | 查询元信息、草稿、当前发布版本摘要 |
| `PUT` | `/api/admin/workflows/{key}` | 更新草稿，带 `expectedRevision` |
| `POST` | `/api/admin/workflows/{key}/validate` | 校验草稿，返回错误/警告/信息诊断 |
| `POST` | `/api/admin/workflows/{key}/publish` | 发布已保存且通过校验的草稿 |
| `GET` | `/api/admin/workflows/{key}/versions` | 查询版本历史 |
| `GET` | `/api/admin/workflows/{key}/versions/{version}` | 读取版本快照 |
| `POST` | `/api/admin/workflows/{key}/versions/{version}/restore` | 将历史快照复制成新草稿 |
| `DELETE` | `/api/admin/workflows/{key}` | 禁用工作流（保留现有语义） |
| `POST` | `/api/admin/workflows/{key}/run` | 测试运行，支持 `draft` 标志；仅管理员权限 |
| `GET` | `/api/admin/workflows/{key}/runs` | 分页运行列表 |
| `GET` | `/api/admin/workflows/runs/{runId}` | 运行与节点详情 |
| `POST` | `/api/admin/workflows/runs/{runId}/cancel` | 后续阶段的运行取消 |

草稿测试只允许管理端调试权限，并必须在运行记录中标记 `source=DRAFT_TEST`、被测试的 revision 和定义快照；线上运行始终只允许已发布版本。现有 `/run` 行为目前使用已发布定义，前端当前编辑器“测试运行”按钮应改为明确选择“测试草稿”或“运行已发布版本”，避免用户误以为测试的是未保存画布。

校验响应示例：

```json
{
  "valid": false,
  "diagnostics": [
    {
      "severity": "ERROR",
      "code": "NODE_CONFIG_REQUIRED",
      "nodeId": "http_2",
      "field": "url",
      "message": "HTTP 请求节点必须配置 URL"
    }
  ]
}
```

外部运行 API `/api/v1/workflows/{workflowKey}/run` 不纳入第一阶段。开放前必须复用 Agent API Key 鉴权、限流、配额、审计、调用主体归属和工作流用量归集，且只执行发布快照。

## 10. 前端交互设计

### 10.1 工作流列表

显示名称、Key、状态、当前发布版本、草稿是否有改动、更新时间和最近一次运行状态。支持分页、搜索、创建、编辑、复制、禁用、发布和查看运行记录。禁用需二次确认；删除操作实际保持当前软禁用语义。

### 10.2 设计器布局

- 顶部：返回、名称和 Key、草稿/发布状态、变更标记、校验、JSON、版本历史、保存、发布。
- 左侧节点面板：基础、AI、执行器、控制、扩展分类；搜索节点；拖到画布时创建实例。
- 中央画布：Vue Flow 节点、边、缩放、适配视图、小地图、框选、多选、撤销/重做、自动布局。
- 右侧属性面板：按节点类型渲染专用表单，展示必填项、字段错误、变量选择器和节点说明。
- 底部运行栏：校验结果、运行草稿、运行已发布版本、运行面板显隐。

画布节点卡片显示类型图标、节点名、关键配置摘要和运行态。条件节点出口显示 true/false；HTTP 节点显示方法和 URL 摘要；Python 节点显示脚本状态而不在卡片展示代码。

### 10.3 编辑与反馈

- 所有节点、边、位置和视口相关持久化状态均由定义中的节点位置及前端本地视口状态分别管理；服务端不需要存储浏览器缩放比例。
- 删除节点时一并移除关联边并支持撤销。
- 不合法连线即时提示；服务端校验仍为最终依据。
- 点击诊断项聚焦对应节点/字段；画布节点标出错误和警告。
- 发布按钮在未保存或存在错误时阻止发布，并给出可操作的原因。
- 运行时将节点状态投影到画布：排队、执行中、成功、失败、跳过；支持高亮已执行边。
- 右侧运行详情显示节点输入输出 JSON、开始/结束时间、耗时、尝试次数、脱敏错误及 Agent Run 关联入口。

### 10.4 组件边界

拆分现有单体 `WorkflowEditorView.vue`：

```text
v5ai-ui/src/components/workflow/
  WorkflowCanvas.vue
  WorkflowPalette.vue
  WorkflowInspector.vue
  WorkflowValidationPanel.vue
  WorkflowRunPanel.vue
  WorkflowVersionPanel.vue
  nodes/
    StartNodeCard.vue
    AgentNodeCard.vue
    ConditionNodeCard.vue
    HttpNodeCard.vue
    PythonNodeCard.vue
    EndNodeCard.vue
  inspectors/
    StartNodeInspector.vue
    AgentNodeInspector.vue
    ConditionNodeInspector.vue
    HttpNodeInspector.vue
    PythonNodeInspector.vue
    EndNodeInspector.vue
```

节点卡片和表单由 `WorkflowNodeType` 到组件的注册表映射；前端类型常量与后端节点类型契约保持一致。未知类型应显示兼容占位卡片和“无法编辑/执行”的明确提示，不能静默丢弃未知配置。

## 11. 权限、凭据和审计

- 管理端接口继续使用 Sa-Token 权限控制；新增 validate、version、restore 等权限应纳入菜单/权限种子数据。
- 草稿测试、发布、禁用、版本恢复和运行均写管理操作审计。
- 对外运行 API 未正式开放前不把工作流 Key 当作安全凭据。
- HTTP 凭据存入平台密钥管理能力或安全引用；工作流 JSON 只保存 secret reference。
- 节点输入输出可能含个人信息、业务凭据和模型内容。定义数据脱敏策略，限制留存长度并避免把完整敏感值放进通用日志。
- Python 节点执行主体没有应用数据库凭据、Agent 凭据或存储凭据。
- 自定义执行器由受信任服务端代码注册；不允许管理 UI 直接创建任意 Java 类或提交任意字节码。

## 12. 可观测性与运行记录

每个运行记录至少能回答：由谁触发、运行哪个工作流版本、输入摘要是什么、执行了哪些节点、每个节点耗时、哪一步失败、最终输出是什么。

日志字段统一包含 `workflowKey`、`workflowVersion`、`runId`、`nodeId`、`nodeType`、`attempt`。运行历史列表使用数据库分页；详情按节点顺序与实际开始时间查询。失败应保存稳定错误码和脱敏消息。

首期使用短轮询获取 RUNNING 的节点状态即可；只有需要低延迟实时调试时，再增加管理端 SSE 事件。运行 API 的同步请求不得因前端断开连接而隐式取消已经建立的运行记录，除非显式调用 cancel。

## 13. 实施阶段

### Phase A：定义和执行框架加固

- 为定义引入 schemaVersion 和 node position，读取兼容 v1。
- 把结构校验升级为结构化诊断，并补齐节点类型、边出口和可达性校验。
- 引入 `WorkflowNodeExecutor`、注册中心、ExecutionContext 和迭代式调度器。
- 将现有 START、AGENT、CONDITION、END 迁移为独立执行器，保持行为兼容。
- 运行状态持久化改为 RUNNING 前置写入和节点逐步状态更新。
- 修复运行列表分页，加入 revision 乐观锁。

### Phase B：画布可用性与调试

- 画布拖拽添加、连线删除、复制粘贴、撤销重做、自动布局。
- 拆分节点卡片和属性表单。
- 校验面板、JSON 查看、保存状态、草稿测试和已发布运行区分。
- 运行节点状态覆盖、路径高亮和节点输入输出详情。

### Phase C：HTTP 与变量节点

- 实现 HTTP 请求执行器与完整 SSRF/凭据/体积/超时边界。
- 实现 VARIABLE 节点、JSONPath 风格受限字段映射（如采用，需定义安全解析和数组路径语义）。
- 增加字段变量选择器、HTTP 响应字段预览和版本历史。

### Phase D：Python Runner 与长任务评估

- 独立部署 Python Runner，完成权限隔离、资源限制、禁网和审计验证后开放节点。
- 按真实长任务需求决定是否将执行入口迁移到现有异步 Worker 模式。
- 再定义取消、重试、检查点、人工等待/恢复和并行调度语义。

### Phase E：对外调用

- 评估对外工作流运行 API、SSE、API Key、限流、配额、审计和用量归集。
- 建立工作流与 Agent 运行记录、用量统计和错误追踪的关联。

## 14. 验收标准

### 定义与发布

- 能打开存量 v1 工作流并保留节点、边和配置。
- 保存后重新打开，节点位置和节点配置保持一致。
- 无效定义不能发布；诊断可定位到节点/字段/边。
- 发布产生不可变递增版本；草稿保存和后续发布不改变既有运行所绑定的版本。
- 并发更新冲突可见，服务端不静默覆盖。

### 执行

- START → AGENT → CONDITION → END 正确传递 JSON 类型变量并产生最终输出。
- 条件分支仅运行选中出口，另一路被标记为 SKIPPED。
- 任意失败节点有明确状态、错误码和耗时；后续节点不再运行。
- 超时、最大节点数、输入输出大小限制生效。
- HTTP 目标安全策略、重定向校验、凭据脱敏和响应大小限制通过验证。
- Python 节点在 Runner 未配置时拒绝发布或执行；未经过隔离安全验收不启用真实脚本执行。

### 管理端

- 节点拖入画布、编辑、删除、撤销重做、连线和保存可用。
- 发布和运行状态在列表、画布、运行详情三处一致。
- 测试草稿与执行发布版本有清楚区分。
- 运行详情能展示分支选择、节点执行状态、输入输出和失败位置。

## 15. 已知限制与后续决策

- 第一阶段引擎为单体内同步运行，适合短流程；长运行切换异步 Worker 前必须补充排队、租约、幂等、取消和恢复协议。
- 当前数据库存储 JSON 文本；版本表与运行记录中的输入输出需设大小上限，避免大响应和模型文本造成表膨胀。大型产物应使用资源存储引用，但该能力需单独设计。
- 并行与多个结束节点的语义尚未纳入首期。首期明确限制单路径结束；若要多出口结束或并行汇聚，需先确定结果合并和失败传播规则。
- Python 运行时不是普通节点实现问题，而是独立安全边界。必须先部署并验证隔离 Runner。
- HTTP 请求允许目标的默认策略（白名单还是管理员配置域名）应结合部署网络环境确定；无显式安全策略时生产环境拒绝任意公网目的地址。
- Agent 节点的一次调用可能触发工具副作用；节点重试必须考虑业务幂等键，默认不自动重试。

## 16. 参考现有文件

- 后端工作流模块：`../v5ai-modules/v5ai-workflow`
- 工作流运行引擎：`../v5ai-modules/v5ai-workflow/src/main/java/xin/v5ai/nb/workflow/core/WorkflowEngine.java`
- 工作流校验：`../v5ai-modules/v5ai-workflow/src/main/java/xin/v5ai/nb/workflow/core/WorkflowDefinitionValidator.java`
- 管理 API：`../v5ai-modules/v5ai-workflow/src/main/java/xin/v5ai/nb/workflow/controller/WorkflowController.java`
- 前端设计器：`../v5ai-ui/src/views/WorkflowEditorView.vue`
- 前端节点卡片：`../v5ai-ui/src/components/workflow/WorkflowNodeCard.vue`
- 前端 API 契约：`../v5ai-ui/src/api/client.ts`
- 方言迁移约定：`adr/0012-multi-dialect-database-support.md`
- 当前 schema 快照：`db/schema.md`
