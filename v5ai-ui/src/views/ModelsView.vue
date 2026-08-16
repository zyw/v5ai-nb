<script setup lang="ts">
import { h, onMounted, reactive, ref } from 'vue'
import {
  NButton,
  NCard,
  NDataTable,
  NForm,
  NFormItem,
  NIcon,
  NInput,
  NInputNumber,
  NModal,
  NSelect,
  NSpace,
  NSwitch,
  NTag,
  useDialog,
  useMessage,
  type DataTableColumns
} from 'naive-ui'
import { Plus } from 'lucide-vue-next'
import PageHeader from '../components/PageHeader.vue'
import StatusTag from '../components/StatusTag.vue'
import {
  createModel,
  createProvider,
  deleteModel,
  deleteProvider,
  listModels,
  listProviders,
  testModelConnection,
  type ModelResponse,
  type ProviderResponse
} from '../api/client'
import { adminToken } from '../stores/session'

const message = useMessage()
const dialog = useDialog()
const loading = ref(false)
const providers = ref<ProviderResponse[]>([])
const models = ref<ModelResponse[]>([])
const testingModelId = ref<number | null>(null)

const showProviderModal = ref(false)
const showModelModal = ref(false)
const savingProvider = ref(false)
const savingModel = ref(false)

const providerForm = reactive({
  providerKey: '',
  name: '',
  providerType: 'OPENAI_COMPATIBLE',
  enabled: true
})

const modelForm = reactive({
  providerKey: '',
  modelKey: '',
  type: 'CHAT',
  apiKey: '',
  baseUrl: '',
  temperature: 0.7,
  maxTokens: 4096
})

const providerTypeOptions = [
  { label: 'OpenAI', value: 'OPENAI' },
  { label: 'OpenAI Compatible', value: 'OPENAI_COMPATIBLE' },
  { label: 'DashScope', value: 'DASHSCOPE' }
]

const modelTypeOptions = [
  { label: 'Chat', value: 'CHAT' },
  { label: 'Embedding', value: 'EMBEDDING' },
  { label: 'Rerank', value: 'RERANK' }
]

const providerOptions = () =>
  providers.value.map((item) => ({ label: `${item.name} (${item.providerKey})`, value: item.providerKey }))

function modelCredentials(): string {
  return JSON.stringify({
    apiKey: modelForm.apiKey,
    baseUrl: modelForm.baseUrl || undefined,
    temperature: modelForm.temperature,
    maxTokens: modelForm.maxTokens
  })
}

const providerColumns: DataTableColumns<ProviderResponse> = [
  { title: 'ID', key: 'id', width: 64 },
  { title: 'Provider Key', key: 'providerKey' },
  { title: '名称', key: 'name' },
  { title: '类型', key: 'providerType' },
  {
    title: '状态',
    key: 'enabled',
    width: 100,
    render: (row) => h(StatusTag, { status: row.enabled ? 'ACTIVE' : 'DISABLED' })
  },
  {
    title: '操作',
    key: 'actions',
    width: 90,
    render: (row) =>
      h(
        NButton,
        {
          size: 'small',
          type: 'error',
          secondary: true,
          onClick: () => handleDeleteProvider(row.providerKey)
        },
        { default: () => '删除' }
      )
  }
]

const modelColumns: DataTableColumns<ModelResponse> = [
  { title: 'ID', key: 'id', width: 64 },
  { title: 'Provider', key: 'providerKey' },
  { title: 'Model Key', key: 'modelKey' },
  {
    title: '类型',
    key: 'type',
    render: (row) => h(NTag, { size: 'small', type: row.type === 'CHAT' ? 'info' : 'default', bordered: false }, { default: () => row.type })
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
    width: 90,
    render: (row) =>
      h(
        NButton,
        { size: 'small', type: 'error', secondary: true, onClick: () => handleDeleteModel(row.id) },
        { default: () => '删除' }
      )
  }
]

async function reload() {
  loading.value = true
  try {
    const [providerRows, modelRows] = await Promise.all([
      listProviders(adminToken.value),
      listModels(adminToken.value)
    ])
    providers.value = providerRows
    models.value = modelRows
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载模型配置失败')
  } finally {
    loading.value = false
  }
}

function openProviderModal() {
  providerForm.providerKey = ''
  providerForm.name = ''
  providerForm.providerType = 'OPENAI_COMPATIBLE'
  providerForm.enabled = true
  showProviderModal.value = true
}

function openModelModal() {
  modelForm.providerKey = providers.value[0]?.providerKey ?? ''
  modelForm.modelKey = ''
  modelForm.type = 'CHAT'
  modelForm.apiKey = ''
  modelForm.baseUrl = ''
  modelForm.temperature = 0.7
  modelForm.maxTokens = 4096
  showModelModal.value = true
}

async function handleCreateProvider() {
  savingProvider.value = true
  try {
    await createProvider(adminToken.value, providerForm)
    message.success('Provider 已保存')
    showProviderModal.value = false
    await reload()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '保存 Provider 失败')
  } finally {
    savingProvider.value = false
  }
}

async function handleCreateModel() {
  savingModel.value = true
  try {
    await createModel(adminToken.value, {
      providerKey: modelForm.providerKey,
      modelKey: modelForm.modelKey,
      type: modelForm.type,
      credentials: modelCredentials()
    })
    message.success('Model 已保存')
    showModelModal.value = false
    await reload()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '保存 Model 失败')
  } finally {
    savingModel.value = false
  }
}

async function handleDeleteProvider(providerKey: string) {
  dialog.warning({
    title: '删除确认',
    content: `确认删除 Provider「${providerKey}」？`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await deleteProvider(adminToken.value, providerKey)
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
    <PageHeader title="模型管理" description="配置模型 Provider 与可用模型，模型凭证将加密存储。">
      <template #actions>
        <n-button size="small" secondary @click="openProviderModal">
          <template #icon><n-icon :component="Plus" /></template>
          新建 Provider
        </n-button>
        <n-button size="small" type="primary" @click="openModelModal">
          <template #icon><n-icon :component="Plus" /></template>
          新建 Model
        </n-button>
      </template>
    </PageHeader>

    <n-card title="Provider 列表" :bordered="true">
      <n-data-table :loading="loading" :columns="providerColumns" :data="providers" :bordered="false" />
    </n-card>

    <n-card title="Model 列表" :bordered="true">
      <n-data-table :loading="loading" :columns="modelColumns" :data="models" :bordered="false" />
    </n-card>

    <n-modal
      v-model:show="showProviderModal"
      preset="card"
      title="新建 Provider"
      style="width: 460px"
      :bordered="false"
    >
      <n-form label-placement="top">
        <n-form-item label="Provider Key">
          <n-input v-model:value="providerForm.providerKey" placeholder="如 openai" />
        </n-form-item>
        <n-form-item label="名称">
          <n-input v-model:value="providerForm.name" placeholder="如 OpenAI" />
        </n-form-item>
        <n-form-item label="Provider Type">
          <n-select v-model:value="providerForm.providerType" :options="providerTypeOptions" />
        </n-form-item>
        <n-form-item label="启用">
          <n-switch v-model:value="providerForm.enabled" />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showProviderModal = false">取消</n-button>
          <n-button type="primary" :loading="savingProvider" @click="handleCreateProvider">保存</n-button>
        </n-space>
      </template>
    </n-modal>

    <n-modal
      v-model:show="showModelModal"
      preset="card"
      title="新建 Model"
      style="width: 500px"
      :bordered="false"
    >
      <n-form label-placement="top">
        <n-form-item label="Provider">
          <n-select v-model:value="modelForm.providerKey" filterable :options="providerOptions()" />
        </n-form-item>
        <n-form-item label="Model Key">
          <n-input v-model:value="modelForm.modelKey" placeholder="如 gpt-4o-mini" />
        </n-form-item>
        <n-form-item label="Model Type">
          <n-select v-model:value="modelForm.type" :options="modelTypeOptions" />
        </n-form-item>
        <n-form-item label="API Key">
          <n-input v-model:value="modelForm.apiKey" type="password" show-password-on="click" placeholder="sk-..." />
        </n-form-item>
        <n-form-item label="Base URL（可选，OpenAI Compatible）">
          <n-input v-model:value="modelForm.baseUrl" placeholder="https://api.openai.com/v1" />
        </n-form-item>
        <n-form-item label="Temperature">
          <n-input-number v-model:value="modelForm.temperature" :min="0" :max="2" :step="0.1" style="width: 100%" />
        </n-form-item>
        <n-form-item label="Max Tokens">
          <n-input-number v-model:value="modelForm.maxTokens" :min="1" :max="32768" style="width: 100%" />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showModelModal = false">取消</n-button>
          <n-button type="primary" :loading="savingModel" @click="handleCreateModel">保存</n-button>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>
