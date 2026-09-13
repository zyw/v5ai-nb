<script setup lang="ts">
/**
 * 预览与调试：智能体编辑页右栏。面板高度固定为右栏高度，只有会话区内部滚动。
 *
 * 两种状态：
 * - 未开始：展示智能体图标/名称/描述 + 推荐问题（用户视角的首屏）。
 * - 已开始（点击推荐问题或发送过消息）：首屏信息与推荐问题整体隐藏，面板变成纯会话窗口。
 */
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { NButton, NIcon, NInput, NTooltip, useMessage } from 'naive-ui'
import { Image, Send } from 'lucide-vue-next'
import ChatMessageBody from '../../components/ChatMessageBody.vue'
import { parseHits, type ChatMsg } from '../../utils/chatStream'
import { adminDebugStream } from '../../api/client'
import { adminToken } from '../../stores/session'

const props = defineProps<{
  agentKey: string
  name: string
  description: string
  avatar: string
  greeting: string
  presetQuestions: string[]
  /** 当前所选模型是否支持图片输入（取决于「模型管理」里的模型配置）。 */
  imageSupported?: boolean
}>()

const message = useMessage()

const query = ref('')
const loading = ref(false)
const runId = ref('')
const messages = ref<ChatMsg[]>([])
/** 会话区滚动：贴底判断阈值与知识库问答一致。 */
const listRef = ref<HTMLElement | null>(null)
const BOTTOM_THRESHOLD = 40

/** 会话是否已开始：一开始就把首屏信息与推荐问题收起来，避免会话中被占位。 */
const started = computed(() => messages.value.length > 0)
const avatarChar = computed(() => (props.name || '智').trim().slice(0, 1))
/** 未填写欢迎语时回退到描述，避免首屏空面板。 */
const greetText = computed(() => props.greeting.trim() || props.description.trim())
const imageTip = computed(() =>
  props.imageSupported
    ? '添加图片'
    : '当前模型不支持图片输入，可在「模型管理」中为模型开启「支持图片输入」'
)

/**
 * 取出增量文本。SSE 的 data 是整个 RuntimeEvent 的 JSON（{runId,type,payload,createdAt}），
 * 直接展示会把 JSON 原样打出来，所以必须解出 payload。
 * 注意用 !== undefined 而不是 ??：payload 为空串时 ?? 会落到 return data，
 * 把整段 JSON 当成文本拼进去（表现为思考里混进 {"runId":...}）。
 */
function parseDelta(data: string): string {
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

function parseRunId(data: string): string {
  if (!data) return ''
  try {
    return (JSON.parse(data) as { runId?: string }).runId ?? ''
  } catch {
    return ''
  }
}

function parseRunFailed(data: string): string {
  if (!data) return '未知错误'
  try {
    return (JSON.parse(data) as { payload?: string }).payload || '未知错误'
  } catch {
    return data
  }
}

function reset() {
  messages.value = []
  runId.value = ''
  query.value = ''
}

/** 点击推荐问题：填入输入框并直接发送（发送后首屏信息即隐藏）。 */
function ask(question: string) {
  query.value = question
  void send()
}

/** 流「静默」多久算卡死（期间一条事件都没来）。协议正常结束时流会自行关闭，不会走到这里。 */
const STREAM_SILENCE_TIMEOUT_MS = 60_000

async function send() {
  const question = query.value.trim()
  if (!question || loading.value) return
  if (!props.agentKey) {
    message.warning('智能体尚未创建，无法调试')
    return
  }
  query.value = ''
  messages.value.push({ role: 'user', content: question })
  // 必须是 reactive 代理：SSE 回调里直接改内容，靠代理触发增量渲染；
  // 裸对象写属性不走 setter，会等 finally 里 loading 变更才整段刷出（表现为"不流式"）。
  const assistant = reactive<ChatMsg>({ role: 'assistant', content: '', loading: true, reasoningOpen: true })
  const assistantIndex = messages.value.push(assistant) - 1
  loading.value = true
  runId.value = ''
  // 主动发问时无视当前滚动位置，直接到底（用户刚发完就想看回答）
  await nextTick()
  scrollToBottom()

  // 看门狗：每条事件都重置计时；静默超时说明连接卡死（服务端不再发也不关），
  // 主动中断，否则发送按钮会永久停在 loading、且 send() 被 loading 守卫挡住无法再发。
  const controller = new AbortController()
  let watchdog: ReturnType<typeof setTimeout> | undefined
  const resetWatchdog = () => {
    if (watchdog) clearTimeout(watchdog)
    watchdog = setTimeout(() => controller.abort(), STREAM_SILENCE_TIMEOUT_MS)
  }
  resetWatchdog()

  try {
    await adminDebugStream(props.agentKey, adminToken.value, { query: question }, (event, data) => {
      resetWatchdog()
      const target = messages.value[assistantIndex]
      if (event === 'RETRIEVAL') {
        target.citations = parseHits(data)
      } else if (event === 'REASONING_DELTA' && data) {
        // 必须与回答同样解析：SSE 的 data 是整段 RuntimeEvent JSON，直接拼接会把 JSON 显示出来
        target.reasoning = (target.reasoning ?? '') + parseDelta(data)
      } else if (event === 'TEXT_DELTA') {
        // 回答开始的第一个片段：思考自动收起（只此一次，之后用户手动开合不再干预）
        if (!target.content) target.reasoningOpen = false
        target.content += parseDelta(data)
      } else if (event === 'RUN_FAILED') {
        target.error = parseRunFailed(data)
      } else if (event === 'MESSAGE_COMPLETED' && !target.content) {
        target.content = parseDelta(data)
      } else if (event === 'RUN_STARTED') {
        runId.value = parseRunId(data)
      }
    }, controller.signal)
  } catch (e) {
    const target = messages.value[assistantIndex]
    // 看门狗主动中断：给出可读原因，避免表现为「莫名其妙失败」
    target.error =
      e instanceof DOMException && e.name === 'AbortError'
        ? `流式响应超时（${STREAM_SILENCE_TIMEOUT_MS / 1000} 秒无数据），已中断`
        : e instanceof Error
          ? e.message
          : '调用失败'
  } finally {
    if (watchdog) clearTimeout(watchdog)
    // 无论流如何结束（正常收尾 / 报错 / 超时中断），都必须复位，否则按钮永久 loading
    loading.value = false
    const target = messages.value[assistantIndex]
    target.loading = false
    // 失败且无内容时不留空壳
    if (!target.content && target.error) target.content = ''
  }
}

/** 流式内容（回答 + 思考）总长度：任一片段到达即变化，作为「该滚到底了」的触发源。 */
const streamedLength = computed(() =>
  messages.value.reduce((n, m) => n + m.content.length + (m.reasoning?.length ?? 0), 0)
)

function scrollToBottom() {
  const el = listRef.value
  if (el) el.scrollTop = el.scrollHeight
}

/**
 * 自动贴底。默认 flush（pre）时刻 DOM 还是「增长前」的旧高度，正好用来判断用户当前是否停在底部：
 * 停在底部就顺势跟到底，往上翻看历史则不动滚动条，等他滚回底部后下次判定自动恢复。
 * 不用 scroll 事件判断——同帧内多次滚动会被浏览器合并成一次且只报最终位置，
 * 用户上翻后紧跟一个增量片的强制贴底会把它覆盖掉，表现为「上翻被拽回底部」。
 */
watch(streamedLength, async () => {
  const el = listRef.value
  if (!el) return
  if (el.scrollHeight - el.scrollTop - el.clientHeight > BOTTOM_THRESHOLD) return
  await nextTick()
  scrollToBottom()
})

defineExpose({ reset })
</script>

<template>
  <div class="preview-panel">
    <div class="preview-head">
      <span class="preview-title">预览与调试</span>
      <n-button v-if="started" size="tiny" quaternary @click="reset">清空对话</n-button>
    </div>

    <!-- 首屏信息：仅在会话开始前展示（会话区此时不渲染，这块居中占位） -->
    <div v-if="!started" class="preview-intro">
      <div class="preview-profile">
        <div class="profile-avatar">
          <img v-if="avatar" :src="avatar" alt="" />
          <span v-else>{{ avatarChar }}</span>
        </div>
        <div class="profile-body">
          <div class="profile-name">{{ name || '未命名智能体' }}</div>
          <div v-if="greetText" class="profile-desc">{{ greetText }}</div>
          <div v-else class="profile-desc muted">暂无描述</div>
        </div>
      </div>

      <div v-if="presetQuestions.length" class="preset-block">
        <div class="preset-label">推荐问题</div>
<!--        <n-grid :x-gap="12" :y-gap="8" :cols="2">-->
<!--          <n-grid-item v-for="(question, index) in presetQuestions" :key="index">-->
<!--            <button class="preset-item" @click="ask(question)">-->
<!--              {{ question }}-->
<!--            </button>-->
<!--          </n-grid-item>-->
<!--        </n-grid>-->
        <div class="preset-list">
          <button
            v-for="(question, index) in presetQuestions"
            :key="index"
            type="button"
            class="preset-item"
            @click="ask(question)"
          >
            {{ question }}
          </button>
        </div>
      </div>
    </div>

    <!-- 会话区：仅开始对话后存在（未开始时整块不渲染），也是唯一的滚动区域 -->
    <div v-if="started" ref="listRef" class="chat-window">
      <div v-for="(msg, index) in messages" :key="index" :class="['chat-row', msg.role]">
        <div class="chat-bubble">
          <!-- 助手消息：思考折叠 + 回答 + 引用，与知识库问答 tab 共用同一组件 -->
          <ChatMessageBody v-if="msg.role === 'assistant'" :msg="msg" />
          <template v-else>{{ msg.content }}</template>
        </div>
      </div>
    </div>

    <div class="chat-input">
      <n-input
        v-model:value="query"
        type="textarea"
        :autosize="{ minRows: 3, maxRows: 6 }"
        placeholder="问我任何问题"
        @keydown.enter.exact.prevent="send"
      />
      <div class="chat-actions">
        <n-tooltip v-if="!imageSupported" trigger="hover">
          <template #trigger>
            <n-button quaternary circle disabled :aria-label="imageTip">
              <template #icon><n-icon :component="Image" /></template>
            </n-button>
          </template>
          {{ imageTip }}
        </n-tooltip>
        <n-button v-else quaternary circle :title="imageTip" aria-label="添加图片">
          <template #icon><n-icon :component="Image" /></template>
        </n-button>
        <n-button circle type="primary" :loading="loading" :disabled="!query.trim()" aria-label="发送" @click="send">
          <template #icon><n-icon :component="Send" /></template>
        </n-button>
      </div>
    </div>
    <div class="chat-foot">
      <div class="chat-runid" v-if="runId">runId: {{ runId }}</div>
      <!-- 图片按钮被禁用时把原因直接写出来，避免用户以为「点不动是坏了」 -->
      <div v-if="!imageSupported" class="chat-image-hint">
        <n-icon :component="Image" size="13" />
        <span>当前模型未开启「支持图片输入」，暂不能发送图片</span>
      </div>
      <div class="chat-disclaimer">AI 生成内容可能有误，请核实重要信息</div>
    </div>
  </div>
</template>

<style scoped>
.preview-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  /* 面板本身不滚动：只有 .chat-window 内部滚动。
     固定区（首屏/推荐问题/输入区）总高小于面板高度，因此不写 overflow 也不会溢出。 */
  overflow: hidden;
  padding: 12px 14px 10px;
  background: var(--n-color-2, #f7f8fa);
  border: 1px solid rgba(128, 128, 128, 0.16);
  border-radius: 8px;
}

.preview-head {
  flex: none;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 10px;
  border-bottom: 1px solid rgba(128, 128, 128, 0.16);
}

.preview-title {
  font-size: 14px;
  font-weight: 600;
}

/* 未开始时占满会话区空出来的高度：内容水平 + 垂直都居中。
   justify-content 居中不依赖「推荐问题是否存在」——之前用 margin-top/bottom:auto 的写法，
   在没有预设问题（.preset-block 因 v-if 不渲染）时会把头像名称整块推到面板底部。
   居中不会裁切：内容超高时 flex 子项自身收缩（min-height:0），父级 overflow:hidden 只是兜底。 */
.preview-intro {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

/* max-width + width:100%：未超出时横向撑满（内容只占一行时会收缩），超出时限制行长。
   没有它，宽面板上的描述会拉成很长一行。 */
.preview-profile {
  flex: none;
  display: flex;
  gap: 14px;
  width: 100%;
  max-width: 700px;
  padding: 12px 2px 2px;
}

.profile-avatar {
  flex: none;
  width: 64px;
  height: 64px;
  border-radius: 50%;
  background: #e9eaee;
  color: #4b5563;
  font-size: 26px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.profile-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.profile-body {
  min-width: 0;
}

.profile-name {
  font-size: 17px;
  font-weight: 600;
  line-height: 1.4;
}

.profile-desc {
  margin-top: 6px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--n-text-color-2, #667085);
  white-space: pre-wrap;
}

.profile-desc.muted {
  color: var(--n-text-color-3, #98a2b3);
}

.preset-block {
  flex: none;
  width: 100%;
  max-width: 700px;
  padding: 14px 0 0;
  min-height: 0;
}

.preset-label {
  font-size: 13px;
  margin-bottom: 8px;
}

.preset-list {
  display: flow;
  flex-direction: column;
  gap: 6px;
}

.preset-item {

  text-align: left;
  padding: 8px 12px;
  font-size: 13px;
  line-height: 1.5;
  color: var(--n-text-color-1, #344054);
  background: var(--n-color-1, #fff);
  border: 1px solid rgba(128, 128, 128, 0.22);
  border-radius: 8px;
  cursor: pointer;
  transition: border-color 0.2s, color 0.2s;
  margin: 5px;
}

.preset-item:hover {
  border-color: var(--n-primary-color, #7c3aed);
  color: var(--n-primary-color, #7c3aed);
}

/* flex:1 + min-height:0：唯一会滚动的区域，始终占满剩余高度。 */
.chat-window {
  flex: 1;
  /* 唯一会滚动的区域；下限要小于「面板高 - 固定区高」，否则会把面板撑破。 */
  min-height: 96px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 10px;
  margin-top: 10px;
  background: var(--n-color-1, #fff);
  border-radius: 8px;
}

.chat-row {
  display: flex;
}

.chat-row.user {
  justify-content: flex-end;
}

.chat-row.assistant {
  justify-content: flex-start;
}

.chat-bubble {
  max-width: 88%;
  padding: 8px 11px;
  border-radius: 10px;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

/* 助手气泡要放折叠面板与引用列表，窄气泡会把它们挤变形：直接撑满会话区宽度。 */
.chat-row.assistant .chat-bubble {
  max-width: 100%;
  width: 100%;
}

.chat-row.user .chat-bubble {
  background: var(--n-primary-color, #7c3aed);
  color: #fff;
  border-bottom-right-radius: 2px;
}

.chat-row.assistant .chat-bubble {
  background: var(--n-color-3, #f2f4f7);
  border-bottom-left-radius: 2px;
}

.chat-input {
  flex: none;
  display: flex;
  align-items: flex-end;
  gap: 8px;
  margin-top: 10px;
}

.chat-input .n-input {
  flex: 1;
}

/* 输入框右侧的多选操作区：图片 + 发送 */
.chat-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  flex: none;
  padding-bottom: 2px;
}

.chat-foot {
  flex: none;
  margin-top: 6px;
}

.chat-runid {
  font-size: 12px;
  color: var(--n-text-color-3, #98a2b3);
  word-break: break-all;
}

.chat-image-hint {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  margin-top: 4px;
  font-size: 12px;
  color: var(--n-text-color-3, #98a2b3);
}

.chat-disclaimer {
  margin-top: 2px;
  text-align: center;
  font-size: 12px;
  color: var(--n-text-color-3, #98a2b3);
}
</style>
