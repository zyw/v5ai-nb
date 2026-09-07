<script setup lang="ts">
/**
 * 知识库管理（列表页）：
 * 点击「名称」进入知识库详情页（四 tab：原始文档/切片详情/知识检索/知识问答）。
 * 文档管理能力已整体迁入详情页，本页只保留库级列表与创建/编辑/启停/删除。
 */
import { h, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  NButton,
  NCard,
  NDataTable,
  NForm,
  NFormItem,
  NFormItemGi,
  NGrid,
  NIcon,
  NInput,
  NInputNumber,
  NModal,
  NPagination,
  NPopover,
  NRadioButton,
  NRadioGroup,
  NSelect,
  NSpace,
  NSwitch,
  NTabPane,
  NTabs,
  NTag,
  useDialog,
  useMessage,
  type DataTableColumns
} from 'naive-ui'
import { CircleQuestionMark, Plus, RefreshCw, Search } from 'lucide-vue-next'
import PageHeader from '../components/PageHeader.vue'
import {
  createKnowledgeBase,
  deleteKnowledgeBase,
  dimensionCheck,
  disableKnowledgeBase,
  enableKnowledgeBase,
  listKnowledgeBases,
  listModelOptions,
  listStoreInstances,
  updateKnowledgeBase,
  type CreateKnowledgeBaseRequest,
  type KnowledgeBaseResponse,
  type OptionResponse,
  type RagConfig,
  type StoreInstanceResponse, DimensionCheckResponse
} from '../api/client'
import { adminToken } from '../stores/session'
import { formatDateTime } from '../utils/dateUtils'

const router = useRouter()
const message = useMessage()
const dialog = useDialog()
const loading = ref(false)
const togglingId = ref<number | null>(null)
const knowledgeBases = ref<KnowledgeBaseResponse[]>([])
const kbPagination = reactive({ page: 1, pageSize: 10, itemCount: 0 })

// ---- 列表搜索（名称模糊 / 状态）----
const kbSearch = reactive({ name: '', status: null as string | null })

const kbStatusOptions = [
  { label: '启用', value: 'ACTIVE' },
  { label: '停用', value: 'DISABLED' }
]

const LIST_STATE_KEY = 'kb-list-state'

// ---- 新建 / 编辑知识库表单 ----

const showKbModal = ref(false)
const saving = ref(false)
const embeddingModels = ref<OptionResponse[]>([])
const vectorStores = ref<StoreInstanceResponse[]>([])
const searchEngines = ref<StoreInstanceResponse[]>([])
const chatModels = ref<OptionResponse[]>([])
const capability = ref<DimensionCheckResponse>()

type ChunkMode = 'length' | 'delimiter' | 'regex' | 'smart'

const form = reactive({
  id: null as number | null,
  name: '',
  description: '',
  embeddingModelId: null as number | null,
  vectorStoreInstanceId: null as number | null,
  dimensionOfVectorModel: null as number | null,
  searchEngineEnable: false,
  searchEngineInstanceId: null as number | null,
  dedupStrategy: 2,
  dedupAction: 0,
  chunkMode: 'length' as ChunkMode,
  maxChunkTokens: 2000,
  chunkOverlap: null as number | null,
  customDelimiter: '\n\n',
  chunkRegex: '',
  chunkModelId: null as number | null,
  mergeShortSegments: false,
  parseEngine: 'default' as 'default' | 'docling',
  docling: { doOcr: false, doTableStructure: false, saveImages: false, imageOcr: false }
})

const dedupStrategyOptions = [
  { label: '不处理', value: 0 },
  { label: '按文件名', value: 1 },
  { label: '按文件内容', value: 2 },
  { label: '按文件名或内容', value: 3 }
]

const dedupActionOptions = [
  { label: '拒绝（报错）', value: 0 },
  { label: '跳过', value: 1 },
  { label: '覆盖（替换旧文档）', value: 2 }
]

const chunkModeOptions: { label: string; value: ChunkMode }[] = [
  { label: '按长度切片', value: 'length' },
  { label: '按分隔符', value: 'delimiter' },
  { label: '正则切片', value: 'regex' },
  { label: '智能切片', value: 'smart' }
]

const quickMaxTokens = [200, 600, 1000, 2000]

/** 列表列使用的切片策略文案（未知值原样显示）。 */
function chunkModeLabel(mode?: string): string {
  return chunkModeOptions.find((o) => o.value === mode)?.label ?? mode ?? '—'
}

function vectorStoreOptions() {
  return vectorStores.value.map((s) => ({ value: s.id, label: s.name }))
}

function searchEngineOptions() {
  return searchEngines.value.map((s) => ({ value: s.id, label: s.name }))
}

/**
 * 嵌入模型或向量库变更时查询「模型 × 库」维度能力并回填/校验：
 * 固定维度模型自动回填其输出维度；可调维度模型提示可用上限（仍可手填更低值）。
 */
async function handleEmbeddingConfigChange() {
  const { embeddingModelId, vectorStoreInstanceId } = form
  if (embeddingModelId == null || vectorStoreInstanceId == null) return
  try {
    capability.value = await dimensionCheck(adminToken.value, embeddingModelId, vectorStoreInstanceId)
    if (capability.value.dimensionAdjustable) {
      if (form.dimensionOfVectorModel == null && capability.value.modelMaxDimension != null) {
        form.dimensionOfVectorModel = capability.value.modelMaxDimension
      }
      if (capability.value.effectiveMaxDimension != null) {
        message.info(`该模型可调维度，可用上限 ${capability.value.effectiveMaxDimension}`)
      }
    } else if (capability.value.modelMaxDimension != null) {
      form.dimensionOfVectorModel = capability.value.effectiveMaxDimension
    }
    if (form.dimensionOfVectorModel != null && capability.value.effectiveMaxDimension != null
      && form.dimensionOfVectorModel > capability.value.effectiveMaxDimension) {
      message.warning(`维度 ${form.dimensionOfVectorModel} 超过可用上限 ${capability.value.effectiveMaxDimension}`)
    }
  } catch {
    // 维度检查失败不阻塞表单：由后端保存时权威校验兜底
  }
}

async function loadFormOptions() {
  const [models, vectorRows, engineRows, chatRows] = await Promise.all([
    listModelOptions(adminToken.value, 'EMBEDDING'),
    listStoreInstances(adminToken.value, { category: 1, status: 1 }),
    listStoreInstances(adminToken.value, { category: 2, status: 1 }),
    listModelOptions(adminToken.value, 'CHAT')
  ])
  embeddingModels.value = models
  vectorStores.value = vectorRows.rows
  searchEngines.value = engineRows.rows
  chatModels.value = chatRows
}

function resetForm() {
  form.id = null
  form.name = ''
  form.description = ''
  form.embeddingModelId = null
  form.vectorStoreInstanceId = null
  form.dimensionOfVectorModel = null
  form.searchEngineEnable = false
  form.searchEngineInstanceId = null
  form.dedupStrategy = 2
  form.dedupAction = 0
  form.chunkMode = 'length'
  form.maxChunkTokens = 2000
  form.chunkOverlap = null
  form.customDelimiter = '\n\n'
  form.chunkRegex = ''
  form.chunkModelId = null
  form.mergeShortSegments = false
  form.parseEngine = 'default'
  form.docling = { doOcr: false, doTableStructure: false, saveImages: false, imageOcr: false }
}

function openCreate() {
  resetForm()
  showKbModal.value = true
}

function openEdit(kb: KnowledgeBaseResponse) {
  resetForm()
  form.id = kb.id
  form.name = kb.name
  form.description = kb.description ?? ''
  form.embeddingModelId = kb.embeddingModelId
  form.vectorStoreInstanceId = kb.vectorStoreInstanceId ?? null
  form.dimensionOfVectorModel = kb.dimensionOfVectorModel
  form.searchEngineEnable = !!kb.searchEngineEnable
  form.searchEngineInstanceId = kb.searchEngineInstanceId ?? null
  form.dedupStrategy = kb.dedupStrategy ?? 2
  form.dedupAction = kb.dedupAction ?? 0
  const chunk = kb.config?.chunkParams
  form.chunkMode = chunk?.mode ?? 'length'
  form.maxChunkTokens = chunk?.maxChunkTokens ?? 2000
  form.chunkOverlap = chunk?.chunkOverlap ?? null
  form.customDelimiter = chunk?.customDelimiter ?? kb.delimiter ?? '\n\n'
  form.chunkRegex = chunk?.chunkRegex ?? ''
  form.chunkModelId = chunk?.chunkModelId ?? null
  form.mergeShortSegments = !!chunk?.mergeShortSegments
  const parse = kb.config?.parseParams
  form.parseEngine = parse?.engine === 'docling' ? 'docling' : 'default'
  form.docling = {
    doOcr: !!parse?.docling?.doOcr,
    doTableStructure: !!parse?.docling?.doTableStructure,
    saveImages: !!parse?.docling?.saveImages,
    imageOcr: !!chunk?.imageOcr
  }
  showKbModal.value = true
}

function buildRequest(): CreateKnowledgeBaseRequest {
  const chunkParams: NonNullable<RagConfig['chunkParams']> = {
    mode: form.chunkMode,
    maxChunkTokens: form.maxChunkTokens,
    chunkOverlap: form.chunkOverlap ?? undefined,
    mergeShortSegments: form.mergeShortSegments,
    imageOcr: form.parseEngine === 'docling' && form.docling.imageOcr ? true : undefined
  }
  if (form.chunkMode === 'delimiter') chunkParams.customDelimiter = form.customDelimiter
  if (form.chunkMode === 'regex') chunkParams.chunkRegex = form.chunkRegex
  if (form.chunkMode === 'smart') chunkParams.chunkModelId = form.chunkModelId ?? undefined
  const parseParams: NonNullable<RagConfig['parseParams']> = { engine: form.parseEngine }
  if (form.parseEngine === 'docling') {
    parseParams.docling = {
      doOcr: form.docling.doOcr,
      doTableStructure: form.docling.doTableStructure,
      saveImages: form.docling.saveImages
    }
  }
  return {
    id: form.id ?? undefined,
    name: form.name,
    description: form.description || undefined,
    embeddingModelId: form.embeddingModelId!,
    vectorStoreInstanceId: form.vectorStoreInstanceId!,
    dimensionOfVectorModel: form.dimensionOfVectorModel!,
    searchEngineEnable: form.searchEngineEnable,
    searchEngineInstanceId: form.searchEngineEnable ? form.searchEngineInstanceId ?? undefined : undefined,
    delimiter: form.chunkMode === 'delimiter' ? form.customDelimiter : undefined,
    config: { chunkParams, parseParams },
    dedupStrategy: form.dedupStrategy,
    dedupAction: form.dedupAction
  }
}

async function handleSaveKb() {
  if (!form.name.trim()) {
    message.warning('请输入知识库名称')
    return
  }
  if (form.embeddingModelId == null || form.vectorStoreInstanceId == null || form.dimensionOfVectorModel == null) {
    message.warning('请完整填写基本信息')
    return
  }
  if (form.searchEngineEnable && form.searchEngineInstanceId == null) {
    message.warning('请选择搜索引擎实例')
    return
  }
  if (form.chunkMode === 'delimiter' && !form.customDelimiter.trim()) {
    message.warning('请配置分隔符')
    return
  }
  if (form.chunkMode === 'regex' && !form.chunkRegex.trim()) {
    message.warning('请配置一级切分正则')
    return
  }
  if (form.chunkMode === 'smart' && form.chunkModelId == null) {
    message.warning('请选择用于语义切片的对话模型')
    return
  }
  saving.value = true
  try {
    if (form.id != null) {
      await updateKnowledgeBase(adminToken.value, buildRequest())
      message.success('知识库已更新，变更的切片配置需重新解析文档后生效')
    } else {
      await createKnowledgeBase(adminToken.value, buildRequest())
      message.success('知识库已创建')
    }
    showKbModal.value = false
    await reload()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    saving.value = false
  }
}

// ---- 列表 ----

const kbColumns: DataTableColumns<KnowledgeBaseResponse> = [
  { title: 'ID', key: 'id', width: 90 },
  {
    title: '名称',
    key: 'name',
    width: 150,
    render: (row) =>
      h(NButton, {
        size: 'small',
        type: 'primary',
        text: true,
        onClick: () => openDetail(row)
      }, { default: () => (row.icon ? `${row.icon} ${row.name}` : row.name) })
  },
  { title: '描述', key: 'description',width: 180, ellipsis: { tooltip: true } },
  { title: '文档数', key: 'docCount', width: 80, render: (row) => row.docCount ?? 0 },
  { title: '切片数', key: 'chunkCount', width: 80, render: (row) => row.chunkCount ?? 0 },
  { title: '切片策略', key: 'chunkMode', width: 110, render: (row) => chunkModeLabel(row.config?.chunkParams?.mode) },
  { title: '向量维度', key: 'dimensionOfVectorModel', width: 100, render: (row) => row.dimensionOfVectorModel ?? '—' },
  {
    title: '状态',
    key: 'status',
    width: 90,
    render: (row) =>
      h(NSwitch, {
        value: row.status === 'ACTIVE',
        loading: togglingId.value === row.id,
        onUpdateValue: () => handleToggleStatus(row)
      }, {
        checked: () => h('span', '启'),
        unchecked: () => h('span', '禁')
      })
  },
  { title: '修改时间', key: 'updatedAt', width: 185, render: (row) => formatDateTime(row.updatedAt) ?? '—' },
  {
    title: '操作',
    key: 'actions',
    width: 180,
    render: (row) =>
      h(NSpace, { size: 6 }, { default: () => [
        h(NButton, { size: 'small', secondary: true, onClick: () => openEdit(row) }, { default: () => '编辑' }),
        h(NButton, { size: 'small', secondary: true, type: 'error', onClick: () => handleDeleteKb(row) }, { default: () => '删除' })
      ] })
  }
]

function openDetail(row: KnowledgeBaseResponse) {
  // 轻量恢复：返回列表时还原分页位置
  sessionStorage.setItem(LIST_STATE_KEY, JSON.stringify({ page: kbPagination.page, pageSize: kbPagination.pageSize }))
  void router.push({ name: 'knowledge-base-detail', params: { id: row.id } })
}

async function handleToggleStatus(row: KnowledgeBaseResponse) {
  const next = row.status === 'ACTIVE' ? '禁用' : '启用'
  const bound = row.referencedAgentKeys ?? []
  // 禁用时若被 Agent 引用：只警告不阻断，明确影响范围后仍允许继续
  const content = next === '禁用' && bound.length > 0
    ? `该知识库正被 Agent（${bound.join(', ')}）绑定，禁用后其检索将停用。确认禁用「${row.name}」？`
    : `确认${next}知识库「${row.name}」？`
  dialog.warning({
    title: `${next}确认`,
    content,
    positiveText: next,
    negativeText: '取消',
    onPositiveClick: async () => {
      togglingId.value = row.id
      try {
        if (row.status === 'ACTIVE') {
          await disableKnowledgeBase(adminToken.value, row.id)
        } else {
          await enableKnowledgeBase(adminToken.value, row.id)
        }
        message.success(`「${row.name}」已${next}`)
        await reload()
      } catch (e) {
        message.error(e instanceof Error ? e.message : `${next}失败`)
      } finally {
        togglingId.value = null
      }
    }
  })
}

async function handleDeleteKb(row: KnowledgeBaseResponse) {
  dialog.error({
    title: '删除确认',
    content: `确认删除知识库「${row.name}」？将删除其下所有文档、切片与向量数据，且不可恢复。`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await deleteKnowledgeBase(adminToken.value, row.id)
        message.success('知识库已删除')
        await reload()
      } catch (e) {
        message.error(e instanceof Error ? e.message : '删除失败')
      }
    }
  })
}

async function reload() {
  loading.value = true
  try {
    const rows = await listKnowledgeBases(adminToken.value, {
      pageNum: kbPagination.page,
      pageSize: kbPagination.pageSize,
      name: kbSearch.name || undefined,
      status: kbSearch.status ?? undefined
    })
    knowledgeBases.value = rows.rows
    kbPagination.itemCount = rows.total
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载知识库失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  kbPagination.page = 1
  void reload()
}

function handleKbPageChange(page: number) {
  kbPagination.page = page
  void reload()
}

function handleKbPageSizeChange(pageSize: number) {
  kbPagination.pageSize = pageSize
  kbPagination.page = 1
  void reload()
}

onMounted(() => {
  const raw = sessionStorage.getItem(LIST_STATE_KEY)
  if (raw) {
    try {
      const saved = JSON.parse(raw) as { page?: number; pageSize?: number }
      if (saved.pageSize) kbPagination.pageSize = saved.pageSize
      if (saved.page) kbPagination.page = saved.page
    } catch {
      // 忽略损坏的状态
    }
    sessionStorage.removeItem(LIST_STATE_KEY)
  }
  void loadFormOptions()
  void reload()
})
</script>

<template>
  <div class="page">
    <PageHeader title="知识库管理" description="创建知识库并导入文档，由后台 Worker 解析、切片并写入向量库；点击知识库名称进入详情页调试。">
      <template #actions>
        <n-button size="small" type="primary" @click="openCreate">
          <template #icon><n-icon :component="Plus" /></template>
          新建知识库
        </n-button>
      </template>
    </PageHeader>

    <n-card :bordered="true">
      <n-space style="margin-bottom: 12px" :size="8" align="center">
        <n-input v-model:value="kbSearch.name" placeholder="名称（模糊搜索）" clearable style="width: 220px" @keyup.enter="handleSearch" />
        <n-select v-model:value="kbSearch.status" placeholder="状态" clearable style="width: 130px" :options="kbStatusOptions" />
        <n-button size="small" type="primary" secondary @click="handleSearch">
          <template #icon><n-icon :component="Search" /></template>
          查询
        </n-button>
        <n-button size="small" quaternary @click="handleSearch">
          <template #icon><n-icon :component="RefreshCw" /></template>
          刷新
        </n-button>
      </n-space>

      <n-data-table
        :loading="loading"
        :columns="kbColumns"
        :data="knowledgeBases"
        :bordered="false"
        :scroll-x="kbColumns.reduce((s, c) => s + (typeof c.width === 'number' ? c.width : 240), 0)"
      />
      <n-pagination
        :page="kbPagination.page"
        :page-size="kbPagination.pageSize"
        :item-count="kbPagination.itemCount"
        :page-sizes="[10, 20, 50, 100]"
        show-size-picker
        style="justify-content: flex-end; margin-top: 12px"
        @update:page="handleKbPageChange"
        @update:page-size="handleKbPageSizeChange"
      />
    </n-card>

    <!-- 创建 / 编辑知识库 -->
    <n-modal
      v-model:show="showKbModal"
      preset="card"
      :title="form.id != null ? '编辑知识库' : '创建知识库'"
      style="width: 70%"
      :bordered="false"
    >
      <div class="kb-form">
        <div class="form-section">
          <div class="section-title">基本信息</div>
          <n-form label-placement="top">
            <n-grid :cols="24" :x-gap="24">
              <n-form-item-gi :span="24" label="知识库名称">
                <n-input v-model:value="form.name" placeholder="请输入 RAG 名称" :maxlength="128" show-count />
              </n-form-item-gi>
              <n-form-item-gi :span="24" label="描述">
                <n-input v-model:value="form.description" type="textarea" :maxlength="1200" show-count :autosize="{ minRows: 3, maxRows: 4 }" placeholder="可选，便于区分用途" />
              </n-form-item-gi>
              <n-form-item-gi :span="12" label="嵌入模型">
                <n-select v-model:value="form.embeddingModelId" placeholder="请选择嵌入模型" :options="embeddingModels" filterable @update:value="handleEmbeddingConfigChange" />
              </n-form-item-gi>
              <n-form-item-gi :span="12" label="向量库">
                <n-select v-model:value="form.vectorStoreInstanceId" placeholder="选择向量库实例（推荐）" :options="vectorStoreOptions()" filterable @update:value="handleEmbeddingConfigChange" />
              </n-form-item-gi>
              <n-form-item-gi :span="12" label="向量维度">
                <template #label>
                  <span style="vertical-align: baseline">向量维度</span>
                  <n-popover trigger="hover">
                    <template #trigger>
                      <n-icon><CircleQuestionMark /></n-icon>
                    </template>
                    <span class="info">
                      模型最大维度: {{capability?.modelMaxDimension ?? '未知'}}，向量库最大维度: {{capability?.storeMaxDimension ?? '未知'}}，当前可用上限: {{ capability?.effectiveMaxDimension ?? '未知' }}
                    </span>
                  </n-popover>
                </template>
                <n-input-number v-model:value="form.dimensionOfVectorModel" :min="1" :max="65536" style="width: 100%" />
              </n-form-item-gi>
              <n-form-item-gi :span="12" label="混合检索">
                <n-switch v-model:value="form.searchEngineEnable" />
              </n-form-item-gi>
              <n-form-item-gi v-if="form.searchEngineEnable" :span="12" label="搜索引擎">
                <n-select v-model:value="form.searchEngineInstanceId" placeholder="选择搜索引擎实例" :options="searchEngineOptions()" filterable />
              </n-form-item-gi>
            </n-grid>
          </n-form>
        </div>

        <div class="form-section">
          <div class="section-title">上传去重</div>
          <div class="muted section-hint">控制同一知识库内重复文档的判定与处理方式。</div>
          <n-form label-placement="top">
            <n-grid :cols="24" :x-gap="24">
              <n-form-item-gi :span="12" label="去重策略">
                <n-select v-model:value="form.dedupStrategy" :options="dedupStrategyOptions" />
              </n-form-item-gi>
              <n-form-item-gi :span="12" label="冲突动作">
                <n-select v-model:value="form.dedupAction" :options="dedupActionOptions" />
              </n-form-item-gi>
            </n-grid>
          </n-form>
        </div>

        <div class="form-section">
          <div class="section-title">切片策略</div>
          <div class="muted section-hint">选择文档如何被拆成向量片段；可随时在后台调整（需重新解析文档后生效）。</div>
          <n-tabs v-model:value="form.chunkMode" type="line" animated>
            <n-tab-pane v-for="opt in chunkModeOptions" :key="opt.value" :name="opt.value" :tab="opt.label">
              <n-form label-placement="top">
                <n-form-item v-if="form.chunkMode === 'delimiter'" label="分隔符">
                  <n-input v-model:value="form.customDelimiter" placeholder="如：\n\n（换行符×2）" class="chunk-inp" />
                  <span class="muted" style="margin-left: 8px">一级切分按所选符号拆分；后续递归切片使用下方最大长度与重叠</span>
                </n-form-item>
                <n-form-item v-if="form.chunkMode === 'regex'" label="一级切分正则">
                  <n-input v-model:value="form.chunkRegex" placeholder="Java Pattern 语法，如：\\n\\n" class="chunk-inp" />
                </n-form-item>
                <n-form-item v-if="form.chunkMode === 'smart'" label="对话模型">
                  <n-select v-model:value="form.chunkModelId" placeholder="请选择用于语义切片的模型（CHAT 类型）" :options="chatModels" filterable class="chunk-inp" />
                  <span class="muted" style="margin-left: 8px">模型将输出 JSON 字符串数组；调用失败时回退为按段落切分</span>
                </n-form-item>
                <n-form-item label="切片最大长度">
                  <n-input-number v-model:value="form.maxChunkTokens" :min="1" class="chunk-inp" />
                  <n-space :size="6" style="margin-left: 10px">
                    <n-button v-for="v in quickMaxTokens" :key="v" size="tiny" quaternary :type="form.maxChunkTokens === v ? 'primary' : 'default'" @click="form.maxChunkTokens = v">
                      {{ v }}
                    </n-button>
                  </n-space>
                  <span class="muted" style="margin-left: 8px">字符量级（递归切分上限）</span>
                </n-form-item>
                <n-form-item label="片段重叠">
                  <n-input-number v-model:value="form.chunkOverlap" :min="0" placeholder="留空" class="chunk-inp" />
                  <span class="muted" style="margin-left: 8px">留空则使用服务端默认</span>
                </n-form-item>
                <n-form-item v-if="form.chunkMode === 'length'" label="合并短文本片">
                  <n-switch v-model:value="form.mergeShortSegments" />
                </n-form-item>
              </n-form>
            </n-tab-pane>
          </n-tabs>
        </div>

        <div class="form-section">
          <div class="section-title">解析引擎</div>
          <n-radio-group v-model:value="form.parseEngine">
            <n-radio-button value="default">内置解析器</n-radio-button>
            <n-radio-button value="docling">Docling 引擎</n-radio-button>
          </n-radio-group>
          <template v-if="form.parseEngine === 'docling'">
            <div class="muted" style="margin: 10px 0">使用外部 Docling Serve 服务，提供更精准的结构化解析、表格识别和图片提取。</div>
            <n-form label-placement="left" label-width="140">
              <n-form-item label="OCR 识别">
                <n-switch v-model:value="form.docling.doOcr" />
                <span class="muted" style="margin-left: 8px">对扫描件 / 图片型 PDF 启用文字识别</span>
              </n-form-item>
              <n-form-item label="表格识别">
                <n-switch v-model:value="form.docling.doTableStructure" />
                <span class="muted" style="margin-left: 8px">识别并导出文档中的表格结构</span>
              </n-form-item>
              <n-form-item label="保存图片">
                <n-switch v-model:value="form.docling.saveImages" />
                <span class="muted" style="margin-left: 8px">将文档中的图片提取保存到资源库</span>
              </n-form-item>
              <n-form-item label="图片 OCR 增强">
                <n-switch v-model:value="form.docling.imageOcr" />
              </n-form-item>
            </n-form>
          </template>
        </div>
      </div>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showKbModal = false">取消</n-button>
          <n-button type="primary" :loading="saving" @click="handleSaveKb">保存</n-button>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>

<style scoped>
.kb-form {
  max-height: 68vh;
  overflow-y: auto;
  padding-right: 4px;
}

.form-section {
  padding: 14px 0;
  border-bottom: 1px dashed rgba(128, 128, 128, 0.25);
}

.form-section:last-child {
  border-bottom: none;
}

.section-title {
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 4px;
}

.section-hint {
  font-size: 12px;
  margin-bottom: 10px;
}

.muted {
  font-size: 12px;
  opacity: 0.9;
}

.chunk-inp {
  width: 300px;
}
</style>
