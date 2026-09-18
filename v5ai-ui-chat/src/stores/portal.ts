import { computed, reactive, ref } from 'vue'
import {
  fetchBootstrap,
  fetchConversations,
  resolveAvatarUrl,
  updateConversation,
  type ConversationSummary,
  type PortalAgent,
  type PortalBootstrap
} from '../api/client'

/**
 * 门户状态（不上 Pinia：门户只有 Key、Agent、会话列表三块状态，模块级 ref 足够）。
 *
 * 本地存储只剩「凭据 + 上次使用的 Agent」：
 * - `v5ai_chat_api_key`：API Key（门户唯一的"登录态"）；
 * - `v5ai_chat_active_agent`：上次使用的 Agent。
 *
 * 会话列表改为**服务端**列表（会话归属 API Key，可改名、可归档）——
 * 旧的 `v5ai_chat_sessions` 本地列表已废弃，登录时直接清掉，不再迁移。
 */

const KEY_STORAGE = 'v5ai_chat_api_key'
const ACTIVE_AGENT_STORAGE = 'v5ai_chat_active_agent'
/** 已废弃的本地会话列表键：会话改由服务端承载，登录时清理 */
const LEGACY_SESSION_STORAGE = 'v5ai_chat_sessions'

export const apiKey = ref(localStorage.getItem(KEY_STORAGE) ?? '')
export const bootstrapData = ref<PortalBootstrap | null>(null)
export const activeAgentKey = ref(localStorage.getItem(ACTIVE_AGENT_STORAGE) ?? '')
/** 当前 Agent 的会话列表（服务端返回，按最近活跃倒序） */
export const sessionList = ref<ConversationSummary[]>([])
/** 会话列表是否显示已归档会话 */
export const showArchived = ref(false)

/** agentKey → 可直接用于 <img> 的地址（blob: 或外部 URL）。 */
export const agentAvatars = reactive<Record<string, string>>({})

export const agents = computed<PortalAgent[]>(() => bootstrapData.value?.agents ?? [])

export const activeAgent = computed<PortalAgent | null>(
  () => agents.value.find((a) => a.agentKey === activeAgentKey.value) ?? agents.value[0] ?? null
)

/** 会话在列表里的标题：自定名 → 首条提问预览 → 占位。 */
export function sessionTitle(session: ConversationSummary): string {
  const name = session.name?.trim()
  if (name) return name
  const preview = session.preview?.trim()
  return preview || '新会话'
}

export function setApiKey(key: string) {
  apiKey.value = key.trim()
  localStorage.setItem(KEY_STORAGE, apiKey.value)
}

/** 退出/Key 失效：清掉本地凭据与内存缓存（会话在服务端，重新登录后仍在）。 */
export function clearApiKey() {
  apiKey.value = ''
  bootstrapData.value = null
  sessionList.value = []
  localStorage.removeItem(KEY_STORAGE)
  for (const url of Object.values(agentAvatars)) {
    if (url.startsWith('blob:')) URL.revokeObjectURL(url)
  }
  for (const key of Object.keys(agentAvatars)) delete agentAvatars[key]
}

/** 校验并加载门户数据（Key 无效会抛出 ApiError）。 */
export async function loadBootstrap(): Promise<PortalBootstrap> {
  // 本地会话列表已废弃：一次性清掉，避免用户看到两套会话
  localStorage.removeItem(LEGACY_SESSION_STORAGE)
  const data = await fetchBootstrap(apiKey.value)
  bootstrapData.value = data
  if (!data.agents.some((a) => a.agentKey === activeAgentKey.value)) {
    setActiveAgent(data.agents[0]?.agentKey ?? '')
  }
  return data
}

export function setActiveAgent(agentKey: string) {
  activeAgentKey.value = agentKey
  localStorage.setItem(ACTIVE_AGENT_STORAGE, agentKey)
}

/** 预取 Agent 头像（打开对话页时调用一次；已在缓存里的不重复请求）。 */
export async function ensureAgentAvatars() {
  const pending = agents.value.filter((a) => a.avatarUrl && !agentAvatars[a.agentKey])
  await Promise.all(
    pending.map(async (agent) => {
      try {
        agentAvatars[agent.agentKey] = await resolveAvatarUrl(apiKey.value, agent.avatarUrl)
      } catch {
        // 单个头像失败不影响使用：组件回退到首字母/图标
      }
    })
  )
}

/** 拉取当前 Agent 的会话列表（服务端已是「限本 Key 本 Agent」）。 */
export async function loadSessions(agentKey: string): Promise<void> {
  if (!agentKey || !apiKey.value) {
    sessionList.value = []
    return
  }
  sessionList.value = await fetchConversations(apiKey.value, agentKey, showArchived.value)
}

export async function renameSession(agentKey: string, conversationId: string, name: string): Promise<void> {
  await updateConversation(apiKey.value, agentKey, conversationId, { name })
  await loadSessions(agentKey)
}

export async function setSessionArchived(
  agentKey: string,
  conversationId: string,
  archived: boolean
): Promise<void> {
  await updateConversation(apiKey.value, agentKey, conversationId, { archived })
  await loadSessions(agentKey)
}

/** 切换「显示已归档」并重新拉取列表。 */
export async function toggleArchivedView(agentKey: string, archived: boolean): Promise<void> {
  showArchived.value = archived
  await loadSessions(agentKey)
}

export function newSessionId(): string {
  return crypto.randomUUID()
}
