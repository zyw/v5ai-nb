# Agent Server ↔ Runtime Client Protocol v2

## 1. 文档目标

本文重新设计 Java Server 与 Agent Runtime Client 之间的接口协议。

目标运行时包括：

- Java Agent Client；
- Python Agent Client；
- TypeScript Agent Client；
- 基于 Pi、Spring AI、LangGraph 或其他 Agent Runtime 的执行端。

协议不绑定具体编程语言、Agent 框架或模型供应商。Server 负责控制面和业务事实，Client 负责执行面和模型调用。

本文同时说明：

1. 当前协议存在的问题；
2. 重新设计协议时应采用的方法；
3. v2 的传输、消息、状态、错误、鉴权、资源和迁移设计；
4. Java、Python、TypeScript Client 的实现边界和验收标准。

## 2. 设计结论

推荐采用三平面模型：

```text
┌─────────────────────────────────────────────────────────────┐
│ Public API Plane                                             │
│ HTTP/JSON：用户、管理端、Agent、会话、资源管理                │
└──────────────────────────┬──────────────────────────────────┘
                           │ Server 内部编排
┌──────────────────────────▼──────────────────────────────────┐
│ Runtime Control Plane                                       │
│ gRPC bidirectional stream：注册、能力、任务、事件、心跳、取消 │
└──────────────────────────┬──────────────────────────────────┘
                           │ 引用或临时授权
┌──────────────────────────▼──────────────────────────────────┐
│ Artifact/Data Plane                                         │
│ HTTPS/Object Storage：模型文件、Skill 文件、附件、工具输出    │
└─────────────────────────────────────────────────────────────┘
```

核心原则：

| 原则 | 设计要求 |
|---|---|
| Server 是控制面事实来源 | Agent、模型配置、工具策略、Skill、RAG、资源权限由 Server 管理 |
| Client 是执行面 | Client 负责 prompt 组装、模型调用、Client-owned Tool 和流式执行 |
| 单一长连接 | Client 主动连接 Server，Server 通过同一双向流下发任务，避免 Server 反向连接 Client 地址 |
| 强类型协议 | 业务消息直接定义为 protobuf message，禁止用通用 `uri + JSON body` 代替 schema |
| 事件可恢复 | 每个执行事件有 `execution_id + sequence + event_id`，支持 ACK、去重和断线恢复 |
| 凭证最小化 | Server 不把长期 API Key、MCP 密钥和用户密钥写入任务快照；只发短期授权或引用 |
| 大对象外置 | 二进制、长文本和 Skill 支撑文件使用资源引用，协议只传 digest、大小和临时地址 |
| 能力协商 | Server 根据 Client Runtime、版本和能力选择可执行 Agent 与工具 |
| 协议可演进 | v2 使用显式版本、optional 字段、不可复用 field number 和兼容测试 |

## 3. 当前协议的问题

### 3.1 协议没有真正的 service schema

当前 `.proto` 只定义了三个 message，没有定义 service：

```protobuf
message GrpcSnailAiRequest {
  int64 reqId = 1;
  Metadata metadata = 2;
  string body = 3;
}
```

真实的 service name、method name 和 RPC 类型隐藏在 Java 常量中，再通过编程式 `MethodDescriptor` 构造。结果是：

- Java 以外的语言不能直接根据 `.proto` 生成标准客户端/服务端代码；
- 方法路径变成隐式约定，拼写错误只能在运行时发现；
- gRPC reflection、grpcurl、代码生成、契约测试都难以使用；
- Server Streaming 是否存在、哪个 URI 使用它，必须阅读 Java 实现才能知道。

v2 应在 `.proto` 中直接定义 service 和 RPC 方法。

### 3.2 `uri + JSON body` 造成弱类型和隐式版本

所有业务请求都进入 `metadata.uri` 和字符串 `body`：

```text
/beat
/callback/rag/search
/callback/skill/content
/chat/dispatch
```

这会带来：

- 字段校验推迟到运行时；
- Java DTO 的字段结构成为事实 schema，语言无关性不足；
- JSON 字段重命名、null、默认值和版本兼容没有统一规则；
- body 中再嵌套 JSON 字符串，日志、追踪和错误定位困难；
- 任意 URI 都可能被错误路由，接口发现能力为零。

v2 使用 protobuf 的 `oneof` 消息类型表示不同业务动作，并按 package/service/version 生成多语言代码。

### 3.3 两个独立连接和 Server 反向访问 Client

当前模型是：

```text
Client → Server：Client 建立 gRPC 连接，发送心跳和回调
Server → Client：Server 根据心跳中的 host-ip:host-port 再建立 Client channel
```

这要求：

- Client 的地址必须被 Server 访问；
- Docker、NAT、Kubernetes、跨网络环境需要额外暴露端口；
- Client 地址变化时，Server 需要更新 channel；
- Server 可能连接到旧地址或失效节点；
- reconnect、任务重试和流恢复没有统一机制。

v2 由 Client 主动建立一个到 Server 的双向 gRPC 流。Server 不再通过 `host-ip:host-port` 访问 Client。

### 3.4 业务失败和 transport 失败混在一起

当前结果同时存在：

```text
gRPC 正常返回 + status=0
gRPC transport error
流正常结束但没有 completion
流正常结束但没有 error
```

调用方必须自行猜测哪种失败可重试、哪种失败已经执行、哪种失败需要恢复。

v2 将两类错误分开：

- gRPC status：连接、鉴权、deadline、协议和服务级错误；
- `ClientEvent.failed`：某次执行已经被接受后的业务失败，并带有稳定的 `ErrorInfo.code`、`retryable`、`phase` 和 `execution_id`。

gRPC 官方也建议显式使用 deadline，并区分 `DEADLINE_EXCEEDED`、`CANCELLED`、`INVALID_ARGUMENT` 等状态；v2 将这些语义固定下来。[gRPC Status Codes](https://grpc.io/docs/guides/status-codes/)、[Deadlines](https://grpc.io/docs/guides/deadlines/)

### 3.5 心跳同时承担注册、负载、能力和存活检测

当前 `/beat` 同时传递：

- appId、token、hostId、hostIp、hostPort、version；
- 最大并发数和当前活跃数；
- 本地函数目录。

问题是：

- 能力变化只能依赖下一次心跳；
- Server 无法区分“进程存活”和“客户端能力已更新”；
- 40 秒过期窗口和 10 秒心跳是硬编码行为；
- heartbeat 请求丢失时没有 lease、generation 和连接代次；
- Client 重连后旧连接与新连接的所有权不清晰。

v2 用 `Hello → Welcome → Heartbeat/Health` 明确划分握手、能力、租约、负载和连接代次。

### 3.6 ChatDispatch 携带过多职责和敏感数据

当前 `ChatDispatchRequest` 同时包含：

- Agent 配置；
- 模型 endpoint 和 API key；
- MCP command、args、env、headers、credential；
- HTTP 工具 URL 和 credential；
- Skill 描述；
- 历史消息、记忆、附件 URL/Base64；
- Server 端口和执行策略。

结果是：

- 一次请求可能接近 10 MiB 限制；
- 长期密钥可能进入内存、日志和 dump；
- 任务快照不适合重放和持久化；
- Server 配置结构和 Client Runtime 结构强耦合；
- MCP/HTTP/本地函数的执行者和权限边界不清晰。

v2 将执行上下文、工具目录、凭证授权和资源内容拆分，并用 `ToolSpec.owner` 明确 `SERVER` 或 `CLIENT`。

### 3.7 没有能力协商和路由约束

当前 Server 只知道 Client 是否心跳在线，不知道：

- Client 使用 Java、Python 还是 TypeScript；
- 使用 Pi、Spring AI 还是其他 Runtime；
- 支持哪些模型 provider；
- 支持哪些工具类型和 MCP transport；
- 最大上下文、并发、输出大小和媒体能力；
- 是否支持 cancel、resume、reasoning、structured output。

v2 在握手阶段上报能力，Server 在提交任务前执行 capability matching。

### 3.8 没有统一的执行状态、顺序和恢复语义

当前流式响应只有 `text`、`thinking`、`completion`、`error`，没有：

- `accepted`、`started`、`tool_started`、`tool_finished`；
- event sequence；
- 去重 key；
- server ack；
- 断线后的 resume cursor；
- cancel acknowledgement；
- 部分完成和已执行副作用的说明。

在网络重试时，Server 无法判断是否应该重新执行模型或工具，容易产生重复工具调用。

v2 把一次执行建模为可追踪的事件状态机，并要求工具调用拥有独立的 idempotency key。

### 3.9 protobuf 兼容规则没有写入协议治理

当前 message 使用隐式的 proto3 字段，没有版本治理、field number 预留和 schema compatibility check。

v2 要求：

- 不修改已发布 field number；
- 删除字段必须 `reserved`；
- 可能需要判断“未设置”的字段使用 `optional`；
- 新增字段默认不影响旧 Client；
- 每次 proto 变更运行 buf breaking check 或等效兼容检查。

Protocol Buffers 官方明确规定 field number 不应更改或复用，删除字段要保留编号。[Proto3 Updating a Message Type](https://protobuf.dev/programming-guides/proto3/)

## 4. 重新设计协议的方法

### 4.1 先固定领域边界

将责任分成两类：

| 能力 | Server | Client |
|---|---|---|
| Agent 元数据、发布版本、租户和权限 | 事实来源 | 只读快照 |
| 模型目录、模型路由、模型凭证策略 | 事实来源 | 调用模型 |
| MCP/Skill/RAG 配置和授权 | 事实来源 | 执行允许的工具或回调 Server |
| Prompt policy、工具 allowlist、确认策略 | 事实来源 | 强制执行，不得扩大权限 |
| 模型调用、Agent loop、工具调用循环 | 观测和编排 | 真正执行 |
| 本地文件、进程、MCP Stdio、局域网资源 | 不直接访问 | 受沙箱约束后执行 |
| 会话历史和审计记录 | 权威存储 | 可缓存、可重建 |
| 资源二进制和大文件 | 存储、鉴权、签名 | 按引用下载和处理 |

### 4.2 再固定不变量

新协议必须保证：

1. Server 不需要访问 Client 的入站端口。
2. 每次执行都可以唯一定位、取消、重试和审计。
3. Client 断线重连后可以恢复未完成任务的事件游标。
4. Server 不向 Client 下发超过本次执行授权范围的工具。
5. 任意语言都能通过 `.proto` 生成基础通信代码。
6. 新增字段不会破坏旧 Client；不支持的能力必须在握手阶段暴露。
7. 大文件和敏感数据不进入通用任务 JSON。
8. “请求已接收”“模型已开始”“工具已执行”“回答已完成”有明确状态。

### 4.3 选择传输模型

候选方案：

| 方案 | 优点 | 问题 | 结论 |
|---|---|---|---|
| 保留双向直连 + 改进 JSON | 迁移小 | 仍需 Server 访问 Client，可靠性问题未解决 | 不推荐 |
| HTTP webhook + SSE | 浏览器友好 | Client 回调、重连、双向控制复杂 | 只适合 Public API |
| 消息队列 | 天然持久化和异步 | 部署成本高，实时流和取消复杂 | 可作为未来大规模调度层 |
| 单一 gRPC bidi stream | 跨语言、低延迟、双向、可取消、可流控 | 需要实现连接恢复和 event ack | 推荐 |

## 5. v2 协议总览

协议名称：`Ntao Agent Runtime Protocol`，简称 `NARP`。

当前版本：`v2`。

protobuf package：

```text
xin.v5ai.nb2.agent.runtime.v2
```

核心 service：

```protobuf
service AgentRuntimeService {
  rpc Connect(stream ClientFrame) returns (stream ServerFrame);
}
```

连接方向：

```text
Client ──主动建立一条 TLS gRPC bidi stream──> Server
Client <────────同一条 stream────────────── Server
```

不再定义：

- Server 根据 Client IP/端口反向建立连接；
- `/chat/dispatch`、`/ping`、`/beat` 等 URI 路由；
- `GrpcSnailAiRequest.body` 中嵌套 JSON；
- 依赖 `status=0/1` 表示所有业务状态。

## 6. protobuf 接口定义

下面是 v2 的协议骨架。实际仓库应将其保存为：

```text
v5ai-api/src/main/proto/agent_runtime_v2.proto
```

并由 Java、Python、TypeScript 的构建流程分别生成代码。

```protobuf
syntax = "proto3";

package xin.v5ai.nb2.agent.runtime.v2;

option java_multiple_files = true;
option java_package = "xin.v5ai.nb2.agent.runtime.v2";
option java_outer_classname = "AgentRuntimeProtocolV2";

import "google/protobuf/duration.proto";
import "google/protobuf/struct.proto";
import "google/protobuf/timestamp.proto";

service AgentRuntimeService {
  rpc Connect(stream ClientFrame) returns (stream ServerFrame);
}

message ClientFrame {
  FrameHeader header = 1;

  oneof payload {
    Hello hello = 10;
    Heartbeat heartbeat = 11;
    ClientEvent event = 12;
    ToolResult tool_result = 13;
    ResourceResponse resource_response = 14;
    ResumeRequest resume = 15;
    ClientGoodbye goodbye = 16;
    ClientAck ack = 17;
    ToolInvokeRequest tool_invoke = 18;
  }
}

message ServerFrame {
  FrameHeader header = 1;

  oneof payload {
    Welcome welcome = 10;
    ServerHeartbeat heartbeat = 11;
    ExecutionRequest execution_request = 12;
    CancelExecution cancel_execution = 13;
    ResourceRequest resource_request = 14;
    ResumeResponse resume = 15;
    ServerAck ack = 16;
    ToolResult tool_result = 17;
    ProtocolError error = 18;
    DrainNotice drain = 19;
  }
}

message FrameHeader {
  string protocol_version = 1;
  string connection_id = 2;
  string trace_id = 3;
  string frame_id = 4;
  google.protobuf.Timestamp sent_at = 5;
}

message Hello {
  string client_id = 1;
  string installation_id = 2;
  string runtime_name = 3;
  string runtime_version = 4;
  string client_version = 5;
  repeated string supported_protocol_versions = 6;
  RuntimeCapabilities capabilities = 7;
  ClientLimits limits = 8;
  string tenant_id = 9;
  string app_id = 10;
  string instance_id = 11;
  string idempotency_key = 12;
}

message Welcome {
  string connection_id = 1;
  string negotiated_protocol_version = 2;
  string lease_id = 3;
  google.protobuf.Duration heartbeat_interval = 4;
  google.protobuf.Duration lease_ttl = 5;
  string server_version = 6;
  RuntimePolicy policy = 7;
}

message Heartbeat {
  string lease_id = 1;
  RuntimeLoad load = 2;
  RuntimeCapabilities capabilities = 3;
  repeated ExecutionSummary active_executions = 4;
  string last_server_frame_id = 5;
}

message ServerHeartbeat {
  string lease_id = 1;
  google.protobuf.Timestamp server_time = 2;
  bool drain_requested = 3;
}

message ClientGoodbye {
  GoodbyeReason reason = 1;
  string message = 2;
}

enum GoodbyeReason {
  GOODBYE_REASON_UNSPECIFIED = 0;
  SHUTDOWN = 1;
  DRAINED = 2;
  REPLACED = 3;
  FATAL_ERROR = 4;
}

message RuntimeCapabilities {
  repeated string agent_runtimes = 1;
  repeated string model_providers = 2;
  repeated string model_adapters = 3;
  repeated string tool_kinds = 4;
  repeated string mcp_transports = 5;
  repeated string media_types = 6;
  repeated string features = 7;
  uint32 max_context_tokens = 8;
  uint32 max_output_tokens = 9;
  bool supports_cancellation = 10;
  bool supports_resume = 11;
  bool supports_reasoning_summary = 12;
  bool supports_structured_output = 13;
}

message ClientLimits {
  uint32 max_concurrent_executions = 1;
  uint64 max_event_bytes = 2;
  uint64 max_tool_output_bytes = 3;
  uint64 max_resource_bytes = 4;
  google.protobuf.Duration max_execution_duration = 5;
}

message RuntimeLoad {
  uint32 active_executions = 1;
  uint32 queued_executions = 2;
  uint32 cpu_millis = 3;
  uint64 memory_bytes = 4;
  uint64 memory_limit_bytes = 5;
}

message RuntimePolicy {
  bool allow_local_process = 1;
  bool allow_network = 2;
  bool allow_mcp_stdio = 3;
  bool allow_raw_reasoning = 4;
  repeated string allowed_egress_domains = 5;
}

message ExecutionRequest {
  string execution_id = 1;
  string request_id = 2;
  string idempotency_key = 3;
  string agent_id = 4;
  string agent_revision = 5;
  string conversation_id = 6;
  string user_id = 7;
  string parent_execution_id = 8;
  ExecutionInput input = 9;
  AgentSnapshot agent = 10;
  ModelAccess model = 11;
  ToolCatalog tools = 12;
  ExecutionPolicy policy = 13;
  repeated ResourceRef resources = 14;
  google.protobuf.Duration deadline = 15;
  string resume_from_sequence = 16;
}

message ExecutionInput {
  string user_text = 1;
  repeated ContentPart content = 2;
  repeated HistoryMessage history = 3;
  string memory_context = 4;
  string client_locale = 5;
  string timezone = 6;
}

message ContentPart {
  oneof value {
    string text = 1;
    ResourceRef resource = 2;
    bytes inline_bytes = 3;
  }
  string mime_type = 4;
  string name = 5;
}

message HistoryMessage {
  string message_id = 1;
  string role = 2;
  string content = 3;
  google.protobuf.Timestamp created_at = 4;
}

message AgentSnapshot {
  string name = 1;
  string instruction = 2;
  bool mcp_enabled = 3;
  bool skill_enabled = 4;
  bool rag_enabled = 5;
  bool memory_enabled = 6;
  string configuration_revision = 7;
  google.protobuf.Struct extensions = 8;
}

message ModelAccess {
  string provider = 1;
  string adapter = 2;
  string model = 3;
  string endpoint = 4;
  string credential_grant_id = 5;
  string credential_token = 6;
  google.protobuf.Timestamp credential_expires_at = 7;
  google.protobuf.Struct parameters = 8;
}

message ToolCatalog {
  repeated ToolSpec tools = 1;
  string revision = 2;
}

message ToolSpec {
  string tool_id = 1;
  string tool_key = 2;
  string version = 3;
  string name = 4;
  string description = 5;
  ToolOwner owner = 6;
  string kind = 7;
  google.protobuf.Struct input_schema = 8;
  google.protobuf.Struct output_schema = 9;
  ToolAuthorization authorization = 10;
  ToolEndpoint endpoint = 11;
}

enum ToolOwner {
  TOOL_OWNER_UNSPECIFIED = 0;
  SERVER = 1;
  CLIENT = 2;
}

message ToolAuthorization {
  bool enabled = 1;
  bool require_confirmation = 2;
  string authorization_revision = 3;
  repeated string allowed_scopes = 4;
}

message ToolEndpoint {
  oneof target {
    ServerToolTarget server = 1;
    ClientToolTarget client = 2;
  }
}

message ServerToolTarget {
  string operation = 1;
}

message ClientToolTarget {
  string runtime = 1;
  string endpoint_ref = 2;
}

message ExecutionPolicy {
  bool allow_tool_calls = 1;
  bool allow_reasoning_output = 2;
  bool allow_network = 3;
  bool allow_local_process = 4;
  uint32 max_tool_rounds = 5;
  uint32 max_tool_output_bytes = 6;
  google.protobuf.Duration tool_timeout = 7;
  repeated string confirmed_tool_ids = 8;
}

message ResourceRef {
  string resource_id = 1;
  string uri = 2;
  string media_type = 3;
  uint64 size_bytes = 4;
  string sha256 = 5;
  string download_grant_id = 6;
  google.protobuf.Timestamp expires_at = 7;
}

message ClientEvent {
  string execution_id = 1;
  uint64 sequence = 2;
  string event_id = 3;
  oneof event {
    ExecutionAccepted accepted = 10;
    ExecutionStarted started = 11;
    TextDelta text_delta = 12;
    ReasoningDelta reasoning_delta = 13;
    ToolCallStarted tool_call_started = 14;
    ToolCallFinished tool_call_finished = 15;
    CitationAdded citation_added = 16;
    UsageReported usage = 17;
    ExecutionCompleted completed = 18;
    ExecutionFailed failed = 19;
    ExecutionCancelled cancelled = 20;
    ProgressReported progress = 21;
  }
}

message ExecutionAccepted {
  google.protobuf.Timestamp accepted_at = 1;
}

message ExecutionStarted {
  google.protobuf.Timestamp started_at = 1;
  string runtime_name = 2;
  string model = 3;
}

message TextDelta {
  string text = 1;
}

message ReasoningDelta {
  string text = 1;
  bool is_summary = 2;
}

message ToolCallStarted {
  string call_id = 1;
  string tool_id = 2;
  string tool_key = 3;
  google.protobuf.Struct arguments = 4;
  ToolOwner owner = 5;
}

message ToolCallFinished {
  string call_id = 1;
  bool success = 2;
  string output = 3;
  string output_sha256 = 4;
  uint64 output_size_bytes = 5;
  string error_code = 6;
  string error_message = 7;
  uint64 duration_ms = 8;
}

message CitationAdded {
  string citation_id = 1;
  string resource_id = 2;
  string title = 3;
  string locator = 4;
  string quote = 5;
}

message UsageReported {
  uint64 input_tokens = 1;
  uint64 output_tokens = 2;
  uint64 cache_read_tokens = 3;
  uint64 cache_write_tokens = 4;
  uint64 estimated_cost_micros = 5;
}

message ExecutionCompleted {
  string final_text = 1;
  string final_reasoning_summary = 2;
  google.protobuf.Timestamp completed_at = 3;
}

message ExecutionFailed {
  ErrorInfo error = 1;
  google.protobuf.Timestamp failed_at = 2;
}

message ExecutionCancelled {
  string reason = 1;
  google.protobuf.Timestamp cancelled_at = 2;
}

message ProgressReported {
  string phase = 1;
  uint32 percent = 2;
  string message = 3;
}

message ToolInvokeRequest {
  string execution_id = 1;
  string call_id = 2;
  string tool_id = 3;
  string tool_key = 4;
  google.protobuf.Struct arguments = 5;
  string idempotency_key = 6;
  google.protobuf.Duration deadline = 7;
}

message ToolResult {
  string execution_id = 1;
  string call_id = 2;
  bool success = 3;
  string output = 4;
  ResourceRef output_resource = 5;
  ErrorInfo error = 6;
  uint64 duration_ms = 7;
}

message ResourceRequest {
  string request_id = 1;
  ResourceRef resource = 2;
  uint64 offset = 3;
  uint64 max_bytes = 4;
}

message ResourceResponse {
  string request_id = 1;
  bytes chunk = 2;
  uint64 offset = 3;
  bool end_of_resource = 4;
  string sha256 = 5;
  ErrorInfo error = 6;
}

message CancelExecution {
  string execution_id = 1;
  CancelReason reason = 2;
  string message = 3;
  google.protobuf.Timestamp deadline = 4;
}

enum CancelReason {
  CANCEL_REASON_UNSPECIFIED = 0;
  USER_REQUESTED = 1;
  DEADLINE_EXCEEDED = 2;
  CLIENT_DRAINING = 3;
  POLICY_REVOKED = 4;
  DUPLICATE_EXECUTION = 5;
}

message ResumeRequest {
  string previous_connection_id = 1;
  repeated ExecutionCursor executions = 2;
}

message ExecutionCursor {
  string execution_id = 1;
  uint64 last_received_sequence = 2;
  uint64 last_acked_sequence = 3;
}

message ResumeResponse {
  bool accepted = 1;
  repeated ExecutionCursor executions = 2;
  repeated ErrorInfo unavailable_executions = 3;
}

message ExecutionSummary {
  string execution_id = 1;
  string state = 2;
  uint64 last_sequence = 3;
  google.protobuf.Timestamp started_at = 4;
}

message ClientAck {
  string execution_id = 1;
  uint64 sequence = 2;
}

message ServerAck {
  string execution_id = 1;
  uint64 sequence = 2;
}

message ProtocolError {
  ErrorInfo error = 1;
  bool fatal = 2;
}

message DrainNotice {
  google.protobuf.Timestamp stop_accepting_at = 1;
  string reason = 2;
}

message ErrorInfo {
  string code = 1;
  string message = 2;
  bool retryable = 3;
  string phase = 4;
  google.protobuf.Struct details = 5;
  string cause_id = 6;
}
```

## 7. 连接生命周期

### 7.1 建立连接

```text
Client                                  Server
  |                                       |
  | Connect()                             |
  | Hello(client/runtime/capabilities)   |
  |-------------------------------------->|
  |                                       |
  | Welcome(connection/lease/policy)     |
  |<--------------------------------------|
  |                                       |
  | Heartbeat(load/capabilities)          |
  |-------------------------------------->|
  |                                       |
```

规则：

1. Client 必须先发送 `Hello`，其他消息必须等待 `Welcome`。
2. Server 为本次连接生成 `connection_id` 和 `lease_id`。
3. `instance_id` 标识进程实例；`installation_id` 标识长期安装；两者不可混用。
4. 同一个 `app_id + instance_id` 出现新连接时，Server 应使旧连接进入 drain 或被拒绝。
5. Server 根据 `supported_protocol_versions` 选择一个交集版本；没有交集则返回 fatal protocol error。
6. 心跳间隔和 lease TTL 由 Server 在 `Welcome` 中下发，不写死在 Client。

### 7.2 重连和恢复

Client 断线后：

1. 保留未完成执行的本地 cursor 和最近事件缓存；
2. 重新建立 `Connect`；
3. 发送带 `previous_connection_id` 的 `ResumeRequest`；
4. 对每个 `execution_id` 上报 `last_received_sequence` 和 `last_acked_sequence`；
5. Server 返回可以恢复的执行；
6. Client 从下一个 sequence 继续发送事件，已发送事件不得重复执行工具。

如果事件缓存已经过期，Server 返回 `unavailable_executions`。此时执行结果必须标记为 `UNKNOWN_AFTER_DISCONNECT`，不能静默重新执行可能有副作用的工具。

### 7.3 Drain 和关闭

Server 发送 `DrainNotice` 后：

- Client 不再接受新的 `ExecutionRequest`；
- 已运行任务继续执行到 deadline，或由 Server 发送 `CancelExecution`；
- Client 发送 `ClientGoodbye(DRAINED)`；
- 关闭 gRPC stream。

这替代当前 `/client/kick-out` 的不可恢复语义。强制踢出仍可使用 `CancelExecution(POLICY_REVOKED)`，但必须留下原因和执行记录。

## 8. 执行生命周期

一次执行由 Server 产生唯一的 `execution_id`，状态如下：

```text
DISPATCHED
   ↓
ACCEPTED → REJECTED
   ↓
RUNNING
   ├── TOOL_RUNNING → RUNNING
   ├── WAITING_SERVER_TOOL → RUNNING
   ├── CANCEL_REQUESTED → CANCELLED
   ├── DEADLINE_EXCEEDED
   ├── FAILED
   └── COMPLETED
```

事件顺序示例：

```text
sequence=1  accepted
sequence=2  started
sequence=3  tool_call_started
sequence=4  tool_call_finished
sequence=5  text_delta
sequence=6  usage
sequence=7  completed
```

规则：

- `sequence` 在单个 `execution_id` 内从 1 单调递增；
- `event_id` 全局唯一，用于去重；
- `execution_id + idempotency_key` 唯一；
- Server 收到重复事件时返回 ACK，但不能重复落库或重复触发业务副作用；
- `completed`、`failed`、`cancelled` 只能出现一个终态；
- 终态后收到新事件属于协议错误；
- Client 只有在模型调用真正开始后才能发送 `started`；
- 工具调用必须成对发送 `tool_call_started` 和 `tool_call_finished`，除非连接在中途断开。

## 9. Server 与 Client 的职责接口

### 9.1 Server 管理的内容

Server 对外维护以下资源：

```text
Agent
Model
ModelCredential
McpServer
Skill
RagCollection
Resource
ToolPolicy
Conversation
Execution
```

Server 在 `ExecutionRequest` 中发送的是“本次执行快照”，不是数据库 Entity：

- `agent_revision`：Agent 配置版本；
- `configuration_revision`：Agent 指令和开关版本；
- `ToolCatalog.revision`：工具目录版本；
- `ResourceRef.sha256`：资源内容版本；
- `credential_grant_id`：短期凭证授权版本。

Server 之后可以审计“当时执行的是什么配置”，而不受配置后续变更影响。

### 9.2 Client 执行的内容

Client 必须：

- 校验 `ExecutionRequest` 的版本、授权和 deadline；
- 构建 system prompt、history 和当前输入；
- 根据 `ModelAccess` 调用实际模型；
- 运行模型工具循环；
- 只执行 `ToolAuthorization.enabled=true` 且符合 `ExecutionPolicy` 的工具；
- 逐事件回传文本、推理摘要、工具、引用、usage 和终态；
- 支持取消、超时、断线恢复和 graceful drain。

Client 不得：

- 修改 Server 下发的工具权限；
- 使用未在 `ToolCatalog` 中声明的工具；
- 将一份执行的凭证用于其他 `execution_id`；
- 在未收到 `ExecutionRequest` 时自行调用模型代表 Server 产生回答；
- 将长期 API Key、MCP 密钥或资源授权写入日志和持久化 session。

## 10. 工具模型

### 10.1 Server-owned Tool

Server-owned Tool 的执行请求由 Client 发送给 Server：

```text
Client Agent
  → ToolCallStarted(owner=SERVER)
  → ToolInvokeRequest(tool_id, arguments, idempotency_key)
  → Server 鉴权、租户校验、执行
  → ToolResult
  → ToolCallFinished
  → Agent 继续下一轮模型调用
```

推荐的 `operation`：

```text
rag.search
skill.read
memory.retrieve
local_function.execute
resource.resolve
```

这些操作替代当前的：

```text
/callback/rag/search
/callback/skill/content
/callback/memory/short-term
```

它们仍然可以在 Server 内部使用不同 handler，但不再把内部 handler URI 暴露给 Client。

### 10.2 Client-owned Tool

Client-owned Tool 完全在 Client 执行：

```text
MCP Streamable HTTP
MCP Stdio
本地 Python/JavaScript 函数
受控 shell
本地文件
局域网资源
```

Server 只负责下发描述、授权和策略。Client 必须将真实执行结果通过 `ToolCallFinished` 回传。

### 10.3 工具输出

小结果可以放在 `ToolResult.output`。大结果必须上传到资源存储后只传 `output_resource`：

```text
ToolResult.output_resource = {
  resource_id,
  sha256,
  size_bytes,
  uri / download_grant_id
}
```

这样可以避免工具输出、RAG 文档和模型附件挤占 gRPC 单消息限制。

## 11. 模型访问和凭证

### 11.1 模型配置

Server 发送：

- provider；
- adapter；
- model；
- endpoint；
- 参数和能力约束；
- 一次性或短期 `credential_grant_id`。

Server 默认不发送长期 API Key。

### 11.2 凭证方案

推荐优先级：

1. Client 使用自身受控的 credential provider，根据 `credential_grant_id` 向 Server 换取短期 token；
2. 如果必须在执行请求内发送凭证，只发送有 `expires_at`、scope 和 execution 绑定的短期 token；
3. 生产环境使用 TLS/mTLS，禁止明文 gRPC；
4. Client 只在内存中保存 grant，执行结束立即清理。

`credential_token` 是协议兼容字段，但实现可以选择不使用它而改用 `credential_grant_id` 的二次兑换流程。

### 11.3 模型结果与思考内容

当前协议直接传 `thinking`。v2 默认只允许：

- `ReasoningDelta.is_summary=true` 的可展示推理摘要；
- 或完全不发送 reasoning，只发送最终文本。

原始 chain-of-thought 不应作为默认跨端协议字段。是否允许 reasoning 由 `ExecutionPolicy.allow_reasoning_output` 和租户策略共同决定。

## 12. 资源和大对象协议

以下内容不应直接放在 `ExecutionRequest`：

- 大文件；
- 图片、音频、视频；
- 完整 Skill 支撑目录；
- 超长 RAG 结果；
- 大型工具输出；
- 模型附件 Base64。

使用 `ResourceRef`：

```text
resource_id
media_type
size_bytes
sha256
download_grant_id
expires_at
```

传输策略：

- 小于配置阈值的文本可以 inline；
- 二进制通过短期 HTTPS URL 或对象存储临时授权下载；
- 下载后 Client 必须校验 `sha256`；
- 资源授权绑定 tenant、agent、execution 和 resource_id；
- 资源过期或 digest 不匹配时返回 `RESOURCE_VERSION_MISMATCH`。

资源协议可以在后续增加 `ResourceService.Get`，但不应恢复把大对象塞进 JSON body 的做法。

## 13. 错误模型

### 13.1 gRPC 层错误

只用于 stream 或协议级别：

| gRPC code | 语义 |
|---|---|
| `UNAUTHENTICATED` | mTLS、token 或 lease 无效 |
| `PERMISSION_DENIED` | Client 不允许连接该 tenant/app |
| `INVALID_ARGUMENT` | protobuf 字段或 Hello 参数非法 |
| `FAILED_PRECONDITION` | Client 能力不满足协议要求 |
| `RESOURCE_EXHAUSTED` | Server/Client 超过并发或消息限制 |
| `UNAVAILABLE` | 临时连接/服务不可用，可重连 |
| `DEADLINE_EXCEEDED` | RPC deadline 到期 |
| `CANCELLED` | 调用方主动取消 |
| `INTERNAL` | 未预期的服务错误 |

### 13.2 执行层错误

`ExecutionFailed.error.code` 使用稳定枚举字符串，至少包括：

```text
PROTOCOL_VERSION_UNSUPPORTED
CAPABILITY_NOT_SUPPORTED
MODEL_CONFIG_INVALID
MODEL_AUTH_FAILED
MODEL_RATE_LIMITED
MODEL_TIMEOUT
MODEL_PROVIDER_UNAVAILABLE
TOOL_NOT_AUTHORIZED
TOOL_NOT_FOUND
TOOL_TIMEOUT
TOOL_FAILED
RESOURCE_NOT_FOUND
RESOURCE_VERSION_MISMATCH
RAG_UNAVAILABLE
SKILL_UNAVAILABLE
EXECUTION_DEADLINE_EXCEEDED
CANCELLED_BY_USER
CANCELLED_BY_SERVER
CLIENT_DRAINING
UNKNOWN_AFTER_DISCONNECT
```

每个错误必须包含：

- `code`；
- 面向调用方的脱敏 `message`；
- `retryable`；
- `phase`；
- 可选结构化 `details`；
- 可选 `cause_id`。

不能只返回 Java exception message，也不能让 Client 通过 `message` 文本判断是否重试。

## 14. 鉴权和安全

### 14.1 连接级鉴权

推荐使用：

```text
TLS + mTLS client certificate
        + tenant/app scoped bootstrap token
        + lease_id / connection_id
```

最低要求：

- TLS；
- token 不放在业务 body；
- token 支持轮换和过期；
- Server 校验 tenant、app、client_id、installation_id；
- 连接建立时绑定 identity，后续 frame 不允许改变身份。

### 14.2 执行级鉴权

Server 每个 `ExecutionRequest` 生成：

- execution_id；
- tenant/user/agent 绑定；
- agent revision；
- tool authorization revision；
- model credential grant；
- deadline；
- idempotency key。

Client 只能使用该执行上下文的授权，不应自行从公共配置加载更高权限的凭证。

### 14.3 凭证和日志

禁止记录：

- API Key；
- MCP credential values；
- credential token；
- 完整授权 URL；
- 完整用户附件；
- 完整工具参数中的敏感字段。

日志只记录 hash、长度、版本、工具 key、耗时和脱敏错误摘要。

## 15. 版本和兼容性

### 15.1 协议版本

版本分为：

```text
major.minor
```

- major 不兼容时不能复用同一 service version；
- minor 允许新增 optional field、enum value 和能力；
- Client Hello 声明支持列表；
- Server Welcome 选择实际版本；
- `protocol_version` 不依赖 package 中的字符串猜测。

### 15.2 protobuf 规则

- 新字段使用新 field number；
- 永不重用已删除 field number；
- 删除字段添加 `reserved`；
- 需要区分“未设置”和默认值的字段使用 `optional`；
- 不把已有字段挪入新的 `oneof`；
- 不在 enum 中依赖语言端 exhaustive switch；
- 对每次 proto 变更运行 breaking check；
- Java、Python、TypeScript 使用同一份 proto 生成代码。

### 15.3 能力兼容

协议版本兼容不等于执行能力兼容。例如 Client 支持 v2，但没有 MCP Stdio，则 Server 不得下发需要该能力的 Agent。

Server 必须在调度前检查：

```text
required runtime capabilities
  ⊆ client capabilities
```

失败时返回 `CAPABILITY_NOT_SUPPORTED`，不要让 Client 执行到中途才发现工具不可用。

## 16. 与当前协议的迁移方案

### 阶段 0：定义和测试

- 新增 `agent_runtime_v2.proto`；
- 生成 Java/Python/TypeScript 代码；
- 建立 golden frame 和跨语言 codec 测试；
- 不改现有 v1 运行链路。

### 阶段 1：Server 双协议

Server 同时支持：

```text
v1：现有 UnaryRequest + ServerStreamingRequest
v2：AgentRuntimeService.Connect
```

Client 注册时声明协议版本。调度器按版本选择 v1 或 v2。

### 阶段 2：实现 v2 Client Adapter

先将现有 Java Client 的内部执行器包在 v2 adapter 后面：

```text
v2 ExecutionRequest
  → Java Client 内部 ChatDispatchRequest adapter
  → 现有 Spring AI 执行器
  → v2 ClientEvent adapter
```

这样可以先验证协议，不必同时重写 Agent Runtime。

### 阶段 3：迁移工具回调

将：

```text
/callback/rag/search
/callback/skill/content
/callback/memory/short-term
```

迁移为统一 `ToolInvokeRequest/ToolResult`，内部 Server handler 可以继续复用原业务 Service。

### 阶段 4：启用 TypeScript/Python Client

使用同一份 proto：

- TypeScript 使用 `@grpc/grpc-js`；
- Python 使用 `grpcio`；
- Java 使用 grpc-java；
- Pi/Spring AI/LangGraph 只实现协议适配层，不影响 Server 协议。

### 阶段 5：下线 v1

满足以下条件后下线：

- 所有生产 Client 支持 v2；
- v2 运行至少一个完整发布周期；
- v1 连接数为零或低于明确阈值；
- v1 任务已完成迁移和审计；
- 删除旧 URI 路由和 direct-to-client channel 代码。

## 17. Server 端模块设计

建议新增或拆分以下组件：

```text
AgentRuntimeConnectionManager
  ├── ConnectionAuthenticator
  ├── ProtocolNegotiator
  ├── LeaseManager
  ├── CapabilityRegistry
  ├── ExecutionDispatcher
  ├── ExecutionEventIngestor
  ├── ExecutionResumeManager
  ├── ServerToolGateway
  ├── ResourceGrantService
  └── RuntimeAuditService
```

职责：

- `ConnectionManager`：维护一个 Client 的当前 stream 和 connection generation；
- `ProtocolNegotiator`：选择协议版本和能力；
- `LeaseManager`：处理 heartbeat、TTL 和过期；
- `ExecutionDispatcher`：根据 Agent、tenant、能力和负载选 Client；
- `EventIngestor`：校验 sequence、去重、落库和转发 Public API；
- `ResumeManager`：保存事件 cursor 和恢复窗口；
- `ServerToolGateway`：执行 RAG、Skill、memory 和 Server-owned function；
- `ResourceGrantService`：颁发绑定 execution 的短期资源授权。

## 18. Client 端模块设计

```text
RuntimeClient
  ├── ProtocolConnection
  ├── HandshakeManager
  ├── HeartbeatManager
  ├── ExecutionManager
  ├── AgentRuntimeAdapter
  ├── ModelProviderAdapter
  ├── ToolExecutor
  ├── ServerToolInvoker
  ├── ResourceFetcher
  ├── EventSequencer
  ├── ResumeBuffer
  └── SecretProvider
```

语言实现只替换：

- `AgentRuntimeAdapter`；
- `ModelProviderAdapter`；
- `ToolExecutor`；
- `SecretProvider`。

协议连接、事件序列、恢复、鉴权和心跳应尽量复用同一套生成式接口测试。

## 19. 验收标准

### 协议层

- Java、Python、TypeScript 都能从同一 `.proto` 生成代码；
- Hello/Welcome 能协商版本和能力；
- Server 不需要访问 Client 入站端口；
- gRPC stream 断线后可以 resume；
- 重复 frame 不会造成重复执行或重复落库；
- protobuf breaking check 通过。

### 执行层

- Server 可以下发模型、Agent、工具策略和资源引用；
- Client 能返回 accepted、started、text、tool、usage、completed/error；
- Server-owned Tool 和 Client-owned Tool 语义清晰；
- 可以取消正在执行的模型和工具；
- deadline 到期后不会继续产生未标记的副作用；
- Client 能报告 `UNKNOWN_AFTER_DISCONNECT`，不会盲目重复执行。

### 安全层

- TLS/mTLS 或等效安全通道启用；
- 长期 API Key 不进入执行事件；
- tool authorization 在 Server 和 Client 都校验；
- 资源和凭证授权绑定 tenant、agent、execution；
- 敏感字段不会进入普通日志。

### 运维层

- 能观察连接、lease、执行、工具、模型和资源指标；
- 能按 `trace_id`、`execution_id`、`event_id` 查询完整链路；
- 能 drain 单个 Client 或某个 runtime 版本；
- 能在不升级 Server 的情况下接入新语言 Client，只要协议和能力兼容。

## 20. 最终建议

不要继续扩展当前的 `GrpcSnailAiRequest + uri + JSON body`。它可以作为 v1 兼容协议保留，但不适合作为 Java、Python、TypeScript 多 Runtime 的长期协议。

推荐的 v2 落地顺序是：

```text
同一份 proto
  → Client 主动 Connect bidi stream
  → Hello/Welcome 能力协商
  → ExecutionRequest/ClientEvent 状态流
  → Tool Gateway 和 ResourceRef
  → ACK、sequence、resume、cancel
  → Java adapter 验证
  → Python/TypeScript/Pi Agent 接入
```

这样 Server 管理模型、资源、MCP、Skill、RAG 和 Agent 的职责不会丢失，Client 仍然可以自由选择 Java、Python、TypeScript 或具体 Agent 框架，同时协议本身不再依赖 Spring AI、Java DTO 或某一种 Agent 实现。
