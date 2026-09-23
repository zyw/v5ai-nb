<script setup lang="ts">
import { onMounted, ref } from 'vue'

const props = defineProps<{ content: string }>()

const emit = defineEmits<{
  /** vditor 渲染完成 */
  rendered: []
  /** 渲染失败 */
  error: [message?: string]
}>()

const container = ref<HTMLDivElement | null>(null)

// vditor 仅按需加载：核心 JS/CSS 随本组件动态 import，
// 可选插件库（highlight.js/katex/mermaid/emoji 等）按内容由 vditor 从默认 CDN 拉取
onMounted(async () => {
  try {
    const Vditor = (await import('vditor')).default
    await import('vditor/dist/index.css')
    if (container.value) {
      await Vditor.preview(container.value, props.content, {
        mode: 'light',
        lang: 'zh_CN'
      })
      emit('rendered')
    } else {
      emit('error')
    }
  } catch (e) {
    emit('error', e instanceof Error ? e.message : '渲染失败')
  }
})
</script>

<template>
  <div ref="container" class="markdown-preview" style="max-height: 70vh; overflow: auto" />
</template>
