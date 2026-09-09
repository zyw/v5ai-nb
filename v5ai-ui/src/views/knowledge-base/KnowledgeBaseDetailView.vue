<script setup lang="ts">
/**
 * 知识库详情页：从知识库列表点击库名进入（站内子路由，保留平台框架）。
 * 头部（返回 + 库名 + ID + 嵌入模型 + 更新时间）与四个 tab 共用；
 * 调试参数为页内会话状态（四 tab 间共享、不回写），初始值读知识库已存配置。
 */
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NButton, NIcon, NPopover, NSkeleton, NSpin, NSpace, NTabs, NTabPane, NText, useMessage } from 'naive-ui'
import { ArrowLeft, CircleQuestionMark, Library } from 'lucide-vue-next'
import {
  getKnowledgeBaseDetail,
  listModelOptions,
  updateKnowledgeBase,
  type CreateKnowledgeBaseRequest,
  type KnowledgeBaseResponse,
  type OptionResponse
} from '../../api/client'
import { adminToken } from '../../stores/session'
import { formatDateTime } from '../../utils/dateUtils'
import { type KbModelParamsState, type KbSearchParamsState } from './KnowledgeDebugParams.vue'
import { DEFAULT_PROMPT, defaultModelParams, defaultSearchParams, toRagModelParams, toRagSearchParams } from './params'
import DocumentsTab from './DocumentsTab.vue'
import ChunksTab from './ChunksTab.vue'
import RetrieveTab from './RetrieveTab.vue'
import QaTab from './QaTab.vue'

const route = useRoute()
const router = useRouter()
const message = useMessage()

const kbId = computed(() => Number(route.params.id))
const loading = ref(true)
const kb = ref<KnowledgeBaseResponse | null>(null)
const embeddingOptions = ref<OptionResponse[]>([])
const chatModels = ref<OptionResponse[]>([])
const tab = ref<'documents' | 'chunks' | 'retrieve' | 'qa'>('documents')
const presetDocId = ref<number | null>(null)

const searchParams = reactive<KbSearchParamsState>(defaultSearchParams())
const modelParams = reactive<KbModelParamsState>(defaultModelParams())
const rerankModels = ref<OptionResponse[]>([])
const initializing = ref(false)
let saveTimer: number | undefined

const embeddingName = computed(() => {
  const found = embeddingOptions.value.find((o) => o.value === kb.value?.embeddingModelId)
  return found?.label ?? (kb.value?.embeddingModelId != null ? `模型 #${kb.value.embeddingModelId}` : '—')
})

function applyConfig(config: KnowledgeBaseResponse['config']) {
  Object.assign(searchParams, defaultSearchParams())
  Object.assign(modelParams, defaultModelParams())
  const sp = config?.searchParams
  if (sp) {
    searchParams.resultCount = sp.resultCount ?? 20
    searchParams.questionRewrite = !!sp.questionRewrite
    searchParams.rerankEnabled = !!sp.rerankEnabled
    searchParams.rerankModelId = sp.rerankModelId ?? null
    searchParams.enterRerankCount = sp.enterRerankCount ?? 30
    searchParams.thresholdEnabled = !!sp.thresholdEnabled
    searchParams.threshold = sp.threshold ?? 0.5
    if (sp.fusionStrategy) {
      const up = sp.fusionStrategy.toUpperCase()
      searchParams.fusionStrategy = (up === 'WEIGHTED_SUM' || up === 'VECTOR' || up === 'KEYWORD')
        ? (up as KbSearchParamsState['fusionStrategy']) : 'RRF'
    }
    searchParams.rrfK = sp.rrfK ?? 60
    searchParams.denseWeight = sp.denseWeight ?? 0.5
  }
  const mp = config?.modelParams
  if (mp) {
    modelParams.modelId = mp.modelId ?? null
    modelParams.nearbySliceCount = mp.nearbySliceCount ?? 5
    modelParams.prompt = mp.prompt?.trim() ? mp.prompt : DEFAULT_PROMPT
  }
}

async function load() {
  loading.value = true
  initializing.value = true
  try {
    const [detail, chatRows, embedRows, rerankRows] = await Promise.all([
      getKnowledgeBaseDetail(adminToken.value, kbId.value),
      listModelOptions(adminToken.value, 'CHAT'),
      listModelOptions(adminToken.value, 'EMBEDDING'),
      listModelOptions(adminToken.value, 'RERANK')
    ])
    kb.value = detail
    chatModels.value = chatRows
    embeddingOptions.value = embedRows
    rerankModels.value = rerankRows
    applyConfig(detail.config)
    if (modelParams.modelId == null && chatModels.value.length > 0) {
      modelParams.modelId = chatModels.value[0].value
    }
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载知识库详情失败')
  } finally {
    initializing.value = false
    loading.value = false
  }
}

function buildWriteBackRequest(): CreateKnowledgeBaseRequest {
  const k = kb.value!
  return {
    id: k.id,
    name: k.name,
    description: k.description ?? undefined,
    embeddingModelId: k.embeddingModelId,
    vectorStoreInstanceId: k.vectorStoreInstanceId!,
    dimensionOfVectorModel: k.dimensionOfVectorModel,
    searchEngineEnable: k.searchEngineEnable,
    searchEngineInstanceId: k.searchEngineInstanceId ?? undefined,
    delimiter: k.delimiter ?? undefined,
    dedupStrategy: k.dedupStrategy,
    dedupAction: k.dedupAction,
    config: {
      ...k.config,
      searchParams: toRagSearchParams(searchParams),
      modelParams: toRagModelParams(modelParams)
    }
  }
}

async function persistParams() {
  if (!kb.value) return
  try {
    await updateKnowledgeBase(adminToken.value, buildWriteBackRequest())
  } catch (e) {
    message.error(e instanceof Error ? e.message : '参数自动保存失败')
  }
}

function scheduleSave() {
  if (saveTimer) window.clearTimeout(saveTimer)
  saveTimer = window.setTimeout(() => {
    saveTimer = undefined
    void persistParams()
  }, 500)
}

// 「知识检索 / 知识问答」tab 参数变更即自动回写知识库配置（防抖 500ms）。
watch([searchParams, modelParams], () => {
  if (initializing.value) return
  scheduleSave()
}, { deep: true })

onBeforeUnmount(() => {
  if (saveTimer) window.clearTimeout(saveTimer)
})

function goToChunks(documentId: number) {
  presetDocId.value = documentId
  tab.value = 'chunks'
}

function goBack() {
  void router.push({ name: 'knowledge-bases' })
}

watch(kbId, () => {
  tab.value = 'documents'
  presetDocId.value = null
  void load()
})

onMounted(load)
</script>

<template>
  <div class="page kb-detail-page">
    <n-spin :show="loading">
      <div v-if="!kb" class="detail-loading">
        <n-skeleton v-if="loading" text :repeat="4" style="max-width: 600px" />
        <div v-else class="detail-missing">
          <n-text depth="2">知识库不存在或已被删除</n-text>
          <n-button size="small" secondary style="margin-top: 10px" @click="goBack">返回知识库列表</n-button>
        </div>
      </div>

      <template v-else>
        <div class="kb-head">
          <div class="kb-head-main">
            <n-button quaternary circle aria-label="返回知识库列表" @click="goBack">
              <template #icon><n-icon :component="ArrowLeft" /></template>
            </n-button>
            <n-icon :component="Library" size="22" class="kb-icon" />
            <div class="kb-title-wrap">
              <div class="kb-title">{{ kb.name }}</div>
              <div class="kb-meta">
                <span>ID {{ kb.id }}</span>
                <span class="sep">·</span>
                <span>嵌入模型 {{ embeddingName }}</span>
                <span class="sep">·</span>
                <span>更新时间 {{ formatDateTime(kb.updatedAt) ?? '—' }}</span>
              </div>
            </div>
            <n-popover trigger="hover" placement="bottom">
              <template #trigger>
                <n-icon :component="CircleQuestionMark" size="14" class="head-help" />
              </template>
              <div style="max-width: 300px">
                本页 4 个 tab 均围绕该知识库调试。左侧「知识检索 / 知识问答」参数变更后会自动保存回该知识库配置（防抖）。
              </div>
            </n-popover>
          </div>
          <n-space :size="8" class="kb-head-stats">
            <n-tag :bordered="false" type="default">文档 {{ kb.docCount ?? 0 }}</n-tag>
            <n-tag :bordered="false" type="default">切片 {{ kb.chunkCount ?? 0 }}</n-tag>
            <n-tag :bordered="false" type="info">{{ kb.status === 'ACTIVE' ? '启用' : '已禁用' }}</n-tag>
          </n-space>
        </div>

        <n-tabs v-model:value="tab" type="line" animated class="kb-tabs">
          <n-tab-pane name="documents" tab="原始文档">
            <DocumentsTab :kb-id="kb.id" :active="tab === 'documents'" @go-chunks="goToChunks" />
          </n-tab-pane>
          <n-tab-pane name="chunks" tab="切片详情">
            <ChunksTab :kb-id="kb.id" :active="tab === 'chunks'" :preset-document-id="presetDocId" />
          </n-tab-pane>
          <n-tab-pane name="retrieve" tab="知识检索">
            <RetrieveTab :kb-id="kb.id" :active="tab === 'retrieve'" :params="searchParams" :model-params="modelParams" :chat-models="chatModels" :rerank-models="rerankModels" />
          </n-tab-pane>
          <n-tab-pane name="qa" tab="知识问答">
            <QaTab :kb-id="kb.id" :active="tab === 'qa'" :params="searchParams" :model-params="modelParams" :chat-models="chatModels" :rerank-models="rerankModels" />
          </n-tab-pane>
        </n-tabs>
      </template>
    </n-spin>
  </div>
</template>

<style scoped>
.kb-detail-page {
  display: flex;
  flex-direction: column;
}

.detail-loading {
  padding: 40px 8px;
}

.detail-missing {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  padding: 40px 8px;
}

.kb-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 4px 0 14px;
  border-bottom: 1px solid rgba(128, 128, 128, 0.2);
  margin-bottom: 6px;
  flex-wrap: wrap;
}

.kb-head-main {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.kb-icon {
  color: #2f6bff;
}

.kb-title-wrap {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.kb-title {
  font-size: 19px;
  font-weight: 700;
  line-height: 1.3;
}

.kb-meta {
  font-size: 12px;
  color: rgba(120, 120, 120, 0.95);
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.sep {
  opacity: 0.6;
}

.head-help {
  color: rgba(130, 130, 130, 0.85);
  cursor: help;
  align-self: flex-start;
  margin-top: 10px;
}

.kb-tabs {
  flex: 1;
}
</style>
