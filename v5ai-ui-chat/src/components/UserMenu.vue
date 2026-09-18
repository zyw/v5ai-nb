<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { NButton, NIcon, NPopover, useMessage, useThemeVars } from 'naive-ui'
import { ChevronDown, Copy, LogOut } from 'lucide-vue-next'
import { bootstrapData, clearApiKey } from '../stores/portal'

/**
 * 顶栏右侧用户区：展示 Key 的归属用户与 Key 信息，下拉里提供退出。
 * 门户没有账号体系 —— 「用户」就是这把 API Key 的归属者，「退出」= 清除本机保存的 Key。
 */
const router = useRouter()
const message = useMessage()
const themeVars = useThemeVars()
const open = ref(false)

const displayName = computed(() => bootstrapData.value?.ownerName || '未命名用户')
const keyName = computed(() => bootstrapData.value?.keyName ?? '-')
const trackingId = computed(() => bootstrapData.value?.trackingId ?? '-')
const initial = computed(() => displayName.value.trim().charAt(0).toUpperCase())

async function copyTrackingId() {
  try {
    await navigator.clipboard.writeText(trackingId.value)
    message.success('跟踪 ID 已复制')
  } catch {
    message.warning('浏览器拒绝访问剪贴板，请手动复制')
  }
}

async function logout() {
  open.value = false
  clearApiKey()
  await router.push({ name: 'key' })
}
</script>

<template>
  <n-popover v-model:show="open" trigger="click" placement="bottom-end" :show-arrow="false" raw>
    <template #trigger>
      <n-button quaternary size="small" :aria-label="'用户菜单'" :aria-expanded="open" class="user-menu">
        <span class="user-avatar" :style="{ background: themeVars.primaryColor }" aria-hidden="true">{{ initial }}</span>
        <span class="user-name">{{ displayName }}</span>
        <template #icon><n-icon :component="ChevronDown" :size="14" /></template>
      </n-button>
    </template>

    <div class="user-panel" :style="{ background: themeVars.popoverColor, borderColor: themeVars.borderColor }">
      <div class="user-panel-name">{{ displayName }}</div>
      <div class="user-panel-row"><span class="user-panel-label">Key 名称</span><span>{{ keyName }}</span></div>
      <div class="user-panel-row">
        <span class="user-panel-label">跟踪 ID</span>
        <span class="user-panel-mono">{{ trackingId }}</span>
        <n-button text size="tiny" aria-label="复制跟踪 ID" @click="copyTrackingId">
          <template #icon><n-icon :component="Copy" :size="13" /></template>
        </n-button>
      </div>
      <n-button block size="small" class="user-panel-exit" @click="logout">
        <template #icon><n-icon :component="LogOut" :size="14" /></template>
        退出
      </n-button>
    </div>
  </n-popover>
</template>

<style scoped>
.user-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  color: #fff;
  font-size: 11px;
  font-weight: 600;
}

.user-name {
  max-width: 140px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-panel {
  width: 280px;
  padding: 12px;
  border: 1px solid;
  border-radius: 10px;
  box-shadow: 0 6px 24px rgba(0, 0, 0, 0.12);
}

.user-panel-name {
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 8px;
}

.user-panel-row {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  margin-bottom: 6px;
}

.user-panel-label {
  flex: none;
  width: 62px;
  opacity: 0.6;
}

.user-panel-mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-panel-exit {
  margin-top: 8px;
}
.user-menu :deep(.n-button__content) {
  gap: 4px;
}
</style>
