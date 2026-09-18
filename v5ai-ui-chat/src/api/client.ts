/**
 * 对话门户的运行时 API 客户端（只用 API Key 鉴权，不涉及管理端令牌）。
 *
 * 数据来源：
 * - `GET  /api/v1/agents/auth/bootstrap`：Key 信息 + 可用 Agent（后端已过滤「仍已发布」）；
 * - `GET  /api/v1/agents/auth/{agentKey}/avatar`：代读上传的头像（管理端资源地址门户拿不到令牌）；
 * - `POST /api/v1/agents/{agentKey}/chat/stream`：SSE 流式对话；
 * - `GET  /api/v1/agents/{agentKey}/chat/conversations/{conversationId}`：会话历史。
 */

const env = import.meta.env
/** 生产同源部署时留空；开发环境走 vite 代理（/dev-api） */
export const apiBase = (env.VITE_APP_BASE_API ?? '').replace(/\/$/, '')
export const appTitle = env.VITE_APP_TITLE ?? 'AI 对话'
const clientId = env.VITE_APP_CLIENT_ID ?? ''

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
}

export interface PortalBootstrap {
  keyName: string
  trackingId: string
  ownerName?: string | null
  agents: PortalAgent[]
}

export interface ConversationMessage {
  role: string
  content: string
}

export interface ChatStreamBody {
  conversationId: string
  query: string
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
 * 取 Agent 头像：外部 http(s) 直接返回；应用内路径用 Key 鉴权拉成 blob URL
 * （统一后端资源读接口都要求令牌，<img src> 无法带 Authorization 头）。
 */
export async function resolveAvatarUrl(apiKey: string, avatarUrl?: string | null): Promise<string> {
  if (!avatarUrl) return ''
  if (/^https?:\/\//i.test(avatarUrl)) return avatarUrl
  const response = await fetch(`${apiBase}${avatarUrl}`, { headers: baseHeaders(apiKey) })
  if (!response.ok) throw new ApiError('头像加载失败', response.status)
  return URL.createObjectURL(await response.blob())
}

/** 拉取某个会话的历史消息（会话 id 由前端生成，随对话一起提交）。 */
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
 * 流式对话：SSE 事件逐条回调。`signal` 用于看门狗超时主动中断，避免发送按钮永久 loading。
 */
export async function streamChat(
  agentKey: string,
  apiKey: string,
  body: ChatStreamBody,
  onEvent: (event: string, data: string) => void,
  signal?: AbortSignal
): Promise<void> {
  const response = await fetch(`${apiBase}/api/v1/agents/${encodeURIComponent(agentKey)}/chat/stream`, {
    method: 'POST',
    headers: { ...baseHeaders(apiKey), 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
    signal
  })
  if (!response.ok || !response.body) {
    throw new ApiError((await readError(response)) || '调用失败', response.status)
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    const chunks = buffer.split('\n\n')
    buffer = chunks.pop() ?? ''
    for (const chunk of chunks) {
      const event = chunk.match(/^event:\s*(.*)$/m)?.[1] ?? 'message'
      const data = chunk.match(/^data:\s*(.*)$/m)?.[1] ?? ''
      onEvent(event, data)
    }
  }
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
