<script setup lang="ts">
/**
 * 知识库详情页「知识问答」tab：左参数面板（检索参数 + 模型回答参数）+ 右侧多轮会话。
 * 事件流：RETRIEVAL（引用）→ TEXT_DELTA* → RUN_COMPLETED / RUN_FAILED；页内会话不落库。
 */
import { computed, ref } from 'vue'
import { NButton, NCollapse, NCollapseItem, NIcon, NInput, NSpin, useMessage } from 'naive-ui'
import { Eraser, FileText, Send } from 'lucide-vue-next'
import { kbQaStream, type KbHitResponse, type KbQaMessage } from '../../api/client'
import { adminToken } from '../../stores/session'
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

interface ChatMsg {
  role: 'user' | 'assistant'
  content: string
  citations?: KbHitResponse[]
  error?: string
  loading?: boolean
}

const messages = ref<ChatMsg[]>([])
const input = ref('')
const streaming = ref(false)

const canSend = computed(() => {
  if (streaming.value) return false
  if (props.modelParams.modelId == null) return false
  const len = input.value.trim().length
  return len > 0 && len <= 8000
})

const inputLen = computed(() => input.value.length)

async function send() {
  const text = input.value.trim()
  if (!text) return
  if (props.modelParams.modelId == null) {
    message.warning('请先在左侧选择对话模型')
    return
  }
  const history: KbQaMessage[] = messages.value
    .filter((m) => m.content && !m.error)
    .map((m) => ({ role: m.role, content: m.content }))
  messages.value.push({ role: 'user', content: text })
  const assistant: ChatMsg = { role: 'assistant', content: '', loading: true }
  messages.value.push(assistant)
  input.value = ''
  streaming.value = true
  try {
    await kbQaStream(
      adminToken.value,
      props.kbId,
      {
        modelId: props.modelParams.modelId,
        nearbySliceCount: props.modelParams.nearbySliceCount,
        prompt: props.modelParams.prompt.trim() || undefined,
        resultCount: props.params.resultCount,
        questionRewrite: props.params.questionRewrite,
        thresholdEnabled: props.params.thresholdEnabled,
        threshold: props.params.thresholdEnabled ? props.params.threshold : undefined,
        fusionStrategy: props.params.fusionStrategy,
        rrfK: props.params.fusionStrategy === 'RRF' ? props.params.rrfK : undefined,
        denseWeight: props.params.fusionStrategy === 'WEIGHTED_SUM' ? props.params.denseWeight : undefined,
        messages: [...history, { role: 'user', content: text }]
      },
      (event, data) => {
        if (event === 'RETRIEVAL') {
          assistant.citations = parseHits(data)
        } else if (event === 'TEXT_DELTA' && data) {
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

function parseHits(data: string): KbHitResponse[] | undefined {
  try {
    const parsed = JSON.parse(data)
    return Array.isArray(parsed) ? (parsed as KbHitResponse[]) : undefined
  } catch {
    return undefined
  }
}

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

      <div v-else class="qa-list">
        <div v-for="(m, i) in messages" :key="i" class="qa-row" :class="m.role">
          <div class="bubble" :class="m.role">
            <template v-if="m.role === 'user'">{{ m.content }}</template>
            <template v-else>
              <n-spin v-if="m.loading && !m.content" size="small" />
              <div v-if="m.content" class="answer-text">{{ m.content }}</div>
              <div v-if="m.error" class="answer-error">{{ m.error }}</div>
              <n-collapse v-if="m.citations && m.citations.length > 0" class="citations">
                <n-collapse-item title="本次引用的切片" name="refs">
                  <div v-for="(c, ci) in m.citations" :key="ci" class="citation">
                    <span class="cite-doc"><n-icon :component="FileText" size="12" />{{ c.documentTitle || `文档 #${c.documentId}` }}</span>
                    <span class="cite-score">相似度 {{ c.score.toFixed(4) }}</span>
                    <div class="cite-snippet">{{ c.content }}</div>
                  </div>
                </n-collapse-item>
              </n-collapse>
            </template>
          </div>
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
.qa-tab {
  display: flex;
  gap: 20px;
  min-height: 520px;
}

.qa-side {
  width: 300px;
  flex-shrink: 0;
  border-right: 1px solid rgba(128, 128, 128, 0.18);
  padding-right: 16px;
}

.qa-main {
  flex: 1;
  min-width: 0;
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

.answer-text {
  white-space: pre-wrap;
  line-height: 1.8;
  font-size: 13.5px;
}

.answer-error {
  color: #d03050;
  font-size: 13px;
}

.citations {
  margin-top: 10px;
}

.citation {
  padding: 6px 0;
  border-top: 1px dashed rgba(128, 128, 128, 0.25);
}

.cite-doc {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12.5px;
  color: #2f6bff;
}

.cite-score {
  float: right;
  font-size: 12px;
  color: rgba(120, 120, 120, 0.9);
}

.cite-snippet {
  font-size: 12px;
  color: rgba(90, 90, 90, 0.95);
  line-height: 1.6;
  margin-top: 2px;
  max-height: 60px;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
}

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
