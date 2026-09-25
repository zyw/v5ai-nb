<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { NAlert, NButton, NCard, NEmpty, NIcon, NInput, NSelect, NTag, useMessage, useThemeVars, type SelectOption } from 'naive-ui'
import { Activity, Bot, Check, CircleStop, Clock3, Copy, Image, MessageSquare, Play, RefreshCw, RotateCcw, Send, Settings2, Sparkles, X } from 'lucide-vue-next'
import PageHeader from '../components/PageHeader.vue'
import ChatMessageBody from '../components/ChatMessageBody.vue'
import MessageActions from '../components/MessageActions.vue'
import { adminDebugStream, listAgents, uploadResource, type AgentResponse, type AttachmentRequest } from '../api/client'
import { adminToken } from '../stores/session'
import { mergeHits, parseHits, type ChatMsg } from '../utils/chatStream'

interface DebugEvent {
  id: number
  type: string
  at: string
  payload: unknown
  summary: string
}

const message = useMessage()
const themeVars = useThemeVars()
/** 调试台区域语义令牌：Naive 的 --n-* 变量只在 Naive 组件元素上生效（不注入 :root），
 *  本页大量自定义容器直接引用会取到空值，导致 tint 背景全部失效、整页糊成同一底色。
 *  这里把主题色读成 --debug-* 变量挂到页根（:style），随明暗主题联动，供区域差异化着色。 */
const debugVars = computed<Record<string, string>>(() => ({
  '--debug-surface': themeVars.value.cardColor,
  '--debug-embedded': themeVars.value.actionColor,
  '--debug-border': themeVars.value.borderColor,
  '--debug-divider': themeVars.value.dividerColor,
  '--debug-ink': themeVars.value.textColor1,
  '--debug-ink-2': themeVars.value.textColor2,
  '--debug-muted': themeVars.value.textColor3,
  '--debug-primary': themeVars.value.primaryColor,
  '--debug-info': themeVars.value.infoColor,
  '--debug-success': themeVars.value.successColor,
  '--debug-warning': themeVars.value.warningColor,
  '--debug-error': themeVars.value.errorColor
}))
const loadingAgents = ref(false)
const agents = ref<AgentResponse[]>([])
const selectedAgentKey = ref<string | null>(null)
const query = ref('')
const messages = ref<ChatMsg[]>([])
const events = ref<DebugEvent[]>([])
const runId = ref('')
const runStatus = ref<'READY' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'CANCELED'>('READY')
const runStartedAt = ref<number | null>(null)
const runFinishedAt = ref<number | null>(null)
const usage = ref<Record<string, unknown> | null>(null)
const loading = ref(false)
const uploading = ref(false)
const pendingImages = ref<{ file: File; url: string }[]>([])
const imageInput = ref<HTMLInputElement | null>(null)
const listRef = ref<HTMLElement | null>(null)
const inspectorRef = ref<HTMLElement | null>(null)
const activeController = ref<AbortController | null>(null)
const eventSequence = ref(0)
const durationTick = ref(0)
let durationTimer: ReturnType<typeof setInterval> | undefined
const BOTTOM_THRESHOLD = 48
const STREAM_SILENCE_TIMEOUT_MS = 60_000

const selectedAgent = computed(() => agents.value.find((agent) => agent.agentKey === selectedAgentKey.value) ?? null)
const agentOptions = computed<SelectOption[]>(() => agents.value.map((agent) => ({
  label: `${agent.name}（${agent.agentKey}）`, value: agent.agentKey, disabled: agent.status === 'DISABLED'
})))
const started = computed(() => messages.value.length > 0)
const canSend = computed(() => !!selectedAgent.value && selectedAgent.value.status !== 'DISABLED' && !loading.value && !uploading.value && (query.value.trim().length > 0 || pendingImages.value.length > 0))
const runDuration = computed(() => {
  if (!runStartedAt.value) return '—'
  void durationTick.value
  const end = runFinishedAt.value ?? Date.now()
  return `${Math.max(0, (end - runStartedAt.value) / 1000).toFixed(1)}s`
})
const currentRunLabel = computed(() => runId.value ? runId.value.slice(0, 12) : '尚未运行')
const capabilityItems = computed(() => {
  const agent = selectedAgent.value
  if (!agent) return []
  return [
    { label: '记忆', enabled: agent.memoryEnabled },
    { label: '知识检索', enabled: agent.ragEnabled },
    { label: 'MCP 工具', enabled: agent.mcpEnabled },
    { label: 'Skill', enabled: agent.skillEnabled },
    { label: '联网搜索', enabled: agent.webSearchEnabled }
  ]
})

function statusType(status?: string): 'default' | 'success' | 'warning' | 'error' | 'info' {
  if (status === 'PUBLISHED' || status === 'SUCCEEDED') return 'success'
  if (status === 'DISABLED' || status === 'FAILED') return 'error'
  if (status === 'RUNNING') return 'warning'
  return 'info'
}

function parseJson(value: string): unknown {
  if (!value) return null
  try { return JSON.parse(value) } catch { return value }
}

function parseRuntimeEvent(data: string): { runId?: string; payload?: unknown } {
  const parsed = parseJson(data)
  if (parsed && typeof parsed === 'object') {
    const body = parsed as { runId?: string; payload?: unknown }
    return { runId: body.runId, payload: body.payload }
  }
  return { payload: parsed }
}

function parseDelta(data: string): string {
  const { payload } = parseRuntimeEvent(data)
  if (typeof payload === 'string') return payload
  if (payload && typeof payload === 'object') {
    const body = payload as { text?: unknown; answer?: unknown; content?: unknown }
    if (typeof body.text === 'string') return body.text
    if (typeof body.answer === 'string') return body.answer
    if (typeof body.content === 'string') return body.content
  }
  return ''
}

function formatPayload(payload: unknown): string {
  if (payload == null || payload === '') return ''
  if (typeof payload === 'string') {
    const parsed = parseJson(payload)
    return typeof parsed === 'string' ? parsed : JSON.stringify(parsed, null, 2)
  }
  return JSON.stringify(payload, null, 2)
}

function eventSummary(type: string, payload: unknown): string {
  if (type === 'TOOL_CALL' && payload && typeof payload === 'object') {
    const body = payload as { toolName?: unknown; name?: unknown }
    return body.toolName || body.name ? `调用工具：${String(body.toolName ?? body.name)}` : '调用工具'
  }
  if (type === 'TOOL_RESULT') return '工具返回结果'
  if (type === 'RETRIEVAL') return Array.isArray(payload) ? `命中 ${payload.length} 条知识片段` : '知识检索完成'
  const labels: Record<string, string> = {
    RUN_STARTED: '运行开始', MODEL_CALL: '模型调用', REASONING_DELTA: '思考过程', TEXT_DELTA: '回答输出',
    MESSAGE_COMPLETED: '消息完成', RUN_COMPLETED: '运行完成', RUN_FAILED: '运行失败', PERMISSION_REQUIRED: '等待授权'
  }
  return labels[type] ?? type
}

function addEvent(type: string, data: string): unknown {
  /* 增长前先记下是否已在底部：只有原本就在底部时才跟随，向上翻阅旧事件时不被拽回 */
  const el = eventScrollEl()
  const wasNearBottom = el ? isNearBottom(el) : true
  const parsed = parseRuntimeEvent(data)
  events.value.push({ id: ++eventSequence.value, type, at: new Date().toLocaleTimeString('zh-CN', { hour12: false }), payload: parsed.payload, summary: eventSummary(type, parsed.payload) })
  if (wasNearBottom) void nextTick(scrollInspectorToBottom)
  return parsed.payload
}

function loadPresetQuestions(agent: AgentResponse | null): string[] {
  if (!agent?.presetQuestions) return []
  try {
    const parsed = JSON.parse(agent.presetQuestions)
    return Array.isArray(parsed) ? parsed.filter((item): item is string => typeof item === 'string') : []
  } catch { return [] }
}

async function loadAgents() {
  loadingAgents.value = true
  try {
    const result = await listAgents(adminToken.value, { pageNum: 1, pageSize: 100 })
    agents.value = result.rows
    if (!selectedAgentKey.value || !agents.value.some((agent) => agent.agentKey === selectedAgentKey.value)) {
      selectedAgentKey.value = agents.value.find((agent) => agent.status !== 'DISABLED')?.agentKey ?? agents.value[0]?.agentKey ?? null
    }
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载 Agent 列表失败')
  } finally { loadingAgents.value = false }
}

function clearPendingImages(revoke = true) {
  if (revoke) pendingImages.value.forEach((item) => URL.revokeObjectURL(item.url))
  pendingImages.value = []
}

function resetRunState() {
  if (durationTimer) clearInterval(durationTimer)
  durationTimer = undefined
  runId.value = ''; runStatus.value = 'READY'; runStartedAt.value = null; runFinishedAt.value = null; usage.value = null; events.value = []; eventSequence.value = 0
}

function resetSession() {
  activeController.value?.abort(); activeController.value = null; loading.value = false; messages.value = []; query.value = ''; clearPendingImages(); resetRunState()
}

function handleAgentChange(agentKey: string) {
  if (agentKey === selectedAgentKey.value) return
  resetSession(); selectedAgentKey.value = agentKey
}

function pickImages() {
  if (!selectedAgent.value || loading.value || uploading.value) return
  imageInput.value?.click()
}

function onImagesChosen(event: Event) {
  const input = event.target as HTMLInputElement
  const chosen = Array.from(input.files ?? [])
  input.value = ''
  for (const file of chosen) {
    if (pendingImages.value.length >= 3) { message.warning('每条消息最多 3 张图片'); break }
    if (!['image/png', 'image/jpeg', 'image/webp'].includes(file.type)) { message.warning('只支持 PNG / JPEG / WebP 图片'); continue }
    if (file.size > 5 * 1024 * 1024) { message.warning('单张图片不能超过 5MB'); continue }
    pendingImages.value.push({ file, url: URL.createObjectURL(file) })
  }
}

function removePendingImage(index: number) {
  const [removed] = pendingImages.value.splice(index, 1)
  if (removed) URL.revokeObjectURL(removed.url)
}

function isNearBottom(el: HTMLElement): boolean { return el.scrollHeight - el.scrollTop - el.clientHeight < BOTTOM_THRESHOLD }
function scrollToBottom() { if (listRef.value) listRef.value.scrollTop = listRef.value.scrollHeight }
/* 事件时间线的滚动发生在事件卡内部的 .n-card-content 上（不是整个右栏 aside），保证「本次运行」卡片固定不动 */
function eventScrollEl(): HTMLElement | null { return inspectorRef.value?.querySelector<HTMLElement>('.event-card > .n-card-content') ?? null }
function scrollInspectorToBottom() { const el = eventScrollEl(); if (el) el.scrollTop = el.scrollHeight }

async function runTurn(question: string, extras: { images?: string[]; attachments?: AttachmentRequest[] } = {}) {
  if (!selectedAgent.value) return
  messages.value.push({ role: 'user', content: question, images: extras.images, attachmentRefs: extras.attachments })
  const assistant = reactive<ChatMsg>({ role: 'assistant', content: '', loading: true, reasoningOpen: true })
  const assistantIndex = messages.value.push(assistant) - 1
  const controller = new AbortController()
  activeController.value = controller; loading.value = true; resetRunState(); runStatus.value = 'RUNNING'; runStartedAt.value = Date.now(); durationTimer = setInterval(() => { durationTick.value++ }, 250)
  await nextTick(); scrollToBottom()
  let watchdog: ReturnType<typeof setTimeout> | undefined
  const resetWatchdog = () => { if (watchdog) clearTimeout(watchdog); watchdog = setTimeout(() => controller.abort(), STREAM_SILENCE_TIMEOUT_MS) }
  resetWatchdog()
  try {
    await adminDebugStream(selectedAgent.value.agentKey, adminToken.value, { query: question, attachments: extras.attachments }, (event, data) => {
      resetWatchdog()
      const wasChatNearBottom = listRef.value ? isNearBottom(listRef.value) : false
      const payload = addEvent(event, data)
      const decoded = parseRuntimeEvent(data)
      if (decoded.runId) runId.value = decoded.runId
      const target = messages.value[assistantIndex]
      if (!target || target.role !== 'assistant') return
      if (event === 'REASONING_DELTA') target.reasoning = (target.reasoning ?? '') + parseDelta(data)
      else if (event === 'TEXT_DELTA') { if (!target.content) target.reasoningOpen = false; target.content += parseDelta(data) }
      else if (event === 'RETRIEVAL') target.citations = mergeHits(target.citations, parseHits(typeof payload === 'string' ? payload : JSON.stringify(payload)))
      else if (event === 'RUN_FAILED') { target.error = typeof payload === 'string' ? payload : formatPayload(payload); runStatus.value = 'FAILED' }
      else if (event === 'RUN_COMPLETED') {
        runStatus.value = 'SUCCEEDED'
        // RUN_COMPLETED 的 payload 是 JSON 串（RunUsage.toJson，字段平铺：promptTokens/completionTokens/totalTokens/durationMs），
        // 不是对象——先解一层再 parse；漏了这一步「用量」永远显示为「—」
        const usagePayload = typeof payload === 'string' ? parseJson(payload as string) : payload
        if (usagePayload && typeof usagePayload === 'object') usage.value = usagePayload as Record<string, unknown>
      } else if (event === 'MESSAGE_COMPLETED' && !target.content) target.content = parseDelta(data)
      if (wasChatNearBottom) void nextTick(scrollToBottom)
    }, controller.signal)
  } catch (e) {
    const target = messages.value[assistantIndex]
    if (e instanceof DOMException && e.name === 'AbortError' && runStatus.value === 'RUNNING') { runStatus.value = 'CANCELED'; if (target) target.error = `流式响应已中断（${STREAM_SILENCE_TIMEOUT_MS / 1000} 秒无数据或手动停止）` }
    else { runStatus.value = 'FAILED'; if (target) target.error = e instanceof Error ? e.message : '调用失败' }
  } finally {
    if (watchdog) clearTimeout(watchdog)
    if (durationTimer) clearInterval(durationTimer)
    durationTimer = undefined
    if (runStatus.value === 'RUNNING') runStatus.value = 'SUCCEEDED'
    runFinishedAt.value = Date.now(); loading.value = false
    const target = messages.value[assistantIndex]
    if (target) target.loading = false
    if (activeController.value === controller) activeController.value = null
  }
}

async function send() {
  const question = query.value.trim()
  if (!canSend.value || !selectedAgent.value) return
  query.value = ''
  const files = pendingImages.value.map((item) => item.file); const urls = pendingImages.value.map((item) => item.url); clearPendingImages(false)
  let attachments: AttachmentRequest[] = []
  if (files.length) {
    uploading.value = true
    try {
      const resources = await Promise.all(files.map((file) => uploadResource(adminToken.value, file, { bizType: 'ATTACHMENT' })))
      attachments = resources.map((resource) => ({ type: 'IMAGE', resourceId: resource.id }))
    } catch (e) { urls.forEach((url) => URL.revokeObjectURL(url)); message.error(e instanceof Error ? e.message : '图片上传失败'); return }
    finally { uploading.value = false }
  }
  await runTurn(question, { images: urls, attachments })
}

function stopRun() { activeController.value?.abort() }
function ask(question: string) { query.value = question; void send() }
async function regenerate(index: number) {
  if (loading.value) return
  const question = messages.value[index - 1]
  if (!question || question.role !== 'user') return
  messages.value.splice(index); await nextTick(); await runTurn(question.content, { images: question.images, attachments: question.attachmentRefs })
}
function copyRunId() { if (runId.value) { void navigator.clipboard?.writeText(runId.value); message.success('Run ID 已复制') } }
function formatUsage(value: unknown): string {
  if (!value || typeof value !== 'object') return '—'
  const record = value as Record<string, unknown>; const total = record.totalTokens ?? record.total_tokens ?? record.tokens
  return total == null ? '已回报' : `${String(total)} tokens`
}

onMounted(() => { void loadAgents() })
onBeforeUnmount(() => { activeController.value?.abort(); if (durationTimer) clearInterval(durationTimer); clearPendingImages() })
</script>

<template>
  <div class="page debug-page" :style="debugVars">
    <PageHeader title="调试工具" description="使用当前 Agent 配置进行实时调试，直接观察回答、工具调用与运行事件。">
      <template #actions><n-button size="small" secondary :loading="loadingAgents" @click="loadAgents"><template #icon><n-icon :component="RefreshCw" /></template>刷新 Agent</n-button></template>
    </PageHeader>

    <div class="debug-toolbar">
      <div class="toolbar-agent"><n-icon :component="Bot" size="18" /><n-select :value="selectedAgentKey" :options="agentOptions" placeholder="选择要调试的 Agent" filterable :loading="loadingAgents" style="min-width: 280px" @update:value="handleAgentChange" /><n-tag v-if="selectedAgent" size="small" :type="statusType(selectedAgent.status)" :bordered="false">{{ selectedAgent.status === 'DRAFT' ? '草稿实时调试' : selectedAgent.status === 'PUBLISHED' ? `已发布 v${selectedAgent.publishedVersion ?? '-'}` : '已禁用' }}</n-tag></div>
      <div class="toolbar-note"><n-icon :component="Sparkles" size="14" />使用管理端调试链路，不需要 Agent API Key</div>
    </div>

    <div class="debug-workbench">
      <aside class="debug-sidebar">
        <n-card size="small" :bordered="false" class="side-card">
          <template #header><span class="section-title"><n-icon :component="Settings2" />调试上下文</span></template>
          <template v-if="selectedAgent">
            <div class="agent-context"><div class="agent-avatar"><n-icon :component="Bot" size="22" /></div><div class="agent-context-copy"><strong>{{ selectedAgent.name }}</strong><span>{{ selectedAgent.agentKey }}</span></div></div>
            <p class="context-description">{{ selectedAgent.description || '暂无描述' }}</p>
            <div class="context-row"><span>对话模型</span><strong>{{ selectedAgent.modelName || `#${selectedAgent.modelId}` }}</strong></div>
            <div class="context-row"><span>调试模式</span><strong>实时编辑配置</strong></div>
          </template>
          <n-empty v-else description="请选择 Agent" size="small" />
        </n-card>
        <n-card v-if="selectedAgent" size="small" :bordered="false" class="side-card">
          <template #header><span class="section-title"><n-icon :component="Activity" />能力状态</span></template>
          <div class="capability-list"><div v-for="item in capabilityItems" :key="item.label" class="capability-row"><span>{{ item.label }}</span><n-tag size="tiny" :type="item.enabled ? 'success' : 'default'" :bordered="false"><template #icon><n-icon :component="item.enabled ? Check : X" /></template>{{ item.enabled ? '已启用' : '未启用' }}</n-tag></div></div>
          <n-alert v-if="selectedAgent.showCitations === false" class="side-alert" type="info" :show-icon="false">引用已关闭，仅影响展示，不影响检索和记录。</n-alert>
        </n-card>
      </aside>

      <main class="debug-chat">
        <div class="chat-header"><div><div class="chat-title"><n-icon :component="MessageSquare" />对话调试</div><div class="chat-subtitle">{{ started ? '流式运行中可实时查看回答和事件' : '发送一条消息开始调试当前配置' }}</div></div><n-button v-if="started" size="small" quaternary @click="resetSession">清空对话</n-button></div>
        <div ref="listRef" class="chat-content">
          <div v-if="!selectedAgent" class="empty-panel"><n-empty description="请选择一个 Agent 开始调试" /></div>
          <div v-else-if="!started" class="chat-intro">
            <div class="intro-icon"><n-icon :component="Bot" size="28" /></div><h2>{{ selectedAgent.name }}</h2><p>{{ selectedAgent.greeting || selectedAgent.description || '开始一轮实时调试，验证当前 Agent 的行为。' }}</p>
            <div v-if="loadPresetQuestions(selectedAgent).length" class="preset-list"><button v-for="question in loadPresetQuestions(selectedAgent)" :key="question" type="button" class="preset-item" @click="ask(question)">{{ question }}</button></div>
          </div>
          <div v-else class="message-list">
            <div v-for="(msg, index) in messages" :key="index" :class="['message-row', msg.role]"><div class="message-avatar"><n-icon :component="msg.role === 'user' ? MessageSquare : Bot" size="15" /></div><div class="message-body"><div class="message-role">{{ msg.role === 'user' ? '调试输入' : selectedAgent.name }}</div><div class="message-bubble"><div v-if="msg.images?.length" class="bubble-images"><img v-for="(url, imageIndex) in msg.images" :key="imageIndex" :src="url" alt="提问携带的图片" /></div><span v-if="msg.role === 'user' && msg.content">{{ msg.content }}</span><ChatMessageBody v-if="msg.role === 'assistant'" :msg="msg" :show-citations="selectedAgent.showCitations ?? true" /></div><MessageActions :text="msg.content" :can-regenerate="msg.role === 'assistant'" :busy="loading" :align="msg.role === 'user' ? 'end' : 'start'" @regenerate="regenerate(index)" /></div></div>
          </div>
        </div>
        <div class="composer">
          <div v-if="pendingImages.length" class="pending-images"><div v-for="(item, index) in pendingImages" :key="item.url" class="pending-image"><img :src="item.url" :alt="item.file.name" /><n-button text size="tiny" class="pending-remove" :aria-label="`移除图片 ${item.file.name}`" @click="removePendingImage(index)"><template #icon><n-icon :component="X" :size="13" /></template></n-button></div></div>
          <n-input v-model:value="query" type="textarea" :autosize="{ minRows: 2, maxRows: 5 }" :disabled="!selectedAgent || selectedAgent.status === 'DISABLED' || loading" placeholder="输入问题，按 Enter 发送，Shift + Enter 换行" @keydown.enter.exact.prevent="send" />
          <input ref="imageInput" class="hidden-file-input" type="file" accept="image/png,image/jpeg,image/webp" multiple tabindex="-1" aria-hidden="true" @change="onImagesChosen" />
          <div class="composer-footer"><span class="composer-hint">{{ selectedAgent?.status === 'DISABLED' ? '当前 Agent 已禁用，无法调试。' : 'AI 输出可能存在误差，请结合右侧事件和实际配置核对结果。' }}</span><div class="composer-actions"><n-button quaternary circle :disabled="!selectedAgent || loading || uploading" aria-label="添加图片" title="添加图片；如果当前模型不支持，发送时会提示" @click="pickImages"><template #icon><n-icon :component="Image" /></template></n-button><n-button v-if="loading" type="warning" secondary @click="stopRun"><template #icon><n-icon :component="CircleStop" /></template>停止</n-button><n-button v-else type="primary" :disabled="!canSend" :loading="uploading" @click="send"><template #icon><n-icon :component="Send" /></template>发送</n-button></div></div>
        </div>
      </main>

      <aside ref="inspectorRef" class="debug-inspector">
        <n-card size="small" :bordered="false" class="inspector-card run-card"><template #header><span class="section-title tone-info"><n-icon :component="Activity" />本次运行</span></template><div class="run-state"><n-tag size="small" :type="statusType(runStatus)" :bordered="false">{{ runStatus }}</n-tag><span>{{ currentRunLabel }}</span><n-button v-if="runId" text size="tiny" aria-label="复制 Run ID" @click="copyRunId"><template #icon><n-icon :component="Copy" /></template></n-button></div><div class="run-metrics"><div><span><n-icon :component="Clock3" />耗时</span><strong>{{ runDuration }}</strong></div><div><span><n-icon :component="Sparkles" />用量</span><strong>{{ formatUsage(usage) }}</strong></div></div><n-button v-if="runStatus === 'FAILED' || runStatus === 'CANCELED'" block size="small" secondary type="primary" :disabled="loading" @click="messages.length ? regenerate(messages.length - 1) : undefined"><template #icon><n-icon :component="RotateCcw" /></template>重试本轮</n-button></n-card>
        <n-card size="small" :bordered="false" class="inspector-card event-card"><template #header><div class="event-header"><span class="section-title tone-info"><n-icon :component="Activity" />事件时间线</span><n-tag size="tiny" :bordered="false">{{ events.length }}</n-tag></div></template><div v-if="!events.length" class="event-empty"><n-icon :component="Play" size="18" /><span>运行后显示模型、工具和检索事件</span></div><div v-else class="event-list"><details v-for="event in events" :key="event.id" class="event-item"><summary><span class="event-dot" :class="event.type.toLowerCase()"></span><span class="event-main"><strong>{{ event.summary }}</strong><small>{{ event.type }}</small></span><time>{{ event.at }}</time></summary><pre v-if="event.payload !== undefined && event.payload !== null" class="event-payload">{{ formatPayload(event.payload) }}</pre></details></div></n-card>
      </aside>
    </div>
  </div>
</template>

<style scoped>
/*
 * 区域差异化调色：Naive 的 --n-* 变量只挂在 Naive 组件元素上（不注入 :root），
 * 页根通过 debugVars（useThemeVars，随明暗主题联动）注入一套 --debug-* 语义令牌，
 * 供本页自定义容器（.debug-chat / .chat-header / .message-bubble…）引用，让各功能区一眼可分：
 *   工具栏 = 主色紫；左栏(上下文/能力) = 中性灰；中栏(对话) = 纯白；右栏(运行/事件) = 青色。
 */
.debug-page { display:flex; flex-direction:column; gap:12px; min-width:0; height:calc(100dvh - var(--app-topbar-height) - 52px); min-height:0; overflow:hidden; }

/* 顶部控制条：唯一带主色淡底 + 主色描边的操作区 */
.debug-toolbar { display:flex; align-items:center; justify-content:space-between; flex:none; gap:16px; padding:10px 14px; border:1px solid color-mix(in srgb,var(--debug-primary) 18%,var(--debug-border)); border-radius:12px; background:color-mix(in srgb,var(--debug-primary) 5%,var(--debug-surface)); box-shadow:0 4px 16px color-mix(in srgb,var(--debug-primary) 8%,transparent); }
.toolbar-agent,.toolbar-note,.event-header,.section-title,.run-state,.run-metrics span,.agent-context,.capability-row { display:flex; align-items:center; }
.toolbar-agent { gap:10px; min-width:0; } .toolbar-note { gap:6px; color:var(--debug-muted); font-size:12px; } .toolbar-note :deep(svg) { color:var(--debug-primary); }
.debug-workbench { display:grid; grid-template-columns:238px minmax(0,1fr) 318px; grid-template-rows:minmax(0,1fr); flex:1; gap:12px; min-width:0; min-height:0; overflow:hidden; }
.debug-sidebar,.debug-inspector { min-width:0; min-height:0; overflow-y:auto; overflow-x:hidden; scrollbar-width:thin; } .debug-sidebar { display:flex; flex-direction:column; gap:12px; }
/* 左栏：中性灰面板（上下文 / 能力配置） */
.side-card { background:color-mix(in srgb,var(--debug-ink) 4%,var(--debug-surface)); border:1px solid var(--debug-border); border-radius:12px; box-shadow:0 2px 8px color-mix(in srgb,var(--debug-ink) 5%,transparent); }
/* 右栏：青色面板（运行 / 事件监控） */
.inspector-card { background:color-mix(in srgb,var(--debug-info) 6%,var(--debug-surface)); border:1px solid color-mix(in srgb,var(--debug-info) 24%,var(--debug-border)); border-radius:12px; box-shadow:0 2px 10px color-mix(in srgb,var(--debug-info) 8%,transparent); }
/* 页卡标题：左侧同色小竖条 + 图标着色，把每个功能块钉成独立分区（tone-info 用于右栏） */
.section-title { --st-accent:var(--debug-primary); position:relative; gap:8px; padding-left:12px; font-size:13px; font-weight:600; } .section-title::before { content:''; position:absolute; left:2px; top:50%; transform:translateY(-50%); width:3px; height:14px; border-radius:2px; background:var(--st-accent); } .section-title :deep(svg) { color:var(--st-accent); } .section-title.tone-info { --st-accent:var(--debug-info); } .section-title.tone-success { --st-accent:var(--debug-success); }
.side-card :deep(.n-card-header),.inspector-card :deep(.n-card-header) { border-bottom:1px solid var(--debug-divider); }
.agent-context { gap:10px; } .agent-avatar,.intro-icon,.message-avatar { display:inline-flex; align-items:center; justify-content:center; flex:none; color:var(--debug-primary); background:color-mix(in srgb,var(--debug-primary) 14%,transparent); }
.agent-avatar { width:36px; height:36px; border-radius:10px; } .agent-context-copy { display:flex; flex-direction:column; min-width:0; } .agent-context-copy strong { overflow:hidden; text-overflow:ellipsis; white-space:nowrap; } .agent-context-copy span { margin-top:2px; color:var(--debug-muted); font-size:11px; overflow-wrap:anywhere; }
.context-description { margin:12px 0; color:var(--debug-ink-2); font-size:12px; line-height:1.6; } .context-row { display:flex; justify-content:space-between; gap:8px; padding:7px 0; border-top:1px solid var(--debug-divider); font-size:12px; } .context-row span { color:var(--debug-muted); } .context-row strong { min-width:0; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; font-weight:500; }
.capability-list { display:flex; flex-direction:column; gap:6px; } .capability-row { justify-content:space-between; gap:8px; padding:7px 8px; border-radius:8px; background:var(--debug-surface); font-size:12px; } .side-alert { margin-top:14px; font-size:11px; }
/* 中栏：纯白聊天面板（主工作区） */
.debug-chat { display:flex; flex-direction:column; min-width:0; min-height:0; overflow:hidden; border:1px solid var(--debug-border); border-radius:12px; background:var(--debug-surface); box-shadow:0 6px 24px color-mix(in srgb,var(--debug-ink) 8%,transparent); }
.chat-header { display:flex; align-items:center; justify-content:space-between; gap:12px; padding:12px 16px; border-bottom:1px solid var(--debug-divider); background:color-mix(in srgb,var(--debug-primary) 3%,var(--debug-surface)); } .chat-title { display:flex; align-items:center; gap:7px; font-size:14px; font-weight:600; } .chat-title :deep(svg) { color:var(--debug-primary); } .chat-subtitle { margin-top:3px; color:var(--debug-muted); font-size:11px; }
.chat-content { flex:1; min-height:0; overflow:auto; padding:20px 22px; } .empty-panel { display:grid; height:100%; place-items:center; } .chat-intro { display:flex; flex-direction:column; align-items:center; justify-content:center; min-height:100%; text-align:center; } .intro-icon { width:58px; height:58px; border-radius:16px; } .chat-intro h2 { margin:12px 0 4px; font-size:18px; } .chat-intro p { max-width:560px; margin:0; color:var(--debug-ink-2); font-size:13px; line-height:1.65; }
.preset-list { display:flex; flex-wrap:wrap; justify-content:center; gap:8px; max-width:680px; margin-top:22px; } .preset-item { padding:8px 12px; border:1px solid var(--debug-border); border-radius:8px; background:var(--debug-surface); color:var(--debug-ink-2); cursor:pointer; font-size:12px; transition:border-color .18s ease,color .18s ease,background .18s ease; } .preset-item:hover { border-color:var(--debug-primary); color:var(--debug-primary); background:color-mix(in srgb,var(--debug-primary) 10%,var(--debug-surface)); }
.message-list { display:flex; flex-direction:column; gap:20px; max-width:860px; margin:0 auto; } .message-row { display:flex; gap:9px; align-items:flex-start; } .message-row.user { flex-direction:row-reverse; } .message-avatar { width:28px; height:28px; border-radius:8px; } .message-row.user .message-avatar { color:var(--debug-ink-2); background:var(--debug-embedded); } .message-body { min-width:0; max-width:min(82%,720px); } .message-row.user .message-body { text-align:right; } .message-role { margin:0 0 5px; color:var(--debug-muted); font-size:11px; } .message-bubble { padding:11px 13px; border-radius:10px; background:var(--debug-embedded); color:var(--debug-ink); font-size:13px; line-height:1.7; text-align:left; overflow-wrap:anywhere; } .message-row.user .message-bubble { background:var(--debug-primary); color:#fff; }
.bubble-images { display:flex; flex-wrap:wrap; gap:6px; margin-bottom:7px; } .bubble-images img { width:110px; height:82px; object-fit:cover; border-radius:7px; }
.composer { flex:none; padding:10px 14px 12px; border-top:1px solid var(--debug-divider); background:color-mix(in srgb,var(--debug-primary) 2%,var(--debug-surface)); } .composer :deep(.n-input) { background:var(--debug-embedded); } .composer-footer { display:grid; grid-template-columns:1fr auto 1fr; align-items:center; margin-top:8px; } .composer-hint { grid-column:2; text-align:center; color:var(--debug-muted); font-size:11px; } .composer-actions { grid-column:3; display:flex; justify-content:flex-end; align-items:center; gap:8px; } .hidden-file-input { display:none; }
.pending-images { display:flex; gap:8px; margin-bottom:8px; } .pending-image { position:relative; width:48px; height:48px; } .pending-image img { width:100%; height:100%; border-radius:7px; object-fit:cover; } .pending-remove { position:absolute; top:-7px; right:-7px; width:20px; height:20px; border-radius:50%; background:var(--debug-surface); box-shadow:0 1px 4px rgba(0,0,0,.18); }
.debug-inspector { display:flex; flex-direction:column; gap:12px; overflow:hidden; } .inspector-card :deep(.n-card-content) { min-width:0; } .run-state { gap:8px; min-width:0; color:var(--debug-muted); font-size:11px; } .run-state span { overflow:hidden; text-overflow:ellipsis; white-space:nowrap; } .run-state .n-button { margin-left:auto; }
.run-metrics { display:grid; grid-template-columns:1fr 1fr; gap:8px; margin:14px 0; } .run-metrics > div { padding:9px; border-radius:8px; background:var(--debug-surface); } .run-metrics span { gap:5px; color:var(--debug-muted); font-size:11px; } .run-metrics strong { display:block; margin-top:4px; font-size:13px; font-weight:600; }
.event-header { justify-content:space-between; } .event-card { flex:1; min-height:0; } .event-card :deep(.n-card-content) { min-height:0; overflow-y:auto; overflow-x:hidden; } .event-empty { display:flex; flex-direction:column; align-items:center; justify-content:center; gap:8px; min-height:180px; color:var(--debug-muted); font-size:12px; text-align:center; } .event-list { min-width:0; } .event-item { border-bottom:1px solid var(--debug-divider); } .event-item summary { display:flex; align-items:center; gap:8px; min-height:48px; cursor:pointer; list-style:none; border-radius:7px; transition:background .18s ease; } .event-item summary:hover { background:color-mix(in srgb,var(--debug-info) 8%,transparent); } .event-item summary::-webkit-details-marker { display:none; } .event-dot { width:7px; height:7px; flex:none; border-radius:50%; background:var(--debug-info); } .event-dot.run_failed { background:var(--debug-error); } .event-dot.run_completed { background:var(--debug-success); } .event-dot.tool_call,.event-dot.tool_result { background:var(--debug-warning); } .event-main { display:flex; flex:1; flex-direction:column; min-width:0; } .event-main strong { overflow:hidden; text-overflow:ellipsis; white-space:nowrap; font-size:12px; font-weight:500; } .event-main small { margin-top:2px; color:var(--debug-muted); font-size:10px; } .event-item time { color:var(--debug-muted); font-size:10px; } .event-payload { max-height:180px; margin:0 0 10px 15px; padding:9px; overflow:auto; border-radius:7px; background:var(--debug-embedded); color:var(--debug-ink-2); font:11px/1.55 ui-monospace,SFMono-Regular,Menlo,Monaco,Consolas,monospace; white-space:pre-wrap; overflow-wrap:anywhere; }
@media (max-width:1180px) { .debug-page { height:auto; min-height:calc(100dvh - var(--app-topbar-height) - 52px); overflow:visible; } .debug-workbench { grid-template-columns:220px minmax(0,1fr); flex:none; min-height:0; overflow:visible; } .debug-inspector { grid-column:1/-1; display:grid; grid-template-columns:minmax(240px,.8fr) minmax(0,1.2fr); max-height:300px; overflow-y:auto; } }
@media (max-width:760px) { .debug-toolbar { align-items:stretch; flex-direction:column; } .toolbar-agent { flex-wrap:wrap; } .toolbar-agent .n-select { flex:1; min-width:180px !important; } .toolbar-note { font-size:11px; } .debug-workbench { display:flex; flex-direction:column; } .debug-sidebar { display:grid; grid-template-columns:1fr 1fr; overflow:visible; } .debug-inspector { display:flex; height:auto; overflow:visible; } .event-card { min-height:260px; } .debug-chat { min-height:640px; } .chat-content { padding:16px 12px; } .message-body { max-width:88%; } }
@media (max-width:520px) { .debug-sidebar { display:flex; } .chat-header { padding:12px; } .composer { padding:8px 10px 10px; } }
</style>
