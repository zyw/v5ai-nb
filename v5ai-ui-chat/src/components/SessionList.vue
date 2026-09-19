<script setup lang="ts">
import { h, nextTick, ref } from 'vue'
import type { Component, ComponentPublicInstance, VNode } from 'vue'
import { NButton, NDropdown, NIcon, NInput, NSwitch } from 'naive-ui'
import type { DropdownOption } from 'naive-ui'
import { Archive, ArchiveRestore, Check, MessageSquare, MoreHorizontal, Pencil, X } from 'lucide-vue-next'
import type { ConversationSummary } from '../api/client'

/**
 * 左栏会话列表：服务端返回的会话（限本 Key 本 Agent，按最近活跃倒序），
 * 支持切换、行内改名、归档 / 取消归档，以及「显示已归档」开关。
 *
 * 每行的操作收在「…」菜单里（重命名 / 归档），鼠标悬停或该行处于当前会话时显示，
 * 避免两个图标常驻挤压标题空间。未命名会话用首条提问预览做标题
 * （名称与预览的取值由 store 的 sessionTitle 统一）。
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

const editingId = ref('')
const editingName = ref('')
/** 当前展开「…」菜单的会话：菜单开着的时候触发器要保持可见（鼠标移开也不再 hover） */
const openMenuId = ref('')
const nameInput = ref<InstanceType<typeof NInput> | null>(null)

function formatTime(value?: string | null): string {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  const today = new Date()
  const time = `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
  return date.toDateString() === today.toDateString() ? time : `${date.getMonth() + 1}/${date.getDate()}`
}

/**
 * 改名输入框的模板 ref：列表是 v-for 渲染的，字符串 ref 会被 Vue 收集成数组
 * （vnode 上带 `ref_for`），拿不到组件实例，因此用函数 ref 只记录正在改名的那一个。
 */
function setNameInput(el: Element | ComponentPublicInstance | null) {
  // 卸载时 Vue 会回调 null；忽略它，免得「新行先挂载、旧行后卸载」把新实例冲掉
  if (el) nameInput.value = el as InstanceType<typeof NInput>
}

function startRename(session: ConversationSummary) {
  editingId.value = session.conversationId
  editingName.value = session.name ?? props.titleOf(session)
  void nextTick(() => nameInput.value?.focus())
}

/**
 * 改名输入框的按键：Enter 保存、Esc 取消，并阻断冒泡以免触发行上的「回车打开会话」。
 * 不写成 `@keydown.enter` + `@keydown.esc`：同一元素的多个 keydown 监听会被编译成数组，
 * 而 n-input 的 `onKeydown` 只接受单个函数（Vue 会报 Invalid prop 警告）。
 */
function onRenameKeydown(event: KeyboardEvent, session: ConversationSummary) {
  if (event.key !== 'Enter' && event.key !== 'Escape') return
  event.preventDefault()
  event.stopPropagation()
  if (event.key === 'Enter') confirmRename(session)
  else cancelRename()
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

/** 下拉项图标：naive-ui 的 `icon` 要求一个返回 VNode 的函数，不能直接给组件。 */
function renderIcon(icon: Component): () => VNode {
  return () => h(NIcon, { size: 14 }, { default: () => h(icon) })
}

/** 每行的「…」菜单：重命名 / 归档（已归档的会话则是取消归档）。 */
function menuOptions(session: ConversationSummary): DropdownOption[] {
  return [
    { label: '重命名', key: 'rename', icon: renderIcon(Pencil) },
    {
      label: session.archived ? '取消归档' : '归档',
      key: 'archive',
      icon: renderIcon(session.archived ? ArchiveRestore : Archive)
    }
  ]
}

function onMenuSelect(key: string | number, session: ConversationSummary) {
  if (key === 'rename') {
    startRename(session)
    return
  }
  if (key === 'archive') {
    emit('archive', session, !session.archived)
  }
}
</script>

<template>
  <div class="session-list portal-scroll">
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
      :class="{
        'is-active': session.conversationId === currentId,
        'is-archived': session.archived,
        'is-menu-open': openMenuId === session.conversationId
      }"
      role="button"
      tabindex="0"
      @click="emit('open', session)"
      @keydown.enter="emit('open', session)"
      @keydown.space.prevent="emit('open', session)"
    >
      <template v-if="editingId === session.conversationId">
        <n-input
          :ref="setNameInput"
          v-model:value="editingName"
          size="tiny"
          maxlength="100"
          :aria-label="'会话名称'"
          @click.stop
          @keydown="onRenameKeydown($event, session)"
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
        <n-dropdown
          trigger="click"
          placement="bottom-end"
          :options="menuOptions(session)"
          @select="(key: string | number) => onMenuSelect(key, session)"
          @update:show="(show: boolean) => (openMenuId = show ? session.conversationId : '')"
        >
          <n-button text size="tiny" class="session-more" aria-label="更多操作" @click.stop>
            <template #icon><n-icon :component="MoreHorizontal" :size="16" /></template>
          </n-button>
        </n-dropdown>
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

.session-item {
  transition: background 0.15s var(--portal-ease);
}

.session-item:hover {
  background: var(--portal-surface-2);
}

/* 当前会话：淡强调色底 + 左侧竖条，不整块刷成高饱和 */
.session-item.is-active {
  background: var(--portal-accent-soft);
  box-shadow: inset 2px 0 0 var(--portal-accent);
}

.session-item:focus-visible {
  outline: 2px solid var(--portal-accent);
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

.session-more {
  flex: none;
  opacity: 0;
}

.session-item:hover .session-more,
.session-item:focus-within .session-more,
.session-item.is-active .session-more,
.session-item.is-menu-open .session-more {
  opacity: 1;
}
</style>
