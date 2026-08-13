<template>
  <div class="grid">
    <n-card title="系统基线" class="panel">
      <div class="summary-row">
        <span>JDK</span>
        <strong>21</strong>
      </div>
      <div class="summary-row">
        <span>Spring Boot</span>
        <strong>4.1.0</strong>
      </div>
      <div class="summary-row">
        <span>ORM / Auth</span>
        <strong>MyBatis-Plus / Sa-Token + JWT</strong>
      </div>
    </n-card>

    <n-card title="运行时说明" class="panel">
      <div class="hint">
        管理接口使用管理员 JWT。运行时接口使用 Application API Key，通过 `Authorization: Bearer &lt;key&gt;`
        调用 `/api/v1/apps/{appKey}/chat` 和 `/api/v1/apps/{appKey}/chat/stream`。
      </div>
    </n-card>

    <n-card title="模型凭证 JSON 示例" class="panel wide">
      <pre class="code-block">{
  "baseUrl": "https://api.openai.com/v1",
  "apiKey": "sk-...",
  "organization": "可选"
}</pre>
      <div class="hint">后端会对凭证进行 AES-GCM 加密存储；前端列表只展示密文字段，避免明文回显。</div>
    </n-card>
  </div>
</template>

<script setup lang="ts">
import { NCard } from 'naive-ui'
</script>
