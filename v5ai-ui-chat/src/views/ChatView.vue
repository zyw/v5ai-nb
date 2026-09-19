<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { NAlert, NButton, NIcon, NSpin, useMessage } from 'naive-ui'
import { ArrowDown, MessageSquarePlus, RefreshCw } from 'lucide-vue-next'
import AgentAvatar from '../components/AgentAvatar.vue'
import AgentIntro from '../components/AgentIntro.vue'
import AgentSwitcher from '../components/AgentSwitcher.vue'
import ChatMessageBody from '../components/ChatMessageBody.vue'
import Composer from '../components/Composer.vue'
import MessageActions from '../components/MessageActions.vue'
import SessionList from '../components/SessionList.vue'
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
  normalizeUsage,
  parseHits,
  parseUsage,
  parseUserMessageId,
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
  sessionTitle,
  setActiveAgent,
  setSessionArchived,
  showArchived,
  toggleArchivedView
} from '../stores/portal'

/**
 * 对话页：顶栏（品牌 + Agent 切换 + 用户菜单）/ 左栏服务端会话列表 / 主区对话。
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

const bootstrapping = ref(true)
const loading = ref(false)
const messages = ref<ChatMsg[]>([])
const conversationId = ref('')
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
          assistant.citations = parseHits(data)
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
  historyToken += 1
  historyLoading.value = false
  conversationId.value = ''
  messages.value = []
  atBottom.value = true
}

async function openSession(session: ConversationSummary) {
  if (loading.value || session.conversationId === conversationId.value) return
  const token = (historyToken += 1)
  conversationId.value = session.conversationId
  messages.value = []
  historyLoading.value = true
  try {
    const history = await fetchConversationMessages(apiKey.value, session.agentKey, session.conversationId)
    const rendered: ChatMsg[] = []
    for (const item of history) {
      if (!(item.content ?? '').trim() && !(item.attachments?.length ?? 0)) continue
      const attachments = await Promise.all(
        (item.attachments ?? []).map((attachment) =>
          resolveResourceUrl(apiKey.value, attachment.accessUrl).catch(() => '')
        )
      )
      rendered.push({
        role: item.role?.toUpperCase() === 'USER' ? 'user' : 'assistant',
        content: item.content,
        attachments: attachments.filter((url) => url.length > 0),
        // 保留资源引用：历史消息也能「重新生成」，且不必重新上传图片
        attachmentRefs: (item.attachments ?? []).map((attachment) => ({
          type: attachment.type,
          resourceId: attachment.resourceId
        })),
        // 历史消息自带用量（V39 起落库），刷新页面后「用量 / 用时」依然在
        usage: normalizeUsage(item.usage)
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
  } catch (e) {
    message.error(e instanceof Error ? e.message : '归档失败')
  }
}

async function switchArchivedView(value: boolean) {
  if (!currentAgentKey.value) {
    showArchived.value = value
    return
  }
  await toggleArchivedView(currentAgentKey.value, value)
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
  <div class="chat-page portal-stage" :class="{ 'is-resizing': resizing }">
    <header class="topbar portal-glass">
      <div class="topbar-brand">
        <span class="brand-logo" aria-hidden="true">v5</span>
        <span class="brand-title">{{ appTitle }}</span>
      </div>
      <div class="topbar-right">
        <AgentSwitcher @switch="newSession" />
        <UserMenu />
      </div>
    </header>

    <div class="chat-body">
      <aside class="sidebar" :style="{ width: `${sidebarWidth}px` }">
        <div class="sidebar-head">
          <n-button block size="small" type="primary" secondary @click="newSession">
            <template #icon><n-icon :component="MessageSquarePlus" :size="15" /></template>
            新建会话
          </n-button>
        </div>
        <SessionList
          :items="sessionList"
          :current-id="conversationId"
          :show-archived="showArchived"
          :title-of="sessionTitle"
          @open="openSession"
          @rename="renameSessionRow"
          @archive="archiveSessionRow"
          @update:show-archived="switchArchivedView"
        />

        <!-- 拖拽调宽（双击复位，聚焦后可用 ←/→ 调） -->
        <div
          class="sidebar-resizer"
          role="separator"
          aria-orientation="vertical"
          aria-label="调整侧栏宽度"
          :aria-valuenow="sidebarWidth"
          :aria-valuemin="200"
          :aria-valuemax="420"
          tabindex="0"
          @pointerdown="startResize"
          @keydown="onResizerKeydown"
          @dblclick="resetSidebarWidth"
        />
      </aside>

      <main class="main">
        <n-spin :show="bootstrapping" class="main-spin">
          <div v-if="agents.length === 0" class="main-empty">
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

                <AgentIntro v-else-if="messages.length === 0" :agent="activeAgent" @ask="(q: string) => send(q, [])" />

                <div v-else class="message-list">
                  <div v-for="(msg, index) in messages" :key="index" class="message-row" :class="msg.role">
                    <AgentAvatar
                      v-if="msg.role === 'assistant'"
                      :agent-key="activeAgent.agentKey"
                      :name="activeAgent.name"
                      :size="28"
                    />
                    <div class="message-main">
                      <div class="bubble" :class="msg.role">
                        <ChatMessageBody v-if="msg.role === 'assistant'" :msg="msg" />
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
  </div>
</template>

<style scoped>
.chat-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  min-height: 0;
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

.brand-logo {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border-radius: 9px;
  background: var(--portal-grad);
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  box-shadow: 0 6px 18px var(--portal-accent-glow);
}

.brand-title {
  font-size: 15px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.topbar-right {
  display: flex;
  align-items: center;
  gap: 4px;
  flex: none;
}

.chat-body {
  flex: 1;
  display: flex;
  min-height: 0;
}

.sidebar {
  position: relative;
  flex: none;
  display: flex;
  flex-direction: column;
  min-height: 0;
  background: var(--portal-surface);
  border-right: 1px solid var(--portal-border);
  backdrop-filter: blur(20px) saturate(140%);
  -webkit-backdrop-filter: blur(20px) saturate(140%);
  /* 拖拽时宽度跟手，不做过渡；只给主题切换留过渡 */
  transition: background-color 0.24s var(--portal-ease);
}

/* 把手：8px 命中区骑在侧栏右边缘上，可见部分只有 2px 线 */
.sidebar-resizer {
  position: absolute;
  top: 0;
  right: -4px;
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

.sidebar-head {
  flex: none;
  padding: 10px;
  border-bottom: 1px solid var(--portal-border);
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

.message-list {
  display: flex;
  flex-direction: column;
  gap: 18px;
  width: 100%;
  max-width: 820px;
  margin: 0 auto;
  padding: 24px 16px 8px;
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
  color: var(--portal-fg-muted);
  opacity: 0.85;
}

/* 窄屏：会话栏收起，主区占满（把手一并收起，否则会留下一条可拖的隐形条） */
@media (max-width: 768px) {
  .sidebar,
  .sidebar-resizer {
    display: none;
  }
}
</style>
