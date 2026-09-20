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
/**
 * 会话列表是否正在整表重拉。
 *
 * 只有「列表要整个换掉」的场景才点这盏灯：首次进入、换 Agent、切未归档 / 已归档。
 * 这几处的共同点是旧内容不该再露脸 —— 分段已经指着新视图了，旧视图那批会话留在原位
 * 只会让人以为切过去还长这样。改名 / 归档后也要重拉，但那是原地刷新，列表没换，
 * 点灯反倒会把用户刚改完的那一行整片盖住，所以那两处走 loadSessions 的 quiet 分支。
 */
export const sessionsLoading = ref(false)

/** agentKey → 可直接用于 <img> 的地址（blob: 或外部 URL）。 */
export const agentAvatars = reactive<Record<string, string>>({})

export const agents = computed<PortalAgent[]>(() => bootstrapData.value?.agents ?? [])

export const activeAgent = computed<PortalAgent | null>(
  () => agents.value.find((a) => a.agentKey === activeAgentKey.value) ?? agents.value[0] ?? null
)

/**
 * 会话在列表里的标题。
 *
 * 名字在服务端就已经生成好了（创建时取首条提问，首轮结束后可能被模型改写成短标题），
 * 客户端不再需要「首条提问预览」兜底；只有提问为空的会话（如纯图片提问）才会是 null。
 */
export function sessionTitle(session: ConversationSummary): string {
  return session.name?.trim() || '新会话'
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

/** 结果归属：只认最后一次发出的请求（见 loadSessions 里的说明） */
let sessionsRequestSeq = 0

/** 在途的「整表重拉」笔数：归零才熄灯（见 loadSessions 的 finally） */
let sessionsLoadingPending = 0

/**
 * 拉取当前 Agent 的会话列表（服务端已是「限本 Key 本 Agent」）。
 *
 * 默认点亮 sessionsLoading —— 调用它的都是「列表要整个换掉」的场景。改名 / 归档后的重拉
 * 传 { quiet: true }：那是原地刷新，不该把列表换成骨架屏（见 sessionsLoading 的说明）。
 */
export async function loadSessions(agentKey: string, options: { quiet?: boolean } = {}): Promise<void> {
  if (!agentKey || !apiKey.value) {
    sessionList.value = []
    sessionsLoadingPending = 0
    sessionsLoading.value = false
    return
  }
  const quiet = options.quiet === true
  const seq = ++sessionsRequestSeq
  if (!quiet) {
    sessionsLoadingPending++
    sessionsLoading.value = true
  }
  try {
    const list = await fetchConversations(apiKey.value, agentKey, showArchived.value)
    // 只认最后一次请求的结果：连点两段时，先发的那次可能后到，
    // 落进去列表就和分段上选着的不是同一个视图了
    if (seq === sessionsRequestSeq) sessionList.value = list
  } finally {
    if (!quiet) {
      // 计数归零才熄灯。不能一收尾就熄：先发后到的那次收尾时，后发的可能还在路上，
      // 此刻熄灯会让列表先亮回旧内容、再跳成新的。
      // 这里不复用 seq 判归属，是因为改名 / 归档那次会推进 seq，
      // 用它判会让这次的点灯永远熄不掉。
      sessionsLoadingPending--
      if (sessionsLoadingPending === 0) sessionsLoading.value = false
    }
  }
}

export async function renameSession(agentKey: string, conversationId: string, name: string): Promise<void> {
  await updateConversation(apiKey.value, agentKey, conversationId, { name })
  await loadSessions(agentKey, { quiet: true })
}

export async function setSessionArchived(
  agentKey: string,
  conversationId: string,
  archived: boolean
): Promise<void> {
  await updateConversation(apiKey.value, agentKey, conversationId, { archived })
  await loadSessions(agentKey, { quiet: true })
}

/** 切换「显示已归档」并重新拉取列表。 */
export async function toggleArchivedView(agentKey: string, archived: boolean): Promise<void> {
  showArchived.value = archived
  await loadSessions(agentKey)
}

export function newSessionId(): string {
  return crypto.randomUUID()
}
