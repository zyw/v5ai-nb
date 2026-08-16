<script setup lang="ts">
import { h, onMounted, reactive, ref } from 'vue'
import {
  NAlert,
  NButton,
  NCard,
  NDataTable,
  NForm,
  NFormItem,
  NIcon,
  NInput,
  NModal,
  NSelect,
  NSpace,
  useDialog,
  useMessage,
  type DataTableColumns
} from 'naive-ui'
import { Plus } from 'lucide-vue-next'
import PageHeader from '../components/PageHeader.vue'
import StatusTag from '../components/StatusTag.vue'
import {
  bindKnowledgeBases,
  bindMcpServers,
  bindSkills,
  createAgent,
  createApiKey,
  disableAgent,
  getKnowledgeBindings,
  getMcpServerBindings,
  getSkillBindings,
  listAgents,
  listKnowledgeBases,
  listMcpServers,
  listModels,
  listSkills,
  publishAgent,
  updateAgent,
  type AgentResponse,
  type KnowledgeBaseResponse,
  type McpServerResponse,
  type ModelResponse,
  type SkillResponse
} from '../api/client'
import { adminToken } from '../stores/session'

const message = useMessage()
const dialog = useDialog()
const loading = ref(false)
const agents = ref<AgentResponse[]>([])
const models = ref<ModelResponse[]>([])
const knowledgeBases = ref<KnowledgeBaseResponse[]>([])
const mcpServers = ref<McpServerResponse[]>([])
const skills = ref<SkillResponse[]>([])

const showCreateModal = ref(false)
const showEditModal = ref(false)
const showPublishModal = ref(false)
const showBindModal = ref(false)
const showKeyModal = ref(false)
const saving = ref(false)

const form = reactive({
  agentKey: '',
  name: '',
  description: '',
  systemPrompt: '',
  modelId: null as number | null
})

const publishForm = reactive({ agentKey: '', description: '' })
const bindForm = reactive({
  agentKey: '',
  knowledgeBaseIds: [] as number[],
  mcpServerIds: [] as number[],
  skillIds: [] as number[]
})
const generatedKey = ref('')

const modelOptions = () =>
  models.value.map((m) => ({ label: `${m.providerKey}/${m.modelKey} (${m.type})`, value: m.id }))
const kbOptions = () => knowledgeBases.value.map((k) => ({ label: `${k.name} (#${k.id})`, value: k.id }))
const mcpOptions = () => mcpServers.value.map((m) => ({ label: `${m.name} (#${m.id})`, value: m.id }))
const skillOptions = () =>
  skills.value
    .filter((s) => s.status === 'ACTIVE' && s.currentVersionId !== null)
    .map((s) => ({ label: `${s.name} (v${s.currentVersion})`, value: s.id }))

const columns: DataTableColumns<AgentResponse> = [
  { title: 'App Key', key: 'agentKey' },
  { title: '名称', key: 'name' },
  {
    title: '状态',
    key: 'status',
    width: 100,
    render: (row) => h(StatusTag, { status: row.status })
  },
  { title: 'Model ID', key: 'modelId', width: 90 },
  {
    title: '发布版本',
    key: 'publishedVersion',
    width: 90,
    render: (row) => row.publishedVersion ?? '-'
  },
  {
    title: '操作',
    key: 'actions',
    width: 340,
    render: (row) =>
      h(
        NSpace,
        { size: 6 },
        {
          default: () => [
            h(NButton, { size: 'small', secondary: true, type: 'info', onClick: () => openEdit(row) }, { default: () => '编辑' }),
            h(NButton, { size: 'small', secondary: true, type: 'warning', onClick: () => openBind(row) }, { default: () => '绑定' }),
            h(NButton, { size: 'small', type: 'primary', onClick: () => openPublish(row) }, { default: () => '发布' }),
            h(NButton, { size: 'small', secondary: true, onClick: () => handleCreateKey(row.agentKey) }, { default: () => 'Key' }),
            h(NButton, { size: 'small', secondary: true, type: 'error', onClick: () => handleDisable(row) }, { default: () => '禁用' })
          ]
        }
      )
  }
]

async function reload() {
  loading.value = true
  try {
    const [agentRows, modelRows, kbRows, mcpRows, skillRows] = await Promise.all([
      listAgents(adminToken.value),
      listModels(adminToken.value),
      listKnowledgeBases(adminToken.value),
      listMcpServers(adminToken.value),
      listSkills(adminToken.value)
    ])
    agents.value = agentRows
    models.value = modelRows
    knowledgeBases.value = kbRows
    mcpServers.value = mcpRows
    skills.value = skillRows
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载数据失败')
  } finally {
    loading.value = false
  }
}

function resetForm() {
  form.agentKey = ''
  form.name = ''
  form.description = ''
  form.systemPrompt = ''
  form.modelId = models.value[0]?.id ?? null
}

function openCreate() {
  resetForm()
  showCreateModal.value = true
}

function openEdit(row: AgentResponse) {
  form.agentKey = row.agentKey
  form.name = row.name
  form.description = row.description ?? ''
  form.systemPrompt = row.systemPrompt ?? ''
  form.modelId = row.modelId
  showEditModal.value = true
}

function openPublish(row: AgentResponse) {
  publishForm.agentKey = row.agentKey
  publishForm.description = ''
  showPublishModal.value = true
}

async function openBind(row: AgentResponse) {
  bindForm.agentKey = row.agentKey
  try {
    const [kb, mcp, skill] = await Promise.all([
      getKnowledgeBindings(adminToken.value, row.agentKey),
      getMcpServerBindings(adminToken.value, row.agentKey),
      getSkillBindings(adminToken.value, row.agentKey)
    ])
    bindForm.knowledgeBaseIds = kb
    bindForm.mcpServerIds = mcp
    bindForm.skillIds = skill
  } catch {
    bindForm.knowledgeBaseIds = []
    bindForm.mcpServerIds = []
    bindForm.skillIds = []
  }
  showBindModal.value = true
}

async function handleCreate() {
  saving.value = true
  try {
    await createAgent(adminToken.value, {
      agentKey: form.agentKey,
      name: form.name,
      description: form.description || undefined,
      systemPrompt: form.systemPrompt || undefined,
      modelId: form.modelId ?? 1
    })
    message.success('Agent 已创建')
    showCreateModal.value = false
    await reload()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '创建 Agent 失败')
  } finally {
    saving.value = false
  }
}

async function handleUpdate() {
  saving.value = true
  try {
    await updateAgent(adminToken.value, form.agentKey, {
      name: form.name,
      description: form.description || undefined,
      modelId: form.modelId ?? undefined,
      systemPrompt: form.systemPrompt || undefined
    })
    message.success('Agent 已更新')
    showEditModal.value = false
    await reload()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '更新 Agent 失败')
  } finally {
    saving.value = false
  }
}

async function handlePublish() {
  saving.value = true
  try {
    await publishAgent(adminToken.value, publishForm.agentKey, publishForm.description)
    message.success(`${publishForm.agentKey} 已发布`)
    showPublishModal.value = false
    await reload()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '发布 Agent 失败')
  } finally {
    saving.value = false
  }
}

async function handleSaveBindings() {
  saving.value = true
  try {
    await Promise.all([
      bindKnowledgeBases(adminToken.value, bindForm.agentKey, bindForm.knowledgeBaseIds),
      bindMcpServers(adminToken.value, bindForm.agentKey, bindForm.mcpServerIds),
      bindSkills(adminToken.value, bindForm.agentKey, bindForm.skillIds)
    ])
    message.success('绑定已更新')
    showBindModal.value = false
  } catch (e) {
    message.error(e instanceof Error ? e.message : '保存绑定失败')
  } finally {
    saving.value = false
  }
}

async function handleCreateKey(agentKey: string) {
  try {
    const result = await createApiKey(adminToken.value, agentKey)
    generatedKey.value = result.apiKey
    showKeyModal.value = true
  } catch (e) {
    message.error(e instanceof Error ? e.message : '生成 Key 失败')
  }
}

async function handleDisable(row: AgentResponse) {
  dialog.warning({
    title: '禁用确认',
    content: `确认禁用 Agent「${row.agentKey}」？禁用后运行时将无法调用。`,
    positiveText: '禁用',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await disableAgent(adminToken.value, row.agentKey)
        message.success(`${row.agentKey} 已禁用`)
        await reload()
      } catch (e) {
        message.error(e instanceof Error ? e.message : '禁用 Agent 失败')
      }
    }
  })
}

onMounted(reload)
</script>

<template>
  <div class="page">
    <PageHeader title="Agent 管理" description="创建、发布 Agent，并绑定模型、知识库、MCP 与 Skill。">
      <template #actions>
        <n-button size="small" type="primary" @click="openCreate">
          <template #icon><n-icon :component="Plus" /></template>
          新建 Agent
        </n-button>
      </template>
    </PageHeader>

    <n-card :bordered="true">
      <n-data-table :loading="loading" :columns="columns" :data="agents" :bordered="false" />
    </n-card>

    <!-- 创建 / 编辑 -->
    <n-modal
      :show="showCreateModal"
      preset="card"
      title="新建 Agent"
      style="width: 560px"
      :bordered="false"
      @update:show="(v: boolean) => (showCreateModal = v)"
    >
      <n-form label-placement="top">
        <n-form-item label="App Key">
          <n-input v-model:value="form.agentKey" placeholder="平台内唯一标识" />
        </n-form-item>
        <n-form-item label="名称">
          <n-input v-model:value="form.name" />
        </n-form-item>
        <n-form-item label="描述">
          <n-input v-model:value="form.description" type="textarea" :autosize="{ minRows: 2, maxRows: 4 }" />
        </n-form-item>
        <n-form-item label="系统提示词">
          <n-input v-model:value="form.systemPrompt" type="textarea" :autosize="{ minRows: 3, maxRows: 8 }" placeholder="可选：定义 Agent 的角色与行为" />
        </n-form-item>
        <n-form-item label="模型">
          <n-select v-model:value="form.modelId" filterable :options="modelOptions()" placeholder="选择已配置的模型" />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showCreateModal = false">取消</n-button>
          <n-button type="primary" :loading="saving" @click="handleCreate">创建</n-button>
        </n-space>
      </template>
    </n-modal>

    <n-modal
      :show="showEditModal"
      preset="card"
      title="编辑 Agent"
      style="width: 560px"
      :bordered="false"
      @update:show="(v: boolean) => (showEditModal = v)"
    >
      <n-form label-placement="top">
        <n-form-item label="App Key">
          <n-input v-model:value="form.agentKey" disabled />
        </n-form-item>
        <n-form-item label="名称">
          <n-input v-model:value="form.name" />
        </n-form-item>
        <n-form-item label="描述">
          <n-input v-model:value="form.description" type="textarea" :autosize="{ minRows: 2, maxRows: 4 }" />
        </n-form-item>
        <n-form-item label="系统提示词">
          <n-input v-model:value="form.systemPrompt" type="textarea" :autosize="{ minRows: 3, maxRows: 8 }" />
        </n-form-item>
        <n-form-item label="模型">
          <n-select v-model:value="form.modelId" filterable :options="modelOptions()" />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showEditModal = false">取消</n-button>
          <n-button type="primary" :loading="saving" @click="handleUpdate">保存</n-button>
        </n-space>
      </template>
    </n-modal>

    <!-- 发布 -->
    <n-modal
      :show="showPublishModal"
      preset="card"
      title="发布 Agent"
      style="width: 460px"
      :bordered="false"
      @update:show="(v: boolean) => (showPublishModal = v)"
    >
      <n-form label-placement="top">
        <n-form-item label="App Key">
          <n-input v-model:value="publishForm.agentKey" disabled />
        </n-form-item>
        <n-form-item label="发布说明">
          <n-input v-model:value="publishForm.description" type="textarea" :autosize="{ minRows: 2, maxRows: 6 }" />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showPublishModal = false">取消</n-button>
          <n-button type="primary" :loading="saving" @click="handlePublish">发布</n-button>
        </n-space>
      </template>
    </n-modal>

    <!-- 绑定 -->
    <n-modal
      :show="showBindModal"
      preset="card"
      title="编辑绑定"
      style="width: 560px"
      :bordered="false"
      @update:show="(v: boolean) => (showBindModal = v)"
    >
      <n-alert type="info" :bordered="false" style="margin-bottom: 16px">
        为「{{ bindForm.agentKey }}」配置运行时可用的知识库、MCP Server 与 Skill。
      </n-alert>
      <n-form label-placement="top">
        <n-form-item label="知识库">
          <n-select v-model:value="bindForm.knowledgeBaseIds" multiple clearable :options="kbOptions()" />
        </n-form-item>
        <n-form-item label="MCP Server">
          <n-select v-model:value="bindForm.mcpServerIds" multiple clearable :options="mcpOptions()" />
        </n-form-item>
        <n-form-item label="Skill">
          <n-select v-model:value="bindForm.skillIds" multiple clearable :options="skillOptions()" />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showBindModal = false">取消</n-button>
          <n-button type="primary" :loading="saving" @click="handleSaveBindings">保存绑定</n-button>
        </n-space>
      </template>
    </n-modal>

    <!-- 生成 Key -->
    <n-modal
      :show="showKeyModal"
      preset="card"
      title="API Key"
      style="width: 520px"
      :bordered="false"
      @update:show="(v: boolean) => (showKeyModal = v)"
    >
      <n-alert type="warning" :bordered="false" style="margin-bottom: 12px">
        Key 仅此一次可见，请妥善保存。
      </n-alert>
      <n-input :value="generatedKey" readonly />
    </n-modal>
  </div>
</template>
