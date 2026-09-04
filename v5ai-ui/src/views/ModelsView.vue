<script setup lang="ts">
import { h, onMounted, reactive, ref } from 'vue'
import {
  NButton,
  NCard,
  NCollapse,
  NCollapseItem,
  NDataTable,
  NForm,
  NFormItem,
  NIcon,
  NInput,
  NInputNumber,
  NModal,
  NPagination,
  NSelect,
  NSlider,
  NSpace,
  NSwitch,
  NTag,
  useDialog,
  useMessage,
  type DataTableColumns
} from 'naive-ui'
import { Plus, RefreshCw, Search } from 'lucide-vue-next'
import PageHeader from '../components/PageHeader.vue'
import IconPicker from '../components/IconPicker.vue'
import StatusTag from '../components/StatusTag.vue'
import {
  createModel,
  createProvider,
  deleteModel,
  deleteProvider,
  listModels,
  listProviders,
  loadImagePreview,
  testModelConnection,
  updateModel,
  updateProvider,
  type ModelResponse,
  type ProviderResponse
} from '../api/client'
import { adminToken } from '../stores/session'

const message = useMessage()
const dialog = useDialog()
const loading = ref(false)
const activeTab = ref('model')
const providers = ref<ProviderResponse[]>([])
const models = ref<ModelResponse[]>([])
const allProviders = ref<ProviderResponse[]>([])
const testingModelId = ref<number | null>(null)

const providerPagination = reactive({ page: 1, pageSize: 10, itemCount: 0 })
const modelPagination = reactive({ page: 1, pageSize: 10, itemCount: 0 })

const providerSearch = reactive({ keyword: '' })
const modelSearch = reactive({
  keyword: '',
  providerKey: null as string | null,
  modelType: null as string | null,
  enabled: null as string | null
})

const enabledOptions = [
  { label: '启用', value: 'true' },
  { label: '禁用', value: 'false' }
]

const showProviderModal = ref(false)
const showModelModal = ref(false)
const savingProvider = ref(false)
const savingModel = ref(false)
const editingProviderId = ref<number | null>(null)
const editingModelId = ref<number | null>(null)

const providerForm = reactive({
  providerKey: '',
  name: '',
  description: '',
  iconUrl: '',
  enabled: true
})

/** LOGO 预览地址（已选图标的鉴权拉取 objectURL / 直链），用于表单内缩略图展示。 */
const providerIconPreview = ref('')
/** 图标选择弹框开关（上传 / 资源库单选 / 网络 URL）。 */
const showIconPicker = ref(false)

const modelForm = reactive({
  providerKey: '',
  modelKey: '',
  type: 'CHAT',
  modelName: '',
  description: '',
  adapterKey: 'openai-compatible',
  scope: 'GLOBAL',
  isDefault: false,
  apiKey: '',
  baseUrl: '',
  enabled: true,
  // 对话模型参数
  temperature: 0.7,
  topP: 1,
  topK: 1,
  maxTokens: 4096,
  frequencyPenalty: 0,
  imageInput: false,
  ocrFallback: false,
  // 向量模型参数
  embeddingDimension: null as number | null,
  timeoutMs: null as number | null,
  // 重排模型参数
  rerankPath: ''
})

const modelTypeOptions = [
  { label: '对话模型', value: 'CHAT' },
  { label: '向量模型', value: 'EMBEDDING' },
  { label: '重排模型', value: 'RERANK' }
]

const adapterKeyOptions = [
  { label: 'OpenAI 兼容', value: 'openai-compatible' },
  { label: 'HTTP', value: 'http' },
  { label: 'Qwen Rerank', value: 'qwen-rerank' }
]

const scopeOptions = [
  { label: '全局（GLOBAL）', value: 'GLOBAL' },
  { label: '个人（PERSONAL）', value: 'PERSONAL' }
]

const providerOptions = () =>
  allProviders.value.map((item) => ({ label: `${item.name} (${item.providerKey})`, value: item.providerKey }))

function modelCredentials(): string {
  return JSON.stringify({
    apiKey: modelForm.apiKey,
    baseUrl: modelForm.baseUrl || undefined,
    temperature: modelForm.temperature ?? undefined,
    maxTokens: modelForm.maxTokens ?? undefined
  })
}

/** 按模型类型构建 config（ConfigExtAttrsDTO JSON）；无参数时返回 undefined。 */
function buildConfig(): string | undefined {
  const cfg: Record<string, unknown> = {}
  if (modelForm.type === 'CHAT') {
    if (modelForm.temperature != null) cfg.temperature = modelForm.temperature
    if (modelForm.topP != null) cfg.topP = modelForm.topP
    if (modelForm.topK != null) cfg.topK = modelForm.topK
    if (modelForm.maxTokens != null) cfg.maxTokens = modelForm.maxTokens
    if (modelForm.frequencyPenalty != null) cfg.frequencyPenalty = modelForm.frequencyPenalty
    if (modelForm.imageInput) cfg.capabilities = ['image']
    if (modelForm.ocrFallback) cfg.defaultVisionModel = true
  } else if (modelForm.type === 'EMBEDDING') {
    if (modelForm.embeddingDimension != null) cfg.embeddingDimension = modelForm.embeddingDimension
    if (modelForm.timeoutMs != null) cfg.timeoutMs = modelForm.timeoutMs
  } else if (modelForm.type === 'RERANK') {
    if (modelForm.rerankPath.trim()) cfg.rerankPath = modelForm.rerankPath.trim()
  }
  const text = JSON.stringify(cfg)
  return text === '{}' ? undefined : text
}

/** 重置各类型配置参数为默认值。 */
function resetConfigParams() {
  modelForm.temperature = 0.7
  modelForm.topP = 1
  modelForm.topK = 1
  modelForm.maxTokens = 4096
  modelForm.frequencyPenalty = 0
  modelForm.imageInput = false
  modelForm.ocrFallback = false
  modelForm.embeddingDimension = null
  modelForm.timeoutMs = null
  modelForm.rerankPath = ''
}

/** 从已有 config JSON 回填各类型配置参数。 */
function applyConfig(configJson?: string | null) {
  resetConfigParams()
  if (!configJson) return
  try {
    const cfg = JSON.parse(configJson) as Record<string, unknown>
    if (typeof cfg.temperature === 'number') modelForm.temperature = cfg.temperature
    if (typeof cfg.topP === 'number') modelForm.topP = cfg.topP
    if (typeof cfg.topK === 'number') modelForm.topK = cfg.topK
    if (typeof cfg.maxTokens === 'number') modelForm.maxTokens = cfg.maxTokens
    if (typeof cfg.frequencyPenalty === 'number') modelForm.frequencyPenalty = cfg.frequencyPenalty
    modelForm.imageInput = Array.isArray(cfg.capabilities) && (cfg.capabilities as string[]).includes('image')
    modelForm.ocrFallback = cfg.defaultVisionModel === true
    if (typeof cfg.embeddingDimension === 'number') modelForm.embeddingDimension = cfg.embeddingDimension
    if (typeof cfg.timeoutMs === 'number') modelForm.timeoutMs = cfg.timeoutMs
    if (typeof cfg.rerankPath === 'string') modelForm.rerankPath = cfg.rerankPath
  } catch {
    // 非法 JSON 忽略，保持默认值
  }
}

/** Provider 图标预览映射（id -> 可直接用于 <img> 的地址）。 */
const providerIcons = reactive<Record<number, string>>({})

function revokeProviderIcons() {
  for (const url of Object.values(providerIcons)) {
    if (url.startsWith('blob:')) URL.revokeObjectURL(url)
  }
  for (const key of Object.keys(providerIcons)) delete providerIcons[Number(key)]
}

async function refreshProviderIcons(rows: ProviderResponse[]) {
  revokeProviderIcons()
  await Promise.all(
    rows
      .filter((r) => r.iconUrl)
      .map(async (r) => {
        try {
          providerIcons[r.id] = await loadImagePreview(adminToken.value, r.iconUrl!)
        } catch {
          // 忽略加载失败的图标
        }
      })
  )
}

const providerColumns: DataTableColumns<ProviderResponse> = [
  { title: 'ID', key: 'id', width: 64 },
  {
    title: '名称',
    key: 'name',
    render: (row) =>
      h('div', { style: 'display:flex;align-items:center;gap:8px' }, [
        providerIcons[row.id]
          ? h('img', { src: providerIcons[row.id], alt: row.name, style: 'width:20px;height:20px;border-radius:4px;object-fit:cover' })
          : null,
        h('span', row.name)
      ])
  },
  { title: '供应商Key', key: 'providerKey' },
  { title: '描述', key: 'description', ellipsis: { tooltip: true } },
  {
    title: '状态',
    key: 'enabled',
    width: 100,
    render: (row) => h(StatusTag, { status: row.enabled ? 'ACTIVE' : 'DISABLED' })
  },
  {
    title: '操作',
    key: 'actions',
    width: 140,
    render: (row) =>
      h(NSpace, { size: 4 }, {
        default: () => [
          h(NButton, { size: 'small', quaternary: true, onClick: () => openEditProvider(row) }, { default: () => '编辑' }),
          h(
            NButton,
            { size: 'small', type: 'error', secondary: true, onClick: () => handleDeleteProvider(row.id) },
            { default: () => '删除' }
          )
        ]
      })
  }
]

function providerLabel(providerId: number): string {
  const provider = allProviders.value.find((item) => item.id === providerId)
  return provider ? `${provider.name} (${provider.providerKey})` : `#${providerId}`
}

const modelColumns: DataTableColumns<ModelResponse> = [
  { title: 'ID', key: 'id', width: 64 },
  {
    title: '供应商',
    key: 'providerId',
    render: (row) => providerLabel(row.providerId)
  },
  { title: '名称', key: 'modelName', render: (row) => row.modelName ?? row.modelKey },
  { title: '模型Key', key: 'modelKey' },
  {
    title: '类型',
    key: 'modelType',
    render: (row) => h(NTag, { size: 'small', type: row.modelType === 'CHAT' ? 'info' : 'default', bordered: false }, { default: () => row.modelType })
  },
  {
    title: '作用域',
    key: 'scope',
    width: 100,
    render: (row) =>
      h(NTag, { size: 'small', type: row.scope === 'PERSONAL' ? 'warning' : 'default', bordered: false }, { default: () => (row.scope === 'PERSONAL' ? '个人' : '全局') })
  },
  {
    title: '默认',
    key: 'isDefault',
    width: 70,
    render: (row) =>
      row.isDefault
        ? h(NTag, { size: 'small', type: 'success', bordered: false, round: true }, { default: () => '默认' })
        : '—'
  },
  {
    title: '状态',
    key: 'enabled',
    width: 100,
    render: (row) => h(StatusTag, { status: row.enabled ? 'ACTIVE' : 'DISABLED' })
  },
  {
    title: '连通性',
    key: 'test',
    width: 120,
    render: (row) =>
      h(
        NButton,
        {
          size: 'small',
          secondary: true,
          loading: testingModelId.value === row.id,
          onClick: () => handleTestModel(row.id)
        },
        { default: () => '测试连接' }
      )
  },
  {
    title: '操作',
    key: 'actions',
    width: 140,
    render: (row) =>
      h(NSpace, { size: 4 }, {
        default: () => [
          h(NButton, { size: 'small', quaternary: true, onClick: () => openEditModel(row) }, { default: () => '编辑' }),
          h(
            NButton,
            { size: 'small', type: 'error', secondary: true, onClick: () => handleDeleteModel(row.id) },
            { default: () => '删除' }
          )
        ]
      })
  }
]

async function reload() {
  loading.value = true
  try {
    const [providerRows, modelRows] = await Promise.all([
      listProviders(adminToken.value, {
        pageNum: providerPagination.page,
        pageSize: providerPagination.pageSize,
        keyword: providerSearch.keyword || undefined
      }),
      listModels(adminToken.value, {
        pageNum: modelPagination.page,
        pageSize: modelPagination.pageSize,
        keyword: modelSearch.keyword || undefined,
        providerKey: modelSearch.providerKey ?? undefined,
        modelType: modelSearch.modelType ?? undefined,
        enabled: modelSearch.enabled === null ? undefined : modelSearch.enabled === 'true'
      })
    ])
    providers.value = providerRows.rows
    providerPagination.itemCount = providerRows.total
    void refreshProviderIcons(providerRows.rows)
    models.value = modelRows.rows
    modelPagination.itemCount = modelRows.total
    allProviders.value = (await listProviders(adminToken.value, { pageNum: 1, pageSize: 1000 })).rows
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载模型配置失败')
  } finally {
    loading.value = false
  }
}

function handleProviderPageChange(page: number) {
  providerPagination.page = page
  void reload()
}

function handleProviderPageSizeChange(pageSize: number) {
  providerPagination.pageSize = pageSize
  providerPagination.page = 1
  void reload()
}

function handleModelPageChange(page: number) {
  modelPagination.page = page
  void reload()
}

function handleModelPageSizeChange(pageSize: number) {
  modelPagination.pageSize = pageSize
  modelPagination.page = 1
  void reload()
}

/** 切换页签时回到第一页并刷新列表。 */
function handleTabChange(tab: string) {
  if (tab === 'provider') {
    providerPagination.page = 1
  } else {
    modelPagination.page = 1
  }
  void reload()
}

function handleProviderSearch() {
  providerPagination.page = 1
  void reload()
}

function handleModelSearch() {
  modelPagination.page = 1
  void reload()
}

function handleCreate() {
  if (activeTab.value === 'provider') {
    openProviderModal()
  } else {
    void openModelModal()
  }
}

function resetProviderIcon() {
  if (providerIconPreview.value) {
    URL.revokeObjectURL(providerIconPreview.value)
  }
  providerIconPreview.value = ''
}

function openProviderModal() {
  editingProviderId.value = null
  providerForm.providerKey = ''
  providerForm.name = ''
  providerForm.description = ''
  providerForm.iconUrl = ''
  providerForm.enabled = true
  resetProviderIcon()
  showProviderModal.value = true
}

async function openEditProvider(row: ProviderResponse) {
  editingProviderId.value = row.id
  providerForm.providerKey = row.providerKey
  providerForm.name = row.name
  providerForm.description = row.description ?? ''
  providerForm.iconUrl = row.iconUrl ?? ''
  providerForm.enabled = row.enabled
  resetProviderIcon()
  if (row.iconUrl) {
    try {
      providerIconPreview.value = await loadImagePreview(adminToken.value, row.iconUrl)
    } catch {
      providerIconPreview.value = ''
    }
  }
  showProviderModal.value = true
}

function openIconPicker() {
  showIconPicker.value = true
}

async function handleIconPicked(value: string) {
  providerForm.iconUrl = value
  if (providerIconPreview.value) {
    URL.revokeObjectURL(providerIconPreview.value)
  }
  providerIconPreview.value = ''
  if (value) {
    try {
      providerIconPreview.value = await loadImagePreview(adminToken.value, value)
    } catch {
      providerIconPreview.value = ''
    }
  }
}

function clearProviderIcon() {
  resetProviderIcon()
  providerForm.iconUrl = ''
}

async function openModelModal() {
  editingModelId.value = null
  try {
    allProviders.value = (await listProviders(adminToken.value, { pageNum: 1, pageSize: 1000 })).rows
  } catch {
    allProviders.value = []
  }
  modelForm.providerKey = allProviders.value[0]?.providerKey ?? ''
  modelForm.modelKey = ''
  modelForm.type = 'CHAT'
  modelForm.modelName = ''
  modelForm.description = ''
  modelForm.adapterKey = 'openai-compatible'
  modelForm.scope = 'GLOBAL'
  modelForm.isDefault = false
  resetConfigParams()
  modelForm.apiKey = ''
  modelForm.baseUrl = ''
  modelForm.enabled = true
  showModelModal.value = true
}

function openEditModel(row: ModelResponse) {
  editingModelId.value = row.id
  const provider = allProviders.value.find((p) => p.id === row.providerId)
  modelForm.providerKey = provider?.providerKey ?? ''
  modelForm.modelKey = row.modelKey
  modelForm.type = row.modelType
  modelForm.modelName = row.modelName ?? row.modelKey
  modelForm.description = row.description ?? ''
  modelForm.adapterKey = row.adapterKey ?? 'openai-compatible'
  modelForm.scope = row.scope ?? 'GLOBAL'
  modelForm.isDefault = !!row.isDefault
  applyConfig(row.config)
  // 凭据加密存储不可回显：留空表示不修改
  modelForm.apiKey = ''
  modelForm.baseUrl = ''
  modelForm.enabled = row.enabled
  showModelModal.value = true
}

function modelExtras() {
  return {
    modelName: modelForm.modelName.trim() || undefined,
    description: modelForm.description.trim() || undefined,
    adapterKey: modelForm.adapterKey || undefined,
    scope: modelForm.scope || undefined,
    isDefault: modelForm.isDefault || undefined,
    endpoint: modelForm.baseUrl.trim() || undefined,
    config: buildConfig()
  }
}

async function handleSaveProvider() {
  savingProvider.value = true
  try {
    const iconUrl = providerForm.iconUrl
    if (editingProviderId.value != null) {
      await updateProvider(adminToken.value, {
        id: editingProviderId.value,
        name: providerForm.name,
        enabled: providerForm.enabled,
        description: providerForm.description.trim() || undefined,
        iconUrl: iconUrl || undefined
      })
      message.success('Provider 已更新')
    } else {
      await createProvider(adminToken.value, {
        providerKey: providerForm.providerKey,
        name: providerForm.name,
        enabled: providerForm.enabled,
        description: providerForm.description.trim() || undefined,
        iconUrl: iconUrl || undefined
      })
      message.success('Provider 已保存')
    }
    showProviderModal.value = false
    await reload()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '保存 Provider 失败')
  } finally {
    savingProvider.value = false
  }
}

async function handleSaveModel() {
  if (!modelForm.modelName.trim()) {
    message.warning('请输入模型名称')
    return
  }
  savingModel.value = true
  try {
    const extras = modelExtras()
    if (editingModelId.value != null) {
      await updateModel(adminToken.value, {
        id: editingModelId.value,
        providerKey: modelForm.providerKey,
        modelKey: modelForm.modelKey,
        type: modelForm.type,
        enabled: modelForm.enabled,
        ...extras,
        // 编辑时凭据留空则保留原密文，仅在填写 API Key 时更新
        credentials: modelForm.apiKey ? modelCredentials() : undefined
      })
      message.success('Model 已更新')
    } else {
      await createModel(adminToken.value, {
        providerKey: modelForm.providerKey,
        modelKey: modelForm.modelKey,
        type: modelForm.type,
        enabled: modelForm.enabled,
        ...extras,
        credentials: modelCredentials()
      })
      message.success('Model 已保存')
    }
    showModelModal.value = false
    await reload()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '保存 Model 失败')
  } finally {
    savingModel.value = false
  }
}

async function handleDeleteProvider(id: number) {
  dialog.warning({
    title: '删除确认',
    content: `确认删除 Provider #${id}？`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await deleteProvider(adminToken.value, [id])
        message.success('Provider 已删除')
        await reload()
      } catch (e) {
        message.error(e instanceof Error ? e.message : '删除 Provider 失败')
      }
    }
  })
}

async function handleDeleteModel(id: number) {
  dialog.warning({
    title: '删除确认',
    content: `确认删除 Model #${id}？`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await deleteModel(adminToken.value, id)
        message.success('Model 已删除')
        await reload()
      } catch (e) {
        message.error(e instanceof Error ? e.message : '删除 Model 失败')
      }
    }
  })
}

async function handleTestModel(id: number) {
  testingModelId.value = id
  try {
    const result = await testModelConnection(adminToken.value, id)
    if (result.ok) {
      message.success(`Model #${id} 连接正常`)
    } else {
      message.error(`Model #${id} 连接失败: ${result.message}`)
    }
  } catch (e) {
    message.error(e instanceof Error ? e.message : '连通性测试失败')
  } finally {
    testingModelId.value = null
  }
}

onMounted(reload)
</script>

<template>
  <div class="page">
    <PageHeader title="模型管理" description="配置模型供应商与可用模型，模型凭证将加密存储。">
      <template #actions>
        <n-button size="small" type="primary" @click="handleCreate">
          <template #icon><n-icon :component="Plus" /></template>
          {{ activeTab === 'provider' ? '新建供应商' : '新建模型' }}
        </n-button>
      </template>
    </PageHeader>

    <n-tabs v-model:value="activeTab" type="line" @update:value="handleTabChange">
      <!-- 模型管理 -->
      <n-tab-pane name="model" tab="模型管理">
        <n-space style="margin-bottom: 12px" :size="8" align="center">
          <n-input v-model:value="modelSearch.keyword" placeholder="模型名称 / Key" clearable style="width: 220px" @keyup.enter="handleModelSearch" />
          <n-select v-model:value="modelSearch.providerKey" placeholder="模型供应商" clearable style="width: 180px" :options="providerOptions()" />
          <n-select v-model:value="modelSearch.modelType" placeholder="模型类型" clearable style="width: 140px" :options="modelTypeOptions" />
          <n-select v-model:value="modelSearch.enabled" placeholder="状态" clearable style="width: 110px" :options="enabledOptions" />
          <n-button size="small" type="primary" secondary @click="handleModelSearch">
            <template #icon><n-icon :component="Search" /></template>
            查询
          </n-button>
          <n-button size="small" quaternary @click="handleModelSearch">
            <template #icon><n-icon :component="RefreshCw" /></template>
            刷新
          </n-button>
        </n-space>
        <n-data-table :loading="loading" :columns="modelColumns" :data="models" :bordered="false" />
        <n-pagination
          :page="modelPagination.page"
          :page-size="modelPagination.pageSize"
          :item-count="modelPagination.itemCount"
          :page-sizes="[10, 20, 50, 100]"
          show-size-picker
          style="justify-content: flex-end; margin-top: 12px"
          @update:page="handleModelPageChange"
          @update:page-size="handleModelPageSizeChange"
        />
      </n-tab-pane>

      <!-- 供应商管理 -->
      <n-tab-pane name="provider" tab="供应商管理">
        <n-space style="margin-bottom: 12px" :size="8" align="center">
          <n-input v-model:value="providerSearch.keyword" placeholder="供应商名称 / Key" clearable style="width: 240px" @keyup.enter="handleProviderSearch" />
          <n-button size="small" type="primary" secondary @click="handleProviderSearch">
            <template #icon><n-icon :component="Search" /></template>
            查询
          </n-button>
          <n-button size="small" quaternary @click="handleProviderSearch">
            <template #icon><n-icon :component="RefreshCw" /></template>
            刷新
          </n-button>
        </n-space>
        <n-data-table :loading="loading" :columns="providerColumns" :data="providers" :bordered="false" />
        <n-pagination
            :page="providerPagination.page"
            :page-size="providerPagination.pageSize"
            :item-count="providerPagination.itemCount"
            :page-sizes="[10, 20, 50, 100]"
            show-size-picker
            style="justify-content: flex-end; margin-top: 12px"
            @update:page="handleProviderPageChange"
            @update:page-size="handleProviderPageSizeChange"
        />
      </n-tab-pane>
    </n-tabs>

    <n-modal
      v-model:show="showProviderModal"
      preset="card"
      :title="editingProviderId != null ? '编辑 Provider' : '新建 Provider'"
      style="width: 460px"
      :bordered="false"
    >
      <n-form label-placement="top">
        <n-form-item label="Provider Key">
          <n-input v-model:value="providerForm.providerKey" :disabled="editingProviderId != null" placeholder="如 openai" />
        </n-form-item>
        <n-form-item label="名称">
          <n-input v-model:value="providerForm.name" placeholder="如 OpenAI" />
        </n-form-item>
        <n-form-item label="描述">
          <n-input v-model:value="providerForm.description" type="textarea" :maxlength="1000" show-count :autosize="{ minRows: 1, maxRows: 3 }" placeholder="请输入提供商描述" />
        </n-form-item>
        <n-form-item label="LOGO 图标">
          <div style="display:flex;align-items:center;gap:12px">
            <div
              title="点击选择图标"
              style="width:56px;height:56px;border:1px dashed #d9d9d9;border-radius:6px;display:flex;align-items:center;justify-content:center;cursor:pointer;overflow:hidden;background:#fafafa"
              @click="openIconPicker"
            >
              <img v-if="providerIconPreview" :src="providerIconPreview" alt="LOGO" style="width:100%;height:100%;object-fit:cover" />
              <span v-else style="font-size:12px;color:#999">选择图标</span>
            </div>
            <n-button v-if="providerForm.iconUrl" size="small" quaternary type="error" @click="clearProviderIcon">移除</n-button>
            <span style="font-size:12px;color:#999">点击图标可选择 / 上传 / 输入网络 URL</span>
          </div>
        </n-form-item>
        <n-form-item label="启用">
          <n-switch v-model:value="providerForm.enabled" />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showProviderModal = false">取消</n-button>
          <n-button type="primary" :loading="savingProvider" @click="handleSaveProvider">保存</n-button>
        </n-space>
      </template>
    </n-modal>

    <IconPicker
      v-model:show="showIconPicker"
      :model-value="providerForm.iconUrl"
      :admin-token="adminToken"
      @update:model-value="handleIconPicked"
    />

    <n-modal
      v-model:show="showModelModal"
      preset="card"
      :title="editingModelId != null ? '编辑 Model' : '新建 Model'"
      style="width: 600px"
      :bordered="false"
    >
      <n-form label-placement="top">
        <n-form-item label="请选择提供商 *">
          <n-select v-model:value="modelForm.providerKey" filterable :options="providerOptions()" />
        </n-form-item>
        <n-form-item label="模型名称 *">
          <n-input v-model:value="modelForm.modelName" :maxlength="255" show-count placeholder="如 GPT-4o Mini" />
        </n-form-item>
        <n-form-item label="模型Key *">
          <n-input v-model:value="modelForm.modelKey" :maxlength="100" show-count placeholder="如 gpt-4o-mini" />
        </n-form-item>
        <n-form-item label="类型 *">
          <n-select v-model:value="modelForm.type" :options="modelTypeOptions" />
        </n-form-item>
        <n-form-item label="适配器 *">
          <n-select v-model:value="modelForm.adapterKey" filterable tag :options="adapterKeyOptions" />
        </n-form-item>
        <n-form-item label="描述">
          <n-input v-model:value="modelForm.description" type="textarea" :maxlength="1000" show-count :autosize="{ minRows: 1, maxRows: 3 }" placeholder="请输入模型描述" />
        </n-form-item>
        <n-form-item :label="editingModelId != null ? 'API Key（留空则不修改凭据）' : 'API Key'">
          <n-input v-model:value="modelForm.apiKey" type="password" show-password-on="click" placeholder="sk-..." />
        </n-form-item>
        <n-form-item label="API端点 *">
          <n-input v-model:value="modelForm.baseUrl" placeholder="https://api.deepseek.com" />
        </n-form-item>

        <n-collapse :default-expanded-names="['params']" :bordered="false" arrow-placement="right">
          <n-collapse-item title="配置参数" name="params">
            <n-form label-placement="top">
              <!-- 对话模型参数 -->
              <template v-if="modelForm.type === 'CHAT'">
                <n-form-item label="支持图片输入">
                  <n-switch v-model:value="modelForm.imageInput" />
                  <span class="param-hint">开启后该模型可接收图片消息</span>
                </n-form-item>
                <n-form-item label="作为 OCR 视觉兜底模型">
                  <n-switch v-model:value="modelForm.ocrFallback" />
                  <span class="param-hint">开启后作为 OCR 视觉兜底模型使用</span>
                </n-form-item>
                <n-form-item label="Temperature">
                  <n-slider v-model:value="modelForm.temperature" :min="0" :max="2" :step="0.1" :format-tooltip="(v: number) => v.toFixed(1)" style="flex: 1" />
                  <span class="param-value">{{ modelForm.temperature.toFixed(1) }}</span>
                </n-form-item>
                <n-form-item label="Top P">
                  <n-slider v-model:value="modelForm.topP" :min="0" :max="1" :step="0.05" :format-tooltip="(v: number) => v.toFixed(2)" style="flex: 1" />
                  <span class="param-value">{{ modelForm.topP.toFixed(2) }}</span>
                </n-form-item>
                <n-form-item label="Top K">
                  <n-slider v-model:value="modelForm.topK" :min="0" :max="100" :step="1" style="flex: 1" />
                  <span class="param-value">{{ modelForm.topK }}</span>
                </n-form-item>
                <n-form-item label="最大Token数">
                  <n-input-number v-model:value="modelForm.maxTokens" :min="1" :max="1048576" style="width: 160px" />
                </n-form-item>
                <n-form-item label="频率惩罚">
                  <n-slider v-model:value="modelForm.frequencyPenalty" :min="0" :max="2" :step="0.1" :format-tooltip="(v: number) => v.toFixed(1)" style="flex: 1" />
                  <span class="param-value">{{ modelForm.frequencyPenalty.toFixed(1) }}</span>
                </n-form-item>
              </template>
              <!-- 向量模型参数 -->
              <template v-else-if="modelForm.type === 'EMBEDDING'">
                <n-form-item label="嵌入维度">
                  <n-input-number v-model:value="modelForm.embeddingDimension" :min="1" :max="65536" style="width: 160px" placeholder="请输入" />
                </n-form-item>
                <n-form-item label="超时时间(ms)">
                  <n-input-number v-model:value="modelForm.timeoutMs" :min="0" :step="1000" style="width: 160px" placeholder="默认" />
                </n-form-item>
              </template>
              <!-- 重排模型参数 -->
              <template v-else-if="modelForm.type === 'RERANK'">
                <n-form-item label="重排端点路径">
                  <n-input v-model:value="modelForm.rerankPath" placeholder="e.g., /v1/rerank" />
                </n-form-item>
              </template>
            </n-form>
          </n-collapse-item>
        </n-collapse>

        <n-form-item label="作用域">
          <n-select v-model:value="modelForm.scope" :options="scopeOptions" />
        </n-form-item>
        <n-form-item label="默认模型">
          <n-switch v-model:value="modelForm.isDefault" />
        </n-form-item>
        <n-form-item label="启用">
          <n-switch v-model:value="modelForm.enabled" />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showModelModal = false">取消</n-button>
          <n-button type="primary" :loading="savingModel" @click="handleSaveModel">保存</n-button>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>

<style scoped>
.param-hint {
  font-size: 12px;
  opacity: 0.6;
  margin-left: 10px;
}

.param-value {
  min-width: 44px;
  margin-left: 12px;
  font-size: 13px;
  font-variant-numeric: tabular-nums;
  text-align: right;
  opacity: 0.85;
}
</style>
