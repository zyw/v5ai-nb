<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { NButton, NIcon, NPopover, NRadioButton, NRadioGroup, useMessage } from 'naive-ui'
import { ChevronDown, Copy, LogOut } from 'lucide-vue-next'
import { bootstrapData, clearApiKey } from '../stores/portal'
import { setThemeMode, themeMode, type ThemeMode } from '../stores/theme'

/**
 * 顶栏右侧用户区：展示 Key 的归属用户与 Key 信息，并提供主题切换与退出。
 * 门户没有账号体系 —— 「用户」就是这把 API Key 的归属者，「退出」= 清除本机保存的 Key。
 */
const router = useRouter()
const message = useMessage()
const open = ref(false)

const displayName = computed(() => bootstrapData.value?.ownerName || '未命名用户')
const keyName = computed(() => bootstrapData.value?.keyName ?? '-')
const trackingId = computed(() => bootstrapData.value?.trackingId ?? '-')
const initial = computed(() => displayName.value.trim().charAt(0).toUpperCase())

/** 三档主题：浅色 / 深色 / 跟随系统（选择持久化在 stores/theme.ts） */
function onThemeChange(value: string | number) {
  setThemeMode(value as ThemeMode)
}

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
      <!--
        箭头原先放在 #icon 里，但 #icon 是前置图标槽（iconPlacement 默认 'left'，图标排在内容之前），
        所以渲染出来是「▾ 头像 名字」—— 下拉触发器的箭头跑到了最前面。
        移进内容槽的末尾，才是惯例的「头像 名字 ▾」（与 AgentSwitcher 一致）。
      -->
      <n-button quaternary size="small" :aria-label="'用户菜单'" :aria-expanded="open" class="user-menu">
        <span class="user-avatar" aria-hidden="true">{{ initial }}</span>
        <span class="user-name">{{ displayName }}</span>
        <n-icon :component="ChevronDown" :size="14" />
      </n-button>
    </template>

    <div class="user-panel">
      <div class="user-panel-name">{{ displayName }}</div>
      <div class="user-panel-row"><span class="user-panel-label">Key 名称</span><span>{{ keyName }}</span></div>
      <div class="user-panel-row">
        <span class="user-panel-label">跟踪 ID</span>
        <span class="user-panel-mono">{{ trackingId }}</span>
        <n-button text size="tiny" aria-label="复制跟踪 ID" @click="copyTrackingId">
          <template #icon><n-icon :component="Copy" :size="13" /></template>
        </n-button>
      </div>

      <div class="user-panel-row user-panel-theme">
        <span class="user-panel-label">主题</span>
        <n-radio-group :value="themeMode" size="small" @update:value="onThemeChange">
          <n-radio-button value="light">浅色</n-radio-button>
          <n-radio-button value="dark">深色</n-radio-button>
          <n-radio-button value="system">跟随系统</n-radio-button>
        </n-radio-group>
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
  background: var(--portal-grad);
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
  /* 窄屏（320px 视口）下固定 292px 会横向溢出：给两边各留 12px */
  width: min(292px, calc(100vw - 24px));
  padding: 12px;
  border: 1px solid var(--portal-border);
  border-radius: var(--portal-radius);
  background: var(--portal-surface-solid);
  box-shadow: var(--portal-shadow-lg);
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

.user-panel-theme {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid var(--portal-border);
}

.user-panel-label {
  flex: none;
  width: 62px;
  color: var(--portal-fg-muted);
}

.user-panel-mono {
  font-family: var(--portal-mono);
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

.user-panel-theme :deep(.n-radio-group) {
  flex: 1;
}

.user-panel-theme :deep(.n-radio-button) {
  flex: 1;
  text-align: center;
  font-size: 12px;
}
</style>
