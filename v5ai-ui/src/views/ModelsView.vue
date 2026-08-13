<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { NAlert, NButton, NCard, NDataTable, NForm, NFormItem, NGrid, NGi, NInput, NSelect, NSpace, NSwitch, useMessage, type DataTableColumns } from 'naive-ui'
import { createModel, createProvider, listModels, listProviders, type ModelResponse, type ProviderResponse } from '../api/client'
import { adminToken, isLoggedIn } from '../stores/session'

const message = useMessage()
const loading = ref(false)
const providers = ref<ProviderResponse[]>([])
const models = ref<ModelResponse[]>([])

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
  credentials: JSON.stringify({ baseUrl: 'https://api.openai.com/v1', apiKey: 'sk-...' }, null, 2)
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

const providerColumns: DataTableColumns<ProviderResponse> = [
  { title: 'Provider Key', key: 'providerKey' },
  { title: '名称', key: 'name' },
  { title: '类型', key: 'providerType' },
  { title: '启用', key: 'enabled', render: row => row.enabled ? '是' : '否' }
]

const modelColumns: DataTableColumns<ModelResponse> = [
  { title: 'Provider Key', key: 'providerKey' },
  { title: 'Model Key', key: 'modelKey' },
  { title: '类型', key: 'type' },
  { title: '凭证密文', key: 'credentialsCiphertext', ellipsis: { tooltip: true } }
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

async function handleCreateModel() {
  try {
    await createModel(adminToken.value, modelForm)
    message.success('Model 已保存')
    await reload()
  } catch (error) {
    message.error(error instanceof Error ? error.message : '保存 Model 失败')
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
        <n-form-item label="Credentials JSON">
          <n-input v-model:value="modelForm.credentials" type="textarea" :autosize="{ minRows: 5, maxRows: 10 }" />
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
