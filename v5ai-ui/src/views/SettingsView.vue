<script setup lang="ts">
import { NCard } from 'naive-ui'
import PageHeader from '../components/PageHeader.vue'

const baseline = [
  { label: 'JDK', value: '21' },
  { label: 'Spring Boot', value: '4.1.0' },
  { label: 'ORM', value: 'MyBatis-Plus' },
  { label: 'Auth', value: 'Sa-Token + JWT' },
  { label: 'Vector DB', value: 'PostgreSQL + pgvector' },
  { label: 'Migrations', value: 'Flyway' }
]
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

    <n-card title="运行时说明" :bordered="true">
      <div class="runtime-section">
        <h4>一、管理接口（/api/admin/**）—— 管理员 JWT 鉴权</h4>
        <ol>
          <li>
            登录获取 JWT：<code>POST /api/auth/login</code>，请求体
            <code>{"username": "admin", "password": "..."}</code>（前端会先做 RSA+AES 加密传输），
            返回 <code>{"tokenType": "Bearer", "token": "&lt;jwt&gt;"}</code>。
          </li>
          <li>
            后续所有管理请求携带 <code>Authorization: Bearer &lt;jwt&gt;</code>，
            用于创建模型、Agent、知识库、MCP、Skill、用户等资源（如
            <code>POST /api/admin/models</code>、<code>POST /api/admin/agents</code>）。
          </li>
          <li>管理接口强制登录；写操作需管理员角色。</li>
        </ol>
      </div>

      <div class="runtime-section">
        <h4>二、运行时接口（/api/v1/agents/**）—— Agent API Key 鉴权</h4>
        <ol>
          <li>
            前置准备：先在管理面创建并<strong>发布</strong> Agent（获得 <code>agentKey</code>），
            再生成运行 Key：<code>POST /api/admin/agents/{'{agentKey}'}/api-keys</code>
            （Key 仅返回一次，请妥善保存）。
          </li>
          <li>
            调用时携带 <code>Authorization: Bearer &lt;api-key&gt;</code>。
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
