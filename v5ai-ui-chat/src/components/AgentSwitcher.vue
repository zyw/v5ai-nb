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
      <n-button quaternary size="small" :aria-label="'切换 Agent'" :aria-expanded="open">
        <template #icon v-if="activeAgent">
          <AgentAvatar :agent-key="activeAgent.agentKey" :name="activeAgent.name" :size="20" />
        </template>
        <span class="switcher-label">{{ activeAgent?.name ?? '选择 Agent' }}</span>
        <template #icon><n-icon :component="ChevronDown" :size="14" /></template>
      </n-button>
    </template>

    <div class="switcher-panel" :style="{ background: themeVars.popoverColor, borderColor: themeVars.borderColor }">
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
.switcher-label {
  max-width: 180px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 500;
}

.switcher-panel {
  width: 320px;
  max-height: 60vh;
  overflow-y: auto;
  padding: 8px;
  border: 1px solid;
  border-radius: 10px;
  box-shadow: 0 6px 24px rgba(0, 0, 0, 0.12);
}

.switcher-title {
  padding: 6px 8px;
  font-size: 12px;
  opacity: 0.65;
}

.switcher-empty {
  padding: 12px 8px;
  font-size: 13px;
  opacity: 0.65;
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

.switcher-item:hover,
.switcher-item.is-active {
  background: rgba(127, 127, 127, 0.12);
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
  opacity: 0.65;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
