<script setup lang="ts">
import { ref } from 'vue'
import { NButton, NCard, NDivider, NForm, NFormItem, NInput, NSpace, NTag, useMessage } from 'naive-ui'
import PageHeader from '../components/PageHeader.vue'
import MarkdownContent from '../components/MarkdownContent.vue'
import { chat, streamChat } from '../api/client'

const message = useMessage()
const agentKey = ref('demo')
const appApiKey = ref('')
const conversationId = ref('')
const query = ref('你好，介绍一下 v5ai-nb')
const blockingAnswer = ref('')
const runId = ref('')
const streamAnswer = ref('')
const events = ref<string[]>([])
const loading = ref(false)
const streaming = ref(false)

function parseDelta(data: string): string {
  if (!data) return ''
  try {
    const body = JSON.parse(data) as { payload?: string; answer?: string; text?: string }
    return body.payload ?? body.answer ?? body.text ?? data
  } catch {
    return data
  }
}

function chatRequest() {
  return {
    conversationId: conversationId.value || undefined,
    query: query.value
  }
}

async function handleBlockingChat() {
  loading.value = true
  blockingAnswer.value = ''
  runId.value = ''
  try {
    const result = await chat(agentKey.value, appApiKey.value, chatRequest())
    runId.value = result.runId
    blockingAnswer.value = result.answer
  } catch (e) {
    message.error(e instanceof Error ? e.message : '阻塞调用失败')
  } finally {
    loading.value = false
  }
}

async function handleStream() {
  streaming.value = true
  streamAnswer.value = ''
  events.value = []
  try {
    await streamChat(agentKey.value, appApiKey.value, chatRequest(), (event, data) => {
      events.value.push(event)
      if (event === 'RUN_STARTED') {
        runId.value = parseRunId(data)
      }
      if (event === 'TEXT_DELTA') {
        streamAnswer.value += parseDelta(data)
      }
      if (event === 'RUN_FAILED') {
        const reason = parseRunFailed(data)
        streamAnswer.value = `调用失败：${reason}`
        message.error(`流式调用失败：${reason}`)
      }
      if (event === 'MESSAGE_COMPLETED' && !streamAnswer.value) {
        streamAnswer.value = parseDelta(data)
      }
    })
  } catch (e) {
    message.error(e instanceof Error ? e.message : '流式调用失败')
  } finally {
    streaming.value = false
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
</script>

<template>
  <div class="page">
    <PageHeader title="调试工具" description="用 Agent API Key 直接调用运行时接口，验证发布效果。" />

    <div class="chat-grid">
      <n-card title="请求参数" :bordered="true">
        <n-form label-placement="top">
          <n-form-item label="App Key">
            <n-input v-model:value="agentKey" />
          </n-form-item>
          <n-form-item label="Agent API Key">
            <n-input v-model:value="appApiKey" type="password" show-password-on="click" />
          </n-form-item>
          <n-form-item label="Conversation ID">
            <n-input v-model:value="conversationId" placeholder="可为空，后端会创建或延续会话" />
          </n-form-item>
          <n-form-item label="Query">
            <n-input v-model:value="query" type="textarea" :autosize="{ minRows: 4, maxRows: 8 }" />
          </n-form-item>
          <n-space>
            <n-button type="primary" :loading="loading" @click="handleBlockingChat">阻塞调用</n-button>
            <n-button type="info" :loading="streaming" @click="handleStream">流式调用</n-button>
          </n-space>
        </n-form>
      </n-card>

      <n-card title="阻塞响应" :bordered="true">
        <div class="kv">
          <span>Run ID</span>
          <strong>{{ runId || '-' }}</strong>
        </div>
        <div class="answer">
          <MarkdownContent v-if="blockingAnswer" :content="blockingAnswer" />
          <template v-else>等待阻塞响应...</template>
        </div>
      </n-card>
    </div>

    <n-card title="流式响应" :bordered="true">
      <div class="answer">
        <MarkdownContent v-if="streamAnswer" :content="streamAnswer" />
        <template v-else>等待流式响应...</template>
      </div>
      <n-divider title-placement="left">事件列表</n-divider>
      <div class="event-list">
        <n-tag v-for="(event, index) in events" :key="`${event}-${index}`" size="small" :bordered="false">{{ event }}</n-tag>
      </div>
    </n-card>
  </div>
</template>

<style scoped>
.chat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: 16px;
}

.kv {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
  font-size: 13px;
}

.kv span {
  opacity: 0.55;
}

.answer {
  white-space: pre-wrap;
  line-height: 1.7;
  min-height: 60px;
  padding: 12px;
  border-radius: 10px;
  background: rgba(128, 128, 128, 0.06);
}

.event-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
</style>
