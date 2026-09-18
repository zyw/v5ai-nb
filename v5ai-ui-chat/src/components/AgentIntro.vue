<script setup lang="ts">
import { useThemeVars } from 'naive-ui'
import AgentAvatar from './AgentAvatar.vue'
import type { PortalAgent } from '../api/client'

/**
 * 空态引导：Agent 头像 + 名称 + 描述 + 欢迎语 + 预设问题。
 * 对话开始前（或新会话）显示，点预设问题即发送。
 */
defineProps<{ agent: PortalAgent }>()
const emit = defineEmits<{ (e: 'ask', question: string): void }>()

const themeVars = useThemeVars()
</script>

<template>
  <div class="intro">
    <AgentAvatar :agent-key="agent.agentKey" :name="agent.name" :size="64" />
    <h2 class="intro-name">{{ agent.name }}</h2>
    <p class="intro-desc">{{ agent.description || '暂无描述' }}</p>
    <p
      v-if="agent.greeting"
      class="intro-greeting"
      :style="{ background: themeVars.actionColor, borderColor: themeVars.borderColor }"
    >
      {{ agent.greeting }}
    </p>

    <div v-if="agent.presetQuestions.length" class="intro-presets">
      <div class="intro-presets-label">试试这样问</div>
      <div class="intro-presets-list">
        <button
          v-for="(question, index) in agent.presetQuestions"
          :key="index"
          type="button"
          class="intro-preset"
          :style="{ borderColor: themeVars.borderColor }"
          @click="emit('ask', question)"
        >
          {{ question }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.intro {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 32px 24px 16px;
  text-align: center;
}

.intro-name {
  margin: 6px 0 0;
  font-size: 20px;
  font-weight: 700;
}

.intro-desc {
  margin: 0;
  max-width: 56ch;
  font-size: 13px;
  line-height: 1.7;
  opacity: 0.7;
}

.intro-greeting {
  margin: 6px 0 0;
  max-width: 56ch;
  padding: 10px 14px;
  border: 1px solid;
  border-radius: 10px;
  font-size: 13px;
  line-height: 1.7;
}

.intro-presets {
  margin-top: 14px;
  width: 100%;
  max-width: 640px;
}

.intro-presets-label {
  font-size: 12px;
  opacity: 0.6;
  margin-bottom: 8px;
}

.intro-presets-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: center;
}

.intro-preset {
  min-height: 36px;
  padding: 8px 14px;
  border: 1px solid;
  border-radius: 999px;
  background: transparent;
  color: inherit;
  font: inherit;
  font-size: 13px;
  cursor: pointer;
}

.intro-preset:hover {
  background: rgba(127, 127, 127, 0.1);
}
</style>
