<script setup lang="ts">
/**
 * 资源图标方块：资源有自定义图标（知识库）时展示它；否则按类别展示 Lucide 矢量图标。
 * 只画 32–40px 左右的圆角方块，颜色由类别决定，跟随主题的无边框设计。
 */
import { computed } from 'vue'
import { NIcon } from 'naive-ui'
import { BookOpen, Cpu, PlugZap, Sparkles } from 'lucide-vue-next'
import type { Component } from 'vue'

const props = withDefaults(
  defineProps<{
    kind: 'model' | 'skill' | 'mcp' | 'rag'
    icon?: string | null
    size?: number
  }>(),
  { size: 36 }
)

const KIND_COLOR: Record<string, string> = {
  model: '#7C3AED',
  skill: '#0891B2',
  mcp: '#F59E0B',
  rag: '#10B981'
}

const KIND_ICON: Record<string, Component> = {
  model: Cpu,
  skill: Sparkles,
  mcp: PlugZap,
  rag: BookOpen
}

const color = computed(() => KIND_COLOR[props.kind] ?? '#7C3AED')
const glyph = computed(() => KIND_ICON[props.kind] ?? Cpu)

/** 自定义图标若是 http(s)/路径则按图片渲染，否则视为短字符（emoji/首字母）按文本渲染。 */
const isImage = computed(() => /^(https?:\/\/|\/)/.test(props.icon ?? ''))
const style = computed(() => ({
  width: `${props.size}px`,
  height: `${props.size}px`,
  background: `${color.value}1F`,
  color: color.value
}))
</script>

<template>
  <span class="resource-icon" :style="style">
    <img v-if="isImage" :src="icon!" alt="" class="resource-icon-img" />
    <span v-else-if="icon" class="resource-icon-text" :style="{ fontSize: `${Math.round(size * 0.5)}px` }">{{ icon }}</span>
    <n-icon v-else :component="glyph" :size="Math.round(size * 0.5)" />
  </span>
</template>

<style scoped>
.resource-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  border-radius: 8px;
  overflow: hidden;
}
.resource-icon-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.resource-icon-text {
  line-height: 1;
}
</style>