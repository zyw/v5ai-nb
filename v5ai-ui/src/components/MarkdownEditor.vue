<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'

/**
 * Markdown 在线编辑组件（vditor IR 编辑模式）。
 * 按需加载 vditor；props.content 变化（切换文件）时同步编辑器内容，
 * 用户输入通过 change 事件回传，程序化 setValue 不会触发 change。
 */
const props = defineProps<{ content: string }>()

const emit = defineEmits<{
  /** 用户编辑产生新内容 */
  change: [value: string]
  /** 加载 / 创建失败 */
  error: [message?: string]
}>()

const container = ref<HTMLDivElement | null>(null)

type VditorCtor = typeof import('vditor')['default']
let vditor: InstanceType<VditorCtor> | null = null
let lastEmitted = props.content

onMounted(async () => {
  try {
    const Vditor = (await import('vditor')).default
    await import('vditor/dist/index.css')
    if (!container.value) {
      emit('error')
      return
    }
    const options: ConstructorParameters<VditorCtor>[1] = {
      value: props.content,
      mode: 'ir',
      lang: 'zh_CN',
      height: '100%',
      cache: { enable: false },
      input: (value: string) => {
        if (value !== lastEmitted) {
          lastEmitted = value
          emit('change', value)
        }
      }
    }
    vditor = new Vditor(container.value, options)
  } catch (e) {
    emit('error', e instanceof Error ? e.message : 'Markdown 编辑器加载失败')
  }
})

watch(
  () => props.content,
  (value) => {
    lastEmitted = value
    vditor?.setValue(value)
  }
)

onBeforeUnmount(() => {
  vditor?.destroy()
  vditor = null
})
</script>

<template>
  <div ref="container" class="markdown-editor" />
</template>

<style scoped>
.markdown-editor {
  height: 100%;
}
</style>
