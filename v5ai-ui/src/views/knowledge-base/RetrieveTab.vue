<script setup lang="ts">
/**
 * 知识库详情页「知识检索」tab：左检索参数面板 + 主区输入检索 → 命中卡片。
 */
import { onMounted, ref, watch } from 'vue'
import { NButton, NEmpty, NIcon, NInput, NSpin, useMessage } from 'naive-ui'
import { FileText, Search } from 'lucide-vue-next'
import { retrieveKnowledgeBase, type KbHitResponse } from '../../api/client'
import { adminToken } from '../../stores/session'
import KnowledgeDebugParams, { type KbModelParamsState, type KbSearchParamsState } from './KnowledgeDebugParams.vue'

const props = defineProps<{
  kbId: number
  active: boolean
  params: KbSearchParamsState
  modelParams: KbModelParamsState
  chatModels: { value: number; label: string; isDefault?: boolean }[]
  rerankModels: { value: number; label: string; isDefault?: boolean }[]
}>()

const message = useMessage()
const query = ref('')
const loading = ref(false)
const hits = ref<KbHitResponse[]>([])
const searched = ref(false)

async function search() {
  if (!query.value.trim()) {
    message.warning('请输入想要检索的内容')
    return
  }
  loading.value = true
  try {
    const request: Record<string, unknown> = {
      query: query.value.trim(),
      resultCount: props.params.resultCount,
      questionRewrite: props.params.questionRewrite,
      thresholdEnabled: props.params.thresholdEnabled,
      threshold: props.params.thresholdEnabled ? props.params.threshold : undefined,
      fusionStrategy: props.params.fusionStrategy,
      rrfK: props.params.fusionStrategy === 'RRF' ? props.params.rrfK : undefined,
      denseWeight: props.params.fusionStrategy === 'WEIGHTED_SUM' ? props.params.denseWeight : undefined
    }
    if (props.params.questionRewrite && props.modelParams.modelId != null) {
      request.modelId = props.modelParams.modelId
    }
    hits.value = await retrieveKnowledgeBase(adminToken.value, props.kbId, request as never)
    searched.value = true
  } catch (e) {
    message.error(e instanceof Error ? e.message : '检索失败')
  } finally {
    loading.value = false
  }
}

watch(
  () => props.active,
  (active) => {
    if (active && query.value.trim() && hits.value.length === 0) void search()
  }
)

onMounted(() => {
  if (props.active) void search()
})

function excerpt(content: string, max = 280): string {
  const collapsed = content.replace(/\s+/g, ' ').trim()
  return collapsed.length > max ? `${collapsed.slice(0, max)}…` : collapsed
}
</script>

<template>
  <div class="retrieve-tab">
    <aside class="retrieve-side">
      <KnowledgeDebugParams :params="params" :chat-models="chatModels" :rerank-models="rerankModels" :model-params="modelParams" mode="retrieval" />
    </aside>
    <main class="retrieve-main">
      <div class="search-bar">
        <n-input
          v-model:value="query"
          placeholder="输入想要检索的内容"
          clearable
          size="large"
          @keyup.enter="search"
        />
        <n-button type="primary" size="large" :loading="loading" @click="search">
          <template #icon><n-icon :component="Search" /></template>
          搜索
        </n-button>
      </div>
      <div v-if="hits.length === 0 && !loading" class="main-empty">
        <n-empty v-if="!searched" description="输入内容开始检索">
          <template #icon><n-icon :component="FileText" /></template>
        </n-empty>
        <n-empty v-else description="未检索到相关内容，可调整左侧参数后重试" />
      </div>
      <n-spin :show="loading">
        <div v-if="hits.length > 0" class="hit-list">
          <div v-for="(hit, i) in hits" :key="`${hit.documentId}-${hit.chunkIndex}`" class="hit-card">
            <div class="hit-head">
              <span class="hit-seq">#{{ i + 1 }}</span>
              <span class="hit-score">相似度 {{ hit.score.toFixed(4) }}</span>
            </div>
            <div class="hit-doc">
              <n-icon :component="FileText" size="13" />
              {{ hit.documentTitle || `文档 #${hit.documentId}` }}（切片 {{ hit.chunkIndex }}）
            </div>
            <p class="hit-content">{{ excerpt(hit.content) }}</p>
          </div>
        </div>
      </n-spin>
    </main>
  </div>
</template>

<style scoped>
.retrieve-tab {
  display: flex;
  gap: 20px;
  min-height: 420px;
}

.retrieve-side {
  width: 300px;
  flex-shrink: 0;
  border-right: 1px solid rgba(128, 128, 128, 0.18);
  padding-right: 16px;
}

.retrieve-main {
  flex: 1;
  min-width: 0;
}

.search-bar {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
}

.main-empty {
  padding: 60px 0;
  display: flex;
  justify-content: center;
}

.hit-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.hit-card {
  border: 1px solid rgba(128, 128, 128, 0.22);
  border-radius: 10px;
  padding: 12px 14px;
}

.hit-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.hit-seq {
  color: #2f7bff;
  font-weight: 600;
  font-size: 13px;
}

.hit-score {
  margin-left: auto;
  font-size: 12px;
  color: rgba(120, 120, 120, 0.95);
}

.hit-doc {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 12.5px;
  color: rgba(90, 90, 90, 0.95);
  margin-bottom: 4px;
}

.hit-content {
  margin: 0;
  font-size: 13px;
  line-height: 1.7;
  color: rgba(50, 50, 50, 0.92);
  white-space: pre-line;
}
</style>
