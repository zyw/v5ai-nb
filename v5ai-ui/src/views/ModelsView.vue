<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
import { NAlert, NButton, NCard, NDataTable, NForm, NFormItem, NInput, NSelect, NSpace, NSwitch, NTag, useMessage, type DataTableColumns } from 'naive-ui'
import { createModel, createProvider, deleteModel, deleteProvider, listModels, listProviders, testModelConnection, type ModelResponse, type ProviderResponse } from '../api/client'
import { adminToken, isLoggedIn } from '../stores/session'

const message = useMessage()
const loading = ref(false)
const providers = ref<ProviderResponse[]>([])
const models = ref<ModelResponse[]>([])
const testingModelId = ref<number | null>(null)

const providerForm = reactive({
  providerKey: 'openai',
  name: 'OpenAI',
  providerType: 'OPENAI_COMPATIBLE',
  enabled: true
})

const modelForm = reactive({
  providerKey: 'openai',
  modelKey: 'gpt-4o-mini',
  type: 'CHAT',
  apiKey: '',
  baseUrl: 'https://api.openai.com/v1',
  temperature: 0.7,
  maxTokens: 1024
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

function modelCredentials(): string {
  return JSON.stringify({
    apiKey: modelForm.apiKey,
    baseUrl: modelForm.baseUrl,
    temperature: modelForm.temperature,
    maxTokens: modelForm.maxTokens
  }, null, 2)
}

const providerColumns: DataTableColumns<ProviderResponse> = [
  { title: 'ID', key: 'id', width: 60 },
  { title: 'Provider Key', key: 'providerKey' },
  { title: '名称', key: 'name' },
  { title: '类型', key: 'providerType' },
  { title: '启用', key: 'enabled', render: row => row.enabled ? '是' : '否' },
  {
    title: '操作',
    key: 'actions',
    width: 90,
    render: row => h(NButton, {
      size: 'small',
      type: 'error',
      secondary: true,
      disabled: !isLoggedIn.value,
      onClick: () => handleDeleteProvider(row.providerKey)
    }, { default: () => '删除' })
  }
]

const modelColumns: DataTableColumns<ModelResponse> = [
  { title: 'ID', key: 'id', width: 60 },
  { title: 'Provider', key: 'providerKey' },
  { title: 'Model Key', key: 'modelKey' },
  {
    title: '类型',
    key: 'type',
    render: row => h(NTag, { size: 'small', type: row.type === 'CHAT' ? 'info' : 'default' }, { default: () => row.type })
  },
  { title: '启用', key: 'enabled', render: row => row.enabled ? '是' : '否' },
  {
    title: '连通性',
    key: 'test',
    width: 130,
    render: row => h(NButton, {
      size: 'small',
      secondary: true,
      loading: testingModelId.value === row.id,
      disabled: !isLoggedIn.value,
      onClick: () => handleTestModel(row.id)
    }, { default: () => '测试连接' })
  },
  {
    title: '操作',
    key: 'actions',
    width: 90,
    render: row => h(NButton, {
      size: 'small',
      type: 'error',
      secondary: true,
      disabled: !isLoggedIn.value,
      onClick: () => handleDeleteModel(row.id)
    }, { default: () => '删除' })
  }
]

async function reload() {
  if (!isLoggedIn.value) return
  loading.value = true
  try {
    const [providerRows, modelRows] = await Promise.all([
      listProviders(adminToken.value),
      listModels(adminToken.value)
    ])
    providers.value = providerRows
    models.value = modelRows
  } catch (error) {
    message.error(error instanceof Error ? error.message : '加载模型配置失败')
  } finally {
    loading.value = false
  }
}

async function handleCreateProvider() {
  try {
    await createProvider(adminToken.value, providerForm)
    message.success('Provider 已保存')
    await reload()
  } catch (error) {
    message.error(error instanceof Error ? error.message : '保存 Provider 失败')
  }
}

async function handleDeleteProvider(providerKey: string) {
  try {
    await deleteProvider(adminToken.value, providerKey)
    message.success('Provider 已删除')
    await reload()
  } catch (error) {
    message.error(error instanceof Error ? error.message : '删除 Provider 失败')
  }
}

async function handleCreateModel() {
  try {
    await createModel(adminToken.value, {
      providerKey: modelForm.providerKey,
      modelKey: modelForm.modelKey,
      type: modelForm.type,
      credentials: modelCredentials()
    })
    message.success('Model 已保存')
    await reload()
  } catch (error) {
    message.error(error instanceof Error ? error.message : '保存 Model 失败')
  }
}

async function handleDeleteModel(id: number) {
  try {
    await deleteModel(adminToken.value, id)
    message.success('Model 已删除')
    await reload()
  } catch (error) {
    message.error(error instanceof Error ? error.message : '删除 Model 失败')
  }
}

async function handleTestModel(id: number) {
  testingModelId.value = id
  try {
    const result = await testModelConnection(adminToken.value, id)
    if (result.ok) {
      message.success(`Model ${id} 连接正常`)
    } else {
      message.error(`Model ${id} 连接失败: ${result.message}`)
    }
  } catch (error) {
    message.error(error instanceof Error ? error.message : '连通性测试失败')
  } finally {
    testingModelId.value = null
  }
}

onMounted(reload)
</script>

<template>
  <div class="grid">
    <n-alert v-if="!isLoggedIn" class="wide" type="warning" title="需要管理员 Token">
      请先在 Dashboard 登录，再管理 Provider 和 Model。
    </n-alert>

    <n-card title="创建 Provider" class="panel">
      <n-form label-placement="top">
        <n-form-item label="Provider Key">
          <n-input v-model:value="providerForm.providerKey" />
        </n-form-item>
        <n-form-item label="名称">
          <n-input v-model:value="providerForm.name" />
        </n-form-item>
        <n-form-item label="Provider Type">
          <n-select v-model:value="providerForm.providerType" :options="providerTypeOptions" />
        </n-form-item>
        <n-form-item label="启用">
          <n-switch v-model:value="providerForm.enabled" />
        </n-form-item>
        <n-space>
          <n-button type="primary" :disabled="!isLoggedIn" @click="handleCreateProvider">保存 Provider</n-button>
          <n-button secondary :disabled="!isLoggedIn" :loading="loading" @click="reload">刷新</n-button>
        </n-space>
      </n-form>
    </n-card>

    <n-card title="创建 Model" class="panel">
      <n-form label-placement="top">
        <n-form-item label="Provider Key">
          <n-select
            v-model:value="modelForm.providerKey"
            filterable
            tag
            :options="providers.map(item => ({ label: item.providerKey, value: item.providerKey }))"
          />
        </n-form-item>
        <n-form-item label="Model Key">
          <n-input v-model:value="modelForm.modelKey" />
        </n-form-item>
        <n-form-item label="Model Type">
          <n-select v-model:value="modelForm.type" :options="modelTypeOptions" />
        </n-form-item>
        <n-form-item label="API Key">
          <n-input v-model:value="modelForm.apiKey" type="password" show-password-on="click" placeholder="sk-..." />
        </n-form-item>
        <n-form-item label="Base URL（仅 OpenAI Compatible）">
          <n-input v-model:value="modelForm.baseUrl" placeholder="https://api.openai.com/v1" />
        </n-form-item>
        <n-form-item label="Temperature">
          <n-input-number v-model:value="modelForm.temperature" :min="0" :max="2" :step="0.1" />
        </n-form-item>
        <n-form-item label="Max Tokens">
          <n-input-number v-model:value="modelForm.maxTokens" :min="1" :max="32768" />
        </n-form-item>
        <n-button type="primary" :disabled="!isLoggedIn" @click="handleCreateModel">保存 Model</n-button>
      </n-form>
    </n-card>

    <n-card title="Provider 列表" class="panel wide">
      <n-data-table :loading="loading" :columns="providerColumns" :data="providers" />
    </n-card>

    <n-card title="Model 列表" class="panel wide">
      <n-data-table :loading="loading" :columns="modelColumns" :data="models" />
    </n-card>
  </div>
</template>
