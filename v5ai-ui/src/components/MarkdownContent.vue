<script setup lang="ts">
/**
 * 对话回答的 Markdown 渲染：marked 解析 → DOMPurify 消毒 → highlight.js 代码高亮 + KaTeX 公式。
 *
 * 为什么不用 components/MarkdownPreview.vue（Vditor.preview）：那是给"整篇文档预览"用的——
 * 每次调用整体重写容器 DOM，且动态 import 整个 vditor（~1MB）并从 CDN 拉可选插件，
 * 放在每秒多次增量的流式气泡里会反复重排、并拖垮离线环境。
 * 这里全部同步渲染，可以在每个增量上安全重算。
 *
 * 模型输出是不可信内容（信任边界）：marked 默认放行原始 HTML，所以必须过 DOMPurify 再 v-html；
 * 外链统一新窗口打开（补 rel），<img> 一律剥离（外链图片可能被当作追踪像素）。
 */
import { computed } from 'vue'
import { Marked, type Tokens } from 'marked'
import markedKatex from 'marked-katex-extension'
import DOMPurify from 'dompurify'
import hljs from 'highlight.js/lib/core'
import bash from 'highlight.js/lib/languages/bash'
import css from 'highlight.js/lib/languages/css'
import go from 'highlight.js/lib/languages/go'
import java from 'highlight.js/lib/languages/java'
import javascript from 'highlight.js/lib/languages/javascript'
import json from 'highlight.js/lib/languages/json'
import markdown from 'highlight.js/lib/languages/markdown'
import python from 'highlight.js/lib/languages/python'
import rust from 'highlight.js/lib/languages/rust'
import sql from 'highlight.js/lib/languages/sql'
import typescript from 'highlight.js/lib/languages/typescript'
import xml from 'highlight.js/lib/languages/xml'
import yaml from 'highlight.js/lib/languages/yaml'
import 'highlight.js/styles/github.css'
import 'katex/dist/katex.min.css'

const props = defineProps<{ content: string }>()

/** 只注册常用语言：hljs 全量包会让引用它的分包多出约 1MB。 */
const LANGUAGES = { bash, css, go, java, javascript, json, markdown, python, rust, sql, typescript, xml, yaml }
for (const [name, language] of Object.entries(LANGUAGES)) {
  hljs.registerLanguage(name, language)
}

function escapeHtml(text: string): string {
  const map: Record<string, string> = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }
  return text.replace(/[&<>"']/g, (c) => map[c] ?? c)
}

const marked = new Marked({ gfm: true, breaks: true })
marked.use(markedKatex({ throwOnError: false }))
marked.use({
  renderer: {
    code({ text, lang }: Tokens.Code): string {
      // 语言标记可能带多余参数（如 ```js title=x），只取第一个词
      const language = (lang ?? '').trim().split(/\s+/)[0]
      if (language && hljs.getLanguage(language)) {
        const { value } = hljs.highlight(text, { language, ignoreIllegals: true })
        return `<pre class="hljs"><code class="language-${escapeHtml(language)}">${value}</code></pre>`
      }
      return `<pre class="hljs"><code>${escapeHtml(text)}</code></pre>`
    }
  }
})

DOMPurify.addHook('afterSanitizeAttributes', (node) => {
  if (node.tagName === 'A' && node.getAttribute('href')) {
    node.setAttribute('target', '_blank')
    node.setAttribute('rel', 'noopener noreferrer')
  }
})

const html = computed(() =>
  DOMPurify.sanitize(marked.parse(props.content) as string, { FORBID_TAGS: ['img'] })
)
</script>

<template>
  <div class="md-content" v-html="html" />
</template>

<style scoped>
/* 外层可能是 white-space: pre-wrap 的纯文本气泡：这里必须复位，
   否则 marked 输出的块标签之间换行会被当成可见空行。scoped 样式打不到 v-html 内容，统一用 :deep()。 */
.md-content {
  white-space: normal;
  font-size: 13.5px;
  line-height: 1.75;
  word-break: break-word;
}

.md-content :deep(> :first-child) {
  margin-top: 0;
}

.md-content :deep(> :last-child) {
  margin-bottom: 0;
}

.md-content :deep(p) {
  margin: 0 0 8px;
}

.md-content :deep(h1),
.md-content :deep(h2),
.md-content :deep(h3),
.md-content :deep(h4),
.md-content :deep(h5),
.md-content :deep(h6) {
  margin: 14px 0 8px;
  font-weight: 600;
  line-height: 1.4;
}

.md-content :deep(h1) {
  font-size: 19px;
}

.md-content :deep(h2) {
  font-size: 17px;
}

.md-content :deep(h3) {
  font-size: 15px;
}

.md-content :deep(h4) {
  font-size: 14px;
}

.md-content :deep(ul),
.md-content :deep(ol) {
  margin: 0 0 8px;
  padding-left: 22px;
}

.md-content :deep(li) {
  margin: 2px 0;
}

.md-content :deep(li > p) {
  margin: 0;
}

.md-content :deep(code) {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12.5px;
  background: rgba(128, 128, 128, 0.14);
  border-radius: 4px;
  padding: 1px 4px;
}

.md-content :deep(pre) {
  margin: 0 0 10px;
  border: 1px solid rgba(128, 128, 128, 0.2);
  border-radius: 8px;
  overflow-x: auto;
}

.md-content :deep(pre code) {
  display: block;
  padding: 10px 12px;
  background: none;
  font-size: 12.5px;
  line-height: 1.6;
}

.md-content :deep(blockquote) {
  margin: 0 0 8px;
  padding: 2px 0 2px 10px;
  border-left: 3px solid rgba(128, 128, 128, 0.35);
  color: rgba(90, 90, 90, 0.95);
}

.md-content :deep(table) {
  display: block;
  max-width: 100%;
  overflow-x: auto;
  border-collapse: collapse;
  margin: 0 0 10px;
}

.md-content :deep(th),
.md-content :deep(td) {
  border: 1px solid rgba(128, 128, 128, 0.25);
  padding: 4px 8px;
  text-align: left;
}

.md-content :deep(th) {
  background: rgba(128, 128, 128, 0.08);
  font-weight: 600;
}

.md-content :deep(a) {
  color: #2f6bff;
  text-decoration: none;
}

.md-content :deep(a:hover) {
  text-decoration: underline;
}

.md-content :deep(hr) {
  border: none;
  border-top: 1px solid rgba(128, 128, 128, 0.25);
  margin: 12px 0;
}

/* 公式可能很长：给横向滚动，别把气泡撑破 */
.md-content :deep(.katex-display) {
  margin: 10px 0;
  overflow-x: auto;
  overflow-y: hidden;
}
</style>
