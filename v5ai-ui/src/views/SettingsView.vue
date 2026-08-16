<script setup lang="ts">
import { NCard, NTag } from 'naive-ui'
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
      <p class="muted" style="margin: 0; line-height: 1.7">
        管理接口使用管理员 JWT；运行时接口使用 Agent API Key，通过
        <code>Authorization: Bearer &lt;key&gt;</code> 调用
        <code>/api/v1/agents/{'{agentKey}'}/chat</code> 和
        <code>/api/v1/agents/{'{agentKey}'}/chat/stream</code>。
      </p>
    </n-card>

    <n-card title="模型凭证 JSON 示例" :bordered="true">
      <pre class="code-block">{
  "baseUrl": "https://api.openai.com/v1",
  "apiKey": "sk-...",
  "organization": "可选"
}</pre>
      <p class="muted" style="margin: 12px 0 0; font-size: 12px">
        后端对凭证进行 AES-GCM 加密存储；前端列表只展示密文字段，避免明文回显。
      </p>
      <n-tag type="info" size="small" :bordered="false" style="margin-top: 12px">v5ai-nb · Agent Platform</n-tag>
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

.code-block {
  margin: 0;
  padding: 16px;
  border-radius: 10px;
  background: rgba(128, 128, 128, 0.08);
  font-family: ui-monospace, 'SFMono-Regular', 'Cascadia Code', Consolas, monospace;
  font-size: 13px;
  line-height: 1.6;
  overflow-x: auto;
}
</style>
