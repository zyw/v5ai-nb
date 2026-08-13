<script setup lang="ts">
import { computed, h, ref, type Component } from 'vue'
import { NButton, NCard, NConfigProvider, NForm, NFormItem, NIcon, NInput, NLayout, NLayoutContent, NLayoutSider, NMenu, NSpace, NTag, darkTheme, useMessage, type MenuOption } from 'naive-ui'
import { Bot, Boxes, KeyRound, LayoutDashboard, MessageSquare, Rocket, Settings } from 'lucide-vue-next'
import { login, streamChat } from './api/client'

const message = useMessage()
const active = ref('dashboard')
const username = ref('admin')
const password = ref('admin')
const adminToken = ref('')
const appKey = ref('demo')
const appApiKey = ref('')
const conversationId = ref('')
const query = ref('你好，介绍一下 v5ai-nb')
const answer = ref('')
const events = ref<string[]>([])
const streaming = ref(false)

const menuOptions: MenuOption[] = [
  { label: 'Dashboard', key: 'dashboard', icon: () => icon(LayoutDashboard) },
  { label: 'Models', key: 'models', icon: () => icon(Boxes) },
  { label: 'Applications', key: 'applications', icon: () => icon(Rocket) },
  { label: 'Chat Debug', key: 'chat', icon: () => icon(MessageSquare) },
  { label: 'API Keys', key: 'keys', icon: () => icon(KeyRound) },
  { label: 'Settings', key: 'settings', icon: () => icon(Settings) }
]

const currentTitle = computed(() => menuOptions.find(item => item.key === active.value)?.label ?? 'Dashboard')

function icon(component: Component) {
  return h(NIcon, { component })
}

async function handleLogin() {
  try {
    const result = await login(username.value, password.value)
    adminToken.value = `${result.tokenType} ${result.token}`
    message.success('登录成功')
  } catch (error) {
    message.error(error instanceof Error ? error.message : '登录失败')
  }
}

async function handleStream() {
  streaming.value = true
  answer.value = ''
  events.value = []
  try {
    await streamChat(appKey.value, appApiKey.value, { conversationId: conversationId.value || undefined, query: query.value }, (event, data) => {
      events.value.push(event)
      if (event === 'TEXT_DELTA' && data) {
        try {
          answer.value += JSON.parse(data).payload ?? ''
        } catch {
          answer.value += data
        }
      }
    })
  } catch (error) {
    message.error(error instanceof Error ? error.message : '流式调用失败')
  } finally {
    streaming.value = false
  }
}
</script>

<template>
  <n-config-provider :theme="darkTheme">
    <n-layout has-sider class="shell">
      <n-layout-sider bordered :width="232" class="sider">
        <div class="brand">
          <div class="brand-mark"><n-icon :component="Bot" /></div>
          <div>
            <div class="brand-name">v5ai-nb</div>
            <div class="brand-sub">Agent Platform</div>
          </div>
        </div>
        <n-menu v-model:value="active" :options="menuOptions" />
      </n-layout-sider>

      <n-layout-content class="content">
        <header class="topbar">
          <div>
            <h1>{{ currentTitle }}</h1>
            <p>基于 AgentScope Java 的中心化 Dify 风格控制台</p>
          </div>
          <n-tag type="success" round>Phase 1</n-tag>
        </header>

        <main class="grid">
          <n-card title="管理员登录" class="panel">
            <n-form label-placement="top">
              <n-form-item label="用户名">
                <n-input v-model:value="username" />
              </n-form-item>
              <n-form-item label="密码">
                <n-input v-model:value="password" type="password" show-password-on="mousedown" />
              </n-form-item>
              <n-button type="primary" block @click="handleLogin">登录</n-button>
            </n-form>
            <p v-if="adminToken" class="token-preview">{{ adminToken }}</p>
          </n-card>

          <n-card title="模型配置" class="panel" v-if="active === 'dashboard' || active === 'models'">
            <div class="summary-row">
              <span>Provider</span>
              <strong>OpenAI Compatible</strong>
            </div>
            <div class="summary-row">
              <span>Model</span>
              <strong>gpt-4o / chat</strong>
            </div>
            <div class="hint">第一阶段已提供后端模型配置服务和 AES-GCM 凭证加密，管理接口将在下一阶段补齐。</div>
          </n-card>

          <n-card title="Application 发布" class="panel" v-if="active === 'dashboard' || active === 'applications'">
            <div class="summary-row">
              <span>App Key</span>
              <strong>demo</strong>
            </div>
            <div class="summary-row">
              <span>Status</span>
              <n-tag type="success">PUBLISHED</n-tag>
            </div>
            <div class="hint">运行时只解析已发布 Application，草稿不会进入 Agent 执行链路。</div>
          </n-card>

          <n-card title="Chat Debug" class="panel wide" v-if="active === 'dashboard' || active === 'chat'">
            <n-space vertical>
              <n-input v-model:value="appKey" placeholder="App Key" />
              <n-input v-model:value="appApiKey" type="password" show-password-on="mousedown" placeholder="Application API Key" />
              <n-input v-model:value="conversationId" placeholder="Conversation ID，可为空" />
              <n-input v-model:value="query" type="textarea" :autosize="{ minRows: 3, maxRows: 6 }" />
              <n-button type="primary" :loading="streaming" @click="handleStream">发送流式消息</n-button>
              <div class="answer">{{ answer || '等待回复...' }}</div>
              <div class="event-list">
                <n-tag v-for="(event, index) in events" :key="`${event}-${index}`" size="small">{{ event }}</n-tag>
              </div>
            </n-space>
          </n-card>

          <n-card title="API Key" class="panel" v-if="active === 'keys'">
            <div class="hint">运行时接口使用 Application API Key。后端当前支持 BCrypt hash 校验和配置化开发 key。</div>
          </n-card>

          <n-card title="系统设置" class="panel" v-if="active === 'settings'">
            <div class="hint">JDK 21、Spring Boot 4.1.0、MyBatis-Plus、Sa-Token + JWT 已作为第一阶段技术基线。</div>
          </n-card>
        </main>
      </n-layout-content>
    </n-layout>
  </n-config-provider>
</template>
