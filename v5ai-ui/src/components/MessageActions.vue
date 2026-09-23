<script setup lang="ts">
/**
 * 消息操作栏：复制（用户消息与助手回答共用）+ 重新生成（仅助手回答）。
 *
 * 位置约定：作为「消息行」的第二个子节点渲染在气泡**下方**（与 DeepSeek 一致），
 * 所以父级必须把消息行改成纵向排布（PreviewPanel 的 .chat-row 原本是横向 flex）。
 *
 * 悬停显现：根元素暴露为 .msg-actions，由父级用
 * `.xxx-row:hover .msg-actions { opacity: 1 }` 显形——Vue 会把父组件的 scope id
 * 打到子组件根节点上，因此父级 scoped 样式能命中这里。**根 class 不能改名**，
 * 改了父级那两条规则就会静默失效（按钮再也点不到）。触摸设备没有悬停，组件内常显。
 */
import { computed, onBeforeUnmount, ref } from 'vue'
import { NIcon, NTooltip, useMessage } from 'naive-ui'
import { Check, Copy, RefreshCw } from 'lucide-vue-next'

const props = defineProps<{
  /** 要复制的文本；为空时不给「复制」（失败空壳没有正文可复制） */
  text: string
  /** 是否提供「重新生成」：仅助手消息为 true */
  canRegenerate?: boolean
  /** 正在生成：复制禁用、「重新生成」隐藏（此刻的回答本就不完整） */
  busy?: boolean
  /** 操作栏靠哪一侧对齐（用户消息靠右） */
  align?: 'start' | 'end'
}>()

const emit = defineEmits<{ regenerate: [] }>()

const message = useMessage()
const copied = ref(false)
let copiedTimer: ReturnType<typeof setTimeout> | undefined

/** 两个按钮都不可用时整块不渲染，避免留下一段空白间距。 */
const visible = computed(() => !!props.text || (!!props.canRegenerate && !props.busy))

/**
 * 写剪贴板。局域网 http 下是非安全上下文，navigator.clipboard 为 undefined，
 * 因此保留 textarea + execCommand 的降级路径；两条都不行才返回 false。
 */
async function writeClipboard(text: string): Promise<boolean> {
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(text)
      return true
    }
  } catch {
    // 权限被拒或非安全上下文：落到下面的降级路径
  }
  try {
    const area = document.createElement('textarea')
    area.value = text
    // 移出视口 + 透明：不触发滚动、不闪烁
    area.style.position = 'fixed'
    area.style.top = '-1000px'
    area.style.opacity = '0'
    document.body.appendChild(area)
    area.select()
    const ok = document.execCommand('copy')
    document.body.removeChild(area)
    return ok
  } catch {
    return false
  }
}

async function copy() {
  if (props.busy || !props.text) return
  if (!(await writeClipboard(props.text))) {
    message.error('复制失败，请手动选择文本复制')
    return
  }
  copied.value = true
  if (copiedTimer) clearTimeout(copiedTimer)
  copiedTimer = setTimeout(() => {
    copied.value = false
  }, 1500)
}

onBeforeUnmount(() => {
  if (copiedTimer) clearTimeout(copiedTimer)
})
</script>

<template>
  <div v-if="visible" class="msg-actions" :class="props.align ?? 'start'">
    <n-tooltip v-if="props.text" trigger="hover">
      <template #trigger>
        <button
          class="act"
          type="button"
          :disabled="props.busy"
          :aria-label="copied ? '已复制' : '复制'"
          @click="copy"
        >
          <n-icon :component="copied ? Check : Copy" size="15" />
        </button>
      </template>
      {{ copied ? '已复制' : '复制' }}
    </n-tooltip>
    <n-tooltip v-if="props.canRegenerate && !props.busy" trigger="hover">
      <template #trigger>
        <button class="act" type="button" aria-label="重新生成" @click="emit('regenerate')">
          <n-icon :component="RefreshCw" size="15" />
        </button>
      </template>
      重新生成
    </n-tooltip>
  </div>
</template>

<style scoped>
.msg-actions {
  display: flex;
  align-items: center;
  gap: 2px;
  margin-top: 2px;
  /* 默认隐藏，由父级行上的 :hover / :focus-within 显形（触摸设备常显见下） */
  opacity: 0;
  transition: opacity 0.15s;
}

.msg-actions.end {
  justify-content: flex-end;
}

/* 触摸设备没有悬停：常显，否则按钮永远点不到 */
@media (hover: none) {
  .msg-actions {
    opacity: 1;
  }
}

.act {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  padding: 0;
  border: none;
  border-radius: 6px;
  background: none;
  color: rgba(120, 120, 120, 0.95);
  cursor: pointer;
  transition: background-color 0.15s, color 0.15s;
}

.act:hover:not(:disabled) {
  background: rgba(128, 128, 128, 0.14);
  color: var(--n-text-color-1, #344054);
}

.act:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

.act:focus-visible {
  outline: 2px solid #2f6bff;
  outline-offset: 1px;
}
</style>
