<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { NButton, NIcon, NTooltip } from 'naive-ui'
import { Check, Clock, Copy, Database, RotateCcw } from 'lucide-vue-next'
import type { ChatMsg } from '../utils/chatStream'

/**
 * 消息操作条：两种角色都能复制；助手回答额外有「重新生成」与本次运行的「用量 / 用时」。
 *
 * 复制在非安全上下文（http + 局域网 IP，navigator.clipboard 不可用）下退回
 * 临时 textarea + execCommand，避免只有 localhost 能复制。
 */
const props = defineProps<{
  msg: ChatMsg
  /** 是否允许重新生成：流式进行中不给点 */
  canRegenerate: boolean
}>()

const emit = defineEmits<{ (e: 'regenerate'): void }>()

const copied = ref(false)
let resetTimer: ReturnType<typeof setTimeout> | undefined

onBeforeUnmount(() => clearTimeout(resetTimer))

async function copy() {
  const text = props.msg.content ?? ''
  if (!text) return
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(text)
    } else {
      fallbackCopy(text)
    }
  } catch {
    fallbackCopy(text)
  }
  copied.value = true
  clearTimeout(resetTimer)
  resetTimer = setTimeout(() => (copied.value = false), 1500)
}

/** 非安全上下文兜底：临时 textarea + execCommand（已废弃但仍是唯一可用手段）。 */
function fallbackCopy(text: string) {
  const area = document.createElement('textarea')
  area.value = text
  area.setAttribute('readonly', '')
  area.style.position = 'fixed'
  area.style.top = '-1000px'
  area.style.opacity = '0'
  document.body.appendChild(area)
  area.select()
  document.execCommand('copy')
  document.body.removeChild(area)
}

/** 用时：不足 1 秒保留毫秒；其余用中文单位，整数秒不带小数（56秒 / 3.4秒 / 820ms） */
function formatDuration(ms: number): string {
  if (ms < 1000) return `${ms}ms`
  const seconds = ms / 1000
  return seconds >= 10 ? `${Math.round(seconds)}秒` : `${seconds.toFixed(1)}秒`
}

/** 用量：千位以上收敛成 K（811K / 1.5K），不显示无意义的小数位。
    不再缀英文 "tok"——它紧跟在中文单位「用量」后面是重复的。 */
function formatTokens(tokens: number): string {
  if (tokens < 1000) return `${tokens}`
  const k = tokens / 1000
  return `${k >= 10 ? Math.round(k) : k.toFixed(1)}K`
}

const usage = computed(() => props.msg.usage)

const usageDetail = computed(() => {
  const value = usage.value
  if (!value) return ''
  // token 以模型回报的真实用量为准；服务端没有拿到回报时才回退为平台估算
  return `输入 ${value.promptTokens} · 输出 ${value.completionTokens}（模型回报；未回报时为平台估算）`
})
</script>

<template>
  <div class="message-actions">
    <n-tooltip trigger="hover">
      <template #trigger>
        <n-button text size="tiny" :aria-label="copied ? '已复制' : '复制'" @click="copy">
          <template #icon><n-icon :component="copied ? Check : Copy" :size="14" /></template>
        </n-button>
      </template>
      {{ copied ? '已复制' : '复制' }}
    </n-tooltip>

    <n-tooltip v-if="msg.role === 'assistant'" trigger="hover">
      <template #trigger>
        <n-button
          text
          size="tiny"
          :disabled="!canRegenerate || msg.loading"
          aria-label="重新生成"
          @click="emit('regenerate')"
        >
          <template #icon><n-icon :component="RotateCcw" :size="14" /></template>
        </n-button>
      </template>
      重新生成
    </n-tooltip>

    <n-tooltip v-if="msg.role === 'assistant' && usage" trigger="hover">
      <template #trigger>
        <span class="message-metrics">
          <span class="message-metric">
            <n-icon :component="Database" :size="13" />
            <span>用量 {{ formatTokens(usage.totalTokens) }}</span>
          </span>
          <span class="message-metric">
            <n-icon :component="Clock" :size="13" />
            <span>用时 {{ formatDuration(usage.durationMs) }}</span>
          </span>
        </span>
      </template>
      {{ usageDetail }}
    </n-tooltip>
  </div>
</template>

<style scoped>
.message-actions {
  display: flex;
  align-items: center;
  /* 图标按钮之间留出可点、可辨的间距（原来 2px 挤在一起） */
  gap: 12px;
  min-height: 20px;
  font-size: 12px;
  color: var(--portal-fg-muted);
  opacity: 0;
  transition: opacity 0.15s ease;
}

/* 图标按钮跟随整行灰度：naive-ui 的文字按钮用自带 CSS 变量设色，这里直接压成继承 */
.message-actions :deep(.n-button) {
  color: inherit;
}

/* 触屏没有 hover：操作条常驻，否则点了复制也看不见反馈 */
@media (hover: none) {
  .message-actions {
    opacity: 1;
  }
}

/* 用量 / 用时：图标 + 标签 + 值，同一档灰度，无底色、无分隔符（对齐参考稿） */
.message-metrics {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  white-space: nowrap;
  cursor: default;
  /* 等宽数字：流式刷新用量/用时时字符宽度不跳，整条操作条不会跟着抖 */
  font-variant-numeric: tabular-nums;
}

.message-metric {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  /* 行盒压到与字号同高：继承来的 1.5 倍行高会把文字整体往下推，跟图标错开 */
  line-height: 1;
}

/*
 * n-icon 内部是 <i><svg/></i>：svg 默认按基线排（底部贴基线），而 <i> 的行盒比字高矮，
 * 结果是图形整体上飘 1~2px，看着就是「图标比文字高一点」。把 <i> 变成 flex 容器让 svg 精确居中。
 */
.message-metric :deep(.n-icon) {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: none;
}
</style>
