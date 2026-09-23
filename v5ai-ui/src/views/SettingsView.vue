<script setup lang="ts">
import { NButton, NCard, NIcon } from 'naive-ui'
import { ExternalLink } from 'lucide-vue-next'
import PageHeader from '../components/PageHeader.vue'

const baseline = [
  { label: '后端运行时', value: 'JDK 21' },
  { label: '应用框架', value: 'Spring Boot 4.1.0（Servlet MVC）' },
  { label: '数据访问', value: 'MyBatis-Plus 3.5.12' },
  { label: '数据库', value: 'PostgreSQL 14+ / pgvector' },
  { label: '模型运行时', value: 'AgentScope Java 2.0' },
  { label: '认证安全', value: 'Sa-Token + JWT / AES-GCM' },
  { label: '前端', value: 'Vue 3.5 + Vite 7 + TypeScript 5.9' },
  { label: '数据库迁移', value: 'Flyway（当前 V49）' }
]
/** 独立对话门户（v5ai-ui-chat）地址；未配置时不显示入口，避免死链。 */
const chatPortalUrl = import.meta.env.VITE_CHAT_UI_URL ?? ''

function openChatPortal() {
  window.open(chatPortalUrl, '_blank', 'noopener')
}
</script>

<template>
  <div class="page">
    <PageHeader title="系统信息" description="平台技术栈与运行时说明。" />

    <n-card title="系统基线" :bordered="true">
      <div class="baseline-grid">
        <div v-for="item in baseline" :key="item.label" class="baseline-item">
          <span class="baseline-label">{{ item.label }}</span>
          <strong class="baseline-value">{{ item.value }}</strong>
        </div>
      </div>
    </n-card>

    <n-card v-if="chatPortalUrl" title="对话门户" :bordered="true">
      <p style="margin: 0 0 12px; font-size: 13px; opacity: 0.75">
        终端用户对话页（独立应用 <code>v5ai-ui-chat</code>）：进入后用 API Key 登录，只能访问该 Key 绑定的已发布 Agent。
        Key 在「API Key 管理」里签发与绑定。
      </p>
      <n-button type="primary" secondary size="small" @click="openChatPortal">
        <template #icon><n-icon :component="ExternalLink" :size="15" /></template>
        打开对话门户
      </n-button>
    </n-card>

    <n-card title="运行时说明" :bordered="true">
      <div class="runtime-section">
        <h4>一、管理接口（/api/admin/**）—— 管理员 JWT 鉴权</h4>
        <ol>
          <li>
            登录获取 JWT：<code>POST /api/auth/login</code>，请求体
            <code>{"username": "admin", "password": "..."}</code>（前端通过 RSA+AES 混合加密传输），
            返回 <code>{"tokenType": "Bearer", "token": "&lt;jwt&gt;"}</code>。
          </li>
          <li>
            后续所有管理请求携带 <code>Authorization: Bearer &lt;jwt&gt;</code>，
            用于管理模型、Agent、知识库、MCP、Skill、工作流以及用户、角色、菜单、API Key 等平台资源。
          </li>
          <li>管理接口强制登录；写操作需管理员角色。</li>
        </ol>
      </div>

      <div class="runtime-section">
        <h4>二、运行时接口（/api/v1/agents/**）—— Agent API Key 鉴权</h4>
        <ol>
          <li>
            前置准备：先在管理面创建并<strong>发布</strong> Agent（获得 <code>agentKey</code>），
            再到<strong>「API Keys 管理」</strong>页面新建 Key 并勾选该 Key 可访问的已发布 Agent：
            <code>POST /api/admin/api-keys</code>，请求体
            <code>{"name": "生产环境", "agentKeys": ["你的agentKey"]}</code>
            （明文 Key 仅创建时返回一次，请妥善保存）。
          </li>
          <li>
            调用时携带 <code>Authorization: Bearer &lt;api-key&gt;</code>；Key 未绑定请求路径中的 Agent
            会返回 <strong>403</strong>，Key 无效/已停用返回 <strong>401</strong>。
          </li>
          <li>
            一次性对话：<code>POST /api/v1/agents/{'{agentKey}'}/chat</code>，请求体
            <code>{"conversationId": "可选", "query": "你好"}</code>，
            返回 <code>{"runId": "...", "answer": "..."}</code>。
          </li>
          <li>
            流式对话（SSE）：<code>POST /api/v1/agents/{'{agentKey}'}/chat/stream</code>，
            请求头需加 <code>Accept: text/event-stream</code>，事件顺序为：
            <code>RUN_STARTED → MODEL_CALL →（TEXT_DELTA / TOOL_CALL→TOOL_RESULT / RETRIEVAL）* → MESSAGE_COMPLETED → RUN_COMPLETED</code>；
            失败时推 <code>RUN_FAILED</code>，需用户授权时推 <code>PERMISSION_REQUIRED</code>。
          </li>
          <li>
            运行时按 Agent 的已发布版本构造 AgentScope Agent，动态接入知识库检索、MCP 工具、Skill、联网搜索和记忆；
            管理端调试接口使用实时编辑配置，不要求先发布。
          </li>
          <li>
            运行请求会按 Agent 执行每分钟限流和每日配额校验（配额设置为 <code>0</code> 表示不限制），
            用量明细记录模型调用次数、输入/输出 Token、耗时和状态。
          </li>
          <li>
            对话门户支持会话管理、停止运行、重新生成和图片附件；图片能力由 Agent 当前发布版本绑定的模型能力决定。
          </li>
        </ol>
      </div>
    </n-card>
  </div>
</template>

<style scoped>
.baseline-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 16px;
}

.baseline-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 14px;
  border: 1px solid rgba(128, 128, 128, 0.16);
  border-radius: 10px;
}

.baseline-label {
  font-size: 12px;
  opacity: 0.6;
}

.baseline-value {
  font-size: 16px;
  font-weight: 600;
}

.runtime-section {
  line-height: 1.8;
  font-size: 13px;
}

.runtime-section + .runtime-section {
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px dashed rgba(128, 128, 128, 0.25);
}

.runtime-section h4 {
  margin: 0 0 8px;
  font-size: 14px;
  font-weight: 600;
}

.runtime-section ol {
  margin: 0;
  padding-left: 20px;
}

.runtime-section li {
  margin: 6px 0;
}

.runtime-section code {
  padding: 1px 6px;
  border-radius: 6px;
  background: rgba(128, 128, 128, 0.12);
  font-family: ui-monospace, 'SFMono-Regular', 'Cascadia Code', Consolas, monospace;
  font-size: 12px;
  word-break: break-all;
}
</style>
