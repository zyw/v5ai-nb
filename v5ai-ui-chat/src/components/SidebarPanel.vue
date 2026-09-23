<script setup lang="ts">
import { NButton, NIcon } from 'naive-ui'
import { MessageSquarePlus } from 'lucide-vue-next'
import SessionList from './SessionList.vue'
import type { ConversationSummary } from '../api/client'

/**
 * 会话栏内容：新建会话 + 会话列表。
 *
 * 桌面端放在常驻侧栏里，窄屏放进抽屉（≤768px 侧栏整体收起，见 ChatView.vue）。
 * 两处共用同一份，免得会话列表的改名 / 归档接线维护两遍。
 *
 * 只负责「新建会话」这一行和列表：再往上的品牌行（v5 标识 + 收起按钮）是桌面端侧栏独有的，
 * 放在 ChatView.vue 里，抽屉不掺和。所以这里不设插槽，也没必要知道侧栏有多宽。
 */
defineProps<{
  items: ConversationSummary[]
  currentId: string
  showArchived: boolean
  /** 列表正在整表重拉：转给 SessionList 显示骨架屏 */
  loading: boolean
  titleOf: (session: ConversationSummary) => string
}>()

const emit = defineEmits<{
  (e: 'new'): void
  (e: 'open', session: ConversationSummary): void
  (e: 'rename', session: ConversationSummary, name: string): void
  (e: 'archive', session: ConversationSummary, archived: boolean): void
  (e: 'update:showArchived', value: boolean): void
}>()
</script>

<template>
  <div class="sidebar-panel">
    <div class="sidebar-head">
      <n-button block size="small" type="primary" secondary @click="emit('new')">
        <template #icon><n-icon :component="MessageSquarePlus" :size="15" /></template>
        新建会话
      </n-button>
    </div>
    <SessionList
      :items="items"
      :current-id="currentId"
      :show-archived="showArchived"
      :loading="loading"
      :title-of="titleOf"
      @open="emit('open', $event)"
      @rename="(session, name) => emit('rename', session, name)"
      @archive="(session, archived) => emit('archive', session, archived)"
      @update:show-archived="(value: boolean) => emit('update:showArchived', value)"
    />
  </div>
</template>

<style scoped>
/* 填满宿主（侧栏或抽屉），让内部的会话列表自己滚 */
.sidebar-panel {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
}

.sidebar-head {
  flex: none;
  padding: 10px;
  border-bottom: 1px solid var(--portal-border);
}
</style>
