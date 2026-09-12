<script setup lang="ts">
/**
 * 预览与调试：智能体编辑页右栏。面板高度固定为右栏高度，只有会话区内部滚动。
 *
 * 两种状态：
 * - 未开始：展示智能体图标/名称/描述 + 推荐问题（用户视角的首屏）。
 * - 已开始（点击推荐问题或发送过消息）：首屏信息与推荐问题整体隐藏，面板变成纯会话窗口。
 */
import { computed, ref } from 'vue'
import { NButton, NInput, NSpin, useMessage } from 'naive-ui'
import { Image, Send } from 'lucide-vue-next'
import MarkdownContent from '../../components/MarkdownContent.vue'
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

interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
}

const query = ref('')
const loading = ref(false)
const runId = ref('')
const messages = ref<ChatMessage[]>([])

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

function parseDelta(data: string): string {
  if (!data) return ''
  try {
    const body = JSON.parse(data) as { payload?: string; answer?: string; text?: string }
    return body.payload ?? body.answer ?? body.text ?? data
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

async function send() {
  const question = query.value.trim()
  if (!question || loading.value) return
  if (!props.agentKey) {
    message.warning('智能体尚未创建，无法调试')
    return
  }
  query.value = ''
  messages.value.push({ role: 'user', content: question })
  const assistantIndex = messages.value.length
  messages.value.push({ role: 'assistant', content: '' })
  loading.value = true
  runId.value = ''
  try {
    await adminDebugStream(props.agentKey, adminToken.value, { query: question }, (event, data) => {
      if (event === 'TEXT_DELTA') {
        messages.value[assistantIndex].content += parseDelta(data)
      }
      if (event === 'RUN_FAILED') {
        messages.value[assistantIndex].content = `调用失败：${parseRunFailed(data)}`
      }
      if (event === 'MESSAGE_COMPLETED' && !messages.value[assistantIndex].content) {
        messages.value[assistantIndex].content = parseDelta(data)
      }
      if (event === 'RUN_STARTED') {
        runId.value = parseRunId(data)
      }
    })
  } catch (e) {
    messages.value[assistantIndex].content = e instanceof Error ? `调用失败：${e.message}` : '调用失败'
  } finally {
    loading.value = false
  }
}

defineExpose({ reset })
</script>

<template>
  <div class="preview-panel">
    <div class="preview-head">
      <span class="preview-title">预览与调试</span>
      <n-button v-if="started" size="tiny" quaternary @click="reset">清空对话</n-button>
    </div>

    <!-- 首屏信息：仅在会话开始前展示 -->
    <template v-if="!started">
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
    </template>

    <!-- 会话区：唯一滚动区域 -->
    <div class="chat-window">
      <div v-if="!started" class="chat-idle">
        <div class="chat-idle-title">开始对话</div>
        <div class="chat-idle-sub">点击上方推荐问题，或在下方输入内容，回复将在这里显示</div>
      </div>
      <div v-for="(msg, index) in messages" :key="index" :class="['chat-row', msg.role]">
        <div class="chat-bubble">
          <MarkdownContent v-if="msg.role === 'assistant' && msg.content" :content="msg.content" />
          <template v-else>{{ msg.content || '…' }}</template>
        </div>
      </div>
      <div v-if="loading && !messages[messages.length - 1]?.content" class="chat-thinking">
        <n-spin size="small" />
        <span>正在生成…</span>
      </div>
    </div>

    <div class="chat-input">
      <n-input
        v-model:value="query"
        type="textarea"
        :autosize="{ minRows: 1, maxRows: 5 }"
        placeholder="问我任何问题"
        @keydown.enter.exact.prevent="send"
      />
      <div class="chat-actions">
        <n-button quaternary circle :disabled="!imageSupported" :title="imageTip" aria-label="添加图片">
          <template #icon><n-icon :component="Image" /></template>
        </n-button>
        <n-button circle type="primary" :loading="loading" :disabled="!query.trim()" aria-label="发送" @click="send">
          <template #icon><n-icon :component="Send" /></template>
        </n-button>
      </div>
    </div>
    <div class="chat-foot">
      <div v-if="runId" class="chat-runid">runId: {{ runId }}</div>
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

.preview-profile {
  flex: none;
  display: flex;
  gap: 12px;
  padding: 12px 2px 2px;
}

.profile-avatar {
  flex: none;
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: #e9eaee;
  color: #4b5563;
  font-size: 20px;
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
  padding: 10px 0 0;
  min-height: 0;
}

.preset-label {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 8px;
}

.preset-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  /* 推荐问题再多也不挤压会话区：超出部分在本区内滚动。 */
  max-height: 140px;
  overflow-y: auto;
}

.preset-item {
  width: 100%;
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

.chat-idle {
  margin: auto;
  text-align: center;
  padding: 8px 12px;
}

.chat-idle-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--n-text-color-2, #667085);
}

.chat-idle-sub {
  margin-top: 6px;
  font-size: 13px;
  color: var(--n-text-color-3, #98a2b3);
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

.chat-row.user .chat-bubble {
  background: var(--n-primary-color, #7c3aed);
  color: #fff;
  border-bottom-right-radius: 2px;
}

.chat-row.assistant .chat-bubble {
  background: var(--n-color-3, #f2f4f7);
  border-bottom-left-radius: 2px;
}

.chat-thinking {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--n-text-color-3, #98a2b3);
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

.chat-disclaimer {
  margin-top: 2px;
  text-align: center;
  font-size: 12px;
  color: var(--n-text-color-3, #98a2b3);
}
</style>
