<script setup lang="ts">
import { computed } from 'vue'
import { agentAvatars } from '../stores/portal'

/**
 * Agent 头像：优先用 bootstrap 预取的头像（外部 URL 或 blob:），
 * 拿不到时回退到名称首字母色块——不出现破图。
 */
const props = withDefaults(defineProps<{ agentKey: string; name?: string | null; size?: number }>(), {
  size: 32
})

const src = computed(() => agentAvatars[props.agentKey] ?? '')
const initial = computed(() => (props.name || props.agentKey || '?').trim().charAt(0).toUpperCase())
const style = computed(() => ({
  width: `${props.size}px`,
  height: `${props.size}px`,
  fontSize: `${Math.round(props.size * 0.42)}px`
}))
</script>

<template>
  <span class="agent-avatar" :style="style" aria-hidden="true">
    <img v-if="src" :src="src" alt="" />
    <span v-else>{{ initial }}</span>
  </span>
</template>

<style scoped>
.agent-avatar {
  display: inline-flex;
  flex: none;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  border: 1px solid var(--portal-border);
  background: var(--portal-accent-soft);
  color: var(--portal-accent);
  overflow: hidden;
  font-weight: 600;
  line-height: 1;
}

.agent-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
</style>
