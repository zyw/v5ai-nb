<script setup lang="ts">
import { NButton, NIcon, NPopconfirm, useThemeVars } from 'naive-ui'
import { MessageSquare, Trash2 } from 'lucide-vue-next'
import type { PortalSession } from '../stores/portal'

/**
 * 左栏会话列表：展示当前 Agent 的会话（按最近更新倒序），支持切换与删除。
 * 会话记录只存在本机浏览器（后端只按会话 id 落消息，没有列表接口）。
 */
defineProps<{ items: PortalSession[]; currentId: string }>()
const emit = defineEmits<{ (e: 'open', session: PortalSession): void; (e: 'remove', session: PortalSession): void }>()

const themeVars = useThemeVars()

function formatTime(timestamp: number): string {
  const date = new Date(timestamp)
  const today = new Date()
  const sameDay = date.toDateString() === today.toDateString()
  const time = `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
  return sameDay ? time : `${date.getMonth() + 1}/${date.getDate()}`
}
</script>

<template>
  <div class="session-list">
    <div v-if="items.length === 0" class="session-empty">
      <n-icon :component="MessageSquare" :size="18" />
      <span>还没有会话，发送第一条消息即可创建</span>
    </div>

    <div
      v-for="session in items"
      :key="session.id"
      class="session-item"
      :class="{ 'is-active': session.id === currentId }"
      :style="session.id === currentId ? { background: themeVars.actionColor } : undefined"
      role="button"
      tabindex="0"
      @click="emit('open', session)"
      @keydown.enter="emit('open', session)"
      @keydown.space.prevent="emit('open', session)"
    >
      <span class="session-text">
        <span class="session-title">{{ session.title || '新会话' }}</span>
        <span class="session-time">{{ formatTime(session.updatedAt) }}</span>
      </span>
      <n-popconfirm @positive-click="emit('remove', session)">
        <template #trigger>
          <n-button text size="tiny" class="session-remove" aria-label="删除会话" @click.stop>
            <template #icon><n-icon :component="Trash2" :size="14" /></template>
          </n-button>
        </template>
        删除这个会话？（仅从本机列表移除）
      </n-popconfirm>
    </div>
  </div>
</template>

<style scoped>
.session-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 4px;
  overflow-y: auto;
  flex: 1;
  min-height: 0;
}

.session-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 24px 12px;
  font-size: 12px;
  text-align: center;
  opacity: 0.6;
}

.session-item {
  display: flex;
  align-items: center;
  gap: 6px;
  min-height: 44px;
  padding: 6px 8px;
  border-radius: 8px;
  cursor: pointer;
}

.session-item:hover {
  background: rgba(127, 127, 127, 0.12);
}

.session-item:focus-visible {
  outline: 2px solid currentColor;
  outline-offset: -2px;
}

.session-text {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
  gap: 2px;
}

.session-title {
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-time {
  font-size: 11px;
  opacity: 0.55;
}

.session-remove {
  opacity: 0;
}

.session-item:hover .session-remove,
.session-item:focus-within .session-remove {
  opacity: 1;
}
</style>
