<script setup lang="ts">
import { computed, h, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { NAlert, NButton, NDrawer, NDrawerContent, NIcon, NSpin, useMessage } from 'naive-ui'
import { ArrowDown, Menu, MessageSquarePlus, PanelLeftClose, PanelLeftOpen, RefreshCw } from 'lucide-vue-next'
import AgentAvatar from '../components/AgentAvatar.vue'
import AgentIntro from '../components/AgentIntro.vue'
import AgentSwitcher from '../components/AgentSwitcher.vue'
import ChatMessageBody from '../components/ChatMessageBody.vue'
import Composer from '../components/Composer.vue'
import MessageActions from '../components/MessageActions.vue'
import SidebarPanel from '../components/SidebarPanel.vue'
import UserMenu from '../components/UserMenu.vue'
import {
  ApiError,
  appTitle,
  fetchConversationMessages,
  parseDelta,
  parseRunFailed,
  regenerateChat,
  resolveResourceUrl,
  stopRun,
  streamChat,
  uploadAttachment,
  type AttachmentRef,
  type ConversationSummary
} from '../api/client'
import {
  mergeHits,
  normalizeUsage,
  parseHits,
  parseUsage,
  parseUserMessageId,
  toMessageId,
  type ChatMsg
} from '../utils/chatStream'
import {
  activeAgent,
  agents,
  apiKey,
  bootstrapData,
  clearApiKey,
  ensureAgentAvatars,
  loadBootstrap,
  loadSessions,
  newSessionId,
  renameSession,
  sessionList,
  sessionsLoading,
  sessionTitle,
  setActiveAgent,
  setSessionArchived,
  showArchived,
  toggleArchivedView
} from '../stores/portal'

/**
 * 对话页：两栏横排 —— 通顶的左栏（服务端会话列表，可收起成窄栏）
 * 与右侧内容列（顶栏：品牌 / 当前会话名 / Agent 切换 / 用户菜单，下面是主区对话）。
 *
 * 会话 id 由前端生成（crypto.randomUUID）并随每次提问提交，但**列表、改名、归档都在服务端**
 * （会话语义归属 API Key，见 docs/adr/0006-api-key-scoped-conversations.md）。
 * 图片附件先上传拿资源 id，再随消息提交；停止按钮同时断流并通知服务端取消本次运行。
 */
const router = useRouter()
const message = useMessage()

/** 流静默超时：超时主动中断，避免发送按钮永久 loading */
const STREAM_SILENCE_MS = 60_000
/** 「贴着底部」的容差：自动跟随滚动与「回到最新」按钮共用同一个阈值 */
const NEAR_BOTTOM_PX = 60

/** 侧栏宽度：可拖拽调整，夹在 CSS 令牌给的范围里，并记住上次的值 */
const SIDEBAR_STORAGE = 'v5ai_chat_sidebar_width'
const SIDEBAR_DEFAULT = 260
const SIDEBAR_MIN = 200
const SIDEBAR_MAX = 420

function readSidebarWidth(): number {
  const raw = Number(localStorage.getItem(SIDEBAR_STORAGE))
  if (!Number.isFinite(raw) || raw <= 0) return SIDEBAR_DEFAULT
  return Math.min(SIDEBAR_MAX, Math.max(SIDEBAR_MIN, Math.round(raw)))
}

const sidebarWidth = ref(readSidebarWidth())
const resizing = ref(false)

function applySidebarWidth(px: number) {
  sidebarWidth.value = Math.min(SIDEBAR_MAX, Math.max(SIDEBAR_MIN, Math.round(px)))
}

function persistSidebarWidth() {
  localStorage.setItem(SIDEBAR_STORAGE, String(sidebarWidth.value))
}

/**
 * 拖拽调宽：从按下那一刻起在 window 上听 pointermove，指针移出把手（甚至移出窗口）也不断链；
 * 用 pointer 事件而不是 mouse，触屏/触控板同样可用。
 */
function startResize(event: PointerEvent) {
  if (event.button !== 0) return
  event.preventDefault()
  const startX = event.clientX
  const startWidth = sidebarWidth.value
  resizing.value = true
  const onMove = (move: PointerEvent) => applySidebarWidth(startWidth + (move.clientX - startX))
  const onUp = () => {
    resizing.value = false
    window.removeEventListener('pointermove', onMove)
    window.removeEventListener('pointerup', onUp)
    window.removeEventListener('pointercancel', onUp)
    persistSidebarWidth()
  }
  window.addEventListener('pointermove', onMove)
  window.addEventListener('pointerup', onUp)
  window.addEventListener('pointercancel', onUp)
}

/** 键盘可达：把手聚焦后 ←/→ 微调（Shift 加速），Home/End 到两端，双击复位 */
function onResizerKeydown(event: KeyboardEvent) {
  const step = event.shiftKey ? 40 : 16
  if (event.key === 'ArrowLeft') applySidebarWidth(sidebarWidth.value - step)
  else if (event.key === 'ArrowRight') applySidebarWidth(sidebarWidth.value + step)
  else if (event.key === 'Home') applySidebarWidth(SIDEBAR_MIN)
  else if (event.key === 'End') applySidebarWidth(SIDEBAR_MAX)
  else return
  event.preventDefault()
  persistSidebarWidth()
}

function resetSidebarWidth() {
  applySidebarWidth(SIDEBAR_DEFAULT)
  persistSidebarWidth()
}

/**
 * 侧栏收起：收成 --portal-rail-width 那一档窄栏（展开按钮、新建会话、会话列表三个入口），
 * 展开时回到上面那个可拖拽的宽度。
 *
 * 收起宽度不写在脚本里 —— 它是 CSS 令牌，脚本只切布尔值，免得同一个 56px 在两边各存一份。
 * 收起状态与展开宽度分开持久化：拖到 320px 再收起、再展开，宽度还得是 320px。
 *
 * 窄屏（≤768px）不在这里分支：那时 .sidebar 整个 display:none（见样式末尾的媒体查询），
 * 入口换成了顶栏的汉堡按钮，直接开抽屉，不再经过这个开关。
 */
const SIDEBAR_COLLAPSED_STORAGE = 'v5ai_chat_sidebar_collapsed'

const sidebarCollapsed = ref(localStorage.getItem(SIDEBAR_COLLAPSED_STORAGE) === '1')

function toggleSidebar() {
  sidebarCollapsed.value = !sidebarCollapsed.value
  localStorage.setItem(SIDEBAR_COLLAPSED_STORAGE, sidebarCollapsed.value ? '1' : '0')
}

const bootstrapping = ref(true)
const loading = ref(false)
const messages = ref<ChatMsg[]>([])
const conversationId = ref('')
/** 会话抽屉是否展开：窄屏的主入口，也是桌面端侧栏收起后「会话列表」按钮的落点 */
const drawerOpen = ref(false)
/** 切换会话时正在拉历史：这期间只显示加载态，不露出「预设问题」的欢迎页（否则会先误导一下再跳变） */
const historyLoading = ref(false)
/** 在途历史请求的序号：切走 / 新建会话 / 直接提问后，迟到的响应一律丢弃，不覆盖当前视图 */
let historyToken = 0
/** 本次提问是否使用联网搜索：默认取 Agent 配置；Agent 未开启时开关禁用 */
const webSearch = ref(false)
/** 当前正在跑的运行 ID（取自 SSE 的 id: 字段），停止时按它通知服务端 */
const currentRunId = ref('')
const listRef = ref<HTMLElement | null>(null)
/** 滚动区是否贴着底部：不贴底时露出「回到最新」的圆形按钮 */
const atBottom = ref(true)
let controller: AbortController | null = null
/** 上传阶段（流还没开始）被点了停止：send() 拿到上传结果后直接放弃这次发送 */
let sendCancelled = false

const currentAgentKey = computed(() => activeAgent.value?.agentKey ?? '')
const imageSupported = computed(() => activeAgent.value?.imageSupported ?? false)

/**
 * 顶栏里的当前会话名。
 *
 * 收起侧栏后、以及窄屏（侧栏整个收进抽屉）时，这是唯一能看出「我在哪个会话」的地方——
 * 没有它，收起侧栏就等于把会话上下文也一起藏了起来。
 *
 * 名字由服务端生成（sessionTitle 只是给未命名的会话兜底），所以新会话在首条消息发出、
 * 服务端建好会话之前是空串，顶栏就先不显示这一段。
 */
const currentSessionTitle = computed(() => {
  const session = sessionList.value.find((item) => item.conversationId === conversationId.value)
  return session ? sessionTitle(session) : ''
})

function syncWebSearchDefault() {
  webSearch.value = activeAgent.value?.webSearchEnabled ?? false
}

async function refreshSessions() {
  if (!currentAgentKey.value) return
  try {
    await loadSessions(currentAgentKey.value)
  } catch (e) {
    if (e instanceof ApiError && e.status === 401) {
      await handleAuthFailure(e)
    }
  }
}

onMounted(async () => {
  try {
    if (!bootstrapData.value) {
      await loadBootstrap()
    }
    await ensureAgentAvatars()
    syncWebSearchDefault()
    await refreshSessions()
  } catch (e) {
    await handleAuthFailure(e)
  } finally {
    bootstrapping.value = false
  }
})

onBeforeUnmount(() => {
  controller?.abort()
})

watch(
  () => activeAgent.value?.agentKey,
  async () => {
    // 会话与 Agent 绑定：切换 Agent 即开一个新会话（同时作废在途的历史请求）
    newSession()
    syncWebSearchDefault()
    await refreshSessions()
  }
)

/** Key 失效：清掉本地 Key 回 Key 页（403 属于「Key 有效但该 Agent 被解绑」，只提示不退出）。 */
async function handleAuthFailure(e: unknown) {
  const status = e instanceof ApiError ? e.status : 0
  if (status === 403) {
    message.error(e instanceof Error ? e.message : '当前 Key 无权访问该 Agent')
    return
  }
  message.error(e instanceof Error ? e.message : 'API Key 已失效，请重新输入')
  clearApiKey()
  await router.push({ name: 'key' })
}

async function scrollToBottom(force: boolean) {
  await nextTick()
  const el = listRef.value
  if (!el) return
  if (!force && !isNearBottom(el)) return
  el.scrollTop = el.scrollHeight
  atBottom.value = true
}

function isNearBottom(el: HTMLElement): boolean {
  return el.scrollHeight - el.scrollTop - el.clientHeight < NEAR_BOTTOM_PX
}

/** 滚动只更新「是否贴底」这一个状态：跟不跟随由 scrollToBottom 决定，不抢用户的滚动 */
function onScroll() {
  const el = listRef.value
  if (el) atBottom.value = isNearBottom(el)
}

/** 点「回到最新」：平滑滚到底，并立刻收起按钮（平滑滚动途中不必让它一直挂着） */
function scrollToLatest() {
  const el = listRef.value
  if (!el) return
  atBottom.value = true
  el.scrollTo({ top: el.scrollHeight, behavior: 'smooth' })
}

/** 上传本次选中的图片：全部成功才发消息，避免「消息发出去了图没带上」。 */
async function uploadAll(files: File[]): Promise<AttachmentRef[] | null> {
  if (files.length === 0) return []
  try {
    return await Promise.all(files.map((file) => uploadAttachment(apiKey.value, file)))
  } catch (e) {
    if (e instanceof ApiError && (e.status === 401 || e.status === 403)) {
      await handleAuthFailure(e)
    } else {
      message.error(e instanceof Error ? e.message : '附件上传失败')
    }
    return null
  }
}

async function send(question: string, files: File[] = []) {
  const agent = activeAgent.value
  if (!agent || loading.value || (!question.trim() && files.length === 0)) return
  if (!conversationId.value) {
    conversationId.value = newSessionId()
  }

  // 立刻进入「进行中」：右侧按钮当场从发送变成停止，同时挡住上传期间重复发送
  loading.value = true
  sendCancelled = false
  currentRunId.value = ''
  const attachments = await uploadAll(files)
  if (attachments === null || sendCancelled) {
    // 上传失败，或这段上传期间用户点了停止：整体放弃这次发送（不留半截状态）
    sendCancelled = false
    loading.value = false
    return
  }

  // 用户消息也要是响应式代理：本轮提问的消息 id 由 RUN_STARTED 载荷回填（重新生成的锚点）
  const userMessage = reactive<ChatMsg>({
    role: 'user',
    content: question,
    attachments: files.map((file) => URL.createObjectURL(file)),
    attachmentRefs: attachments
  })
  messages.value.push(userMessage)

  const agentKey = agent.agentKey
  const search = webSearch.value
  await deliver(
    (onEvent, signal) =>
      streamChat(
        agentKey,
        apiKey.value,
        { conversationId: conversationId.value, query: question, webSearch: search, attachments },
        onEvent,
        signal
      ),
    (event, data) => {
      if (event !== 'RUN_STARTED') return
      const userMessageId = parseUserMessageId(data)
      if (userMessageId) userMessage.messageId = userMessageId
    }
  )
}

/**
 * 重新生成某条回答：丢弃它及其之后的全部消息，用**同一条提问**重跑，
 * 新回答就地替换原回答、不保留多版本（见 CONTEXT.md「重新生成」）。
 */
async function regenerate(index: number) {
  const agent = activeAgent.value
  const answer = messages.value[index]
  const prompt = messages.value[index - 1]
  if (!agent || loading.value) return
  if (!answer || answer.role !== 'assistant' || !prompt || prompt.role !== 'user') return
  const anchorId = prompt.messageId
  if (!anchorId) {
    message.warning('这条提问缺少消息标识，无法重新生成（可重新提问一次）')
    return
  }

  loading.value = true
  sendCancelled = false
  currentRunId.value = ''
  // 先乐观地就地丢弃这条回答及其之后的消息；服务端在校验阶段拒绝时再还原
  const dropped = messages.value.slice(index)
  messages.value = messages.value.slice(0, index)

  const agentKey = agent.agentKey
  const conversation = conversationId.value
  const search = webSearch.value
  const outcome = await deliver((onEvent, signal) =>
    regenerateChat(
      agentKey,
      apiKey.value,
      conversation,
      { fromMessageId: anchorId, webSearch: search },
      onEvent,
      signal
    )
  )

  if (!outcome.started) {
    // 服务端什么都没动（404/409/400 等校验类拒绝）：还原本地列表，并改用提示条说明失败原因
    messages.value = [...messages.value.slice(0, index), ...dropped]
    if (outcome.error instanceof Error) {
      message.error(outcome.error.message)
    }
  }
}

/** 一轮对话怎么发（新对话 / 重新生成）；两者共用同一套 SSE 渲染管线。 */
type TurnRunner = (
  onEvent: (event: string, data: string, runId: string) => void,
  signal: AbortSignal
) => Promise<void>

/**
 * 跑一轮对话并渲染到界面：push 助手占位 → 订阅 SSE 增量 → 收尾。
 * 调用方负责置 loading，并用 runner 决定这轮走「新对话」还是「重新生成」。
 *
 * @param onFirstEvent 首个事件到达时的回调（此时服务端已落库 / 已作废完毕）
 * @return 流是否真的开始过、以及捕获到的错误；调用方据此判断服务端有没有动过数据
 */
async function deliver(
  runner: TurnRunner,
  onFirstEvent?: (event: string, data: string) => void
): Promise<{ started: boolean; error: unknown }> {
  // 必须是响应式代理：SSE 回调里就地增量渲染
  const assistant = reactive<ChatMsg>({ role: 'assistant', content: '', loading: true, reasoningOpen: true })
  messages.value.push(assistant)
  await scrollToBottom(true)

  // 首个事件只处理一次：刷新会话列表（服务端此时已建好会话）+ 通知调用方；
  // 新会话因此立刻出现在左栏，而不是等整轮回答流结束
  let firstEventHandled = false
  let started = false
  let caught: unknown = null
  controller = new AbortController()
  let watchdog = setTimeout(() => controller?.abort(), STREAM_SILENCE_MS)
  const resetWatchdog = () => {
    clearTimeout(watchdog)
    watchdog = setTimeout(() => controller?.abort(), STREAM_SILENCE_MS)
  }

  try {
    await runner(
      (event, data, runId) => {
        started = true
        resetWatchdog()
        if (runId) currentRunId.value = runId
        if (!firstEventHandled) {
          firstEventHandled = true
          void refreshSessions()
          onFirstEvent?.(event, data)
        }
        if (event === 'RETRIEVAL') {
          // 一次运行可能检索多次：累积去重，与服务端落库口径一致（见 docs/adr/0009）
          assistant.citations = mergeHits(assistant.citations, parseHits(data))
        } else if (event === 'REASONING_DELTA' && data) {
          assistant.reasoning = (assistant.reasoning ?? '') + parseDelta(data)
        } else if (event === 'TEXT_DELTA') {
          if (!assistant.content) assistant.reasoningOpen = false
          assistant.content += parseDelta(data)
        } else if (event === 'RUN_COMPLETED') {
          // 用量 / 用时由服务端在完成事件里带出来（与 v5ai_model_usage 记账同一份数字）
          assistant.usage = parseUsage(data)
        } else if (event === 'RUN_FAILED') {
          assistant.error = parseRunFailed(data)
        } else if (event === 'MESSAGE_COMPLETED' && !assistant.content) {
          assistant.content = parseDelta(data)
        }
        void scrollToBottom(false)
      },
      controller.signal
    )
  } catch (e) {
    caught = e
    // 主动停止不是错误：保留已生成的内容，只标记「已停止」
    if (assistant.stopped) {
      // 停止路径已在 stop() 里标记，这里不再覆盖
    } else if (e instanceof ApiError && (e.status === 401 || e.status === 403)) {
      assistant.error = e.message
      await handleAuthFailure(e)
    } else {
      assistant.error =
        e instanceof DOMException && e.name === 'AbortError'
          ? `流式响应超时（${STREAM_SILENCE_MS / 1000} 秒无数据），已中断`
          : e instanceof Error
            ? e.message
            : '调用失败'
    }
  } finally {
    clearTimeout(watchdog)
    controller = null
    currentRunId.value = ''
    assistant.loading = false
    loading.value = false
    await refreshSessions()
    void scrollToBottom(true)
  }
  return { started, error: caught }
}

/**
 * 停止生成。按钮要立刻从「停止」变回「发送」，所以先断本地流（流一断，
 * send() 的 finally 就会把 loading 置回 false），再补一次服务端取消。
 *
 * 两条路径都要：断流让服务端走 doOnCancel 兜底，显式 stop 保证即使断流没被感知到
 * 也能把 run 落定为 CANCELED（且幂等）。
 */
async function stop() {
  // 只标记"本次正在生成的那条回答"：assistant.loading 为真才说明它还在跑
  const assistant = messages.value[messages.value.length - 1]
  if (assistant && assistant.role === 'assistant' && assistant.loading) {
    assistant.stopped = true
  }
  const agent = activeAgent.value
  const runId = currentRunId.value
  if (!controller) {
    // 流还没开始（附件还在上传）：让 send() 收尾时放弃这次发送
    sendCancelled = true
  }
  controller?.abort()
  if (agent && runId) {
    try {
      await stopRun(apiKey.value, agent.agentKey, runId)
    } catch {
      // 服务端已完成/已取消同样是正常结果，这里不打扰用户
    }
  }
}

function newSession() {
  // 窄屏是从抽屉里点进来的：收起抽屉，否则它会盖着刚开好的新会话
  drawerOpen.value = false
  historyToken += 1
  historyLoading.value = false
  conversationId.value = ''
  messages.value = []
  atBottom.value = true
}

async function openSession(session: ConversationSummary) {
  // 点抽屉里的任意一条都收起抽屉，包括点的就是当前会话（否则抽屉会一直盖着）
  drawerOpen.value = false
  if (loading.value || session.conversationId === conversationId.value) return
  const token = (historyToken += 1)
  conversationId.value = session.conversationId
  messages.value = []
  historyLoading.value = true
  try {
    const history = await fetchConversationMessages(apiKey.value, session.agentKey, session.conversationId)
    const rendered: ChatMsg[] = []
    for (const item of history) {
      // 只思考就被停止的那一轮没有正文也没有附件，但思考本身就是要回看的内容，不能整条跳过
      if (!(item.content ?? '').trim() && !(item.attachments?.length ?? 0) && !(item.reasoning ?? '').trim()) continue
      const attachments = await Promise.all(
        (item.attachments ?? []).map((attachment) =>
          resolveResourceUrl(apiKey.value, attachment.accessUrl).catch(() => '')
        )
      )
      rendered.push({
        role: item.role?.toUpperCase() === 'USER' ? 'user' : 'assistant',
        content: item.content,
        // 思考随助手消息落库（V42 起）：历史回放复用同一套折叠展示，用户消息没有思考
        reasoning: item.reasoning ?? undefined,
        // 引用随助手消息落库（V45 起）：刷新或重进会话后仍能回看当时引用的切片（见 docs/adr/0009）
        citations: item.citations && item.citations.length > 0 ? item.citations : undefined,
        attachments: attachments.filter((url) => url.length > 0),
        // 保留资源引用：历史消息也能「重新生成」，且不必重新上传图片
        attachmentRefs: (item.attachments ?? []).map((attachment) => ({
          type: attachment.type,
          resourceId: attachment.resourceId
        })),
        // 历史消息自带用量（V39 起落库），刷新页面后「用量 / 用时」依然在
        usage: normalizeUsage(item.usage),
        // 锚点也带上：刷新页面后再点「重新生成」照样能跑（id 一律归一成字符串，见 toMessageId）
        messageId: toMessageId(item.messageId)
      })
    }
    // 期间用户已切到别的会话 / 新建会话 / 直接提问：丢弃这次迟到的结果
    if (token !== historyToken || loading.value) return
    messages.value = rendered
    void scrollToBottom(true)
  } catch (e) {
    if (e instanceof ApiError && e.status === 401) {
      await handleAuthFailure(e)
      return
    }
    message.error(e instanceof Error ? e.message : '会话历史加载失败')
  } finally {
    if (token === historyToken) historyLoading.value = false
  }
}

async function renameSessionRow(session: ConversationSummary, name: string) {
  try {
    await renameSession(session.agentKey, session.conversationId, name)
  } catch (e) {
    message.error(e instanceof Error ? e.message : '改名失败')
  }
}

async function archiveSessionRow(session: ConversationSummary, archived: boolean) {
  try {
    await setSessionArchived(session.agentKey, session.conversationId, archived)
    // 归档后当前会话不能再继续对话：直接退回新会话
    if (archived && session.conversationId === conversationId.value) {
      newSession()
    }
    if (archived) offerArchiveUndo(session)
  } catch (e) {
    message.error(e instanceof Error ? e.message : '归档失败')
  }
}

/**
 * 归档后的撤销入口。
 *
 * 归档虽然可逆，但要找回来得先开「显示已归档」再翻列表，路径太长——
 * 所以补一条带按钮的提示条（setSessionArchived 自带重新拉取，撤销后列表即刻恢复）。
 */
function offerArchiveUndo(session: ConversationSummary) {
  const undo = async () => {
    try {
      await setSessionArchived(session.agentKey, session.conversationId, false)
    } catch (e) {
      message.error(e instanceof Error ? e.message : '撤销归档失败')
    }
  }
  message.info(
    () =>
      h('span', [
        '会话已归档',
        h(
          NButton,
          { text: true, size: 'tiny', style: 'margin-left: 10px', onClick: () => void undo() },
          { default: () => '撤销' }
        )
      ]),
    { duration: 5000 }
  )
}

async function switchArchivedView(value: boolean) {
  if (!currentAgentKey.value) {
    showArchived.value = value
    return
  }
  // showArchived 是在发请求之前就乐观改掉的（分段得立刻跟手），拉取失败就得把它拨回去：
  // 不退的话，分段停在「已归档」而列表还是未归档那批，两边对不上。
  // 这里不会退错列表 —— 请求失败时 sessionList 没被赋值，仍是旧视图那批，正好配拨回后的分段。
  const previous = showArchived.value
  try {
    await toggleArchivedView(currentAgentKey.value, value)
  } catch (e) {
    showArchived.value = previous
    message.error(e instanceof Error ? e.message : '切换失败')
  }
}

async function reloadAgents() {
  bootstrapping.value = true
  try {
    await loadBootstrap()
    await ensureAgentAvatars()
    syncWebSearchDefault()
    await refreshSessions()
  } catch (e) {
    await handleAuthFailure(e)
  } finally {
    bootstrapping.value = false
  }
}
</script>

<template>
  <!--
    --sidebar-expanded-w 挂在这一层：侧栏（含收起后的窄栏）和外面的拖拽把手都要读它。
    整页是「侧栏 | 内容列」两栏横排，侧栏通顶 —— 顶栏只在右侧内容列里，
    收起后的窄栏因此能一直顶到页面最上面，和顶栏那一行共用一条竖分隔线。
  -->
  <div
    class="chat-page portal-stage"
    :class="{ 'is-resizing': resizing }"
    :style="{ '--sidebar-expanded-w': `${sidebarWidth}px` }"
  >
    <aside id="portal-sidebar" class="sidebar" :class="{ 'is-collapsed': sidebarCollapsed }">
      <!--
        展开态内容：宽度钉在展开宽度上。收起过程中外层在变窄，这一层不跟着重排
        （否则会话标题会一路折行、截断，动画看着就是一团字在抖）。
        收起后靠 visibility 藏起来 —— 顺带把它移出 Tab 顺序和无障碍树。
      -->
      <div class="sidebar-full">
        <!--
          品牌行：标识 + 应用名在左、收起按钮在右。标识那一格与窄栏顶部那一格重合
          （都是 56px 宽、内容居中在 x=28），所以收起时标识原地不动，只有名字和按钮淡出。
        -->
        <div class="sidebar-top">
          <div class="sidebar-top-brand">
            <span class="brand-logo" aria-hidden="true"></span>
            <span class="brand-title">{{ appTitle }}</span>
          </div>
          <n-tooltip trigger="hover" placement="right">
            <template #trigger>
              <n-button
                circle
                size="large"
                quaternary
                aria-label="收起会话栏"
                :aria-expanded="true"
                @click="toggleSidebar"
              >
                <template #icon><n-icon :component="PanelLeftClose" :size="18" /></template>
              </n-button>
            </template>
            收起会话栏
          </n-tooltip>
        </div>

        <SidebarPanel
          :items="sessionList"
          :current-id="conversationId"
          :show-archived="showArchived"
          :loading="sessionsLoading"
          :title-of="sessionTitle"
          @new="newSession"
          @open="openSession"
          @rename="renameSessionRow"
          @archive="archiveSessionRow"
          @update:show-archived="switchArchivedView"
        />
      </div>

      <!--
        收起态窄栏：顶部一格与展开态的品牌行等高、同样放标识，下面是展开 / 新建 / 会话列表。
        展开按钮排在标识下面（展开态它在品牌行最右）—— 56px 宽的一格塞不下标识加按钮两样。
        「会话列表」开的是窄屏那个抽屉 —— 收起侧栏后还能翻会话，不用先展开再点。
      -->
      <div class="sidebar-rail">
        <!-- 顶部这格放标识，与展开态品牌行左边那格位置重合 -->
        <div class="sidebar-rail-head">
          <span class="brand-logo" aria-hidden="true"></span>
        </div>

        <!-- 收起后就没得收了，这一格换成展开 -->
        <div class="sidebar-rail-body">
          <n-tooltip trigger="hover" placement="right">
            <template #trigger>
              <n-button
                circle
                size="large"
                quaternary
                aria-label="展开会话栏"
                :aria-expanded="false"
                @click="toggleSidebar"
              >
                <template #icon><n-icon :component="PanelLeftOpen" :size="18" /></template>
              </n-button>
            </template>
            展开会话栏
          </n-tooltip>
          <n-tooltip trigger="hover" placement="right">
            <template #trigger>
              <n-button circle size="large" type="primary" secondary aria-label="新建会话" @click="newSession">
                <template #icon><n-icon :component="MessageSquarePlus" :size="18" /></template>
              </n-button>
            </template>
            新建会话
          </n-tooltip>
          <n-tooltip trigger="hover" placement="right">
            <template #trigger>
              <n-button circle size="large" quaternary aria-label="打开会话列表" @click="drawerOpen = true">
                <template #icon><n-icon :component="Menu" :size="18" /></template>
              </n-button>
            </template>
            会话列表
          </n-tooltip>
        </div>
      </div>
    </aside>

    <!--
      拖拽调宽（双击复位，聚焦后可用 ←/→ 调）。
      必须放在 .sidebar 外面：收起时 .sidebar 要 overflow:hidden 裁掉钉宽的展开内容，
      把手留在里面的话，骑在侧栏边缘外的那 4px 会被一起裁掉，8px 命中区只剩一半。
    -->
    <div
      class="sidebar-resizer"
      :class="{ 'is-hidden': sidebarCollapsed }"
      role="separator"
      aria-orientation="vertical"
      aria-label="调整侧栏宽度"
      :aria-valuenow="sidebarWidth"
      :aria-valuemin="SIDEBAR_MIN"
      :aria-valuemax="SIDEBAR_MAX"
      tabindex="0"
      @pointerdown="startResize"
      @keydown="onResizerKeydown"
      @dblclick="resetSidebarWidth"
    />

    <!-- 顶栏只盖内容列：左侧那一条让给通顶的侧栏/窄栏 -->
    <div class="chat-column">
      <header class="topbar portal-glass">
        <div class="topbar-brand">
          <!--
            汉堡按钮和标识都是窄屏专用（≤768px，见样式末尾的媒体查询）：侧栏那时整体
            display:none，汉堡是打开会话抽屉的唯一入口，标识也只剩这一处 ——
            桌面端它跟着侧栏待在页面左上角，顶栏再放一个就重了。
            两者在桌面端都被 display:none 关掉，连带移出无障碍树。
          -->
          <n-button
            quaternary
            size="small"
            class="sidebar-toggle"
            aria-label="打开会话列表"
            @click="drawerOpen = true"
          >
            <template #icon><n-icon :component="Menu" :size="18" /></template>
          </n-button>
          <span class="brand-logo" aria-hidden="true"></span>
<!--          <span class="brand-title">{{ appTitle }}</span>-->
          <!-- 当前会话：收起侧栏后与窄屏上，这是唯一能看出「我在哪个会话」的地方 -->
          <template v-if="currentSessionTitle">
            <span class="brand-divider" aria-hidden="true">/</span>
            <span class="brand-session" :title="currentSessionTitle">{{ currentSessionTitle }}</span>
          </template>
        </div>
        <div class="topbar-right">
          <AgentSwitcher @switch="newSession" />
          <UserMenu />
        </div>
      </header>

      <main class="main">
        <n-spin :show="bootstrapping" class="main-spin">
          <!--
            光转圈不给话，主区就是一大片空白加一个弧，刷新时看着像卡住了。
            role="status" 与 ChatMessageBody 播报生成进度是同一套做法：这里挂在可见文字本身上
            （那边是再放一句 .portal-sr-only），所以不必另加隐藏副本，读屏至少能听到一次「加载中」。
          -->
          <template #description>
            <span role="status">加载中…</span>
          </template>

          <!--
            「没有可访问的 Agent」是个终局判断，得等加载完再说。
            agents 初值是空数组，刷新后要到 bootstrap 回来才有内容 —— 只按长度判断，
            加载途中就会先亮出这条报错，和底下那个转圈同时挂在页面上，看着像「Key 废了」。
            这是把「加载中」和「加载完但确实没有」混成了一个状态，分界线就是 bootstrapping。

            这条线为什么不用 bootstrapData（它才是决定 agents 的那个值）：点「重新加载」时
            bootstrapping 立刻转起来，但 bootstrapData 还留着上一次的值，那时它一样是空的 ——
            按它判断，重载途中这条报错会照样亮着，等于没修。bootstrapping 覆盖「正在拉」的整段。
          -->
          <div v-if="!bootstrapping && agents.length === 0" class="main-empty">
            <n-alert type="warning" :bordered="false">
              当前 API Key 没有可访问的 Agent：可能绑定的 Agent 已被下线，或管理员收回了绑定。
            </n-alert>
            <n-button size="small" secondary @click="reloadAgents">
              <template #icon><n-icon :component="RefreshCw" :size="14" /></template>
              重新加载
            </n-button>
          </div>

          <template v-else-if="activeAgent">
            <div class="scroll-wrap">
              <div ref="listRef" class="scroll-area portal-scroll" @scroll.passive="onScroll">
                <div v-if="historyLoading" class="history-loading">
                  <n-spin size="small" />
                  <span>正在加载会话历史…</span>
                </div>

                <!-- 包一层定列宽并居中：一是与消息列、输入区对齐，二是让引导垂直居中，
                     否则它贴着滚动区顶部、下面空一大片，是这一页最显眼的失衡 -->
                <div v-else-if="messages.length === 0" class="intro-wrap">
                  <AgentIntro :agent="activeAgent" @ask="(q: string) => send(q, [])" />
                </div>

                <div v-else class="message-list" :class="{ 'is-scroll-bottom-visible': !atBottom }">
                  <div v-for="(msg, index) in messages" :key="index" class="message-row" :class="msg.role">
                    <AgentAvatar
                      v-if="msg.role === 'assistant'"
                      :agent-key="activeAgent.agentKey"
                      :name="activeAgent.name"
                      :size="28"
                    />
                    <div class="message-main">
                      <div class="bubble" :class="msg.role">
                        <ChatMessageBody
                          v-if="msg.role === 'assistant'"
                          :msg="msg"
                          :show-citations="activeAgent?.showCitations ?? true"
                        />
                        <template v-else>
                          <div v-if="msg.attachments?.length" class="bubble-attachments">
                            <img
                              v-for="(url, imageIndex) in msg.attachments"
                              :key="imageIndex"
                              :src="url"
                              alt="消息附件"
                              class="bubble-attachment"
                            />
                          </div>
                          <span v-if="msg.content" class="bubble-text">{{ msg.content }}</span>
                        </template>
                        <p v-if="msg.stopped" class="bubble-stopped">已停止</p>
                      </div>
                      <MessageActions
                        v-if="!msg.loading"
                        :msg="msg"
                        :can-regenerate="!loading"
                        @regenerate="regenerate(index)"
                      />
                    </div>
                  </div>
                </div>
              </div>

              <!-- 不在底部时露出「回到最新」；生成中圆环转起来，回答结束恢复常边框（同 chat.deepseek.com） -->
              <transition name="scroll-bottom">
                <button
                  v-if="!atBottom"
                  type="button"
                  class="scroll-bottom"
                  :class="{ 'is-generating': loading }"
                  :aria-label="loading ? '正在生成，回到最新' : '回到最新'"
                  @click="scrollToLatest"
                >
                  <n-icon :component="ArrowDown" :size="16" />
                </button>
              </transition>
            </div>

            <div class="composer-wrap">
              <Composer
                :loading="loading"
                :web-search-enabled="activeAgent.webSearchEnabled"
                :web-search="webSearch"
                :image-supported="imageSupported"
                @send="send"
                @stop="stop"
                @update:web-search="(value: boolean) => (webSearch = value)"
              />
              <p class="disclaimer">AI 生成内容可能有误，请核实重要信息</p>
            </div>
          </template>
        </n-spin>
      </main>
    </div>

    <!--
      会话列表抽屉，两个入口共用一个：
      窄屏（≤768px 时 .sidebar 整体 display:none，见样式末尾的媒体查询）是唯一入口，
      新建 / 切换 / 改名 / 归档全靠它，没有它手机上这些操作一个都够不到；
      桌面端由窄栏里的「会话列表」按钮打开（侧栏收起后总得有个地方翻会话）。
      抽屉由 naive-ui 挂到 body 上，不参与这两栏的布局。
    -->
    <n-drawer v-model:show="drawerOpen" :width="280" placement="left">
      <n-drawer-content title="会话" :native-scrollbar="false" body-content-style="padding: 0">
        <SidebarPanel
          :items="sessionList"
          :current-id="conversationId"
          :show-archived="showArchived"
          :loading="sessionsLoading"
          :title-of="sessionTitle"
          @new="newSession"
          @open="openSession"
          @rename="renameSessionRow"
          @archive="archiveSessionRow"
          @update:show-archived="switchArchivedView"
        />
      </n-drawer-content>
    </n-drawer>
  </div>
</template>

<style scoped>
.chat-page {
  /* 两栏横排：通顶侧栏 | 内容列（顶栏 + 主区）。
     侧栏占满整页高度，所以顶栏只盖右边那一列 —— 收起后的窄栏才能一直顶到页面最上面。 */
  display: flex;
  /* 拖拽把手以这一层为定位基准（它是 .chat-page 的直接子元素，不在侧栏里） */
  position: relative;
  /* dvh 跟随移动端地址栏收放，否则底部输入区会被顶到地址栏底下；
     先声明 100vh 给不认 dvh 的旧浏览器兜底 */
  height: 100vh;
  height: 100dvh;
  min-height: 0;
}

/* 内容列：顶栏 + 主区，占满侧栏右边剩下的宽度 */
.chat-column {
  flex: 1;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

/* 拖拽调宽期间整页禁选中，并把指针锁成 col-resize，免得拖到文字上变成选词 */
.chat-page.is-resizing {
  user-select: none;
  cursor: col-resize;
}

.topbar {
  flex: none;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  height: var(--portal-topbar-height);
  padding: 0 16px;
  /* .portal-glass 给的是四边发丝描边，顶栏只需要底边那一条 */
  border: 0;
  border-bottom: 1px solid var(--portal-border);
  border-radius: 0;
}

.topbar-brand {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

/* 顶栏里的标识是窄屏专用的：桌面端它跟着通顶侧栏待在页面左上角
   （.sidebar-top / .sidebar-rail-head 各一份），顶栏再放一个就重了。
   只在窄屏放出来 —— 那时侧栏整个隐藏，这是唯一一处标识（见样式末尾的媒体查询）。 */
.topbar-brand .brand-logo {
  display: none;
}

.brand-logo {
  display: inline-flex;
  flex: none;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: 1px solid var(--portal-brand-icon-border);
  border-radius: 10px;
  background-color: transparent;
  background-image: var(--portal-brand-icon);
  background-position: center;
  background-repeat: no-repeat;
  background-size: cover;
  box-shadow:
    0 0 0 3px var(--portal-brand-icon-ring),
    0 8px 22px rgba(37, 99, 235, 0.16),
    inset 0 0 14px rgba(37, 99, 235, 0.08);
}

.brand-title {
  font-size: 15px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.brand-divider {
  flex: none;
  color: var(--portal-border-strong);
}

/* 当前会话名：挤到不够时它先让位（品牌名是身份，会话名是上下文，两者都可截断）。
   对比度用 muted 令牌给足：顶栏是玻璃面，实测 7.15:1（浅）/ 5.91:1（深）。 */
.brand-session {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  color: var(--portal-fg-muted);
}

.topbar-right {
  display: flex;
  align-items: center;
  gap: 4px;
  flex: none;
}

/* 汉堡按钮是窄屏专用的：桌面端侧栏常驻，收起/展开按钮在侧栏自己的表头里
   （见 SidebarPanel 的 head-actions 插槽）。默认关掉，桌面端不多一个重复入口，
   也少一个 Tab 停靠点。 */
.sidebar-toggle {
  display: none;
}

.sidebar {
  position: relative;
  flex: none;
  width: var(--sidebar-expanded-w);
  /* 通顶：横排 flex 的默认 stretch 让它撑满 .chat-page，一直顶到页面上沿。
     边框因此始终有意义 —— 收起后那 56px 的右边框往上接到顶栏的底边，是个正常的 ┬ 接头
     （侧栏不到顶的时候它会在顶栏下方悬空，那才要按状态去掉）。 */
  /* 收起时靠裁切藏起钉宽的展开内容；也正因为要裁，拖拽把手不能放在这里面 */
  overflow: hidden;
  background: var(--portal-surface);
  border-right: 1px solid var(--portal-border);
  backdrop-filter: blur(20px) saturate(140%);
  -webkit-backdrop-filter: blur(20px) saturate(140%);
  /* 拖拽时宽度跟手，不做过渡；只给主题切换留过渡 */
  transition: background-color 0.24s var(--portal-ease);
}

/* 收起：宽度交给 CSS 令牌，脚本只切这个类 */
.sidebar.is-collapsed {
  width: var(--portal-rail-width);
}

/*
 * 宽度过渡只在非拖拽时挂上：拖拽要跟手，有过渡就会慢半拍。
 * 把手在 .sidebar 之外，得跟着同一个缓动一起走，否则调宽时它和侧栏边缘会脱开
 * （同一个起点、同一条曲线，两边算出来的位置才逐帧相等）。
 */
.chat-page:not(.is-resizing) .sidebar {
  transition: width 0.24s var(--portal-ease), background-color 0.24s var(--portal-ease);
}

.chat-page:not(.is-resizing) .sidebar-resizer {
  transition: left 0.24s var(--portal-ease);
}

/*
 * 品牌行：标识在左、收起按钮在右，高度与顶栏一致（两边的底线因此连成一条横线）。
 * 左右内边距不对称是有意的：左边 15px 让 26px 的标识落在 x=28 这条中线上，
 * 也是窄栏顶部那一格里标识居中后的位置（(56-26)/2）；右边 8px 让 40px 的圆钮也落在同一条线上
 * （(56-40)/2）。两边都对着窄栏的中线，收起时标识原地不动，不会挪窝。
 */
.sidebar-top {
  flex: none;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  height: var(--portal-topbar-height);
  padding: 0 8px 0 15px;
  border-bottom: 1px solid var(--portal-border);
}

/* 标识 + 应用名，摆法与顶栏左边那一组一致（同样的 8px 间距、同样的截断）。
   flex:1 吃掉中间的空档，把收起按钮顶到最右；min-width:0 让侧栏拖窄时
   先截断应用名，而不是把这一行撑破。 */
.sidebar-top-brand {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 8px;
}

/* 展开态内容：绝对定位 + 钉宽，两种状态下都不参与外层宽度计算 */
.sidebar-full {
  position: absolute;
  top: 0;
  left: 0;
  bottom: 0;
  width: var(--sidebar-expanded-w);
  display: flex;
  flex-direction: column;
  min-height: 0;
  transition: opacity 0.2s var(--portal-ease), visibility 0.2s;
}

/*
 * visibility 的过渡是「走到终点才真正生效」：visible → hidden 期间元素全程可见，
 * 正好当淡出用；收起结束后它才转成 hidden，于是顺带移出了 Tab 顺序和无障碍树
 * ——这一层里还有一整列会话按钮，不能只靠 opacity:0 藏着（那仍然点得到、Tab 得到）。
 */
.sidebar.is-collapsed .sidebar-full {
  opacity: 0;
  visibility: hidden;
  pointer-events: none;
}

/* 收起态窄栏：宽度与 .sidebar 收起后一致，收起过程中它始终贴着左边不跟着撑开 */
.sidebar-rail {
  position: absolute;
  top: 0;
  left: 0;
  bottom: 0;
  width: var(--portal-rail-width);
  display: flex;
  flex-direction: column;
  opacity: 0;
  visibility: hidden;
  transition: opacity 0.2s var(--portal-ease), visibility 0.2s;
}

/*
 * 窄栏顶部这一格与展开态的品牌行等高，放同一个标识、同样居中：
 * 展开时它在品牌行左边那格、收起时在这一格，位置逐像素重合，收起过程中标识是不动的。
 *
 * 底边那道线接着品牌行的 border-bottom 画：少了它，顶栏的底线到了侧栏边缘就断掉。
 */
.sidebar-rail-head {
  flex: none;
  display: flex;
  align-items: center;
  justify-content: center;
  height: var(--portal-topbar-height);
  border-bottom: 1px solid var(--portal-border);
}

/* 按钮一律 40px 圆钮：两侧各 8px 内边距正好填满 56px 窄栏，触屏也够得着 */
.sidebar-rail-body {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 8px;
}

.sidebar.is-collapsed .sidebar-rail {
  opacity: 1;
  visibility: visible;
}

/* 把手：8px 命中区骑在侧栏右边缘上，可见部分只有 2px 线 */
.sidebar-resizer {
  position: absolute;
  top: 0;
  left: calc(var(--sidebar-expanded-w) - 4px);
  width: 8px;
  height: 100%;
  z-index: 2;
  cursor: col-resize;
  touch-action: none;
}

.sidebar-resizer::after {
  content: '';
  position: absolute;
  top: 0;
  left: 3px;
  width: 2px;
  height: 100%;
  border-radius: 2px;
  background: transparent;
  transition: background 0.18s var(--portal-ease);
}

.sidebar-resizer:hover::after,
.sidebar-resizer:focus-visible::after,
.chat-page.is-resizing .sidebar-resizer::after {
  background: var(--portal-accent);
}

.sidebar-resizer:focus-visible {
  outline: none;
}

/* 收起后是窄栏，没有「宽度」可调；visibility 一并把它移出 Tab 顺序
   （只写 pointer-events:none 的话，键盘仍然 Tab 得到这个看不见的把手） */
.sidebar-resizer.is-hidden {
  visibility: hidden;
  pointer-events: none;
}

.main {
  flex: 1;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.main-spin {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
}

.main-spin :deep(.n-spin-container) {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
}

.main-spin :deep(.n-spin-content) {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
}

/*
 * 转圈旁边那句话（模板里的 #description）。
 *
 * 字号取 13px、颜色取门户令牌，都不跟 naive 的默认值：Spin 的 self.textColor 直接取
 * primaryColor，也就是这一档靛蓝 —— 而它压在带光斑的舞台上没验过对比度。光斑本身是靛蓝的，
 * 同色相的文字叠上去比中性灰掉得更快；--portal-fg-muted 这一档是 base.css 按最坏叠加
 * 倒推出来的（装饰层上 4.95:1）。字号则与同一个视图里的 .history-loading 对齐。
 */
.main-spin :deep(.n-spin-description) {
  font-size: 13px;
  color: var(--portal-fg-muted);
}

.main > .n-spin,
.main-empty {
  padding: 24px;
}

.main-empty {
  display: flex;
  flex-direction: column;
  gap: 12px;
  align-items: flex-start;
}

/* 滚动区 + 悬浮的「回到最新」按钮：按钮相对这一层定位，不跟着内容滚走 */
.scroll-wrap {
  position: relative;
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.scroll-area {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
}

.scroll-bottom {
  position: absolute;
  left: 50%;
  bottom: 12px;
  transform: translateX(-50%);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  padding: 0;
  border: 1px solid var(--portal-border);
  border-radius: 50%;
  background: var(--portal-surface);
  color: var(--portal-fg-muted);
  backdrop-filter: blur(16px) saturate(140%);
  -webkit-backdrop-filter: blur(16px) saturate(140%);
  cursor: pointer;
  box-shadow: var(--portal-shadow-sm);
  transition: opacity 0.15s ease, transform 0.15s ease, border-color 0.15s ease, color 0.15s ease;
}

.scroll-bottom:hover {
  color: var(--portal-accent);
  border-color: var(--portal-accent);
}

/* 触屏把「回到最新」撑到 44pt：它是移动端唯一的回底入口 */
@media (hover: none) {
  .scroll-bottom {
    width: 44px;
    height: 44px;
  }
}

/* 生成中：常边框让位给一圈转动的弧；回答结束（loading=false）恢复常边框 */
.scroll-bottom.is-generating {
  border-color: transparent;
}

.scroll-bottom.is-generating::after {
  content: '';
  position: absolute;
  inset: -1px;
  border-radius: 50%;
  border: 2px solid var(--portal-border-strong);
  border-top-color: var(--portal-accent);
  animation: scroll-bottom-spin 0.9s linear infinite;
}

@keyframes scroll-bottom-spin {
  to {
    transform: rotate(360deg);
  }
}

.scroll-bottom-enter-from,
.scroll-bottom-leave-to {
  opacity: 0;
  transform: translateX(-50%) translateY(6px);
}

.history-loading {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 24px;
  font-size: 13px;
  color: var(--portal-fg-muted);
}

/*
 * 空态引导：与消息列、输入区共用同一列宽（820px），左边缘三者对齐 ——
 * 首条消息发出去时，版式只是从「引导」换成「气泡」，整块不会横跳。
 *
 * 纵向用 auto 外边距而不是 justify-content:center —— 后者在内容超出容器时会把溢出部分
 * 推到起点方向，而滚动容器起点侧的溢出是永远滚不到的（顶部那截看不见）；
 * auto 外边距在空间不足时归零，不会吃掉内容。横向的 auto 是让它在滚动区里居中。
 * 横向的 auto 会让 flex 项退化成 fit-content，所以 width:100% 不能省（同 .message-list）。
 */
.intro-wrap {
  margin: auto;
  width: 100%;
  max-width: 820px;
  padding: 24px 16px;
}

.message-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  width: 100%;
  max-width: 820px;
  margin: 0 auto;
  padding: 24px 16px 24px;
}

/*
 * 轮次边界：一问一答之间是 gap 给的 12px，回答与下一次提问之间再叠 12px，合计 24px。
 * 原先整段对话等距，看不出「这一轮从哪开始」；现在问答成组，长回答里能跳着读。
 * 用户消息必是每轮的第一条（消息列表要么为空，要么由提问开头），所以按它分组即可。
 */
.message-row.user:not(:first-child) {
  margin-top: 12px;
}

/*
 * 「回到最新」是悬浮在滚动区底部的圆钮：给它让出高度，
 * 免得遮住最后一条消息的操作条（WCAG 2.2：键盘焦点不能被遮挡）。
 * 只在滚上去、按钮真的出现时才加，不产生无谓的底部空白。
 */
.message-list.is-scroll-bottom-visible {
  padding-bottom: 64px;
}

.message-row {
  display: flex;
  gap: 10px;
  align-items: flex-start;
}

.message-row.user {
  justify-content: flex-end;
}

/* 气泡 + 操作条的列容器：操作条跟着气泡对齐，宽度约束从气泡移到这一层 */
.message-main {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
  max-width: 78%;
}

.message-row.assistant .message-main {
  max-width: 100%;
}

.message-row.user .message-main {
  align-items: flex-end;
}

.bubble {
  padding: 10px 14px;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.7;
  overflow-wrap: anywhere;
}

.bubble.assistant {
  background: transparent;
  padding: 2px 0;
}

/* 操作条平时隐藏，鼠标悬停/键盘聚焦到这条消息时出现（触屏由组件内部常驻） */
.message-row:hover :deep(.message-actions),
.message-row:focus-within :deep(.message-actions) {
  opacity: 1;
}

.bubble.user {
  background: var(--portal-accent-soft);
  border: 1px solid var(--portal-border);
}

.bubble-text {
  white-space: pre-wrap;
}

.bubble-attachments {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 6px;
}

.bubble-attachment {
  max-width: 160px;
  max-height: 160px;
  border-radius: 10px;
  border: 1px solid var(--portal-border);
  display: block;
}

.bubble-stopped {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--portal-fg-muted);
}

.composer-wrap {
  flex: none;
  width: 100%;
  max-width: 820px;
  margin: 0 auto;
  padding: 8px 16px 10px;
}

.disclaimer {
  margin: 8px 0 0;
  text-align: center;
  font-size: 11px;
  letter-spacing: 0.02em;
  /* 11px 叠 opacity 只剩 3.9:1：改用 muted 给足对比度（底色上 5.34:1） */
  color: var(--portal-fg-muted);
}

/* 窄屏：会话栏整体收进抽屉（顶栏那个按钮打开），主区占满，也就没有收起态这一说。
   把手在 .sidebar 之外，必须一并关掉，否则会在主区左边缘留一条可拖的隐形条。 */
@media (max-width: 768px) {
  .sidebar,
  .sidebar-resizer {
    display: none;
  }

  /* 侧栏没了，汉堡按钮顶上：它是窄屏打开会话列表的唯一入口 */
  .sidebar-toggle {
    display: inline-flex;
  }

  /* 标识也跟着回来 —— 窄屏侧栏整个隐藏，这是唯一一处标识了 */
  .topbar-brand .brand-logo {
    display: inline-flex;
  }

  /* 顶栏让位给会话名：窄屏看不到侧栏，会话名是唯一能说明「这是哪段会话」的信息；
     应用名有左边那个标识兜底，让给它。 */
  .brand-title,
  .brand-divider {
    display: none;
  }
}

/* 宽度/淡出的过渡一律停用：状态仍然切换，只是不再动。
   前两条必须带上 .chat-page:not(.is-resizing) 前缀 —— 上面那几条过渡就是三档类名的权重，
   只写 .sidebar 权重不够，会被它们盖掉，过渡照样跑。 */
@media (prefers-reduced-motion: reduce) {
  .chat-page:not(.is-resizing) .sidebar,
  .chat-page:not(.is-resizing) .sidebar-resizer,
  .sidebar-full,
  .sidebar-rail {
    transition: none;
  }
}
</style>
