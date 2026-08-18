<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
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
  NStep,
  NSteps,
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
  adminDebugStream,
  bindKnowledgeBases,
  bindMcpServers,
  bindSkills,
  createAgent,
  createApiKey,
  deleteAgent,
  disableAgent,
  generateAgentConfig,
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

const showModal = ref(false)
const showPublishModal = ref(false)
const showBindModal = ref(false)
const showKeyModal = ref(false)
const saving = ref(false)

// ---- 新建智能体向导：准备 → 生成 → 确认 → 完成 ----
const mode = ref<'create' | 'edit'>('create')
type WizardStep = 'prepare' | 'generating' | 'confirm' | 'done'
const wizardStep = ref<WizardStep>('prepare')
const wizardStepIndex = computed(() =>
  ['prepare', 'generating', 'confirm', 'done'].indexOf(wizardStep.value)
)
const prepareForm = reactive({
  modelId: null as number | null,
  description: ''
})
const generatingError = ref('')

// ---- 编辑表单（左栏 + 预览/调试右栏共用） ----
const form = reactive({
  agentKey: '',
  name: '',
  description: '',
  avatar: '',
  systemPrompt: '',
  greeting: '',
  presetQuestions: [] as string[],
  memoryEnabled: false,
  mcpEnabled: false,
  skillEnabled: false,
  webSearchEnabled: false,
  ragEnabled: false,
  modelId: null as number | null,
  skillIds: [] as number[],
  mcpServerIds: [] as number[],
  knowledgeBaseIds: [] as number[]
})
const newQuestion = ref('')
const draftAgentKey = ref('')

const publishForm = reactive({ agentKey: '', description: '' })
const bindForm = reactive({
  agentKey: '',
  knowledgeBaseIds: [] as number[],
  mcpServerIds: [] as number[],
  skillIds: [] as number[]
})
const generatedKey = ref('')

// ---- 调试区（聊天窗口） ----
interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
}
const debug = reactive({
  query: '',
  runId: '',
  loading: false
})
const chatMessages = ref<ChatMessage[]>([])

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
    width: 420,
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
            h(NButton, { size: 'small', secondary: true, type: 'error', onClick: () => handleDisable(row) }, { default: () => '禁用' }),
            h(NButton, { size: 'small', secondary: true, type: 'error', onClick: () => handleDelete(row) }, { default: () => '删除' })
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
    agents.value = agentRows.rows
    models.value = modelRows.rows
    knowledgeBases.value = kbRows.rows
    mcpServers.value = mcpRows.rows
    skills.value = skillRows.rows
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载数据失败')
  } finally {
    loading.value = false
  }
}

// ---- 表单初始化 ----
function resetForm() {
  form.agentKey = ''
  form.name = ''
  form.description = ''
  form.avatar = ''
  form.systemPrompt = ''
  form.greeting = ''
  form.presetQuestions = []
  form.memoryEnabled = false
  form.mcpEnabled = false
  form.skillEnabled = false
  form.webSearchEnabled = false
  form.ragEnabled = false
  form.modelId = models.value[0]?.id ?? null
  form.skillIds = []
  form.mcpServerIds = []
  form.knowledgeBaseIds = []
  newQuestion.value = ''
  draftAgentKey.value = ''
}

function resetDebug() {
  debug.query = ''
  debug.runId = ''
  debug.loading = false
  chatMessages.value = []
}

// ---- 新建向导 ----
function openCreate() {
  mode.value = 'create'
  resetForm()
  prepareForm.modelId = models.value[0]?.id ?? null
  prepareForm.description = ''
  generatingError.value = ''
  wizardStep.value = 'prepare'
  resetDebug()
  showModal.value = true
}

async function handleGenerate() {
  if (!prepareForm.modelId) {
    message.warning('请先选择模型')
    return
  }
  if (!prepareForm.description.trim()) {
    message.warning('请描述你想要的智能体')
    return
  }
  wizardStep.value = 'generating'
  generatingError.value = ''
  try {
    const generated = await generateAgentConfig(adminToken.value, {
      modelId: prepareForm.modelId,
      description: prepareForm.description
    })
    // 回填到编辑表单
    form.modelId = prepareForm.modelId
    form.name = generated.name ?? ''
    form.description = generated.description ?? ''
    form.greeting = generated.greeting ?? ''
    form.presetQuestions = generated.presetQuestions ?? []
    form.systemPrompt = generated.systemPrompt ?? ''
    if (!form.agentKey && form.name) {
      form.agentKey = form.name.trim().toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '') || ''
    }
    wizardStep.value = 'confirm'
  } catch (e) {
    generatingError.value = e instanceof Error ? e.message : '生成失败'
    wizardStep.value = 'prepare'
  }
}

// ---- 编辑 ----
async function openEdit(row: AgentResponse) {
  mode.value = 'edit'
  resetForm()
  form.agentKey = row.agentKey
  form.name = row.name
  form.description = row.description ?? ''
  form.systemPrompt = row.systemPrompt ?? ''
  form.avatar = row.avatar ?? ''
  form.greeting = row.greeting ?? ''
  form.presetQuestions = parseQuestions(row.presetQuestions)
  form.memoryEnabled = row.memoryEnabled ?? false
  form.mcpEnabled = row.mcpEnabled ?? false
  form.skillEnabled = row.skillEnabled ?? false
  form.webSearchEnabled = row.webSearchEnabled ?? false
  form.ragEnabled = row.ragEnabled ?? false
  form.modelId = row.modelId
  try {
    const [skill, mcp, kb] = await Promise.all([
      getSkillBindings(adminToken.value, row.agentKey),
      getMcpServerBindings(adminToken.value, row.agentKey),
      getKnowledgeBindings(adminToken.value, row.agentKey)
    ])
    form.skillIds = skill
    form.mcpServerIds = mcp
    form.knowledgeBaseIds = kb
  } catch {
    form.skillIds = []
    form.mcpServerIds = []
    form.knowledgeBaseIds = []
  }
  wizardStep.value = 'confirm'
  resetDebug()
  showModal.value = true
}

function parseQuestions(raw: string | null | undefined): string[] {
  if (!raw) return []
  try {
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) ? parsed.filter((q) => typeof q === 'string') : []
  } catch {
    return []
  }
}

function addQuestion() {
  const q = newQuestion.value.trim()
  if (!q) return
  form.presetQuestions.push(q)
  newQuestion.value = ''
}

function removeQuestion(index: number) {
  form.presetQuestions.splice(index, 1)
}

function usePresetQuestion(question: string) {
  debug.query = question
}

// ---- 保存 ----
async function handleSave() {
  if (!form.agentKey || !form.agentKey.trim()) {
    message.warning('请填写 App Key')
    return
  }
  if (!form.name || !form.name.trim()) {
    message.warning('请填写名称')
    return
  }
  saving.value = true
  try {
    const payload = {
      name: form.name,
      description: form.description || undefined,
      systemPrompt: form.systemPrompt || undefined,
      avatar: form.avatar || undefined,
      greeting: form.greeting || undefined,
      presetQuestions: JSON.stringify(form.presetQuestions) || undefined,
      memoryEnabled: form.memoryEnabled,
      mcpEnabled: form.mcpEnabled,
      skillEnabled: form.skillEnabled,
      webSearchEnabled: form.webSearchEnabled,
      ragEnabled: form.ragEnabled,
      modelId: form.modelId ?? 1
    }
    if (mode.value === 'create') {
      await createAgent(adminToken.value, { agentKey: form.agentKey, ...payload })
      await saveBindings(form.agentKey)
      message.success('Agent 已创建')
    } else {
      await updateAgent(adminToken.value, form.agentKey, { ...payload, modelId: form.modelId ?? undefined })
      await saveBindings(form.agentKey)
      message.success('Agent 已更新')
    }
    draftAgentKey.value = form.agentKey
    wizardStep.value = 'done'
    await reload()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    saving.value = false
  }
}

async function saveBindings(agentKey: string) {
  await Promise.all([
    bindSkills(adminToken.value, agentKey, form.skillIds),
    bindMcpServers(adminToken.value, agentKey, form.mcpServerIds),
    bindKnowledgeBases(adminToken.value, agentKey, form.knowledgeBaseIds)
  ])
}

function closeModal() {
  showModal.value = false
}

// ---- 调试 ----
function parseDelta(data: string): string {
  if (!data) return ''
  try {
    const body = JSON.parse(data) as { payload?: string; answer?: string; text?: string }
    return body.payload ?? body.answer ?? body.text ?? data
  } catch {
    return data
  }
}

async function handleDebug() {
  if (!form.agentKey) {
    message.warning('请先填写 App Key 并保存 Agent')
    return
  }
  const question = debug.query.trim()
  if (!question) return
  debug.query = ''
  chatMessages.value.push({ role: 'user', content: question })
  const assistantIndex = chatMessages.value.length
  chatMessages.value.push({ role: 'assistant', content: '' })
  debug.loading = true
  debug.runId = ''
  try {
    await adminDebugStream(form.agentKey, adminToken.value, { query: question }, (event, data) => {
      if (event === 'TEXT_DELTA') {
        chatMessages.value[assistantIndex].content += parseDelta(data)
      }
      if (event === 'RUN_FAILED') {
        chatMessages.value[assistantIndex].content = `调用失败：${parseRunFailed(data)}`
      }
      if (event === 'MESSAGE_COMPLETED' && !chatMessages.value[assistantIndex].content) {
        chatMessages.value[assistantIndex].content = parseDelta(data)
      }
      if (event === 'RUN_STARTED') {
        debug.runId = parseRunId(data)
      }
    })
  } catch (e) {
    chatMessages.value[assistantIndex].content = e instanceof Error ? `调用失败：${e.message}` : '调用失败'
  } finally {
    debug.loading = false
  }
}

function parseRunId(data: string): string {
  if (!data) return ''
  try {
    return (JSON.parse(data) as { runId?: string }).runId ?? ''
  } catch {
    return ''
  }
}

function parseRunFailed(data: string): string {
  if (!data) return '未知错误'
  try {
    return (JSON.parse(data) as { payload?: string }).payload || '未知错误'
  } catch {
    return data
  }
}

async function openPublish(row: AgentResponse) {
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

async function handleDelete(row: AgentResponse) {
  dialog.error({
    title: '删除确认',
    content: `确认删除 Agent「${row.agentKey}」？将级联删除其版本、绑定、API Key、会话、运行记录与用量数据，且不可恢复。`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await deleteAgent(adminToken.value, row.agentKey)
        message.success(`${row.agentKey} 已删除`)
        await reload()
      } catch (e) {
        message.error(e instanceof Error ? e.message : '删除 Agent 失败')
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

    <!-- 新建向导 / 编辑：左右分栏 -->
    <n-modal
      :show="showModal"
      preset="card"
      :title="mode === 'create' ? '新建 Agent' : `编辑 Agent：${form.agentKey}`"
      style="width: 1280px"
      :bordered="false"
      @update:show="(v: boolean) => {
        showModal = v
        if (!v) closeModal()
      }"
    >
      <!-- 向导步骤条（仅新建） -->
      <n-steps v-if="mode === 'create'" :current="wizardStepIndex" style="margin-bottom: 20px">
        <n-step title="准备" description="选择模型与描述" />
        <n-step title="生成" description="AI 生成配置" />
        <n-step title="确认" description="预览与调整" />
        <n-step title="完成" description="保存成功" />
      </n-steps>

      <!-- 步骤一：准备 -->
      <div v-if="mode === 'create' && wizardStep === 'prepare'" class="wizard-panel">
        <n-form label-placement="top">
          <n-form-item label="选择模型">
            <n-select v-model:value="prepareForm.modelId" filterable :options="modelOptions()" placeholder="选择用于生成的模型" />
          </n-form-item>
          <n-form-item label="描述你想要的智能体">
            <n-input
              v-model:value="prepareForm.description"
              type="textarea"
              :autosize="{ minRows: 4, maxRows: 8 }"
              placeholder="例如：帮我设计一个电商售后客服，能解答退换货、物流、发票问题，语气亲切专业"
            />
          </n-form-item>
        </n-form>
        <n-alert v-if="generatingError" type="error" :bordered="false" style="margin-bottom: 12px">
          {{ generatingError }}
        </n-alert>
        <n-space justify="end">
          <n-button @click="closeModal">取消</n-button>
          <n-button type="primary" :loading="saving" @click="handleGenerate">生成配置</n-button>
        </n-space>
      </div>

      <!-- 步骤二：生成中 -->
      <div v-else-if="mode === 'create' && wizardStep === 'generating'" class="wizard-panel center">
        <n-spin size="large" description="AI 正在根据描述生成智能体配置…" />
      </div>

      <!-- 步骤四：完成 -->
      <div v-else-if="mode === 'create' && wizardStep === 'done'" class="wizard-panel center">
        <n-result status="success" title="智能体创建完成" :description="`「${draftAgentKey}」已保存，可发布或继续管理。`">
          <template #footer>
            <n-space justify="center">
              <n-button type="primary" @click="closeModal">完成</n-button>
            </n-space>
          </template>
        </n-result>
      </div>

      <!-- 步骤三：确认（新建，仅表单）/ 编辑（左右分栏：表单 + 预览与调试） -->
      <div v-else class="editor-grid" :class="{ single: mode === 'create' }">
        <!-- 左栏：基本信息与配置 -->
        <n-card title="基本信息" size="small" :bordered="true" class="editor-left">
          <n-form label-placement="top" size="small">
            <n-form-item :label="mode === 'create' ? 'App Key' : 'App Key（不可修改）'">
              <n-input v-model:value="form.agentKey" :disabled="mode === 'edit'" placeholder="平台内唯一标识" />
            </n-form-item>
            <n-form-item label="名称">
              <n-input v-model:value="form.name" />
            </n-form-item>
            <n-form-item label="描述">
              <n-input v-model:value="form.description" type="textarea" :autosize="{ minRows: 2, maxRows: 4 }" />
            </n-form-item>
            <n-form-item label="头像 URL">
              <n-input v-model:value="form.avatar" placeholder="可选：Agent 头像图片地址" />
            </n-form-item>
            <n-form-item label="模型">
              <n-select v-model:value="form.modelId" filterable :options="modelOptions()" placeholder="选择已配置的模型" />
            </n-form-item>
            <n-form-item label="Skill">
              <n-select v-model:value="form.skillIds" multiple clearable :options="skillOptions()" placeholder="选择已发布的 Skill" />
            </n-form-item>
            <n-form-item label="MCP Server">
              <n-select v-model:value="form.mcpServerIds" multiple clearable :options="mcpOptions()" placeholder="选择已发现的 MCP Server" />
            </n-form-item>
            <n-form-item label="知识库">
              <n-select v-model:value="form.knowledgeBaseIds" multiple clearable :options="kbOptions()" placeholder="选择知识库（RAG）" />
            </n-form-item>
            <n-form-item label="系统提示词">
              <n-input v-model:value="form.systemPrompt" type="textarea" :autosize="{ minRows: 3, maxRows: 8 }" placeholder="定义 Agent 的角色与行为" />
            </n-form-item>
            <n-form-item label="欢迎语">
              <n-input v-model:value="form.greeting" type="textarea" :autosize="{ minRows: 2, maxRows: 4 }" placeholder="对话开始时展示的欢迎语" />
            </n-form-item>
            <n-form-item label="预设问题">
              <div class="question-list">
                <div v-for="(question, index) in form.presetQuestions" :key="index" class="question-row">
                  <n-input v-model:value="form.presetQuestions[index]" size="small" />
                  <n-button size="small" quaternary type="error" @click="removeQuestion(index)">删除</n-button>
                </div>
                <div class="question-row">
                  <n-input v-model:value="newQuestion" size="small" placeholder="输入新问题后回车或点击添加" @keyup.enter="addQuestion" />
                  <n-button size="small" secondary @click="addQuestion">添加</n-button>
                </div>
              </div>
            </n-form-item>
            <n-form-item label="能力开关">
              <n-space size="large" wrap>
                <n-space align="center" :size="8"><n-switch v-model:value="form.memoryEnabled" /><span>记忆库</span></n-space>
                <n-space align="center" :size="8"><n-switch v-model:value="form.mcpEnabled" /><span>MCP</span></n-space>
                <n-space align="center" :size="8"><n-switch v-model:value="form.skillEnabled" /><span>Skill</span></n-space>
                <n-space align="center" :size="8"><n-switch v-model:value="form.webSearchEnabled" /><span>联网搜索</span></n-space>
                <n-space align="center" :size="8"><n-switch v-model:value="form.ragEnabled" /><span>RAG</span></n-space>
              </n-space>
            </n-form-item>
          </n-form>
          <n-space justify="end">
            <n-button @click="closeModal">取消</n-button>
            <n-button type="primary" :loading="saving" @click="handleSave">
              {{ mode === 'create' ? '创建' : '保存' }}
            </n-button>
          </n-space>
        </n-card>

        <!-- 右栏：预览与调试（仅编辑模式显示） -->
        <div v-if="mode === 'edit'" class="editor-right">
          <n-card size="small" :bordered="true" class="debug-panel">
            <div class="debug-header">
              <div class="preview-name">{{ form.name || '未命名 Agent' }}</div>
              <div v-if="form.description" class="preview-desc">{{ form.description }}</div>
              <div v-else class="preview-desc muted">暂无描述</div>
            </div>
            <div v-if="form.presetQuestions.length" class="preset-questions">
              <n-tag
                v-for="(question, index) in form.presetQuestions"
                :key="index"
                size="small"
                :bordered="false"
                class="preset-tag"
                @click="usePresetQuestion(question)"
              >
                {{ question }}
              </n-tag>
            </div>
            <div class="chat-window">
              <div v-for="(msg, index) in chatMessages" :key="index" :class="['chat-row', msg.role]">
                <div class="chat-bubble">{{ msg.content || '…' }}</div>
              </div>
              <div v-if="chatMessages.length === 0" class="chat-empty">
                输入问题开始调试（未发布也可调试），回复将在这里显示。
              </div>
            </div>
            <div class="chat-input">
              <n-input
                v-model:value="debug.query"
                type="textarea"
                :autosize="{ minRows: 1, maxRows: 3 }"
                placeholder="输入内容调试 Agent…（Enter 发送）"
                @keydown.enter.prevent="handleDebug"
              />
              <n-button type="primary" :loading="debug.loading" @click="handleDebug">发送</n-button>
            </div>
            <div v-if="debug.runId" class="debug-runid">runId: {{ debug.runId }}</div>
          </n-card>
        </div>
      </div>
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

    <!-- 绑定（知识库等） -->
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

<style scoped>
.wizard-panel {
  min-height: 260px;
  padding: 8px 4px;
}
.wizard-panel.center {
  display: flex;
  align-items: center;
  justify-content: center;
}
.editor-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}
.editor-grid.single {
  grid-template-columns: 1fr;
}
.editor-grid.single .editor-left {
  max-height: none;
}
.editor-left {
  max-height: 640px;
  overflow-y: auto;
}
.editor-right {
  min-width: 0;
  display: flex;
}
.question-list {
  width: 100%;
}
.question-row {
  display: flex;
  gap: 6px;
  margin-bottom: 6px;
}
.question-row .n-input {
  flex: 1;
}
.debug-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
}
.debug-panel :deep(.n-card__content) {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.debug-header {
  padding-bottom: 8px;
  border-bottom: 1px solid var(--n-border-color);
}
.preset-questions {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  padding: 8px 0;
}
.preset-tag {
  cursor: pointer;
}
.preview-name {
  font-size: 18px;
  font-weight: 600;
}
.preview-desc {
  margin-top: 4px;
  color: var(--n-text-color-2);
  font-size: 13px;
  white-space: pre-wrap;
}
.preview-desc.muted {
  color: var(--n-text-color-3);
}
.chat-window {
  flex: 1;
  min-height: 200px;
  max-height: 360px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 8px;
  background: var(--n-color-2);
  border-radius: 6px;
}
.chat-empty {
  margin: auto;
  color: var(--n-text-color-3);
  font-size: 13px;
}
.chat-row {
  display: flex;
}
.chat-row.user {
  justify-content: flex-end;
}
.chat-row.assistant {
  justify-content: flex-start;
}
.chat-bubble {
  max-width: 85%;
  padding: 8px 10px;
  border-radius: 10px;
  font-size: 13px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-word;
}
.chat-row.user .chat-bubble {
  background: var(--n-primary-color);
  color: var(--n-primary-color-suppl);
  border-bottom-right-radius: 2px;
}
.chat-row.assistant .chat-bubble {
  background: var(--n-color-3);
  border-bottom-left-radius: 2px;
}
.chat-input {
  display: flex;
  gap: 8px;
  margin-top: 8px;
}
.chat-input .n-input {
  flex: 1;
}
.debug-runid {
  margin-top: 6px;
  font-size: 12px;
  color: var(--n-text-color-3);
}
</style>
