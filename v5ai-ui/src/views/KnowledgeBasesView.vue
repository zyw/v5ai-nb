<script setup lang="ts">
/**
 * 知识库管理（列表页）：
 * 点击「名称」进入知识库详情页（四 tab：原始文档/切片详情/知识检索/知识问答）。
 * 文档管理能力已整体迁入详情页，本页只保留库级列表与创建/编辑/启停/删除。
 */
import {computed, h, onMounted, reactive, ref} from 'vue'
import { useRouter } from 'vue-router'
import {
  NButton,
  NCard,
  NDataTable,
  NDivider,
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
  NText,
  useDialog,
  useMessage,
  type DataTableColumns, SelectOption
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
  type RagModelParams,
  type RagSearchParams,
  type StoreInstanceResponse, DimensionCheckResponse
} from '../api/client'
import { adminToken } from '../stores/session'
import { formatDateTime } from '../utils/dateUtils'
import { defaultModelParams, defaultSearchParams, toRagModelParams, toRagSearchParams } from './knowledge-base/params'

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
const rerankModels = ref<OptionResponse[]>([])
const capability = ref<DimensionCheckResponse>()

type SliceStrategy = 'length' | 'delimiter' | 'regex' | 'smart'

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
  sliceStrategy: 'length' as SliceStrategy,
  maxChunkLength: 2000,
  chunkOverlap: null as number | null,
  chunkRegex: '',
  chunkModelId: null as number | null,
  mergeShortSegments: false,
  parseEngine: 'default' as 'default' | 'docling',
  docling: { doOcr: false, doTableStructure: false, saveImages: false, imageOcr: false },
  // 检索/模型回答参数：本页不直接编辑（编辑在详情页 tab 完成），仅用于新建默认值 / 编辑回写 round-trip。
  searchParams: {} as RagSearchParams,
  modelParams: {} as RagModelParams
})

const dedupStrategyOptions = [
  { label: '不去重', value: 0 },
  { label: '按文件名', value: 1 },
  { label: '按文件内容', value: 2 },
  { label: '按文件名或内容', value: 3 }
]

const dedupActionOptions = [
  { label: '拒绝（报错）', value: 0 },
  { label: '跳过（不入库）', value: 1 },
  { label: '覆盖（替换旧文档）', value: 2 }
]

const sliceStrategyOptions: { label: string; value: SliceStrategy, describe: string }[] = [
  { label: '按长度切片', value: 'length', describe: '可配置切片最大长度（字符数）、片段重叠等；先按空行粗分段落，再对超长段落按最大长度切分（带重叠），短段落保持独立。' },
  { label: '按分隔符', value: 'delimiter', describe: '先按所选分隔符做一级切分，再按下方「按长度切片」同款的最大长度与重叠做递归切分（与旧版 delimiter 行为一致）。' },
  { label: '正则切片', value: 'regex', describe: '使用 Java 正则对全文做一级切分（Pattern 语法），再对每个片段按最大长度递归切分。请谨慎编写正则，避免过度切分或性能问题。' },
  { label: '智能切片', value: 'smart', describe: '由所选对话模型将全文切分为 JSON 字符串数组（语义片段），再对每段按最大长度递归切分。模型调用失败时会回退为按段落切分。' }
]

/** 分隔符多选（value 为真实切分字符串，提交时 JSON 序列化给后端） */
const delimiterSelectOptions = computed<Array<SelectOption & { sym: string; sub: string }>>(() => [
  { value: '\n', sym: '\\n', sub: '换行符', label: `\\n 换行符` },
  {
    value: '\n\n',
    sym: '\\n\\n',
    sub: '换行符×2',
    label: `\\n\\n 换行符×2`
  },
  {
    value: '。',
    sym: '。',
    sub: '中文句号',
    label: `。 中文句号`
  },
  {
    value: '！',
    sym: '！',
    sub: '中文感叹号',
    label: `！ 中文感叹号`
  },
  {
    value: '？',
    sym: '？',
    sub: '中文问号',
    label: `？ 中文问号`
  },
  {
    value: '.',
    sym: '.',
    sub: '英文句号',
    label: `. 英文字号`
  },
  { value: '!', sym: '!', sub: '英文感叹号', label: `! 英文字号` },
  {
    value: '?',
    sym: '?',
    sub: '英文问号',
    label: `? 英文字号`
  },
  {
    value: ';',
    sym: ';',
    sub: '英文分号',
    label: `; 英文字号`
  }
]);

const selectedDelimiters = ref<string[]>(['\n\n']);

/** 回显：解析后端存的自定义分隔符 JSON 数组；失败/空回退默认换行×2 */
function parseDelimiters(raw?: string): string[] {
  if (!raw) return ['\n\n']
  try {
    const parsed = JSON.parse(raw)
    if (Array.isArray(parsed)) {
      const values = parsed.filter((v): v is string => typeof v === 'string')
      return values.length > 0 ? values : ['\n\n']
    }
  } catch {
    // 非法 JSON → 回退默认
  }
  return ['\n\n']
}

function renderDelimiterLabel(option: SelectOption) {
  const o = option as SelectOption & { sym?: string; sub?: string };
  return h('div', { class: 'delimiter-option-row' }, [
    h('span', { class: 'delimiter-option-row__sym' }, o.sym ?? ''),
    h('span', { class: 'delimiter-option-row__sub' }, o.sub ?? '')
  ]);
}

const quickMaxTokens = [200, 600, 1000, 2000]

/** 列表列使用的切片策略文案（未知值原样显示）。 */
function sliceStrategyLabel(strategy?: string): string {
  return sliceStrategyOptions.find((o) => o.value === strategy)?.label ?? strategy ?? '—'
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
  const [models, vectorRows, engineRows, chatRows, rerankRows] = await Promise.all([
    listModelOptions(adminToken.value, 'EMBEDDING'),
    listStoreInstances(adminToken.value, { category: 1, status: 1 }),
    listStoreInstances(adminToken.value, { category: 2, status: 1 }),
    listModelOptions(adminToken.value, 'CHAT'),
    listModelOptions(adminToken.value, 'RERANK')
  ])
  embeddingModels.value = models
  vectorStores.value = vectorRows.rows
  searchEngines.value = engineRows.rows
  chatModels.value = chatRows
  rerankModels.value = rerankRows
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
  form.sliceStrategy = 'length'
  form.maxChunkLength = 2000
  form.chunkOverlap = null
  selectedDelimiters.value = ['\n\n']
  form.chunkRegex = ''
  form.chunkModelId = null
  form.mergeShortSegments = false
  form.parseEngine = 'default'
  form.docling = { doOcr: false, doTableStructure: false, saveImages: false, imageOcr: false }
  // 新建：对话模型 / 重排模型默认取同类型首个（若有），其余走共享默认值。
  form.searchParams = {
    ...toRagSearchParams(defaultSearchParams()),
    rerankModelId: rerankModels.value[0]?.value
  }
  form.modelParams = {
    ...toRagModelParams(defaultModelParams()),
    modelId: chatModels.value[0]?.value
  }
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
  form.sliceStrategy = chunk?.sliceStrategy ?? 'length'
  form.maxChunkLength = chunk?.maxChunkLength ?? 2000
  form.chunkOverlap = chunk?.chunkOverlap ?? null
  selectedDelimiters.value = parseDelimiters(chunk?.customDelimiter)
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
  // 编辑：原样保留已存检索/模型回答参数（这些参数在详情页 tab 中编辑，本页不做改动）。
  form.searchParams = kb.config?.searchParams ?? toRagSearchParams(defaultSearchParams())
  form.modelParams = kb.config?.modelParams ?? toRagModelParams(defaultModelParams())
  showKbModal.value = true
}

function buildRequest(): CreateKnowledgeBaseRequest {
  const chunkParams: NonNullable<RagConfig['chunkParams']> = {
    sliceStrategy: form.sliceStrategy,
    maxChunkLength: form.maxChunkLength,
    chunkOverlap: form.chunkOverlap ?? undefined,
    mergeShortSegments: form.mergeShortSegments,
    imageOcr: form.parseEngine === 'docling' && form.docling.imageOcr ? true : undefined
  }
  if (form.sliceStrategy === 'delimiter') chunkParams.customDelimiter = JSON.stringify(selectedDelimiters.value)
  if (form.sliceStrategy === 'regex') chunkParams.chunkRegex = form.chunkRegex
  if (form.sliceStrategy === 'smart') chunkParams.chunkModelId = form.chunkModelId ?? undefined
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
    config: { chunkParams, parseParams, searchParams: form.searchParams, modelParams: form.modelParams },
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
  if (form.sliceStrategy === 'delimiter' && selectedDelimiters.value.length === 0) {
    message.warning('请选择至少一个分隔符')
    return
  }
  if (form.sliceStrategy === 'regex' && !form.chunkRegex.trim()) {
    message.warning('请配置一级切分正则')
    return
  }
  if (form.sliceStrategy === 'smart' && form.chunkModelId == null) {
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
  { title: '切片策略', key: 'sliceStrategy', width: 110, render: (row) => sliceStrategyLabel(row.config?.chunkParams?.sliceStrategy) },
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
      style="width: 760px"
      :bordered="false"
    >
      <div class="kb-form">
        <n-divider title-placement="left" style="margin: 0 0 16px">基本信息</n-divider>
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
                  <n-text depth="3" style="font-size: 12px">
                    模型最大维度: {{capability?.modelMaxDimension ?? '未知'}}，向量库最大维度: {{capability?.storeMaxDimension ?? '未知'}}，当前可用上限: {{ capability?.effectiveMaxDimension ?? '未知' }}
                  </n-text>
                </n-popover>
              </template>
              <n-input-number v-model:value="form.dimensionOfVectorModel" :min="1" :max="65536" style="width: 100%" />
            </n-form-item-gi>
            <n-form-item-gi :span="12" label="混合检索">
              <n-switch v-model:value="form.searchEngineEnable">
                <template #checked>是</template>
                <template #unchecked>否</template>
              </n-switch>
              <n-text depth="3" style="font-size: 12px; margin-left: 10px">关键词 + 向量混合检索</n-text>
            </n-form-item-gi>
            <n-form-item-gi v-if="form.searchEngineEnable" :span="12" label="搜索引擎">
              <n-select v-model:value="form.searchEngineInstanceId" placeholder="选择搜索引擎实例" :options="searchEngineOptions()" filterable />
            </n-form-item-gi>
          </n-grid>
        </n-form>

        <n-divider title-placement="left" style="margin: 8px 0 16px">上传去重</n-divider>
        <n-text depth="3" style="display: block; font-size: 12px; margin-bottom: 10px">控制同一知识库内重复文档的判定与处理方式。</n-text>
        <n-form label-placement="top">
          <n-grid :cols="24" :x-gap="24">
            <n-form-item-gi :span="24" label="去重策略">
              <n-radio-group v-model:value="form.dedupStrategy">
                <n-radio-button v-for="opt in dedupStrategyOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</n-radio-button>
              </n-radio-group>
            </n-form-item-gi>
            <n-form-item-gi :span="24" label="冲突动作">
              <n-radio-group v-model:value="form.dedupAction">
                <n-radio-button v-for="opt in dedupActionOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</n-radio-button>
              </n-radio-group>
            </n-form-item-gi>
          </n-grid>
        </n-form>

        <n-divider title-placement="left" style="margin: 8px 0 16px">切片策略</n-divider>
        <n-text depth="3" style="display: block; font-size: 12px; margin-bottom: 10px">选择文档如何被拆成向量片段；可随时在后台调整（需重新解析文档后生效）。</n-text>
        <n-tabs v-model:value="form.sliceStrategy" type="line" animated>
          <n-tab-pane v-for="opt in sliceStrategyOptions" :key="opt.value" :name="opt.value" :tab="opt.label">
            <n-alert type="info" style="margin-bottom: 10px;">
              {{ opt.describe }}
            </n-alert>
            <n-form label-placement="top">
              <n-form-item v-if="form.sliceStrategy === 'delimiter'" label="分隔符">
                <n-select
                    v-model:value="selectedDelimiters"
                    :options="delimiterSelectOptions"
                    multiple
                    filterable
                    clearable
                    placeholder="选择一级切分符，可多选"
                    :max-tag-count="3"
                    class="delimiter-multi-select"
                    :render-label="renderDelimiterLabel"
                    style="width: 60%"
                />
                <n-text depth="3" style="font-size: 12px; margin-left: 8px">一级切分按所选符号拆分；后续递归切片使用下方最大长度与重叠</n-text>
              </n-form-item>
              <n-form-item v-if="form.sliceStrategy === 'regex'" label="一级切分正则">
                <n-input v-model:value="form.chunkRegex" placeholder="Java Pattern 语法" type="textarea" :maxlength="2048" show-count :autosize="{ minRows: 3, maxRows: 4 }" style="width: 60%" />
                <n-text depth="3" style="font-size: 12px; margin-left: 8px">全文将按该正则 split 得到多段，再对每段做最大长度递归切分。</n-text>
              </n-form-item>
              <n-form-item v-if="form.sliceStrategy === 'smart'" label="对话模型">
                <n-select v-model:value="form.chunkModelId" placeholder="请选择用于语义切片的模型（CHAT 类型）" :options="chatModels" filterable class="chunk-inp" />
                <n-text depth="3" style="font-size: 12px; margin-left: 8px">模型将输出 JSON 字符串数组；调用失败时回退为按段落切分</n-text>
              </n-form-item>
              <n-form-item label="切片最大长度">
                <n-input-number v-model:value="form.maxChunkLength" :min="1" class="chunk-inp" />
                <n-space :size="6" style="margin-left: 10px">
                  <n-button v-for="v in quickMaxTokens" :key="v" size="tiny" quaternary :type="form.maxChunkLength === v ? 'primary' : 'default'" @click="form.maxChunkLength = v">
                    {{ v }}
                  </n-button>
                </n-space>
                <n-text depth="3" style="font-size: 12px; margin-left: 8px">字符量级（递归切分上限）</n-text>
              </n-form-item>
              <n-form-item label="片段重叠">
                <n-input-number v-model:value="form.chunkOverlap" :min="0" placeholder="留空" class="chunk-inp" />
                <n-text depth="3" style="font-size: 12px; margin-left: 8px">留空则使用服务端默认</n-text>
              </n-form-item>
              <n-form-item v-if="form.sliceStrategy === 'length'" label="合并短文本片">
                <n-switch v-model:value="form.mergeShortSegments">
                  <template #checked>是</template>
                  <template #unchecked>否</template>
                </n-switch>
              </n-form-item>
            </n-form>
          </n-tab-pane>
        </n-tabs>

        <n-divider title-placement="left" style="margin: 8px 0 16px">解析引擎</n-divider>
        <n-radio-group v-model:value="form.parseEngine">
          <n-radio-button value="default">内置解析器</n-radio-button>
          <n-radio-button value="docling">Docling 引擎</n-radio-button>
        </n-radio-group>
        <template v-if="form.parseEngine === 'docling'">
          <n-text depth="3" style="display: block; font-size: 12px; margin: 10px 0">使用外部 Docling Serve 服务，提供更精准的结构化解析、表格识别和图片提取。</n-text>
          <n-form label-placement="top">
            <n-grid :cols="2" :x-gap="16">
              <n-form-item-gi label="OCR 识别">
                <n-space align="center" :size="10" :wrap="false">
                  <n-switch v-model:value="form.docling.doOcr">
                    <template #checked>是</template>
                    <template #unchecked>否</template>
                  </n-switch>
                  <n-text depth="3" style="font-size: 12px">对扫描件 / 图片型 PDF 启用文字识别</n-text>
                </n-space>
              </n-form-item-gi>
              <n-form-item-gi label="表格识别">
                <n-space align="center" :size="10" :wrap="false">
                  <n-switch v-model:value="form.docling.doTableStructure">
                    <template #checked>是</template>
                    <template #unchecked>否</template>
                  </n-switch>
                  <n-text depth="3" style="font-size: 12px">识别并导出文档中的表格结构</n-text>
                </n-space>
              </n-form-item-gi>
              <n-form-item-gi label="保存图片">
                <n-space align="center" :size="10" :wrap="false">
                  <n-switch v-model:value="form.docling.saveImages">
                    <template #checked>是</template>
                    <template #unchecked>否</template>
                  </n-switch>
                  <n-text depth="3" style="font-size: 12px">将文档中的图片提取保存到资源库</n-text>
                </n-space>
              </n-form-item-gi>
              <n-form-item-gi label="图片 OCR 增强">
                <n-switch v-model:value="form.docling.imageOcr">
                  <template #checked>是</template>
                  <template #unchecked>否</template>
                </n-switch>
              </n-form-item-gi>
            </n-grid>
          </n-form>
        </template>
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

.chunk-inp {
  width: 350px;
}
</style>
