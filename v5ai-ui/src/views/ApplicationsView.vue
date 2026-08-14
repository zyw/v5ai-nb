<script setup lang="ts">
import { h, onMounted, reactive, ref } from 'vue'
import { NAlert, NButton, NCard, NDataTable, NDivider, NForm, NFormItem, NInput, NSelect, NSpace, NTag, useMessage, type DataTableColumns } from 'naive-ui'
import { bindKnowledgeBases, createApiKey, createApplication, disableApplication, getKnowledgeBindings, listApplications, listKnowledgeBases, listModels, publishApplication, type ApplicationResponse, type KnowledgeBaseResponse, type ModelResponse } from '../api/client'
import { adminToken, isLoggedIn } from '../stores/session'

const message = useMessage()
const loading = ref(false)
const applications = ref<ApplicationResponse[]>([])
const models = ref<ModelResponse[]>([])
const knowledgeBases = ref<KnowledgeBaseResponse[]>([])
const bindings = ref<number[]>([])

const form = reactive({
  appKey: 'demo',
  name: 'Demo App',
  description: '',
  modelId: null as number | null
})

const publishDescription = ref('Phase 2 release')

const bindForm = reactive({
  appKey: 'demo'
})

const columns: DataTableColumns<ApplicationResponse> = [
  { title: 'App Key', key: 'appKey' },
  { title: '名称', key: 'name' },
  {
    title: '状态',
    key: 'status',
    render: row => h(NTag, { type: row.status === 'PUBLISHED' ? 'success' : row.status === 'DISABLED' ? 'default' : 'warning', size: 'small' }, { default: () => row.status })
  },
  { title: 'Model ID', key: 'modelId' },
  { title: '发布版本', key: 'publishedVersion', render: row => row.publishedVersion ?? '-' },
  {
    title: '操作',
    key: 'actions',
    render: row => h(NSpace, { size: 8 }, { default: () => [
      h(NButton, { size: 'small', type: 'primary', disabled: !isLoggedIn.value, onClick: () => handlePublish(row.appKey) }, { default: () => '发布' }),
      h(NButton, { size: 'small', type: 'warning', secondary: true, disabled: !isLoggedIn.value, onClick: () => handleDisable(row.appKey) }, { default: () => '禁用' }),
      h(NButton, { size: 'small', type: 'info', secondary: true, disabled: !isLoggedIn.value, onClick: () => handleCreateKey(row.appKey) }, { default: () => '生成 Key' })
    ] })
  }
]

async function reload() {
  if (!isLoggedIn.value) return
  loading.value = true
  try {
    const [appRows, modelRows, kbRows] = await Promise.all([
      listApplications(adminToken.value),
      listModels(adminToken.value),
      listKnowledgeBases(adminToken.value)
    ])
    applications.value = appRows
    models.value = modelRows
    knowledgeBases.value = kbRows
    await reloadBindings()
  } catch (error) {
    message.error(error instanceof Error ? error.message : '加载数据失败')
  } finally {
    loading.value = false
  }
}

async function reloadBindings() {
  try {
    bindings.value = await getKnowledgeBindings(adminToken.value, bindForm.appKey)
  } catch {
    bindings.value = []
  }
}

async function handleCreate() {
  try {
    await createApplication(adminToken.value, {
      appKey: form.appKey,
      name: form.name,
      description: form.description || undefined,
      modelId: form.modelId ?? 1
    })
    message.success('Application 已创建')
    await reload()
  } catch (error) {
    message.error(error instanceof Error ? error.message : '创建 Application 失败')
  }
}

async function handlePublish(appKey: string) {
  try {
    await publishApplication(adminToken.value, appKey, publishDescription.value)
    message.success(`${appKey} 已发布`)
    await reload()
  } catch (error) {
    message.error(error instanceof Error ? error.message : '发布 Application 失败')
  }
}

async function handleDisable(appKey: string) {
  try {
    await disableApplication(adminToken.value, appKey)
    message.success(`${appKey} 已禁用`)
    await reload()
  } catch (error) {
    message.error(error instanceof Error ? error.message : '禁用 Application 失败')
  }
}

async function handleCreateKey(appKey: string) {
  try {
    const result = await createApiKey(adminToken.value, appKey)
    message.success(`已生成 Key（仅此一次可见）: ${result.apiKey}`)
  } catch (error) {
    message.error(error instanceof Error ? error.message : '生成 Key 失败')
  }
}

async function handleBind() {
  try {
    await bindKnowledgeBases(adminToken.value, bindForm.appKey, bindings.value)
    message.success(`已更新 ${bindForm.appKey} 的知识库绑定`)
    await reloadBindings()
  } catch (error) {
    message.error(error instanceof Error ? error.message : '绑定知识库失败')
  }
}

onMounted(reload)
</script>

<template>
  <div class="grid">
    <n-alert v-if="!isLoggedIn" class="wide" type="warning" title="需要管理员 Token">
      请先在 Dashboard 登录，再创建或发布 Application。
    </n-alert>

    <n-card title="创建 Application" class="panel">
      <n-form label-placement="top">
        <n-form-item label="App Key">
          <n-input v-model:value="form.appKey" />
        </n-form-item>
        <n-form-item label="名称">
          <n-input v-model:value="form.name" />
        </n-form-item>
        <n-form-item label="描述">
          <n-input v-model:value="form.description" type="textarea" :autosize="{ minRows: 2, maxRows: 4 }" />
        </n-form-item>
        <n-form-item label="模型">
          <n-select
            v-model:value="form.modelId"
            filterable
            :options="models.map(item => ({ label: `${item.providerKey}/${item.modelKey} (${item.type})`, value: item.id }))"
            placeholder="选择已配置的模型"
          />
        </n-form-item>
        <n-button type="primary" :disabled="!isLoggedIn" @click="handleCreate">创建 Application</n-button>
      </n-form>
    </n-card>

    <n-card title="发布与知识库绑定" class="panel">
      <n-form label-placement="top">
        <n-form-item label="发布说明">
          <n-input v-model:value="publishDescription" type="textarea" :autosize="{ minRows: 3, maxRows: 6 }" />
        </n-form-item>
        <n-divider style="margin: 8px 0" />
        <n-form-item label="绑定知识库（Application App Key）">
          <n-select
            v-model:value="bindForm.appKey"
            filterable
            tag
            :options="applications.map(item => ({ label: item.appKey, value: item.appKey }))"
            @update:value="reloadBindings"
          />
        </n-form-item>
        <n-form-item label="选择知识库">
          <n-select
            v-model:value="bindings"
            multiple
            clearable
            :options="knowledgeBases.map(item => ({ label: `${item.name} (#${item.id})`, value: item.id }))"
            placeholder="选择该 Application 绑定的知识库"
          />
        </n-form-item>
        <n-button secondary type="info" :disabled="!isLoggedIn" @click="handleBind">保存知识库绑定</n-button>
      </n-form>
    </n-card>

    <n-card title="Application 列表" class="panel wide">
      <n-space vertical>
        <n-button secondary :disabled="!isLoggedIn" :loading="loading" @click="reload">刷新列表</n-button>
        <n-data-table :loading="loading" :columns="columns" :data="applications" />
      </n-space>
    </n-card>
  </div>
</template>
