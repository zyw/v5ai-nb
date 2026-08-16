<script setup lang="ts">
import { h, onMounted, reactive, ref } from 'vue'
import { NAlert, NButton, NCard, NDataTable, NDivider, NForm, NFormItem, NInput, NSelect, NSpace, NTag, useMessage, type DataTableColumns } from 'naive-ui'
import { bindKnowledgeBases, createApiKey, createAgent, disableAgent, getKnowledgeBindings, listAgents, listKnowledgeBases, listModels, publishAgent, type AgentResponse, type KnowledgeBaseResponse, type ModelResponse } from '../api/client'
import { adminToken, isLoggedIn } from '../stores/session'

const message = useMessage()
const loading = ref(false)
const agents = ref<AgentResponse[]>([])
const models = ref<ModelResponse[]>([])
const knowledgeBases = ref<KnowledgeBaseResponse[]>([])
const bindings = ref<number[]>([])

const form = reactive({
  agentKey: 'demo',
  name: 'Demo Agent',
  description: '',
  systemPrompt: '',
  modelId: null as number | null
})

const publishDescription = ref('Phase 2 release')

const bindForm = reactive({
  agentKey: 'demo'
})

const columns: DataTableColumns<AgentResponse> = [
  { title: 'App Key', key: 'agentKey' },
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
      h(NButton, { size: 'small', type: 'primary', disabled: !isLoggedIn.value, onClick: () => handlePublish(row.agentKey) }, { default: () => '发布' }),
      h(NButton, { size: 'small', type: 'warning', secondary: true, disabled: !isLoggedIn.value, onClick: () => handleDisable(row.agentKey) }, { default: () => '禁用' }),
      h(NButton, { size: 'small', type: 'info', secondary: true, disabled: !isLoggedIn.value, onClick: () => handleCreateKey(row.agentKey) }, { default: () => '生成 Key' })
    ] })
  }
]

async function reload() {
  if (!isLoggedIn.value) return
  loading.value = true
  try {
    const [appRows, modelRows, kbRows] = await Promise.all([
      listAgents(adminToken.value),
      listModels(adminToken.value),
      listKnowledgeBases(adminToken.value)
    ])
    agents.value = appRows
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
    bindings.value = await getKnowledgeBindings(adminToken.value, bindForm.agentKey)
  } catch {
    bindings.value = []
  }
}

async function handleCreate() {
  try {
    await createAgent(adminToken.value, {
      agentKey: form.agentKey,
      name: form.name,
      description: form.description || undefined,
      systemPrompt: form.systemPrompt || undefined,
      modelId: form.modelId ?? 1
    })
    message.success('Agent 已创建')
    await reload()
  } catch (error) {
    message.error(error instanceof Error ? error.message : '创建 Agent 失败')
  }
}

async function handlePublish(agentKey: string) {
  try {
    await publishAgent(adminToken.value, agentKey, publishDescription.value)
    message.success(`${agentKey} 已发布`)
    await reload()
  } catch (error) {
    message.error(error instanceof Error ? error.message : '发布 Agent 失败')
  }
}

async function handleDisable(agentKey: string) {
  try {
    await disableAgent(adminToken.value, agentKey)
    message.success(`${agentKey} 已禁用`)
    await reload()
  } catch (error) {
    message.error(error instanceof Error ? error.message : '禁用 Agent 失败')
  }
}

async function handleCreateKey(agentKey: string) {
  try {
    const result = await createApiKey(adminToken.value, agentKey)
    message.success(`已生成 Key（仅此一次可见）: ${result.apiKey}`)
  } catch (error) {
    message.error(error instanceof Error ? error.message : '生成 Key 失败')
  }
}

async function handleBind() {
  try {
    await bindKnowledgeBases(adminToken.value, bindForm.agentKey, bindings.value)
    message.success(`已更新 ${bindForm.agentKey} 的知识库绑定`)
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
      请先在 Dashboard 登录，再创建或发布 Agent。
    </n-alert>

    <n-card title="创建 Agent" class="panel">
      <n-form label-placement="top">
        <n-form-item label="App Key">
          <n-input v-model:value="form.agentKey" />
        </n-form-item>
        <n-form-item label="名称">
          <n-input v-model:value="form.name" />
        </n-form-item>
        <n-form-item label="描述">
          <n-input v-model:value="form.description" type="textarea" :autosize="{ minRows: 2, maxRows: 4 }" />
        </n-form-item>
        <n-form-item label="系统提示词">
          <n-input
            v-model:value="form.systemPrompt"
            type="textarea"
            :autosize="{ minRows: 4, maxRows: 10 }"
            placeholder="可选：定义 Agent 的角色与行为；未配置时使用默认提示词"
          />
        </n-form-item>
        <n-form-item label="模型">
          <n-select
            v-model:value="form.modelId"
            filterable
            :options="models.map(item => ({ label: `${item.providerKey}/${item.modelKey} (${item.type})`, value: item.id }))"
            placeholder="选择已配置的模型"
          />
        </n-form-item>
        <n-button type="primary" :disabled="!isLoggedIn" @click="handleCreate">创建 Agent</n-button>
      </n-form>
    </n-card>

    <n-card title="发布与知识库绑定" class="panel">
      <n-form label-placement="top">
        <n-form-item label="发布说明">
          <n-input v-model:value="publishDescription" type="textarea" :autosize="{ minRows: 3, maxRows: 6 }" />
        </n-form-item>
        <n-divider style="margin: 8px 0" />
        <n-form-item label="绑定知识库（Agent App Key）">
          <n-select
            v-model:value="bindForm.agentKey"
            filterable
            tag
            :options="agents.map(item => ({ label: item.agentKey, value: item.agentKey }))"
            @update:value="reloadBindings"
          />
        </n-form-item>
        <n-form-item label="选择知识库">
          <n-select
            v-model:value="bindings"
            multiple
            clearable
            :options="knowledgeBases.map(item => ({ label: `${item.name} (#${item.id})`, value: item.id }))"
            placeholder="选择该 Agent 绑定的知识库"
          />
        </n-form-item>
        <n-button secondary type="info" :disabled="!isLoggedIn" @click="handleBind">保存知识库绑定</n-button>
      </n-form>
    </n-card>

    <n-card title="Agent 列表" class="panel wide">
      <n-space vertical>
        <n-button secondary :disabled="!isLoggedIn" :loading="loading" @click="reload">刷新列表</n-button>
        <n-data-table :loading="loading" :columns="columns" :data="agents" />
      </n-space>
    </n-card>
  </div>
</template>
