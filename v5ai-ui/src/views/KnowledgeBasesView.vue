<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
import {
  NButton,
  NCard,
  NDataTable,
  NEmpty,
  NForm,
  NFormItem,
  NIcon,
  NInput,
  NInputNumber,
  NModal,
  NPagination,
  NRadioButton,
  NRadioGroup,
  NSelect,
  NSpace,
  NSwitch,
  NTabPane,
  NTabs,
  NTag,
  NUpload,
  useDialog,
  useMessage,
  type DataTableColumns,
  type UploadFileInfo
} from 'naive-ui'
import { Plus } from 'lucide-vue-next'
import PageHeader from '../components/PageHeader.vue'
import StatusTag from '../components/StatusTag.vue'
import {
  createKnowledgeBase,
  createStoreInstance,
  deleteDocument,
  disableKnowledgeBase,
  importUrl,
  listDocuments,
  listKnowledgeBases,
  listModelOptions,
  listStoreInstances,
  listTasks,
  retryDocument,
  updateKnowledgeBase,
  uploadDocument,
  type CreateKnowledgeBaseRequest,
  type DocumentResponse,
  type KnowledgeBaseResponse,
  type OptionResponse,
  type RagConfig,
  type StoreInstanceResponse,
  type TaskResponse
} from '../api/client'
import { adminToken } from '../stores/session'

const message = useMessage()
const dialog = useDialog()
const loading = ref(false)
const knowledgeBases = ref<KnowledgeBaseResponse[]>([])
const documents = ref<DocumentResponse[]>([])
const tasks = ref<TaskResponse[]>([])
const selectedKb = ref<KnowledgeBaseResponse | null>(null)
const uploadFile = ref<File | null>(null)
const kbPagination = reactive({ page: 1, pageSize: 10, itemCount: 0 })
const docPagination = reactive({ page: 1, pageSize: 10, itemCount: 0 })

// ---- 新建 / 编辑知识库表单 ----

const showKbModal = ref(false)
const saving = ref(false)
const embeddingModels = ref<OptionResponse[]>([])
const vectorStores = ref<StoreInstanceResponse[]>([])
const searchEngines = ref<StoreInstanceResponse[]>([])
const chatModels = ref<OptionResponse[]>([])

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
  confirmBeforeUpload: false,
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

function vectorStoreOptions() {
  return vectorStores.value.map((s) => ({ value: s.id, label: s.name }))
}

function searchEngineOptions() {
  return searchEngines.value.map((s) => ({ value: s.id, label: s.name }))
}

/** 从向量库 config JSON 中读取 dimension，用于自动填充向量维度。 */
function extractDimension(config?: string | null): number | null {
  if (!config) return null
  try {
    const dim = (JSON.parse(config) as { dimension?: unknown })?.dimension
    return typeof dim === 'number' && dim > 0 ? dim : null
  } catch {
    return null
  }
}

function handleVectorStoreSelect() {
  const store = vectorStores.value.find((s) => s.id === form.vectorStoreInstanceId)
  const dim = extractDimension(store?.config)
  if (dim) form.dimensionOfVectorModel = dim
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
  form.confirmBeforeUpload = false
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

// ---- 内联新增存储实例（向量库 / 搜索引擎） ----

const showStoreModal = ref(false)
const storeCategory = ref(1)
const storeForm = reactive({ name: '', type: 1, config: '', isDefault: false })
const storeTypeOptions = computed(() =>
  storeCategory.value === 1
    ? [
        { label: 'PG_VECTOR', value: 1 },
        { label: 'MILVUS', value: 2 }
      ]
    : [
        { label: 'ELASTICSEARCH', value: 3 },
        { label: 'PG_FULLTEXT', value: 4 }
      ]
)

function openStoreModal(category: number) {
  storeCategory.value = category
  storeForm.name = ''
  storeForm.type = category === 1 ? 1 : 3
  storeForm.config = ''
  storeForm.isDefault = false
  showStoreModal.value = true
}

async function handleSaveStore() {
  if (!storeForm.name.trim()) {
    message.warning('请输入实例名称')
    return
  }
  saving.value = true
  try {
    await createStoreInstance(adminToken.value, {
      name: storeForm.name.trim(),
      category: storeCategory.value,
      type: storeForm.type,
      config: storeForm.config.trim() || undefined,
      status: 1,
      isDefault: storeForm.isDefault
    })
    message.success('存储实例已创建')
    showStoreModal.value = false
    await loadFormOptions()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '创建存储实例失败')
  } finally {
    saving.value = false
  }
}

// ---- 列表与文档管理 ----

const documentColumns: DataTableColumns<DocumentResponse> = [
  { title: 'ID', key: 'id', width: 70 },
  { title: '标题', key: 'title' },
  { title: '类型', key: 'fileType', width: 90 },
  { title: '状态', key: 'status', width: 110, render: (row) => h(StatusTag, { status: row.status }) },
  { title: '错误', key: 'errorMessage', ellipsis: { tooltip: true } },
  {
    title: '操作',
    key: 'actions',
    width: 130,
    render: (row) =>
      h(NSpace, { size: 6 }, { default: () => [
        h(NButton, { size: 'small', secondary: true, onClick: () => handleRetry(row.id) }, { default: () => '重试' }),
        h(NButton, { size: 'small', secondary: true, type: 'error', onClick: () => handleDeleteDoc(row.id) }, { default: () => '删除' })
      ] })
  }
]

const taskColumns: DataTableColumns<TaskResponse> = [
  { title: 'ID', key: 'id', width: 70 },
  { title: '文档 ID', key: 'documentId', width: 90 },
  { title: '状态', key: 'status', width: 110, render: (row) => h(StatusTag, { status: row.status }) },
  { title: '尝试', key: 'attemptCount', width: 80, render: (row) => `${row.attemptCount}/${row.maxAttempts}` },
  { title: '错误', key: 'errorMessage', ellipsis: { tooltip: true } }
]

async function reload() {
  loading.value = true
  try {
    const kbRows = await listKnowledgeBases(adminToken.value, { pageNum: kbPagination.page, pageSize: kbPagination.pageSize })
    knowledgeBases.value = kbRows.rows
    kbPagination.itemCount = kbRows.total
    if (selectedKb.value == null && knowledgeBases.value.length > 0) {
      selectedKb.value = knowledgeBases.value[0]
    }
    if (selectedKb.value != null) {
      await reloadDocuments(selectedKb.value.id)
    }
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载知识库失败')
  } finally {
    loading.value = false
  }
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

function handleDocPageChange(page: number) {
  docPagination.page = page
  if (selectedKb.value) void reloadDocuments(selectedKb.value.id)
}

function handleDocPageSizeChange(pageSize: number) {
  docPagination.pageSize = pageSize
  docPagination.page = 1
  if (selectedKb.value) void reloadDocuments(selectedKb.value.id)
}

async function reloadDocuments(kbId: number) {
  const [docRows, taskRows] = await Promise.all([
    listDocuments(adminToken.value, kbId, { pageNum: docPagination.page, pageSize: docPagination.pageSize }),
    listTasks(adminToken.value, kbId)
  ])
  documents.value = docRows.rows
  docPagination.itemCount = docRows.total
  tasks.value = taskRows.rows
}

async function selectKb(kb: KnowledgeBaseResponse) {
  selectedKb.value = kb
  await reloadDocuments(kb.id)
}

function handleFileChange(options: { file: UploadFileInfo }) {
  const raw = options.file.file
  if (raw instanceof File) {
    uploadFile.value = raw
  }
}

function doUpload() {
  if (!selectedKb.value || !uploadFile.value) return
  void uploadDocument(adminToken.value, selectedKb.value.id, uploadFile.value)
    .then(() => {
      message.success('文档已上传，Worker 将异步索引')
      uploadFile.value = null
      return selectedKb.value ? reloadDocuments(selectedKb.value.id) : undefined
    })
    .catch((e) => message.error(e instanceof Error ? e.message : '上传文档失败'))
}

async function handleUpload() {
  if (!selectedKb.value) {
    message.warning('请先选择知识库')
    return
  }
  if (!uploadFile.value) {
    message.warning('请选择文件')
    return
  }
  const file = uploadFile.value
  if (form.confirmBeforeUpload) {
    dialog.warning({
      title: '上传确认',
      content: `确认上传「${file.name}」？上传后将进行解析、切片并写入向量库。`,
      positiveText: '确认上传',
      negativeText: '取消',
      onPositiveClick: doUpload
    })
  } else {
    doUpload()
  }
}

async function handleImportUrl() {
  if (!selectedKb.value) {
    message.warning('请先选择知识库')
    return
  }
  try {
    await importUrl(adminToken.value, selectedKb.value.id, urlForm)
    message.success('URL 导入任务已创建')
    urlForm.url = ''
    urlForm.title = ''
    await reloadDocuments(selectedKb.value.id)
  } catch (e) {
    message.error(e instanceof Error ? e.message : 'URL 导入失败')
  }
}

async function handleRetry(documentId: number) {
  try {
    await retryDocument(adminToken.value, documentId)
    message.success('已重置任务为 PENDING')
    if (selectedKb.value) await reloadDocuments(selectedKb.value.id)
  } catch (e) {
    message.error(e instanceof Error ? e.message : '重试失败')
  }
}

async function handleDeleteDoc(documentId: number) {
  dialog.warning({
    title: '删除确认',
    content: `确认删除文档 #${documentId}？`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await deleteDocument(adminToken.value, documentId)
        message.success('文档已删除')
        if (selectedKb.value) await reloadDocuments(selectedKb.value.id)
      } catch (e) {
        message.error(e instanceof Error ? e.message : '删除失败')
      }
    }
  })
}

async function handleDisableKb(row: KnowledgeBaseResponse) {
  dialog.warning({
    title: '禁用确认',
    content: `确认禁用知识库「${row.name}」？`,
    positiveText: '禁用',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await disableKnowledgeBase(adminToken.value, row.id)
        message.success('知识库已禁用')
        await reload()
      } catch (e) {
        message.error(e instanceof Error ? e.message : '禁用失败')
      }
    }
  })
}

const urlForm = reactive({ url: '', title: '' })

onMounted(() => {
  void loadFormOptions()
  void reload()
})
</script>

<template>
  <div class="page">
    <PageHeader title="知识库管理" description="创建知识库并导入文档，由后台 Worker 解析、切片并写入向量库。">
      <template #actions>
        <n-button size="small" type="primary" @click="openCreate">
          <template #icon><n-icon :component="Plus" /></template>
          新建知识库
        </n-button>
      </template>
    </PageHeader>

    <n-card title="知识库列表" :bordered="true">
      <n-data-table
        :loading="loading"
        :columns="[
          { title: 'ID', key: 'id', width: 90 },
          {
            title: '名称',
            key: 'name',
            render: (row: KnowledgeBaseResponse) =>
              h('div', [
                row.icon ? h(NTag, { size: 'small', bordered: false, style: 'margin-right: 6px' }, { default: () => row.icon }) : null,
                row.name
              ])
          },
          { title: '描述', key: 'description', ellipsis: { tooltip: true } },
          { title: '向量维度', key: 'dimensionOfVectorModel', width: 100, render: (row: KnowledgeBaseResponse) => row.dimensionOfVectorModel ?? '—' },
          { title: '状态', key: 'status', width: 100, render: (row: KnowledgeBaseResponse) => h(StatusTag, { status: row.status }) },
          {
            title: '操作',
            key: 'actions',
            width: 230,
            render: (row: KnowledgeBaseResponse) => h(NSpace, { size: 6 }, { default: () => [
              h(NButton, { size: 'small', type: 'primary', secondary: true, onClick: () => openEdit(row) }, { default: () => '编辑' }),
              h(NButton, { size: 'small', type: 'primary', secondary: true, onClick: () => selectKb(row) }, { default: () => '管理文档' }),
              h(NButton, { size: 'small', type: 'error', secondary: true, disabled: row.status !== 'ACTIVE', onClick: () => handleDisableKb(row) }, { default: () => '禁用' })
            ] })
          }
        ]"
        :data="knowledgeBases"
        :bordered="false"
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

    <n-card v-if="selectedKb" :title="`文档管理 · ${selectedKb.name}`" :bordered="true">
      <n-space vertical :size="16">
        <div class="doc-toolbar">
          <n-upload :default-upload="false" :show-file-list="false" accept=".txt,.md,.markdown,.pdf,.docx" @change="handleFileChange">
            <n-button secondary>选择文件（TXT/MD/PDF/DOCX）</n-button>
          </n-upload>
          <n-button type="primary" @click="handleUpload">上传文档</n-button>
          <div class="url-toolbar">
            <n-input v-model:value="urlForm.url" placeholder="https://example.com/doc" style="width: 280px" />
            <n-input v-model:value="urlForm.title" placeholder="标题（可选）" style="width: 160px" />
            <n-button secondary type="info" @click="handleImportUrl">导入 URL</n-button>
          </div>
        </div>
        <n-empty v-if="documents.length === 0" description="暂无文档" />
        <n-data-table v-else :loading="loading" :columns="documentColumns" :data="documents" :bordered="false" />
        <n-pagination
          :page="docPagination.page"
          :page-size="docPagination.pageSize"
          :item-count="docPagination.itemCount"
          :page-sizes="[10, 20, 50, 100]"
          show-size-picker
          style="justify-content: flex-end; margin-top: 12px"
          @update:page="handleDocPageChange"
          @update:page-size="handleDocPageSizeChange"
        />
      </n-space>
    </n-card>

    <n-card v-if="selectedKb" title="索引任务" :bordered="true">
      <n-empty v-if="tasks.length === 0" description="暂无索引任务" />
      <n-data-table v-else :loading="loading" :columns="taskColumns" :data="tasks" :bordered="false" />
      <div class="muted" style="margin-top: 12px; font-size: 12px">
        Worker 每 5 秒扫描一次待处理任务，按知识库切片配置执行 解析 → 切片 → Embedding → 写入向量库。
      </div>
    </n-card>

    <!-- 创建 / 编辑知识库 -->
    <n-modal
      v-model:show="showKbModal"
      preset="card"
      :title="form.id != null ? '编辑知识库' : '创建 RAG'"
      style="width: 760px"
      :bordered="false"
    >
      <div class="kb-form">
        <div class="form-section">
          <div class="section-title">基本信息</div>
          <n-form label-placement="top">
            <n-form-item label="RAG 名称 *">
              <n-input v-model:value="form.name" placeholder="请输入 RAG 名称" :maxlength="128" show-count />
            </n-form-item>
            <n-form-item label="描述">
              <n-input v-model:value="form.description" type="textarea" :autosize="{ minRows: 2, maxRows: 4 }" placeholder="可选，便于区分用途" />
            </n-form-item>
            <n-form-item label="嵌入模型 *">
              <n-select v-model:value="form.embeddingModelId" placeholder="请选择嵌入模型" :options="embeddingModels" filterable />
            </n-form-item>
            <n-form-item label="向量库 *">
              <n-space :size="8" style="width: 100%">
                <n-select v-model:value="form.vectorStoreInstanceId" placeholder="选择向量库实例（推荐）" :options="vectorStoreOptions()" filterable style="flex: 1" @update:value="handleVectorStoreSelect" />
                <n-button secondary size="small" @click="openStoreModal(1)">+ 新增</n-button>
              </n-space>
            </n-form-item>
            <n-form-item label="向量维度 *">
              <n-input-number v-model:value="form.dimensionOfVectorModel" :min="1" :max="65536" style="width: 200px">
                <template #suffix>−</template>
              </n-input-number>
              <span class="muted" style="margin-left: 8px">选择向量库后自动填充，可按需调整</span>
            </n-form-item>
            <n-form-item label="混合检索">
              <n-switch v-model:value="form.searchEngineEnable" />
            </n-form-item>
            <n-form-item v-if="form.searchEngineEnable" label="搜索引擎 *">
              <n-space :size="8" style="width: 100%">
                <n-select v-model:value="form.searchEngineInstanceId" placeholder="选择搜索引擎实例" :options="searchEngineOptions()" filterable style="flex: 1" />
                <n-button secondary size="small" @click="openStoreModal(2)">+ 新增</n-button>
              </n-space>
            </n-form-item>
          </n-form>
        </div>

        <div class="form-section">
          <div class="section-title">上传去重</div>
          <div class="muted section-hint">控制同一知识库内重复文档的判定与处理方式。</div>
          <n-form label-placement="top">
            <n-form-item label="去重策略">
              <n-select v-model:value="form.dedupStrategy" :options="dedupStrategyOptions" />
            </n-form-item>
            <n-form-item label="冲突动作">
              <n-select v-model:value="form.dedupAction" :options="dedupActionOptions" />
            </n-form-item>
            <n-form-item label="上传前二次确认">
              <n-switch v-model:value="form.confirmBeforeUpload" />
              <span class="muted" style="margin-left: 8px">开启后上传将先弹出预览确认，确认后再入库</span>
            </n-form-item>
          </n-form>
        </div>

        <div class="form-section">
          <div class="section-title">切片策略</div>
          <div class="muted section-hint">选择文档如何被拆成向量片段；可随时在后台调整（需重新解析文档后生效）。</div>
          <n-tabs v-model:value="form.chunkMode" type="line" animated>
            <n-tab-pane v-for="opt in chunkModeOptions" :key="opt.value" :name="opt.value" :tab="opt.label">
              <n-form label-placement="top">
                <n-form-item v-if="form.chunkMode === 'delimiter'" label="分隔符 *">
                  <n-input v-model:value="form.customDelimiter" placeholder="如：\n\n（换行符×2）" />
                  <span class="muted" style="margin-left: 8px">一级切分按所选符号拆分；后续递归切片使用下方最大长度与重叠</span>
                </n-form-item>
                <n-form-item v-if="form.chunkMode === 'regex'" label="一级切分正则 *">
                  <n-input v-model:value="form.chunkRegex" placeholder="Java Pattern 语法，如：\\n\\n" />
                </n-form-item>
                <n-form-item v-if="form.chunkMode === 'smart'" label="对话模型 *">
                  <n-select v-model:value="form.chunkModelId" placeholder="请选择用于语义切片的模型（CHAT 类型）" :options="chatModels" filterable />
                  <span class="muted" style="margin-left: 8px">模型将输出 JSON 字符串数组；调用失败时回退为按段落切分</span>
                </n-form-item>
                <n-form-item label="切片最大长度 *">
                  <n-input-number v-model:value="form.maxChunkTokens" :min="1" style="width: 160px" />
                  <n-space :size="6" style="margin-left: 10px">
                    <n-button v-for="v in quickMaxTokens" :key="v" size="tiny" quaternary :type="form.maxChunkTokens === v ? 'primary' : 'default'" @click="form.maxChunkTokens = v">
                      {{ v }}
                    </n-button>
                  </n-space>
                  <span class="muted" style="margin-left: 8px">字符量级（递归切分上限）</span>
                </n-form-item>
                <n-form-item label="片段重叠">
                  <n-input-number v-model:value="form.chunkOverlap" :min="0" style="width: 160px" placeholder="留空" />
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

    <!-- 内联新增存储实例 -->
    <n-modal v-model:show="showStoreModal" preset="card" :title="`新增${storeCategory === 1 ? '向量库' : '搜索引擎'}实例`" style="width: 480px" :bordered="false">
      <n-form label-placement="top">
        <n-form-item label="实例名称">
          <n-input v-model:value="storeForm.name" placeholder="实例显示名称" />
        </n-form-item>
        <n-form-item label="类型">
          <n-select v-model:value="storeForm.type" :options="storeTypeOptions" />
        </n-form-item>
        <n-form-item label="连接参数（JSON）">
          <n-input
            v-model:value="storeForm.config"
            type="textarea"
            :autosize="{ minRows: 3, maxRows: 6 }"
            placeholder='{"url":"http://localhost:5432","database":"v5ai_nb","dimension":1536}'
          />
        </n-form-item>
        <n-form-item label="设为默认实例">
          <n-switch v-model:value="storeForm.isDefault" />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showStoreModal = false">取消</n-button>
          <n-button type="primary" :loading="saving" @click="handleSaveStore">创建</n-button>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>

<style scoped>
.doc-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.url-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-left: auto;
}

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
  opacity: 0.6;
}

@media (max-width: 720px) {
  .url-toolbar {
    margin-left: 0;
    width: 100%;
  }
}
</style>
