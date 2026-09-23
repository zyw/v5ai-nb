<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
import {
  NButton,
  NCard,
  NCollapse,
  NCollapseItem,
  NDataTable,
  NDivider,
  NForm,
  NFormItem,
  NFormItemGi,
  NGrid,
  NGridItem,
  NIcon,
  NInput,
  NInputNumber,
  NModal,
  NPagination,
  NRadioButton,
  NRadioGroup,
  NSelect,
  NSlider,
  NSpace,
  NSwitch,
  NTag,
  NText,
  useDialog,
  useMessage,
  type DataTableColumns
} from 'naive-ui'
import { CircleQuestionMark, Plus, RefreshCw, Search } from 'lucide-vue-next'
import PageHeader from '../components/PageHeader.vue'
import IconPicker from '../components/IconPicker.vue'
import {
  createModel,
  createProvider,
  deleteModel,
  deleteProvider,
  listModels,
  listProviders,
  loadImagePreview,
  setModelDefault,
  setModelEnabled,
  testModelConnection,
  updateModel,
  updateProvider,
  type ModelResponse,
  type ProviderResponse
} from '../api/client'
import { adminToken } from '../stores/session'
import {formatDateTime} from "../utils/dateUtils";

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
/** 供应商列表「启用/禁用」Switch 正在切换的供应商 id（行内 loading）。 */
const togglingProviderId = ref<number | null>(null)
/** 模型列表「启用/禁用」Switch 正在切换的模型 id（行内 loading）。 */
const togglingModelId = ref<number | null>(null)
/** 模型列表「是否默认」Switch 正在切换的模型 id（行内 loading）。 */
const defaultingModelId = ref<number | null>(null)
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
  providerId: 0,
  modelKey: '',
  modelType: 'CHAT',
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
  dimensionAdjustable: false,
  maxDimension: null as number | null,
  timeoutMs: null as number | null,
  // 重排模型参数
  rerankPath: ''
})

const modelTypeOptions = [
  { label: '对话模型', value: 'CHAT' },
  { label: '向量模型', value: 'EMBEDDING' },
  { label: '重排模型', value: 'RERANK' }
]

const ADAPTERS_BY_TYPE: Record<string, string[]> = {
  CHAT: ['openai-compatible'],
  EMBEDDING: ['openai-compatible'],
  RERANK: ['qwen-rerank']
}

const ADAPTER_LABELS: Record<string, string> = {
  'openai-compatible': 'OpenAI 兼容',
  'qwen-rerank': 'Qwen Rerank'
}

/** 各模型类型的默认适配器（取该类型清单首项）。 */
function defaultAdapterKey(modelType: string): string {
  return ADAPTERS_BY_TYPE[modelType]?.[0] ?? 'openai-compatible'
}

const adapterKeyOptions = computed(() => {
  const values = ADAPTERS_BY_TYPE[modelForm.modelType] ?? ['openai-compatible']
  const options = values.map((value) => ({ label: ADAPTER_LABELS[value] ?? value, value }))
  // 编辑回显：存量 adapterKey 不在当前类型清单内时，仍纳入选项以显示原值（避免 select 显示空白）
  if (modelForm.adapterKey && !values.includes(modelForm.adapterKey)) {
    options.unshift({ label: modelForm.adapterKey, value: modelForm.adapterKey })
  }
  return options
})

/** 切换模型类型时，将适配器重置为该类型默认值。 */
function onModelTypeChange(modelType: string) {
  modelForm.modelType = modelType
  modelForm.adapterKey = defaultAdapterKey(modelType)
}

const providerOptions = () =>
  allProviders.value.map((item) => ({ label: `${item.name} (${item.providerKey})`, value: item.id }))

function modelCredentials(): string {
  return JSON.stringify({
    apiKey: modelForm.apiKey,
    baseUrl: modelForm.baseUrl || undefined
  })
}

/** 按模型类型构建 config（ConfigExtAttrsDTO JSON）；无参数时返回 undefined。 */
function buildConfig(): string | undefined {
  const cfg: Record<string, unknown> = {}
  if (modelForm.modelType === 'CHAT') {
    if (modelForm.temperature != null) cfg.temperature = modelForm.temperature
    if (modelForm.topP != null) cfg.topP = modelForm.topP
    if (modelForm.topK != null) cfg.topK = modelForm.topK
    if (modelForm.maxTokens != null) cfg.maxTokens = modelForm.maxTokens
    if (modelForm.frequencyPenalty != null) cfg.frequencyPenalty = modelForm.frequencyPenalty
    if (modelForm.imageInput) cfg.capabilities = ['image']
    if (modelForm.ocrFallback) cfg.defaultVisionModel = true
  } else if (modelForm.modelType === 'EMBEDDING') {
    if (modelForm.embeddingDimension != null) cfg.embeddingDimension = modelForm.embeddingDimension
    if (modelForm.dimensionAdjustable) {
      cfg.dimensionAdjustable = true
      if (modelForm.maxDimension != null) cfg.maxDimension = modelForm.maxDimension
    }
    if (modelForm.timeoutMs != null) cfg.timeoutMs = modelForm.timeoutMs
  } else if (modelForm.modelType === 'RERANK') {
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
  modelForm.dimensionAdjustable = false
  modelForm.maxDimension = null
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
    modelForm.dimensionAdjustable = cfg.dimensionAdjustable === true
    if (typeof cfg.maxDimension === 'number') modelForm.maxDimension = cfg.maxDimension
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
    width: 200,
    render: (row) =>
      h('div', { style: 'display:flex;align-items:center;gap:8px' }, [
        providerIcons[row.id]
          ? h('img', { src: providerIcons[row.id], alt: row.name, style: 'width:20px;height:20px;border-radius:4px;object-fit:cover' })
          : null,
        h('span', row.name)
      ])
  },
  { title: '供应商Key', key: 'providerKey', width: 150 },
  { title: '描述', key: 'description', width: 200, ellipsis: { tooltip: true } },
  {
    title: '状态',
    key: 'enabled',
    width: 100,
    render: (row) =>
      h(
        NSwitch,
        {
          value: row.enabled,
          loading: togglingProviderId.value === row.id,
          onUpdateValue: () => handleToggleProvider(row)
        },
        {
          checked: () => h('span', '启'),
          unchecked: () => h('span', '停')
        }
      )
  },
  { title: '创建时间', key: 'createdAt', width: 182, render: (row) => fmtTime(row.createdAt) },
  { title: '更新时间', key: 'updatedAt', width: 182, render: (row) => fmtTime(row.updatedAt) },
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
  // return provider ? `${provider.name} (${provider.providerKey})` : `#${providerId}`
  return provider ? `${provider.name}` : `#${providerId}`
}

const modelColumns: DataTableColumns<ModelResponse> = [
  { title: 'ID', key: 'id', width: 64 },
  {
    title: '名称',
    key: 'modelName',
    width: 180,
    render: (row) =>
      h('div', { style: 'display:flex;align-items:center;gap:6px;min-width:0' }, [
        row.isDefault ? h(NTag, { size: 'small', type: 'warning', bordered: false }, { default: () => '默认' }) : null,
        h('span', { style: 'overflow:hidden;text-overflow:ellipsis;white-space:nowrap', title: row.modelName ?? row.modelKey }, row.modelName ?? row.modelKey)
      ])
  },
  { title: '模型ID', key: 'modelKey',width: 180 },
  {
    title: '供应商',
    key: 'providerId',
    width: 180,
    render: (row) => providerLabel(row.providerId)
  },
  { title: '描述', key: 'description', width: 200, ellipsis: { tooltip: true } },
  {
    title: '类型',
    key: 'modelType',
    width: 90,
    render: (row) => h(NTag,
        { size: 'small', type: row.modelType === 'CHAT' ? 'info' : 'default', bordered: false },
        { default: () => modelTypeOptions.find((option) => option.value === row.modelType)?.label })
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
    width: 90,
    render: (row) =>
      h(NSwitch, {
        value: !!row.isDefault,
        loading: defaultingModelId.value === row.id,
        onUpdateValue: () => handleToggleModelDefault(row)
      }, {
        checked: () => h('span', '是'),
        unchecked: () => h('span', '否')
      })
  },
  {
    title: '状态',
    key: 'enabled',
    width: 90,
    render: (row) =>
      h(NSwitch, {
        value: row.enabled,
        loading: togglingModelId.value === row.id,
        onUpdateValue: () => handleToggleModelEnabled(row)
      }, {
        checked: () => h('span', '启'),
        unchecked: () => h('span', '停')
      })
  },
  { title: '创建时间', key: 'createdAt', width: 182, render: (row) => fmtTime(row.createdAt) },
  { title: '更新时间', key: 'updatedAt', width: 182, render: (row) => fmtTime(row.updatedAt) },
  {
    title: '操作',
    key: 'actions',
    width: 210,
    render: (row) =>
      h(NSpace, { size: 4 }, {
        default: () => [
          h(
              NButton,
              {
                size: 'small',
                secondary: true,
                loading: testingModelId.value === row.id,
                onClick: () => handleTestModel(row)
              },
              { default: () => '测试' }
          ),
          h(NButton, { size: 'small', quaternary: true, onClick: () => openEditModel(row) }, { default: () => '编辑' }),
          h(
            NButton,
            { size: 'small', type: 'error', secondary: true, onClick: () => handleDeleteModel(row) },
            { default: () => '删除' }
          )
        ]
      })
  }
]

/** 表格横向滚动：列宽总和（auto 列按 240px 估算），窄屏时出现横向滚动条 */
const modelScrollX = computed(() =>
  modelColumns.reduce((sum, col) => sum + (typeof col.width === 'number' ? col.width : 240), 0)
)

/** 供应商表格横向滚动：列宽总和（auto 列按 240px 估算） */
const providerScrollX = computed(() =>
  providerColumns.reduce((sum, col) => sum + (typeof col.width === 'number' ? col.width : 240), 0)
)

function fmtTime(value?: string | null): string {
  return value ? (formatDateTime(value) ?? '—') : '—'
}

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
  modelForm.providerId = allProviders.value[0]?.id ?? undefined
  modelForm.modelKey = ''
  modelForm.modelType = 'CHAT'
  modelForm.modelName = ''
  modelForm.description = ''
  modelForm.adapterKey = defaultAdapterKey(modelForm.modelType)
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
  modelForm.providerId = provider?.id ?? 0
  modelForm.modelKey = row.modelKey
  modelForm.modelType = row.modelType
  modelForm.modelName = row.modelName ?? row.modelKey
  modelForm.description = row.description ?? ''
  modelForm.adapterKey = row.adapterKey ?? defaultAdapterKey(row.modelType)
  modelForm.scope = row.scope ?? 'GLOBAL'
  modelForm.isDefault = !!row.isDefault
  applyConfig(row.config)
  // 凭据加密存储不可回显：留空表示不修改
  modelForm.apiKey = ''
  modelForm.baseUrl = row.baseUrl ?? ''
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
    baseUrl: modelForm.baseUrl.trim() || undefined,
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
        iconUrl: iconUrl
      })
      message.success('模型供应商已更新')
    } else {
      await createProvider(adminToken.value, {
        providerKey: providerForm.providerKey,
        name: providerForm.name,
        enabled: providerForm.enabled,
        description: providerForm.description.trim() || undefined,
        iconUrl: iconUrl
      })
      message.success('模型供应商已保存')
    }
    showProviderModal.value = false
    await reload()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '保存模型供应商失败')
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
        providerId: modelForm.providerId,
        modelKey: modelForm.modelKey,
        modelType: modelForm.modelType,
        enabled: modelForm.enabled,
        ...extras,
        // 编辑时凭据留空则保留原密文，仅在填写 API Key 时更新
        credentials: modelForm.apiKey ? modelCredentials() : undefined
      })
      message.success('Model 已更新')
    } else {
      await createModel(adminToken.value, {
        providerId: modelForm.providerId,
        modelKey: modelForm.modelKey,
        modelType: modelForm.modelType,
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

async function handleToggleProvider(row: ProviderResponse) {
  const next = row.enabled ? '停用' : '启用'
  dialog.warning({
    title: `${next}确认`,
    content: `确认${next}模型供应商「${row.name}」？`,
    positiveText: next,
    negativeText: '取消',
    onPositiveClick: async () => {
      togglingProviderId.value = row.id
      try {
        await updateProvider(adminToken.value, {
          id: row.id,
          name: row.name,
          enabled: !row.enabled
        })
        message.success(`模型供应商「${row.name}」已${next}`)
        await reload()
      } catch (e) {
        message.error(e instanceof Error ? e.message : `${next}失败`)
      } finally {
        togglingProviderId.value = null
      }
    }
  })
}

/** 模型行内「启用/禁用」开关：走专用端点（后端校验引用；停用默认模型时自动取消其默认标记）。 */
async function handleToggleModelEnabled(row: ModelResponse) {
  const next = row.enabled ? '停用' : '启用'
  const label = row.modelName ?? row.modelKey
  // 停用默认模型时后端会同步取消其默认标记，文案提前告知
  const clearsDefault = row.enabled && !!row.isDefault
  dialog.warning({
    title: `${next}确认`,
    content: clearsDefault
      ? `确认${next}模型「${label}」？该模型为同类型默认模型，停用后将同时取消其默认标记。`
      : `确认${next}模型「${label}」？`,
    positiveText: next,
    negativeText: '取消',
    onPositiveClick: async () => {
      togglingModelId.value = row.id
      try {
        await setModelEnabled(adminToken.value, row.id, !row.enabled)
        message.success(`模型「${label}」已${next}`)
        await reload()
      } catch (e) {
        message.error(e instanceof Error ? e.message : `${next}失败`)
      } finally {
        togglingModelId.value = null
      }
    }
  })
}

/** 模型行内「是否默认」开关：走专用端点（同类型唯一默认由后端保证）。 */
async function handleToggleModelDefault(row: ModelResponse) {
  const next = row.isDefault ? '取消默认' : '设为默认'
  const label = row.modelName ?? row.modelKey
  dialog.warning({
    title: `${next}确认`,
    content: `确认${next}模型「${label}」？`,
    positiveText: next,
    negativeText: '取消',
    onPositiveClick: async () => {
      defaultingModelId.value = row.id
      try {
        await setModelDefault(adminToken.value, row.id, !row.isDefault)
        message.success(`模型「${label}」已${next}`)
        await reload()
      } catch (e) {
        message.error(e instanceof Error ? e.message : `${next}失败`)
      } finally {
        defaultingModelId.value = null
      }
    }
  })
}

async function handleDeleteProvider(id: number) {
  dialog.warning({
    title: '删除确认',
    content: `确认删除模型供应商 #${id}？`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await deleteProvider(adminToken.value, [id])
        message.success('模型供应商已删除')
        await reload()
      } catch (e) {
        message.error(e instanceof Error ? e.message : '删除模型供应商失败')
      }
    }
  })
}

async function handleDeleteModel(row: ModelResponse) {
  const id = row.id;
  const name = row.modelName;
  dialog.warning({
    title: '删除确认',
    content: `确认删除模型 ${name}？`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await deleteModel(adminToken.value, id)
        message.success('模型已删除')
        await reload()
      } catch (e) {
        message.error(e instanceof Error ? e.message : '删除模型失败')
      }
    }
  })
}

async function handleTestModel(row: ModelResponse) {
  testingModelId.value = row.id
  const name = row.modelName
  try {
    const result = await testModelConnection(adminToken.value, row.id)
    if (result.ok) {
      const suffix = result.message && result.message !== 'connection ok' ? `：${result.message}` : ''
      message.success(`模型(${name})连接正常${suffix}`)
    } else {
      message.error(`模型(${name})连接失败: ${result.message}`)
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
          <n-input v-model:value="modelSearch.keyword" placeholder="模型名称 / 模型ID" clearable style="width: 220px" @keyup.enter="handleModelSearch" />
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
        <n-data-table :loading="loading" :columns="modelColumns" :data="models" :bordered="false" :scroll-x="modelScrollX" />
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
        <n-data-table :loading="loading" :columns="providerColumns" :data="providers" :bordered="false" :scroll-x="providerScrollX" />
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
      :title="editingProviderId != null ? '编辑供应商' : '新建供应商'"
      style="width: 560px"
      :bordered="false"
    >
      <n-form label-placement="top">
        <n-grid :cols="2" :x-gap="16">
          <n-form-item-gi label="供应商Key">
            <n-input v-model:value="providerForm.providerKey" :disabled="editingProviderId != null" placeholder="如 openai" />
          </n-form-item-gi>
          <n-form-item-gi label="名称">
            <n-input v-model:value="providerForm.name" placeholder="如 OpenAI" />
          </n-form-item-gi>
        </n-grid>
        <n-form-item label="描述">
          <n-input v-model:value="providerForm.description" type="textarea" :maxlength="1000" show-count :autosize="{ minRows: 3, maxRows: 5 }" placeholder="请输入提供商描述" />
        </n-form-item>
        <n-form-item label="图标">
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
<!--        <n-form-item label="启用">
          <n-switch v-model:value="providerForm.enabled" />
        </n-form-item>-->
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
      :title="editingModelId != null ? '编辑模型' : '新建模型'"
      style="width: 760px"
      :bordered="false"
    >
      <n-form label-placement="top">
        <n-grid :cols="24" :x-gap="24">
          <n-grid-item :span="24">
            <n-divider title-placement="left" style="margin: 0 0 16px">基本信息</n-divider>
          </n-grid-item>
          <n-form-item-gi :span="12" label="请选择提供商">
          <n-select v-model:value="modelForm.providerId" filterable :options="providerOptions()" />
        </n-form-item-gi>
          <n-form-item-gi :span="12" label="作用域">
            <n-radio-group v-model:value="modelForm.scope">
              <n-radio-button value="GLOBAL">全局</n-radio-button>
              <n-radio-button value="PERSONAL">个人</n-radio-button>
            </n-radio-group>
          </n-form-item-gi>
        <n-form-item-gi :span="12" label="模型名称">
          <n-input v-model:value="modelForm.modelName" :maxlength="255" show-count placeholder="如 GPT-4o Mini" />
        </n-form-item-gi>
        <n-form-item-gi :span="12" label="模型ID">
          <n-input v-model:value="modelForm.modelKey" :maxlength="100" show-count placeholder="如 gpt-4o-mini" />
        </n-form-item-gi>
          <n-form-item-gi :span="24" label="描述">
            <n-input v-model:value="modelForm.description" type="textarea" :maxlength="1000" show-count :autosize="{ minRows: 3, maxRows: 5 }" placeholder="请输入模型描述" />
          </n-form-item-gi>
          <n-grid-item :span="24">
            <n-divider title-placement="left" style="margin: 8px 0 16px">连接与凭据</n-divider>
          </n-grid-item>
          <n-form-item-gi :span="12" label="类型">
            <n-radio-group :value="modelForm.modelType" @update:value="onModelTypeChange">
              <n-radio-button value="CHAT">对话模型</n-radio-button>
              <n-radio-button value="EMBEDDING">向量模型</n-radio-button>
              <n-radio-button value="RERANK">重排模型</n-radio-button>
            </n-radio-group>
          </n-form-item-gi>
        <n-form-item-gi :span="12" label="适配器">
          <n-select v-model:value="modelForm.adapterKey" :options="adapterKeyOptions" />
        </n-form-item-gi>

        <n-form-item-gi :span="12" :label="editingModelId != null ? 'API Key（留空则不修改凭据）' : 'API Key'">
          <n-input v-model:value="modelForm.apiKey" type="password" show-password-on="click" placeholder="sk-..." />
        </n-form-item-gi>
        <n-form-item-gi :span="12" label="API 基础地址">
          <n-input v-model:value="modelForm.baseUrl" placeholder="https://api.deepseek.com" />
        </n-form-item-gi>

        <n-grid-item :span="24">
          <n-collapse :default-expanded-names="['params']" :bordered="false" arrow-placement="right">
            <n-collapse-item title="配置参数" name="params">
              <!-- 对话模型参数 -->
              <template v-if="modelForm.modelType === 'CHAT'">
                <n-grid :cols="24" :x-gap="24">
                <n-form-item-gi :span="12" label="支持图片输入">
                  <n-switch v-model:value="modelForm.imageInput">
                    <template #checked>是</template>
                    <template #unchecked>否</template>
                  </n-switch>
                  <n-text depth="3" style="font-size: 12px; margin-left: 10px">开启后该模型可接收图片消息</n-text>
                </n-form-item-gi>
                <n-form-item-gi :span="12" label="作为 OCR 视觉兜底模型">
                  <n-switch v-model:value="modelForm.ocrFallback">
                    <template #checked>是</template>
                    <template #unchecked>否</template>
                  </n-switch>
                  <n-text depth="3" style="font-size: 12px; margin-left: 10px">开启后作为 OCR 视觉兜底模型使用</n-text>
                </n-form-item-gi>
                <n-form-item-gi :span="12" label="Temperature">
                  <n-slider v-model:value="modelForm.temperature" :min="0" :max="2" :step="0.1" :format-tooltip="(v: number) => v.toFixed(1)" style="flex: 1" />
                  <span class="param-value">{{ modelForm.temperature.toFixed(1) }}</span>
                </n-form-item-gi>
                <n-form-item-gi :span="12" label="Top P">
                  <n-slider v-model:value="modelForm.topP" :min="0" :max="1" :step="0.05" :format-tooltip="(v: number) => v.toFixed(2)" style="flex: 1" />
                  <span class="param-value">{{ modelForm.topP.toFixed(2) }}</span>
                </n-form-item-gi>
                <n-form-item-gi :span="12" label="Top K">
                  <n-slider v-model:value="modelForm.topK" :min="0" :max="100" :step="1" style="flex: 1" />
                  <span class="param-value">{{ modelForm.topK }}</span>
                </n-form-item-gi>
                  <n-form-item-gi :span="12" label="频率惩罚">
                    <n-slider v-model:value="modelForm.frequencyPenalty" :min="0" :max="2" :step="0.1" :format-tooltip="(v: number) => v.toFixed(1)" style="flex: 1" />
                    <span class="param-value">{{ modelForm.frequencyPenalty.toFixed(1) }}</span>
                  </n-form-item-gi>
                <n-form-item-gi :span="12" label="最大Token数">
                  <n-input-number v-model:value="modelForm.maxTokens" :min="1" :max="1048576" style="width: 100%" />
                </n-form-item-gi>
                </n-grid>
              </template>
              <!-- 向量模型参数 -->
              <template v-else-if="modelForm.modelType === 'EMBEDDING'">
                <n-grid :cols="24" :x-gap="24">
                <n-form-item-gi :span="12" label="嵌入维度">
                  <n-input-number v-model:value="modelForm.embeddingDimension" :min="1" :max="65536" style="width: 100%" placeholder="请输入" />
                </n-form-item-gi>
                <n-form-item-gi :span="12" label="维度可调">
                  <template #label>
                    <span style="vertical-align: baseline">维度可调</span>
                    <n-popover trigger="hover">
                      <template #trigger>
                        <n-icon><CircleQuestionMark /></n-icon>
                      </template>
                      <span class="muted">支持 dimensions 参数的模型（如 OpenAI text-embedding-3）：可对每个知识库冻结更低的输出维度</span>
                    </n-popover>
                  </template>
                  <n-switch v-model:value="modelForm.dimensionAdjustable">
                    <template #checked>是</template>
                    <template #unchecked>否</template>
                  </n-switch>
                </n-form-item-gi>
                <n-form-item-gi v-if="modelForm.dimensionAdjustable" :span="12" label="可调上限">
                  <n-input-number v-model:value="modelForm.maxDimension" :min="1" :max="65536" style="width: 100%" placeholder="默认等于嵌入维度" />
                </n-form-item-gi>
                <n-form-item-gi :span="12" label="超时时间(ms)">
                  <n-input-number v-model:value="modelForm.timeoutMs" :min="0" :step="1000" style="width: 100%" placeholder="默认" />
                </n-form-item-gi>
                </n-grid>
              </template>
              <!-- 重排模型参数 -->
              <template v-else-if="modelForm.modelType === 'RERANK'">
                <n-form-item label="重排端点路径">
                  <n-input v-model:value="modelForm.rerankPath" placeholder="/rerank, /v1/rerank" />
                </n-form-item>
              </template>
            </n-collapse-item>
          </n-collapse>
        </n-grid-item>


<!--        <n-form-item-gi :span="12" label="默认模型">-->
<!--          <n-switch v-model:value="modelForm.isDefault" />-->
<!--        </n-form-item-gi>-->
<!--        <n-form-item-gi :span="24" label="启用">-->
<!--          <n-switch v-model:value="modelForm.enabled" />-->
<!--        </n-form-item-gi>-->
        </n-grid>
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
.param-value {
  min-width: 44px;
  margin-left: 12px;
  font-size: 13px;
  font-variant-numeric: tabular-nums;
  text-align: right;
  opacity: 0.85;
}
</style>
