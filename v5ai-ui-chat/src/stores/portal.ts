import { computed, reactive, ref } from 'vue'
import { fetchBootstrap, resolveAvatarUrl, type PortalAgent, type PortalBootstrap } from '../api/client'

/**
 * 门户会话状态（不上 Pinia：门户只有 Key、Agent、会话列表三块状态，模块级 ref 足够）。
 *
 * 本地存储约定：
 * - `v5ai_chat_api_key`：API Key（门户唯一的"登录态"）；
 * - `v5ai_chat_active_agent`：上次使用的 Agent；
 * - `v5ai_chat_sessions`：会话列表（会话 id 由前端生成并随每次提问提交，后端按它落库）。
 */

const KEY_STORAGE = 'v5ai_chat_api_key'
const SESSION_STORAGE = 'v5ai_chat_sessions'
const ACTIVE_AGENT_STORAGE = 'v5ai_chat_active_agent'
/** 本地会话列表上限：只保留最近若干条，避免 localStorage 无限增长 */
const MAX_SESSIONS = 100

export interface PortalSession {
  id: string
  agentKey: string
  title: string
  updatedAt: number
}

export const apiKey = ref(localStorage.getItem(KEY_STORAGE) ?? '')
export const bootstrapData = ref<PortalBootstrap | null>(null)
export const activeAgentKey = ref(localStorage.getItem(ACTIVE_AGENT_STORAGE) ?? '')
export const sessions = ref<PortalSession[]>(loadSessions())

/** agentKey → 可直接用于 <img> 的地址（blob: 或外部 URL）。 */
export const agentAvatars = reactive<Record<string, string>>({})

export const agents = computed<PortalAgent[]>(() => bootstrapData.value?.agents ?? [])

export const activeAgent = computed<PortalAgent | null>(
  () => agents.value.find((a) => a.agentKey === activeAgentKey.value) ?? agents.value[0] ?? null
)

function loadSessions(): PortalSession[] {
  try {
    const raw = JSON.parse(localStorage.getItem(SESSION_STORAGE) ?? '[]') as PortalSession[]
    return Array.isArray(raw) ? raw.filter((s) => s && typeof s.id === 'string' && typeof s.agentKey === 'string') : []
  } catch {
    return []
  }
}

function persistSessions() {
  sessions.value = [...sessions.value].sort((a, b) => b.updatedAt - a.updatedAt).slice(0, MAX_SESSIONS)
  localStorage.setItem(SESSION_STORAGE, JSON.stringify(sessions.value))
}

export function setApiKey(key: string) {
  apiKey.value = key.trim()
  localStorage.setItem(KEY_STORAGE, apiKey.value)
}

/** 退出/Key 失效：清掉本地凭据与内存缓存（会话列表保留，重新登录后仍在）。 */
export function clearApiKey() {
  apiKey.value = ''
  bootstrapData.value = null
  localStorage.removeItem(KEY_STORAGE)
  for (const url of Object.values(agentAvatars)) {
    if (url.startsWith('blob:')) URL.revokeObjectURL(url)
  }
  for (const key of Object.keys(agentAvatars)) delete agentAvatars[key]
}

/** 校验并加载门户数据（Key 无效会抛出 ApiError）。 */
export async function loadBootstrap(): Promise<PortalBootstrap> {
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

export function newSessionId(): string {
  return crypto.randomUUID()
}

export function upsertSession(session: PortalSession) {
  const index = sessions.value.findIndex((s) => s.id === session.id)
  if (index >= 0) {
    sessions.value[index] = { ...sessions.value[index], ...session }
  } else {
    sessions.value.push(session)
  }
  persistSessions()
}

export function removeSession(id: string) {
  sessions.value = sessions.value.filter((s) => s.id !== id)
  persistSessions()
}

/** 某 Agent 下的会话（按最近更新倒序）。 */
export function sessionsOf(agentKey: string): PortalSession[] {
  return sessions.value.filter((s) => s.agentKey === agentKey)
}
