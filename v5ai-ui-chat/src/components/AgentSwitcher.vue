<script setup lang="ts">
import { ref } from 'vue'
import { NButton, NIcon, NPopover, useThemeVars } from 'naive-ui'
import { Check, ChevronDown } from 'lucide-vue-next'
import AgentAvatar from './AgentAvatar.vue'
import { activeAgent, agents, setActiveAgent } from '../stores/portal'

/**
 * 顶栏右侧的 Agent 切换：弹窗里列出当前 API Key 可访问的 Agent（头像 + 名称 + 描述），
 * 当前项高亮；只有一个 Agent 时不显示箭头但仍可点开确认。
 */
const emit = defineEmits<{ (e: 'switch', agentKey: string): void }>()

const themeVars = useThemeVars()
const open = ref(false)

function pick(agentKey: string) {
  open.value = false
  if (agentKey === activeAgent.value?.agentKey) return
  setActiveAgent(agentKey)
  emit('switch', agentKey)
}
</script>

<template>
  <n-popover v-model:show="open" trigger="click" placement="bottom-end" :show-arrow="false" raw>
    <template #trigger>
      <!--
        n-button 的 #icon 是「前置」图标槽（iconPlacement 默认 'left'，图标排在内容之前）。
        这里原先声明了两个同名的 #icon，编译出来是 _createSlots({icon: 箭头}, [有 Agent ? {name:'icon'} : ...])，
        后者覆盖前者 —— 有 Agent 时只剩头像，箭头一次都不会渲染，「只有一个 Agent 时不显示箭头」
        的意图其实从未生效（多 Agent 时也照样没有箭头）。
        现在头像留在前置槽，箭头改成跟在名称后的尾随图标（下拉触发器的惯例位置），
        条件也照注释的本意改成「多于一个 Agent 才显示」。
      -->
      <n-button
        quaternary
        size="small"
        class="agent-switcher"
        :aria-label="'切换 Agent'"
        :aria-expanded="open"
      >
        <template #icon v-if="activeAgent">
          <AgentAvatar :agent-key="activeAgent.agentKey" :name="activeAgent.name" :size="20" />
        </template>
        <span class="switcher-label">{{ activeAgent?.name ?? '选择 Agent' }}</span>
        <n-icon v-if="agents.length > 1" :component="ChevronDown" :size="14" />
      </n-button>
    </template>

    <div class="switcher-panel">
      <div class="switcher-title">可访问的 Agent（{{ agents.length }}）</div>
      <div v-if="agents.length === 0" class="switcher-empty">当前 Key 没有可访问的 Agent</div>
      <button
        v-for="agent in agents"
        :key="agent.agentKey"
        type="button"
        class="switcher-item"
        :class="{ 'is-active': agent.agentKey === activeAgent?.agentKey }"
        @click="pick(agent.agentKey)"
      >
        <AgentAvatar :agent-key="agent.agentKey" :name="agent.name" :size="32" />
        <span class="switcher-text">
          <span class="switcher-name">{{ agent.name }}</span>
          <span class="switcher-desc">{{ agent.description || '暂无描述' }}</span>
        </span>
        <n-icon v-if="agent.agentKey === activeAgent?.agentKey" :component="Check" :size="16" :color="themeVars.primaryColor" />
      </button>
    </div>
  </n-popover>
</template>

<style scoped>
/* 名称与尾随箭头之间要留缝：n-button 的 .n-button__content 默认没有 gap，
   而前置图标槽自带的 6px 外边距管不到内容槽内部。取值与 UserMenu 一致。 */
.agent-switcher :deep(.n-button__content) {
  gap: 4px;
}

.switcher-label {
  max-width: 180px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 500;
}

.switcher-panel {
  /* 窄屏（320px 视口）下固定 320px 会横向溢出：给两边各留 12px */
  width: min(320px, calc(100vw - 24px));
  max-height: 60vh;
  overflow-y: auto;
  padding: 8px;
  border: 1px solid var(--portal-border);
  border-radius: var(--portal-radius);
  background: var(--portal-surface-solid);
  box-shadow: var(--portal-shadow-lg);
}

.switcher-title {
  padding: 6px 8px;
  font-size: 12px;
  color: var(--portal-fg-muted);
}

.switcher-empty {
  padding: 12px 8px;
  font-size: 13px;
  color: var(--portal-fg-muted);
}

.switcher-item {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  min-height: 48px;
  padding: 6px 8px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  text-align: left;
  cursor: pointer;
  color: inherit;
  font: inherit;
}

.switcher-item {
  transition: background 0.15s var(--portal-ease);
}

.switcher-item:hover {
  background: var(--portal-surface-2);
}

.switcher-item.is-active {
  background: var(--portal-accent-soft);
}

.switcher-item:focus-visible {
  outline: 2px solid var(--portal-accent);
  outline-offset: -2px;
}

.switcher-text {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
  gap: 2px;
}

.switcher-name {
  font-size: 13px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.switcher-desc {
  font-size: 12px;
  color: var(--portal-fg-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
