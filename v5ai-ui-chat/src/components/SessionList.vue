<script setup lang="ts">
import { nextTick, ref } from 'vue'
import { NButton, NIcon, NInput, NSwitch, useThemeVars } from 'naive-ui'
import { Archive, ArchiveRestore, Check, MessageSquare, Pencil, X } from 'lucide-vue-next'
import type { ConversationSummary } from '../api/client'

/**
 * 左栏会话列表：服务端返回的会话（限本 Key 本 Agent，按最近活跃倒序），
 * 支持切换、行内改名、归档 / 取消归档，以及「显示已归档」开关。
 *
 * 未命名会话用首条提问预览做标题（名称与预览的取值由 store 的 sessionTitle 统一）。
 */
const props = defineProps<{
  items: ConversationSummary[]
  currentId: string
  showArchived: boolean
  titleOf: (session: ConversationSummary) => string
}>()

const emit = defineEmits<{
  (e: 'open', session: ConversationSummary): void
  (e: 'rename', session: ConversationSummary, name: string): void
  (e: 'archive', session: ConversationSummary, archived: boolean): void
  (e: 'update:showArchived', value: boolean): void
}>()

const themeVars = useThemeVars()
const editingId = ref('')
const editingName = ref('')
const nameInput = ref<InstanceType<typeof NInput> | null>(null)

function formatTime(value?: string | null): string {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  const today = new Date()
  const time = `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
  return date.toDateString() === today.toDateString() ? time : `${date.getMonth() + 1}/${date.getDate()}`
}

function startRename(session: ConversationSummary) {
  editingId.value = session.conversationId
  editingName.value = session.name ?? props.titleOf(session)
  void nextTick(() => nameInput.value?.focus())
}

function cancelRename() {
  editingId.value = ''
  editingName.value = ''
}

function confirmRename(session: ConversationSummary) {
  const name = editingName.value.trim()
  if (!name) return
  emit('rename', session, name)
  cancelRename()
}
</script>

<template>
  <div class="session-list">
    <label class="session-filter">
      <n-switch
        size="small"
        :value="showArchived"
        :aria-label="'显示已归档会话'"
        @update:value="(value: boolean) => emit('update:showArchived', value)"
      />
      <span>显示已归档</span>
    </label>

    <div v-if="items.length === 0" class="session-empty">
      <n-icon :component="MessageSquare" :size="18" />
      <span>{{ showArchived ? '没有已归档的会话' : '还没有会话，发送第一条消息即可创建' }}</span>
    </div>

    <div
      v-for="session in items"
      :key="session.conversationId"
      class="session-item"
      :class="{ 'is-active': session.conversationId === currentId, 'is-archived': session.archived }"
      :style="session.conversationId === currentId ? { background: themeVars.actionColor } : undefined"
      role="button"
      tabindex="0"
      @click="emit('open', session)"
      @keydown.enter="emit('open', session)"
      @keydown.space.prevent="emit('open', session)"
    >
      <template v-if="editingId === session.conversationId">
        <n-input
          ref="nameInput"
          v-model:value="editingName"
          size="tiny"
          maxlength="100"
          :aria-label="'会话名称'"
          @click.stop
          @keydown.enter.stop.prevent="confirmRename(session)"
          @keydown.esc.stop.prevent="cancelRename"
        />
        <n-button text size="tiny" aria-label="保存名称" @click.stop="confirmRename(session)">
          <template #icon><n-icon :component="Check" :size="14" /></template>
        </n-button>
        <n-button text size="tiny" aria-label="取消改名" @click.stop="cancelRename">
          <template #icon><n-icon :component="X" :size="14" /></template>
        </n-button>
      </template>

      <template v-else>
        <span class="session-text">
          <span class="session-title">{{ titleOf(session) }}</span>
          <span class="session-time">
            <span v-if="session.archived" class="session-flag">已归档</span>
            {{ formatTime(session.updatedAt) }}
          </span>
        </span>
        <span class="session-actions">
          <n-button text size="tiny" aria-label="重命名" @click.stop="startRename(session)">
            <template #icon><n-icon :component="Pencil" :size="14" /></template>
          </n-button>
          <n-button
            text
            size="tiny"
            :aria-label="session.archived ? '取消归档' : '归档'"
            @click.stop="emit('archive', session, !session.archived)"
          >
            <template #icon>
              <n-icon :component="session.archived ? ArchiveRestore : Archive" :size="14" />
            </template>
          </n-button>
        </span>
      </template>
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

.session-filter {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 6px 8px;
  font-size: 12px;
  opacity: 0.75;
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
  gap: 4px;
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

.session-item.is-archived .session-title {
  opacity: 0.6;
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
  display: flex;
  gap: 6px;
}

.session-flag {
  opacity: 0.9;
}

.session-actions {
  display: flex;
  align-items: center;
  gap: 2px;
  flex: none;
  opacity: 0;
}

.session-item:hover .session-actions,
.session-item:focus-within .session-actions {
  opacity: 1;
}
</style>
