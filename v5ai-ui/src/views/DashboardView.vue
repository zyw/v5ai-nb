<script setup lang="ts">
import { NButton, NCard, NGi, NGrid, NTag } from 'naive-ui'
import { useRouter } from 'vue-router'
import AdminLoginCard from '../components/AdminLoginCard.vue'

const router = useRouter()
</script>

<template>
  <div class="grid">
    <admin-login-card />

    <n-card title="Phase 1 控制台" class="panel">
      <div class="summary-row">
        <span>Frontend Router</span>
        <n-tag type="success">vue-router</n-tag>
      </div>
      <div class="summary-row">
        <span>Backend Style</span>
        <strong>Dify 风格中心化运行时</strong>
      </div>
      <div class="hint">当前前端覆盖模型 Provider、模型、Application 发布，以及阻塞/流式 Chat Debug。</div>
    </n-card>

    <n-card title="快速入口" class="panel wide">
      <n-grid :cols="3" :x-gap="16" :y-gap="16" responsive="screen">
        <n-gi>
          <n-button block secondary type="info" @click="router.push({ name: 'models' })">配置 Provider / Model</n-button>
        </n-gi>
        <n-gi>
          <n-button block secondary type="success" @click="router.push({ name: 'applications' })">创建并发布 Application</n-button>
        </n-gi>
        <n-gi>
          <n-button block secondary type="warning" @click="router.push({ name: 'chat' })">运行 Chat Debug</n-button>
        </n-gi>
      </n-grid>
    </n-card>
  </div>
</template>
