/**
 * 对话门户的运行时 API 客户端（只用 API Key 鉴权，不涉及管理端令牌）。
 *
 * 数据来源：
 * - `GET    /api/v1/agents/auth/bootstrap`：Key 信息 + 可用 Agent（后端已过滤「仍已发布」）；
 * - `GET    /api/v1/agents/auth/{agentKey}/avatar`：代读上传的头像（管理端资源地址门户拿不到令牌）；
 * - `POST   /api/v1/agents/{agentKey}/chat/stream`：SSE 流式对话；
 * - `POST   /api/v1/agents/{agentKey}/chat/runs/{runId}/stop`：停止一次进行中的运行；
 * - `POST   /api/v1/agents/{agentKey}/chat/conversations/{id}/regenerate`：重新生成某一轮的回答
 *   （服务端作废旧回答并复用原提问重跑，返回同一套 SSE 事件）；
 * - `GET    /api/v1/agents/{agentKey}/chat/conversations`：会话列表（限本 Key 本 Agent）；
 * - `PATCH  /api/v1/agents/{agentKey}/chat/conversations/{id}`：改名 / 归档；
 * - `GET    /api/v1/agents/{agentKey}/chat/conversations/{id}`：会话历史；
 * - `POST   /api/v1/agents/resource/upload`：上传图片附件（JSON + base64）；
 * - `GET    /api/v1/agents/attachments/{id}`：读取附件字节。
 */

const env = import.meta.env
/** 生产同源部署时留空；开发环境走 vite 代理（/dev-api） */
export const apiBase = (env.VITE_APP_BASE_API ?? '').replace(/\/$/, '')
export const appTitle = env.VITE_APP_TITLE ?? 'AI 对话'
const clientId = env.VITE_APP_CLIENT_ID ?? ''

/** 单张附件的体积上限：与后端 v5ai.chat.attachment.max-file-size-bytes 默认值一致（5MB） */
export const MAX_ATTACHMENT_BYTES = 5 * 1024 * 1024
/** 单条消息最多携带的图片数量：与后端 max-per-message 默认值一致 */
export const MAX_ATTACHMENTS_PER_MESSAGE = 3
/** 附件允许的类型：后端按文件头校验，这里只做客户端预校验以便尽早提示 */
export const ACCEPTED_ATTACHMENT_TYPES = ['image/png', 'image/jpeg', 'image/webp']

export interface KbHitResponse {
  knowledgeBaseId: number
  documentId: number
  documentTitle?: string | null
  chunkIndex: number
  content: string
  score: number
}

/** 后端 bootstrap 返回的门户 Agent（已过滤为「已发布」）。 */
export interface PortalAgent {
  agentKey: string
  name: string
  description?: string | null
  avatarUrl?: string | null
  greeting?: string | null
  presetQuestions: string[]
  webSearchEnabled: boolean
  /** 所绑 CHAT 模型是否支持图片输入：为假时附件入口禁用（后端也会拒绝） */
  imageSupported: boolean
  /**
   * 是否展示助手回答下方的「引用（N 条）」折叠块（Agent 级配置，随发布快照下发）。
   *
   * 关掉只是不渲染：检索、RETRIEVAL 事件与引用落库都不受影响（见 ADR-0009）。
   * 后端未下发时按 true 处理（与改动前的行为一致）。
   */
  showCitations?: boolean
}

export interface PortalBootstrap {
  keyName: string
  trackingId: string
  ownerName?: string | null
  agents: PortalAgent[]
}

/** 随消息提交的附件引用。 */
export interface AttachmentRef {
  type: string
  resourceId: number
}

/**
 * 一次运行的用量汇总：SSE 的 RUN_COMPLETED 载荷与历史消息的 usage 字段**形状完全相同**，
 * 前者用于边收边展示，后者保证刷新页面后历史回答仍显示「用量 / 用时」。
 *
 * token 以模型回报的真实用量为准，服务端没回报时才回退为平台估算（与记账同一口径）；
 * durationMs 是服务端墙钟耗时。
 */
export interface RunUsage {
  promptTokens: number
  completionTokens: number
  totalTokens: number
  durationMs: number
}

/** 消息历史里返回的附件（带门户读取地址）。 */
export interface MessageAttachment {
  type: string
  resourceId: number
  accessUrl: string
}

export interface ConversationMessage {
  role: string
  content: string
  /**
   * 模型的思考过程（仅助手消息，且当时开启了持久化时有值）。
   * 「只思考就被停止」的那一轮在历史里只有它——因此它也算「这条消息有内容」。
   */
  reasoning?: string | null
  /**
   * 本条回答当时引用的切片（仅助手消息、V45 之后有值；无引用即空数组）。
   * 与实时流 RETRIEVAL 载荷同构，因此复用同一套渲染组件（见 docs/adr/0009）。
   */
  citations?: KbHitResponse[] | null
  attachments?: MessageAttachment[]
  /** 该回答的用量与用时；用户消息、或 V39 之前落库的历史消息为 null */
  usage?: RunUsage | null
  /**
   * 消息主键：前端用它作为「重新生成」的锚点（取该轮提问的 id）。
   * 雪花主键超出 JS 安全整数，服务端按字符串给出（小 id 仍可能是数字），取用前归一：
   * 见 `toMessageId`。
   */
  messageId?: string | number | null
}

/** 会话列表行。 */
export interface ConversationSummary {
  conversationId: string
  /** 会话名：首条提问自动生成，随后可能被模型改写成短标题；只有「提问为空」的会话为 null */
  name?: string | null
  agentKey: string
  userId?: number | null
  archived: boolean
  createdAt?: string | null
  updatedAt?: string | null
}

export interface ChatStreamBody {
  conversationId: string
  query: string
  /** 仅用于显式关闭联网；Agent 未开启时后端无论如何不会联网 */
  webSearch?: boolean
  attachments?: AttachmentRef[]
}

/** 重新生成某一轮的请求体。 */
export interface RegenerateBody {
  /**
   * 该轮提问的消息 id（历史消息里的 messageId，或本轮 RUN_STARTED 载荷给出的 userMessageId）。
   * **字符串**：主键是 19 位雪花号，用 number 传会被 JS 四舍五入成别的 id（服务端 404「提问不存在」）。
   */
  fromMessageId: string
  /** 仅用于显式关闭联网；Agent 未开启时后端无论如何不会联网 */
  webSearch?: boolean
}

export class ApiError extends Error {
  readonly status: number

  constructor(message: string, status: number) {
    super(message)
    this.status = status
  }
}

async function readError(response: Response): Promise<string> {
  const text = await response.text().catch(() => '')
  if (!text) return ''
  try {
    const body = JSON.parse(text) as { msg?: string; message?: string }
    return body.msg || body.message || text
  } catch {
    return text
  }
}

function baseHeaders(apiKey: string): Record<string, string> {
  return {
    Accept: 'application/json',
    ...(clientId ? { clientid: clientId } : {}),
    Authorization: `Bearer ${apiKey.trim()}`
  }
}

function jsonHeaders(apiKey: string): Record<string, string> {
  return { ...baseHeaders(apiKey), 'Content-Type': 'application/json' }
}

/** 拉取门户初始化数据；Key 无效/停用 → 401，带上后端 msg 便于提示。 */
export async function fetchBootstrap(apiKey: string): Promise<PortalBootstrap> {
  const response = await fetch(`${apiBase}/api/v1/agents/auth/bootstrap`, { headers: baseHeaders(apiKey) })
  if (!response.ok) {
    throw new ApiError((await readError(response)) || 'API Key 无效或已停用', response.status)
  }
  const body = (await response.json()) as { data?: PortalBootstrap }
  if (!body.data) {
    throw new ApiError('门户初始化返回为空', response.status)
  }
  return body.data
}

/**
 * 取受鉴权保护的资源为 blob URL：<img src> 带不了 Authorization 头，
 * 头像与消息附件都走这条路（外部 http(s) 地址直接返回）。
 */
export async function resolveResourceUrl(apiKey: string, url?: string | null): Promise<string> {
  if (!url) return ''
  if (/^https?:\/\//i.test(url)) return url
  const response = await fetch(`${apiBase}${url}`, { headers: baseHeaders(apiKey) })
  if (!response.ok) throw new ApiError('资源加载失败', response.status)
  return URL.createObjectURL(await response.blob())
}

/** 取 Agent 头像（外部地址原样返回，应用内资源用 Key 鉴权拉成 blob URL）。 */
export async function resolveAvatarUrl(apiKey: string, avatarUrl?: string | null): Promise<string> {
  return resolveResourceUrl(apiKey, avatarUrl)
}

/** 会话列表：archived=true 查已归档，默认查进行中的。 */
export async function fetchConversations(
  apiKey: string,
  agentKey: string,
  archived = false
): Promise<ConversationSummary[]> {
  const query = archived ? '?archived=true' : ''
  const response = await fetch(
    `${apiBase}/api/v1/agents/${encodeURIComponent(agentKey)}/chat/conversations${query}`,
    { headers: baseHeaders(apiKey) }
  )
  if (!response.ok) {
    throw new ApiError((await readError(response)) || '会话列表加载失败', response.status)
  }
  const body = (await response.json()) as { data?: ConversationSummary[] }
  return body.data ?? []
}

/** 改名与归档共用一个端点：字段为 undefined 表示不改该项。 */
export async function updateConversation(
  apiKey: string,
  agentKey: string,
  conversationId: string,
  patch: { name?: string; archived?: boolean }
): Promise<void> {
  const response = await fetch(
    `${apiBase}/api/v1/agents/${encodeURIComponent(agentKey)}/chat/conversations/${encodeURIComponent(conversationId)}`,
    { method: 'PATCH', headers: jsonHeaders(apiKey), body: JSON.stringify(patch) }
  )
  if (!response.ok) {
    throw new ApiError((await readError(response)) || '会话更新失败', response.status)
  }
}

/** 拉取某个会话的历史消息（含附件）。 */
export async function fetchConversationMessages(
  apiKey: string,
  agentKey: string,
  conversationId: string
): Promise<ConversationMessage[]> {
  const response = await fetch(
    `${apiBase}/api/v1/agents/${encodeURIComponent(agentKey)}/chat/conversations/${encodeURIComponent(conversationId)}`,
    { headers: baseHeaders(apiKey) }
  )
  if (!response.ok) {
    throw new ApiError((await readError(response)) || '会话历史加载失败', response.status)
  }
  const body = (await response.json()) as { data?: ConversationMessage[] }
  return body.data ?? []
}

/**
 * 上传图片附件：门户只持有 API Key，用不了管理端的 multipart 入口，
 * 因此按后端约定走 JSON + base64。
 */
export async function uploadAttachment(apiKey: string, file: File): Promise<AttachmentRef> {
  const bytes = new Uint8Array(await file.arrayBuffer())
  const response = await fetch(`${apiBase}/api/v1/agents/resource/upload`, {
    method: 'POST',
    headers: jsonHeaders(apiKey),
    body: JSON.stringify({
      originalName: file.name,
      fileSize: bytes.length,
      content: toBase64(bytes)
    })
  })
  if (!response.ok) {
    throw new ApiError((await readError(response)) || '附件上传失败', response.status)
  }
  const body = (await response.json()) as { data?: { id: number } }
  if (!body.data?.id) {
    throw new ApiError('附件上传返回为空', response.status)
  }
  return { type: 'IMAGE', resourceId: body.data.id }
}

/** 停止一次进行中的运行：幂等（已结束的运行同样返回 200）。 */
export async function stopRun(apiKey: string, agentKey: string, runId: string): Promise<void> {
  const response = await fetch(
    `${apiBase}/api/v1/agents/${encodeURIComponent(agentKey)}/chat/runs/${encodeURIComponent(runId)}/stop`,
    { method: 'POST', headers: baseHeaders(apiKey) }
  )
  if (!response.ok && response.status !== 404) {
    throw new ApiError((await readError(response)) || '停止失败', response.status)
  }
}

/**
 * 流式对话：SSE 事件逐条回调，并回报本次运行的 runId（取自 SSE 的 `id:` 字段，
 * 停止对话要按它调用 stop 端点）。`signal` 用于主动中断/看门狗超时。
 */
export async function streamChat(
  agentKey: string,
  apiKey: string,
  body: ChatStreamBody,
  onEvent: (event: string, data: string, runId: string) => void,
  signal?: AbortSignal
): Promise<void> {
  const url = `${apiBase}/api/v1/agents/${encodeURIComponent(agentKey)}/chat/stream`
  await readSse(await postSse(url, apiKey, body, signal), onEvent)
}

/**
 * 重新生成某一轮的回答：服务端**作废该轮提问之后的消息**并复用那条提问重跑，
 * 因此历史里不会再多出「作废的回答 + 重发的提问」。返回与 streamChat 相同的事件流。
 */
export async function regenerateChat(
  agentKey: string,
  apiKey: string,
  conversationId: string,
  body: RegenerateBody,
  onEvent: (event: string, data: string, runId: string) => void,
  signal?: AbortSignal
): Promise<void> {
  const url = `${apiBase}/api/v1/agents/${encodeURIComponent(agentKey)}/chat/conversations/${encodeURIComponent(conversationId)}/regenerate`
  await readSse(await postSse(url, apiKey, body, signal), onEvent)
}

/** 发一个 SSE 请求并做统一的非 2xx 处理（两个流式端点共用）。 */
async function postSse(url: string, apiKey: string, body: unknown, signal?: AbortSignal): Promise<Response> {
  const response = await fetch(url, {
    method: 'POST',
    headers: jsonHeaders(apiKey),
    body: JSON.stringify(body),
    signal
  })
  if (!response.ok || !response.body) {
    throw new ApiError((await readError(response)) || '调用失败', response.status)
  }
  return response
}

/** SSE 读取循环：按空行切事件块，取其中的 event / data / id 三行。 */
async function readSse(
  response: Response,
  onEvent: (event: string, data: string, runId: string) => void
): Promise<void> {
  const reader = response.body!.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let runId = ''
  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    const chunks = buffer.split('\n\n')
    buffer = chunks.pop() ?? ''
    for (const chunk of chunks) {
      const event = chunk.match(/^event:\s*(.*)$/m)?.[1] ?? 'message'
      const data = chunk.match(/^data:\s*(.*)$/m)?.[1] ?? ''
      runId = chunk.match(/^id:\s*(.*)$/m)?.[1] ?? runId
      onEvent(event, data, runId)
    }
  }
}

/** Uint8Array → base64：分块处理，避免大文件下 String.fromCharCode 爆栈。 */
function toBase64(bytes: Uint8Array): string {
  let binary = ''
  const chunkSize = 0x8000
  for (let offset = 0; offset < bytes.length; offset += chunkSize) {
    binary += String.fromCharCode(...bytes.subarray(offset, offset + chunkSize))
  }
  return btoa(binary)
}

/** SSE data 是 RuntimeEvent JSON：文本/思考增量取其中的文本字段。 */
export function parseDelta(data: string): string {
  if (!data) return ''
  try {
    const body = JSON.parse(data) as { payload?: unknown; answer?: unknown; text?: unknown }
    if (typeof body.payload === 'string') return body.payload
    if (typeof body.answer === 'string') return body.answer
    if (typeof body.text === 'string') return body.text
    return ''
  } catch {
    return data
  }
}

/** RUN_FAILED 的载荷是错误信息字符串。 */
export function parseRunFailed(data: string): string {
  if (!data) return '运行失败'
  try {
    return (JSON.parse(data) as { payload?: string }).payload || '运行失败'
  } catch {
    return data
  }
}
