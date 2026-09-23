<script setup lang="ts">
/**
 * 知识库详情页「知识问答」tab：左参数面板（检索参数 + 模型回答参数）+ 右侧多轮会话。
 * 事件流：RETRIEVAL（引用）→ REASONING_DELTA*（思考）→ TEXT_DELTA*（回答）→ RUN_COMPLETED / RUN_FAILED；
 * 思考与回答分开展示（思考默认展开，思考期间标题显示"思考中..."），页内会话不落库。
 */
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { NButton, NIcon, NInput, useMessage } from 'naive-ui'
import { Eraser, Send } from 'lucide-vue-next'
import { kbQaStream, type KbQaMessage } from '../../api/client'
import { adminToken } from '../../stores/session'
import ChatMessageBody from '../../components/ChatMessageBody.vue'
import MessageActions from '../../components/MessageActions.vue'
import { awaitingFirstToken, mergeHits, parseHits, type ChatMsg } from '../../utils/chatStream'
import KnowledgeDebugParams, { type KbModelParamsState, type KbSearchParamsState } from './KnowledgeDebugParams.vue'

const props = defineProps<{
  kbId: number
  active: boolean
  params: KbSearchParamsState
  modelParams: KbModelParamsState
  chatModels: { value: number; label: string; isDefault?: boolean }[]
  rerankModels: { value: number; label: string; isDefault?: boolean }[]
}>()

const message = useMessage()

const messages = ref<ChatMsg[]>([])
const input = ref('')
const streaming = ref(false)
const listRef = ref<HTMLElement | null>(null)
const BOTTOM_THRESHOLD = 40

const canSend = computed(() => {
  if (streaming.value) return false
  if (props.modelParams.modelId == null) return false
  const len = input.value.trim().length
  return len > 0 && len <= 8000
})

const inputLen = computed(() => input.value.length)

/** 多轮历史口径：只带上「有正文且无错误」的消息（沿用页内既有过滤规则）。 */
function historyOf(list: ChatMsg[]): KbQaMessage[] {
  return list.filter((m) => m.content && !m.error).map((m) => ({ role: m.role, content: m.content }))
}

/** 模型未选时的统一提示：发送与重新生成共用（提示后再动手，避免留下半截消息）。 */
function ensureModel(): boolean {
  if (props.modelParams.modelId == null) {
    message.warning('请先在左侧选择对话模型')
    return false
  }
  return true
}

async function send() {
  const text = input.value.trim()
  if (!text) return
  if (!ensureModel()) return
  const history = historyOf(messages.value)
  messages.value.push({ role: 'user', content: text })
  input.value = ''
  // 主动发问时无视当前滚动位置，直接到底（用户刚发完就想看回答）。
  // 消息从 0 到 1 时 .qa-list 还是刚挂载的，这里等一拍再量高度。
  await nextTick()
  if (listRef.value) listRef.value.scrollTop = listRef.value.scrollHeight
  await runTurn(text, [...history, { role: 'user', content: text }])
}

/**
 * 重新生成第 i 条（助手）回答：丢弃该回答及其之后的全部消息，用上一条提问重跑。
 * 参数取「当前」左侧面板值——调完检索参数再点一次重新生成，正是这个页面的用法。
 */
async function regenerate(i: number) {
  if (streaming.value) return
  if (!ensureModel()) return
  const question = messages.value[i - 1]
  if (!question || question.role !== 'user' || !question.content) return
  const history = historyOf(messages.value.slice(0, i - 1))
  messages.value.splice(i)
  await nextTick()
  if (listRef.value) listRef.value.scrollTop = listRef.value.scrollHeight
  await runTurn(question.content, [...history, { role: 'user', content: question.content }])
}

/** 跑一轮助手回答：追加助手消息并就地流式填充（发送与重新生成同一条路径）。 */
async function runTurn(question: string, history: KbQaMessage[]) {
  // 调用方已用 ensureModel() 拦过；此处再取一次，顺带把 props 上的 number | null 收窄成 number
  const modelId = props.modelParams.modelId
  if (modelId == null) return
  // 必须是 reactive 代理：SSE 回调里直接改内容，靠代理触发增量渲染；
  // 裸对象写属性不走 setter，会等 finally 里 streaming 变更才整段刷出（表现为"不流式"）。
  const assistant = reactive<ChatMsg>({ role: 'assistant', content: '', loading: true, reasoningOpen: true })
  messages.value.push(assistant)
  streaming.value = true
  // 重新生成时列表已存在，等一拍是为了让刚插入的助手消息参与本次贴底测量。
  await nextTick()
  if (listRef.value) listRef.value.scrollTop = listRef.value.scrollHeight
  try {
    await kbQaStream(
      adminToken.value,
      props.kbId,
      {
        modelId,
        nearbySliceCount: props.modelParams.nearbySliceCount,
        prompt: props.modelParams.prompt.trim() || undefined,
        resultCount: props.params.resultCount,
        questionRewrite: props.params.questionRewrite,
        thresholdEnabled: props.params.thresholdEnabled,
        threshold: props.params.thresholdEnabled ? props.params.threshold : undefined,
        fusionStrategy: props.params.fusionStrategy,
        rrfK: props.params.fusionStrategy === 'RRF' ? props.params.rrfK : undefined,
        denseWeight: props.params.fusionStrategy === 'WEIGHTED_SUM' ? props.params.denseWeight : undefined,
        messages: [...history, { role: 'user', content: question }]
      },
      (event, data) => {
        if (event === 'RETRIEVAL') {
          assistant.citations = mergeHits(assistant.citations, parseHits(data))
        } else if (event === 'REASONING_DELTA' && data) {
          assistant.reasoning = (assistant.reasoning ?? '') + data
        } else if (event === 'TEXT_DELTA' && data) {
          // 回答开始的第一个片段：思考自动收起（只此一次，之后用户手动开合不再干预）
          if (!assistant.content) assistant.reasoningOpen = false
          assistant.content += data
        } else if (event === 'RUN_FAILED') {
          assistant.error = data || '问答失败，请稍后重试'
        }
      }
    )
  } catch (e) {
    assistant.error = e instanceof Error ? e.message : '问答请求失败'
  } finally {
    assistant.loading = false
    streaming.value = false
    // 清空本会话内失败的空壳
    if (!assistant.content && assistant.error) assistant.content = ''
  }
}

/** 流式内容（回答 + 思考）总长度：任一片段到达即变化，作为「该滚到底了」的触发源。 */
const streamedLength = computed(() =>
  messages.value.reduce((n, m) => n + m.content.length + (m.reasoning?.length ?? 0), 0)
)

/**
 * 自动贴底。默认 flush（pre）时刻 DOM 还是「增长前」的旧高度，正好用来判断用户当前是否停在底部：
 * 停在底部就顺势跟到底，往上翻看历史则不动滚动条，等他滚回底部后下次判定自动恢复。
 * 不用 scroll 事件判断——浏览器把同帧内的多次滚动合并成一个 scroll 事件且只报最终位置，
 * 用户上翻后紧跟一个增量片的强制贴底会把它覆盖掉，表现为「上翻被拽回底部」。
 */
watch(streamedLength, async () => {
  const el = listRef.value
  if (!el) return
  if (el.scrollHeight - el.scrollTop - el.clientHeight > BOTTOM_THRESHOLD) return
  await nextTick()
  el.scrollTop = el.scrollHeight
})

function clearChat() {
  messages.value = []
}
</script>

<template>
  <div class="qa-tab">
    <aside class="qa-side">
      <KnowledgeDebugParams :params="params" :chat-models="chatModels" :rerank-models="rerankModels" :model-params="modelParams" mode="qa" />
    </aside>
    <main class="qa-main">
      <div v-if="messages.length === 0" class="qa-welcome">
        <div class="qa-avatar">Hi</div>
        <div class="qa-welcome-title">我是知识问答助手</div>
        <div class="qa-welcome-sub">
          我可以阅读 RAG 里的资料，让回答更可靠准确。你可以调整左侧的参数，预览和调试问答效果。
        </div>
      </div>

      <div v-else ref="listRef" class="qa-list">
        <div v-for="(m, i) in messages" :key="i" class="qa-row" :class="m.role">
          <div class="bubble" :class="m.role">
            <template v-if="m.role === 'user'">{{ m.content }}</template>
            <!-- 思考折叠 + 回答 + 引用：与智能体编辑页「预览与调试」共用同一组件 -->
            <ChatMessageBody v-else :msg="m" />
          </div>
          <!-- 操作栏在气泡外、气泡下方（DeepSeek 的摆法）；生成中复制禁用、「重新生成」隐藏 -->
          <MessageActions
            :text="m.content"
            :can-regenerate="m.role === 'assistant'"
            :busy="streaming"
            :align="m.role === 'user' ? 'end' : 'start'"
            @regenerate="regenerate(i)"
          />
        </div>
      </div>

      <div class="qa-input-bar">
        <n-input
          v-model:value="input"
          type="textarea"
          :autosize="{ minRows: 2, maxRows: 6 }"
          placeholder="输入你的问题，Enter 发送（Shift+Enter 换行）…"
          :disabled="streaming"
          @keydown.enter.exact.prevent="canSend && send()"
        />
        <div class="input-tools">
          <span class="char-count" :class="{ over: inputLen > 8000 }">{{ inputLen }}/8000</span>
          <n-button size="small" secondary :disabled="messages.length === 0 || streaming" @click="clearChat">
            <template #icon><n-icon :component="Eraser" /></template>
            清空对话
          </n-button>
          <n-button type="primary" :disabled="!canSend" :loading="streaming" @click="send">
            <template #icon><n-icon :component="Send" /></template>
            发送
          </n-button>
        </div>
      </div>
    </main>
  </div>
</template>

<style scoped>
/* 占满 .n-tab-pane 的可用高度（详情页问答 tab 下 .kb-detail-page 是定高的）。
   左侧参数栏、右侧消息列表各自内部滚动，页面本身不再出滚动条。
   父级已是 flex column + min-height:0，这里 flex:1 才接得住；
   原来是 min-height:520px 的固定下限，会把 .qa-tab 顶出可用高度、重新撑出整页滚动条。 */
.qa-tab {
  flex: 1;
  min-height: 0;
  display: flex;
  gap: 20px;
}

/* 参数面板实测高约 1370px，远超视口：限高后内部滚动。
   高度不再写死 calc(100vh - Npx)——那是固定高度链建立之前的兜底，会把 .qa-tab 顶到 702px
   从而重新产生整页滚动条；现在由 KnowledgeBaseDetailView 的 .kb-detail-fixed 链算出可用高度，
   min-height:0 让本项能被压缩到实际可用高度。 */
.qa-side {
  width: 300px;
  flex-shrink: 0;
  border-right: 1px solid rgba(128, 128, 128, 0.18);
  padding-right: 16px;
  min-height: 0;
  overflow: hidden auto;
}

.qa-main {
  flex: 1;
  min-width: 0;
  /* flex 行(item) 自动最小尺寸是内容高：不归零，消息列表会把 .qa-main 顶出 .qa-tab 的定高，
     整页就会出现滚动条。归零后 .qa-list 才在自身内部滚动、输入条固定在底部。 */
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.qa-welcome {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: rgba(80, 80, 80, 0.9);
}

.qa-avatar {
  width: 56px;
  height: 56px;
  border-radius: 14px;
  background: linear-gradient(135deg, #4f7cff, #2f6bff);
  color: #fff;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
}

.qa-welcome-title {
  font-size: 17px;
  font-weight: 600;
  margin-top: 6px;
}

.qa-welcome-sub {
  font-size: 13px;
  max-width: 440px;
  text-align: center;
  color: rgba(110, 110, 110, 0.95);
}

.qa-list {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding-bottom: 12px;
}

.qa-row.user {
  align-self: flex-end;
  max-width: 78%;
}

.qa-row.assistant {
  align-self: stretch;
}

/* 操作栏默认透明（见 MessageActions.vue），悬停或键盘聚焦该行时显形。
   注意 .msg-actions 是子组件的根元素，父级 scoped 样式能命中它——改名会静默失效。 */
.qa-row:hover .msg-actions,
.qa-row:focus-within .msg-actions {
  opacity: 1;
}

.bubble.user {
  background: #2f6bff;
  color: #fff;
  border-radius: 12px 12px 2px 12px;
  padding: 10px 14px;
  white-space: pre-wrap;
}

.bubble.assistant {
  background: rgba(245, 245, 245, 0.9);
  border: 1px solid rgba(128, 128, 128, 0.18);
  border-radius: 12px 12px 12px 2px;
  padding: 12px 14px;
}

/* 思考折叠 / 回答 / 引用的样式已随渲染逻辑迁到 components/ChatMessageBody.vue（两处共用）。 */

.qa-input-bar {
  display: flex;
  flex-direction: column;
  gap: 8px;
  border-top: 1px solid rgba(128, 128, 128, 0.18);
  padding-top: 12px;
}

.input-tools {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
}

.char-count {
  font-size: 12px;
  color: rgba(120, 120, 120, 0.9);
  margin-right: auto;
}

.char-count.over {
  color: #d03050;
}
</style>
