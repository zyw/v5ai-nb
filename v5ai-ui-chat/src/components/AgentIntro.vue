<script setup lang="ts">
import AgentAvatar from './AgentAvatar.vue'
import type { PortalAgent } from '../api/client'

/**
 * 空态引导：Agent 头像 + 名称 + 描述 + 欢迎语 + 预设问题。
 * 对话开始前（或新会话）显示，点预设问题即发送。
 */
defineProps<{ agent: PortalAgent }>()
const emit = defineEmits<{ (e: 'ask', question: string): void }>()
</script>

<template>
  <div class="intro">
    <AgentAvatar :agent-key="agent.agentKey" :name="agent.name" :size="64" />
    <h2 class="intro-name">{{ agent.name }}</h2>
    <p class="intro-desc">{{ agent.description || '暂无描述' }}</p>
    <p v-if="agent.greeting" class="intro-greeting">
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
  color: var(--portal-fg-muted);
}

.intro-greeting {
  margin: 6px 0 0;
  max-width: 56ch;
  padding: 10px 14px;
  border: 1px solid var(--portal-border);
  border-radius: var(--portal-radius);
  background: var(--portal-surface);
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
  color: var(--portal-fg-muted);
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
  border: 1px solid var(--portal-border);
  border-radius: 999px;
  background: var(--portal-surface);
  color: inherit;
  font: inherit;
  font-size: 13px;
  cursor: pointer;
  transition: background 0.15s var(--portal-ease), border-color 0.15s var(--portal-ease),
    transform 0.15s var(--portal-ease);
}

.intro-preset:hover {
  background: var(--portal-accent-soft);
  border-color: var(--portal-accent);
}

.intro-preset:active {
  transform: scale(0.985);
}

.intro-preset:focus-visible {
  outline: 2px solid var(--portal-accent);
  outline-offset: 2px;
}
</style>
