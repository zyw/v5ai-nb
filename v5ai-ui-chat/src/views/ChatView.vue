<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { NAlert, NButton, NIcon, NSpin, useMessage, useThemeVars } from 'naive-ui'
import { MessageSquarePlus, RefreshCw, Square } from 'lucide-vue-next'
import AgentAvatar from '../components/AgentAvatar.vue'
import AgentIntro from '../components/AgentIntro.vue'
import AgentSwitcher from '../components/AgentSwitcher.vue'
import ChatMessageBody from '../components/ChatMessageBody.vue'
import Composer from '../components/Composer.vue'
import SessionList from '../components/SessionList.vue'
import UserMenu from '../components/UserMenu.vue'
import {
  ApiError,
  appTitle,
  fetchConversationMessages,
  parseDelta,
  parseRunFailed,
  resolveResourceUrl,
  stopRun,
  streamChat,
  uploadAttachment,
  type AttachmentRef,
  type ConversationSummary
} from '../api/client'
import { parseHits, type ChatMsg } from '../utils/chatStream'
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
const themeVars = useThemeVars()

/** 流静默超时：超时主动中断，避免发送按钮永久 loading */
const STREAM_SILENCE_MS = 60_000

const bootstrapping = ref(true)
const loading = ref(false)
const messages = ref<ChatMsg[]>([])
const conversationId = ref('')
/** 本次提问是否使用联网搜索：默认取 Agent 配置；Agent 未开启时开关禁用 */
const webSearch = ref(false)
/** 当前正在跑的运行 ID（取自 SSE 的 id: 字段），停止时按它通知服务端 */
const currentRunId = ref('')
const listRef = ref<HTMLElement | null>(null)
let controller: AbortController | null = null

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
    // 会话与 Agent 绑定：切换 Agent 即开一个新会话
    messages.value = []
    conversationId.value = ''
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
  const nearBottom = el.scrollHeight - el.scrollTop - el.clientHeight < 80
  if (force || nearBottom) {
    el.scrollTop = el.scrollHeight
  }
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

  const attachments = await uploadAll(files)
  if (attachments === null) return

  messages.value.push({
    role: 'user',
    content: question,
    attachments: files.map((file) => URL.createObjectURL(file))
  })
  // 必须是响应式代理：SSE 回调里就地增量渲染
  const assistant = reactive<ChatMsg>({ role: 'assistant', content: '', loading: true, reasoningOpen: true })
  messages.value.push(assistant)
  loading.value = true
  currentRunId.value = ''
  await scrollToBottom(true)

  controller = new AbortController()
  let watchdog = setTimeout(() => controller?.abort(), STREAM_SILENCE_MS)
  const resetWatchdog = () => {
    clearTimeout(watchdog)
    watchdog = setTimeout(() => controller?.abort(), STREAM_SILENCE_MS)
  }

  try {
    await streamChat(
      agent.agentKey,
      apiKey.value,
      {
        conversationId: conversationId.value,
        query: question,
        webSearch: webSearch.value,
        attachments
      },
      (event, data, runId) => {
        resetWatchdog()
        if (runId) currentRunId.value = runId
        if (event === 'RETRIEVAL') {
          assistant.citations = parseHits(data)
        } else if (event === 'REASONING_DELTA' && data) {
          assistant.reasoning = (assistant.reasoning ?? '') + parseDelta(data)
        } else if (event === 'TEXT_DELTA') {
          if (!assistant.content) assistant.reasoningOpen = false
          assistant.content += parseDelta(data)
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
}

/** 停止：先通知服务端取消（幂等），再断开本地流——两条路径都要，缺一条都可能留下「永远运行中」。 */
async function stop() {
  const assistant = messages.value[messages.value.length - 1]
  const agent = activeAgent.value
  if (assistant && assistant.role === 'assistant') {
    assistant.stopped = true
  }
  const runId = currentRunId.value
  if (agent && runId) {
    try {
      await stopRun(apiKey.value, agent.agentKey, runId)
    } catch {
      // 服务端已完成/已取消同样是正常结果，这里不打扰用户
    }
  }
  controller?.abort()
}

function newSession() {
  conversationId.value = ''
  messages.value = []
}

async function openSession(session: ConversationSummary) {
  if (loading.value || session.conversationId === conversationId.value) return
  conversationId.value = session.conversationId
  messages.value = []
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
        attachments: attachments.filter((url) => url.length > 0)
      })
    }
    messages.value = rendered
    void scrollToBottom(true)
  } catch (e) {
    if (e instanceof ApiError && e.status === 401) {
      await handleAuthFailure(e)
      return
    }
    message.error(e instanceof Error ? e.message : '会话历史加载失败')
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
  <div class="chat-page" :style="{ background: themeVars.bodyColor }">
    <header class="topbar" :style="{ borderColor: themeVars.borderColor, background: themeVars.cardColor }">
      <div class="topbar-brand">
        <span class="brand-logo" :style="{ background: themeVars.primaryColor }" aria-hidden="true">v5</span>
        <span class="brand-title">{{ appTitle }}</span>
      </div>
      <div class="topbar-right">
        <AgentSwitcher @switch="newSession" />
        <UserMenu />
      </div>
    </header>

    <div class="chat-body">
      <aside class="sidebar" :style="{ borderColor: themeVars.borderColor, background: themeVars.cardColor }">
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
            <div ref="listRef" class="scroll-area">
              <AgentIntro v-if="messages.length === 0" :agent="activeAgent" @ask="(q: string) => send(q, [])" />

              <div v-else class="message-list">
                <div v-for="(msg, index) in messages" :key="index" class="message-row" :class="msg.role">
                  <AgentAvatar
                    v-if="msg.role === 'assistant'"
                    :agent-key="activeAgent.agentKey"
                    :name="activeAgent.name"
                    :size="28"
                  />
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
                </div>
              </div>
            </div>

            <div class="composer-wrap">
              <div v-if="loading" class="composer-stop">
                <n-button size="small" secondary :aria-label="'停止生成'" @click="stop">
                  <template #icon><n-icon :component="Square" :size="14" /></template>
                  停止生成
                </n-button>
              </div>
              <Composer
                :loading="loading"
                :web-search-enabled="activeAgent.webSearchEnabled"
                :web-search="webSearch"
                :image-supported="imageSupported"
                @send="send"
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

.topbar {
  flex: none;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  height: var(--portal-topbar-height);
  padding: 0 16px;
  border-bottom: 1px solid;
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
  border-radius: 8px;
  color: #fff;
  font-size: 12px;
  font-weight: 700;
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
  flex: none;
  display: flex;
  flex-direction: column;
  width: var(--portal-sidebar-width);
  border-right: 1px solid;
  min-height: 0;
}

.sidebar-head {
  flex: none;
  padding: 10px;
  border-bottom: 1px solid rgba(127, 127, 127, 0.18);
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

.scroll-area {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
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

.bubble {
  max-width: 78%;
  padding: 10px 14px;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.7;
  overflow-wrap: anywhere;
}

.bubble.assistant {
  background: transparent;
  padding: 2px 0;
  max-width: 100%;
}

.bubble.user {
  background: rgba(127, 127, 127, 0.14);
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
  border-radius: 8px;
  display: block;
}

.bubble-stopped {
  margin: 4px 0 0;
  font-size: 12px;
  opacity: 0.6;
}

.composer-wrap {
  flex: none;
  width: 100%;
  max-width: 820px;
  margin: 0 auto;
  padding: 8px 16px 10px;
}

.composer-stop {
  display: flex;
  justify-content: center;
  padding-bottom: 6px;
}

.disclaimer {
  margin: 6px 0 0;
  text-align: center;
  font-size: 11px;
  opacity: 0.55;
}

/* 窄屏：会话栏收起，主区占满 */
@media (max-width: 768px) {
  .sidebar {
    display: none;
  }
}
</style>
