<script setup lang="ts">
/**
 * 助手消息正文：思考折叠 + 回答 + 错误 + 引用。
 * 知识库详情页「知识问答」tab 与智能体编辑页「预览与调试」共用，避免同一套渲染维护两份。
 *
 * 交互（与知识库问答一致）：思考默认展开，思考期间标题走「思考中…」高光；
 * 回答第一个片段到达时自动收起一次，之后用户手动开合不再干预。引用默认收起。
 */
import { computed } from 'vue'
import { NCollapse, NCollapseItem, NIcon } from 'naive-ui'
import { FileText } from 'lucide-vue-next'
import MarkdownContent from './MarkdownContent.vue'
import { awaitingFirstToken, isThinking, onReasoningToggle, type ChatMsg } from '../utils/chatStream'

/**
 * showCitations：是否渲染引用折叠块（Agent 的「引用展示」开关，默认展示）。
 *
 * 只影响渲染——引用照常检索、照常随 RETRIEVAL 事件推送、照常随消息落库（见 ADR-0009）：
 * 引用是「这条回答当时依据了什么」的快照，事后无法重建，所以开关不能是破坏性的。
 * 默认 true 是「未传参即展示」，与本次改动之前的行为一致（管理端另有一份本组件的副本，两处默认值必须相同）。
 */
const props = withDefaults(defineProps<{ msg: ChatMsg; showCitations?: boolean }>(), {
  showCitations: true
})

/**
 * 给读屏播报的那句话（见模板里的 role="status"）。
 *
 * 文案只在「开始生成」和「结束」这两个时刻变化，所以读屏整轮只念两次；
 * 正文本身不加 aria-live——那会让每个 token 都去抢播报，反而听不清。
 */
const statusText = computed(() => {
  if (props.msg.error) return `生成失败：${props.msg.error}`
  if (props.msg.stopped) return '已停止生成'
  if (props.msg.loading) return '正在生成回答'
  return props.msg.content ? '回答已完成' : ''
})
</script>

<template>
  <!-- 生成进度只在这一处播报（role="status" 隐含 aria-live="polite"） -->
  <span class="portal-sr-only" role="status">{{ statusText }}</span>

  <!-- 首 token 等待：DeepSeek 风格"思考中..."（已有思考内容时改由折叠标题承担） -->
  <div v-if="awaitingFirstToken(props.msg)" class="thinking-wait"><span class="shimmer">思考中...</span></div>
  <!-- 思考过程默认展开；思考期间标题为"思考中..."，回答开始后自动收起并恢复"思考过程" -->
  <n-collapse
    v-if="props.msg.reasoning"
    class="reasoning"
    :expanded-names="props.msg.reasoningOpen ? ['thinking'] : []"
    @update:expanded-names="(names) => onReasoningToggle(props.msg, names)"
  >
    <n-collapse-item name="thinking">
      <template #header>
        <span :class="{ shimmer: isThinking(props.msg) }">{{ isThinking(props.msg) ? '思考中...' : '思考过程' }}</span>
      </template>
      <div class="reasoning-text"><MarkdownContent :content="props.msg.reasoning" /></div>
    </n-collapse-item>
  </n-collapse>
  <MarkdownContent v-if="props.msg.content" :content="props.msg.content" />
  <div v-if="props.msg.error" class="answer-error">{{ props.msg.error }}</div>
  <n-collapse
    v-if="props.showCitations && props.msg.citations && props.msg.citations.length > 0"
    class="citations"
  >
    <n-collapse-item :title="`引用（${props.msg.citations?.length ?? 0} 条）`" name="refs">
      <div v-for="(c, ci) in props.msg.citations" :key="ci" class="citation">
        <span class="cite-doc">
          <n-icon :component="FileText" size="12" />{{ c.documentTitle || `文档 #${c.documentId}` }}
        </span>
        <!-- 历史回放读的是服务端落库数据，score 缺省过：不加判断会抛 TypeError，整条气泡渲染中断 -->
        <span v-if="Number.isFinite(c.score)" class="cite-score">相似度 {{ c.score.toFixed(4) }}</span>
        <div class="cite-snippet">{{ c.content }}</div>
      </div>
    </n-collapse-item>
  </n-collapse>
</template>

<style scoped>
.thinking-wait {
  font-size: 13.5px;
  line-height: 1.8;
  color: var(--portal-fg-muted);
}

/* "思考中..."：灰字上掠过一道强调色高光的渐变动画（background-clip:text）。 */
.shimmer {
  background: linear-gradient(
    90deg,
    var(--portal-fg-muted) 0%,
    var(--portal-fg-muted) 35%,
    var(--portal-accent) 50%,
    var(--portal-fg-muted) 65%,
    var(--portal-fg-muted) 100%
  );
  background-size: 250% 100%;
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  animation: thinking-shimmer 1.5s linear infinite;
}

@keyframes thinking-shimmer {
  from {
    background-position-x: 150%;
  }
  to {
    background-position-x: -150%;
  }
}

@media (prefers-reduced-motion: reduce) {
  .shimmer {
    animation: none;
    color: var(--portal-fg-muted);
    background: none;
  }
}

/* 思考过程：与回答同气泡但视觉降级；默认展开，思考进行中标题走 .shimmer 动画 */
.reasoning {
  margin-bottom: 8px;
}

/* 思考内容同样按 markdown 渲染：块级排版交给 MarkdownContent，这里只保留视觉降级
   （不改回 pre-wrap，否则块标签之间的换行会变成可见空行） */
.reasoning-text {
  color: var(--portal-fg-muted);
  border-left: 2px solid var(--portal-border-strong);
  padding-left: 10px;
}

.reasoning-text :deep(.md-content) {
  font-size: 12.5px;
  line-height: 1.7;
}

.answer-error {
  color: var(--portal-danger);
  font-size: 13px;
}

.citations {
  margin-top: 10px;
}

.citation {
  padding: 6px 0;
  border-top: 1px dashed var(--portal-border);
}

.cite-doc {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12.5px;
  color: var(--portal-accent);
}

.cite-score {
  float: right;
  font-size: 12px;
  color: var(--portal-fg-muted);
}

.cite-snippet {
  font-size: 12px;
  color: var(--portal-fg-muted);
  line-height: 1.6;
  margin-top: 2px;
  max-height: 60px;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
}
</style>
