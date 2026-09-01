<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type * as Monaco from 'monaco-editor/editor/editor.api'

const props = withDefaults(
  defineProps<{ content: string; fileName?: string; readOnly?: boolean; height?: string }>(),
  { readOnly: true, height: '70vh' }
)

const emit = defineEmits<{
  /** 编辑器创建完成 */
  rendered: []
  /** 加载 / 创建失败 */
  error: [message?: string]
  /** 非只读模式下用户编辑产生的新内容（程序化 setValue 不触发） */
  change: [value: string]
}>()

const container = ref<HTMLDivElement | null>(null)
let editor: Monaco.editor.IStandaloneCodeEditor | null = null
let lastEmitted = props.content

onMounted(async () => {
  try {
    const monaco = await loadMonaco()
    if (!container.value) {
      emit('error')
      return
    }
    editor = monaco.editor.create(container.value, {
      value: props.content,
      language: languageOf(monaco, props.fileName ?? ''),
      readOnly: props.readOnly,
      automaticLayout: true,
      minimap: { enabled: false },
      scrollBeyondLastLine: false,
      fontSize: 13,
      wordWrap: 'on'
    })
    editor.onDidChangeModelContent(() => {
      if (props.readOnly || !editor) return
      const value = editor.getValue()
      if (value !== lastEmitted) {
        lastEmitted = value
        emit('change', value)
      }
    })
    emit('rendered')
  } catch (e) {
    emit('error', e instanceof Error ? e.message : '渲染失败')
  }
})

watch(
  () => props.content,
  (value) => {
    lastEmitted = value
    editor?.setValue(value)
  }
)

onBeforeUnmount(() => {
  editor?.dispose()
  editor = null
})

/**
 * 按需加载 monaco：editor.main（核心 + 全部语言 + 全部编辑器特性）+ 样式 + 各语言 worker。
 * monaco-editor 0.56 的 exports 已隐藏 esm/vs 目录，子路径不带该前缀；
 * editor.main.css 未在 esm 链内被引用，需单独引入（相对路径直达 node_modules）。
 */
async function loadMonaco(): Promise<typeof Monaco> {
  const [
    { default: EditorWorker },
    { default: JsonWorker },
    { default: CssWorker },
    { default: HtmlWorker },
    { default: TsWorker },
    monaco
  ] = await Promise.all([
    import('monaco-editor/editor/editor.worker?worker'),
    import('monaco-editor/language/json/json.worker?worker'),
    import('monaco-editor/language/css/css.worker?worker'),
    import('monaco-editor/language/html/html.worker?worker'),
    import('monaco-editor/language/typescript/ts.worker?worker'),
    import('monaco-editor/editor/editor.main'),
    import('../../node_modules/monaco-editor/min/vs/editor/editor.main.css')
  ])
  self.MonacoEnvironment = {
    getWorker(_workerId: string, label: string) {
      if (label === 'json') return new JsonWorker()
      if (label === 'css' || label === 'scss' || label === 'less') return new CssWorker()
      if (label === 'html' || label === 'handlebars' || label === 'razor') return new HtmlWorker()
      if (label === 'typescript' || label === 'javascript') return new TsWorker()
      return new EditorWorker()
    }
  }
  return monaco
}

/** 按文件后缀匹配 monaco 内置语言，未命中回退 plaintext */
function languageOf(monaco: typeof Monaco, fileName: string): string {
  const dot = fileName.lastIndexOf('.')
  const ext = dot >= 0 ? fileName.slice(dot).toLowerCase() : ''
  const lang = monaco.languages.getLanguages().find((l) => l.extensions?.includes(ext))
  return lang?.id ?? 'plaintext'
}
</script>

<template>
  <div ref="container" :style="{ height }" />
</template>
