<script setup lang="ts">
/**
 * 智能体管理（列表页）：
 * - 「新建智能体」为三步向导弹框：准备（描述需求）→ 生成（AI 生成配置）→ 确认（可编辑）→ 创建并跳转编辑页。
 * - 创建/编辑的具体配置在独立页面 /agents/:agentKey（左配置 / 右预览调试），与知识库详情页一致的交互。
 */
import { computed, h, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  NAlert,
  NButton,
  NCard,
  NDataTable,
  NForm,
  NFormItem,
  NIcon,
  NInput,
  NInputNumber,
  NModal,
  NPagination,
  NSelect,
  NSpace,
  NStep,
  NSteps,
  NTag,
  useDialog,
  useMessage,
  type DataTableColumns
} from 'naive-ui'
import { Plus, RefreshCw, Search } from 'lucide-vue-next'
import PageHeader from '../components/PageHeader.vue'
import RowActions from '../components/RowActions.vue'
import StatusTag from '../components/StatusTag.vue'
import {
  createAgent,
  deleteAgent,
  disableAgent,
  getAppDailyUsage,
  getAppQuota,
  generateAgentConfig,
  listAgents,
  listKnowledgeBaseOptions,
  listMcpServerOptions,
  listModelOptions,
  listSkillOptions,
  publishAgent,
  updateAppQuota,
  type AgentResponse,
  type OptionResponse, loadImagePreview
} from '../api/client'
import { adminToken } from '../stores/session'
import {formatDateTime} from "../utils/dateUtils";

const router = useRouter()
const message = useMessage()
const dialog = useDialog()
const loading = ref(false)
const agents = ref<AgentResponse[]>([])
const pagination = reactive({ page: 1, pageSize: 10, itemCount: 0 })
const search = reactive({ keyword: '', status: null as string | null })
const statusOptions = [
  { label: '草稿', value: 'DRAFT' },
  { label: '已发布', value: 'PUBLISHED' },
  { label: '已禁用', value: 'DISABLED' }
]
const modelOptions = ref<OptionResponse[]>([])
const knowledgeBaseOptions = ref<OptionResponse[]>([])
const mcpServerOptions = ref<OptionResponse[]>([])
const skillOptions = ref<OptionResponse[]>([])

const showModal = ref(false)
const showPublishModal = ref(false)
const showQuotaModal = ref(false)
const saving = ref(false)
const savingQuota = ref(false)

// ---- 新建智能体向导：准备 → 生成 → 确认（创建成功后跳转独立编辑页） ----
type WizardStep = 'prepare' | 'generating' | 'confirm'
const wizardStep = ref<WizardStep>('prepare')
const wizardStepIndex = computed(() =>
  (['prepare', 'generating', 'confirm'] as WizardStep[]).indexOf(wizardStep.value)
)
const prepareForm = reactive({ description: '' })
/** 生成失败时停留在「生成」步骤展示错误，可取消或重新生成。 */
const generatingError = ref('')
/** 生成出的配置（确认阶段各字段均可编辑）。 */
const draft = reactive({
  agentKey: '',
  name: '',
  description: '',
  greeting: '',
  presetQuestions: [] as string[],
  systemPrompt: ''
})

const publishForm = reactive({ agentKey: '', description: '' })
const quotaForm = reactive({
  agentKey: '',
  dailyModelCalls: 0,
  dailyTokens: 0,
  ratePerMinute: 0
})
const dailyUsage = ref('')

/** Agent 图标预览映射（id -> 可直接用于 <img> 的地址）。 */
const agentIcons = reactive<Record<number, string>>({})

function revokeAgentIcons() {
  for (const url of Object.values(agentIcons)) {
    if (url.startsWith('blob:')) URL.revokeObjectURL(url)
  }
  for (const key of Object.keys(agentIcons)) delete agentIcons[Number(key)]
}

async function refreshAgentIcons(rows: AgentResponse[]) {
  revokeAgentIcons()
  await Promise.all(
      rows
          .filter((r) => r.avatar)
          .map(async (r) => {
            try {
              agentIcons[r.id] = await loadImagePreview(adminToken.value, r.avatar!)
            } catch {
              // 忽略加载失败的图标
            }
          })
  )
}

const columns: DataTableColumns<AgentResponse> = [
  { title: 'ID', key: 'id', width: 54 },
  { title: '名称(key)', key: 'name', width: 'auto',
    render: (row) =>
        h('div', { style: 'display: flex;align-items: flex-start;gap: 8px;text-overflow: ellipsis;overflow: hidden;white-space: nowrap;' }, [
          agentIcons[row.id]
              ? h('img', { src: agentIcons[row.id], alt: row.name, style: 'width:20px;height:20px;border-radius:4px;object-fit:cover' })
              : null,
          h(NButton, {
            size: 'small',
            type: 'primary',
            text: true,
            title: `${row.name}(${row.agentKey})`,
            onClick: () => openEdit(row)
          }, { default: () => (`${row.name}(${row.agentKey})`) })
        ])},
  { title: '描述', key: 'description', width: 'auto', ellipsis: { tooltip: true } },
  {
    title: '状态',
    key: 'status',
    width: 90,
    render: (row) => h(StatusTag, { status: row.status })
  },
  {
    title: '模型名称',
    key: 'modelId',
    width: 180,
    ellipsis: { tooltip: true },
    render: (row) => row.modelName ?? `#${row.modelId}`
  },
  {
    title: '发布版本',
    key: 'publishedVersion',
    width: 90,
    render: (row) => row.publishedVersion ?? '-'
  },
  { title: '更新时间', key: 'updatedAt', width: 182, render: (row) => fmtTime(row.updatedAt) },
  {
    title: '操作',
    key: 'actions',
    width: 210,
    render: (row) =>
      h(RowActions, {
        maxInline: 2,
        actions: [
          { key: 'publish', label: '发布', type: 'primary', onClick: () => openPublish(row) },
          { key: 'quota', label: '配额', secondary: true, type: 'info', onClick: () => openQuota(row) },
          { key: 'disable', label: '禁用', secondary: true, type: 'error', onClick: () => handleDisable(row) },
          { key: 'delete', label: '删除', secondary: true, type: 'error', onClick: () => handleDelete(row) }
        ]
      })
  }
]

/** 表格横向滚动：列宽总和（auto 列按 240px 估算）。描述列带 ellipsis 会切到 table-layout: fixed，必须显式给 scroll-x，否则窄屏下溢出到页面级横向滚动 */
const tableScrollX = computed(() =>
  columns.reduce((sum, col) => sum + (typeof col.width === 'number' ? col.width : 240), 0)
)

async function reload() {
  loading.value = true
  try {
    const agentRows = await listAgents(adminToken.value, {
      pageNum: pagination.page,
      pageSize: pagination.pageSize,
      keyword: search.keyword.trim() || undefined,
      status: search.status ?? undefined
    })
    agents.value = agentRows.rows
    pagination.itemCount = agentRows.total
    void refreshAgentIcons(agentRows.rows)
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载数据失败')
  } finally {
    loading.value = false
  }
}

function fmtTime(value?: string | null): string {
  return value ? (formatDateTime(value) ?? '—') : '—'
}

function handlePageChange(page: number) {
  pagination.page = page
  void reload()
}

function handlePageSizeChange(pageSize: number) {
  pagination.pageSize = pageSize
  pagination.page = 1
  void reload()
}

function handleSearch() {
  pagination.page = 1
  void reload()
}

// ---- 下拉选项按需加载（打开创建/绑定弹窗时才拉取，Promise 缓存去重） ----
let optionsPromise: Promise<void> | null = null

async function ensureOptions() {
  if (!optionsPromise) {
    optionsPromise = Promise.all([
      listModelOptions(adminToken.value, 'CHAT').then((rows) => (modelOptions.value = rows)),
      listKnowledgeBaseOptions(adminToken.value).then((rows) => (knowledgeBaseOptions.value = rows)),
      listMcpServerOptions(adminToken.value).then((rows) => (mcpServerOptions.value = rows)),
      listSkillOptions(adminToken.value).then((rows) => (skillOptions.value = rows))
    ]).then(
      () => undefined,
      (e) => {
        optionsPromise = null // 加载失败后允许下次重试
        throw e
      }
    )
  }
  await optionsPromise
}

/** 生成与创建都使用默认对话模型：优先 isDefault，其次第一条。 */
function resolveDefaultChatModelId(): number | null {
  return modelOptions.value.find((option) => option.isDefault)?.value ?? modelOptions.value[0]?.value ?? null
}

/** 由名称派生 agentKey（名称可能含中文，派生结果为空时留空由用户填写）。 */
function slugify(name: string): string {
  return name.trim().toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '')
}

// ---- 新建向导 ----
async function openCreate() {
  generatingError.value = ''
  wizardStep.value = 'prepare'
  prepareForm.description = ''
  Object.assign(draft, {
    agentKey: '',
    name: '',
    description: '',
    greeting: '',
    presetQuestions: [],
    systemPrompt: ''
  })
  try {
    await ensureOptions()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载下拉选项失败')
  }
  if (!modelOptions.value.length) {
    message.warning('尚未配置可用的对话模型，请先在「模型管理」中配置模型')
  }
  showModal.value = true
}

async function handleGenerate() {
  const modelId = resolveDefaultChatModelId()
  if (modelId == null) {
    message.warning('尚未配置可用的对话模型，请先在「模型管理」中配置模型')
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
      modelId,
      description: prepareForm.description
    })
    draft.name = generated.name ?? ''
    draft.description = generated.description ?? ''
    draft.greeting = generated.greeting ?? ''
    draft.presetQuestions = generated.presetQuestions ?? []
    draft.systemPrompt = generated.systemPrompt ?? ''
    if (!draft.agentKey) {
      draft.agentKey = slugify(draft.name)
    }
    wizardStep.value = 'confirm'
  } catch (e) {
    generatingError.value = e instanceof Error ? e.message : '生成失败'
  }
}

function addDraftQuestion() {
  draft.presetQuestions.push('')
}

function removeDraftQuestion(index: number) {
  draft.presetQuestions.splice(index, 1)
}

/** 确认阶段 → 创建智能体，成功后进入独立编辑页。 */
async function handleConfirmCreate() {
  if (!draft.agentKey.trim()) {
    message.warning('请填写 Agent Key')
    return
  }
  if (!draft.name.trim()) {
    message.warning('请填写名称')
    return
  }
  const modelId = resolveDefaultChatModelId()
  if (modelId == null) {
    message.warning('尚未配置可用的对话模型，请先在「模型管理」中配置模型')
    return
  }
  saving.value = true
  try {
    const created = await createAgent(adminToken.value, {
      agentKey: draft.agentKey.trim(),
      name: draft.name.trim(),
      description: draft.description || undefined,
      systemPrompt: draft.systemPrompt || undefined,
      greeting: draft.greeting || undefined,
      presetQuestions: JSON.stringify(draft.presetQuestions) || undefined,
      modelId
    })
    message.success('智能体已创建')
    showModal.value = false
    await reload()
    void router.push({ name: 'agent-edit', params: { agentKey: created?.agentKey ?? draft.agentKey.trim() } })
  } catch (e) {
    message.error(e instanceof Error ? e.message : '创建失败')
  } finally {
    saving.value = false
  }
}

/** 确认阶段返回：回到「生成」，保留原描述以便重新生成。 */
function backToGenerate() {
  generatingError.value = ''
  wizardStep.value = 'generating'
  void handleGenerate()
}

/** 确认阶段返回准备步骤（保留原描述）。 */
function backToPrepare() {
  generatingError.value = ''
  wizardStep.value = 'prepare'
}

// ---- 编辑：跳转独立编辑页 ----
function openEdit(row: AgentResponse) {
  void router.push({ name: 'agent-edit', params: { agentKey: row.agentKey } })
}

async function openPublish(row: AgentResponse) {
  publishForm.agentKey = row.agentKey
  publishForm.description = ''
  showPublishModal.value = true
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

async function openQuota(row: AgentResponse) {
  quotaForm.agentKey = row.agentKey
  quotaForm.dailyModelCalls = 0
  quotaForm.dailyTokens = 0
  quotaForm.ratePerMinute = 0
  dailyUsage.value = ''
  showQuotaModal.value = true
  await reloadQuota()
}

async function reloadQuota() {
  if (!quotaForm.agentKey) return
  try {
    const [quota, usageToday] = await Promise.all([
      getAppQuota(adminToken.value, quotaForm.agentKey),
      getAppDailyUsage(adminToken.value, quotaForm.agentKey)
    ])
    quotaForm.dailyModelCalls = quota.dailyModelCalls
    quotaForm.dailyTokens = quota.dailyTokens
    quotaForm.ratePerMinute = quota.ratePerMinute
    dailyUsage.value = `今日调用 ${usageToday.modelCalls} 次 / ${usageToday.tokens} tokens`
  } catch {
    dailyUsage.value = '今日暂无用量'
  }
}

async function handleSaveQuota() {
  savingQuota.value = true
  try {
    await updateAppQuota(adminToken.value, quotaForm.agentKey, {
      dailyModelCalls: quotaForm.dailyModelCalls,
      dailyTokens: quotaForm.dailyTokens,
      ratePerMinute: quotaForm.ratePerMinute
    })
    message.success('配额已更新（0 = 不限）')
    showQuotaModal.value = false
  } catch (e) {
    message.error(e instanceof Error ? e.message : '更新配额失败')
  } finally {
    savingQuota.value = false
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
    content: `确认删除 Agent「${row.agentKey}」？将级联删除其版本、绑定、仅绑定该 Agent 的 API Key、会话、运行记录与用量数据，且不可恢复。`,
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
      <n-space style="margin-bottom: 12px" :size="8" align="center">
        <n-input v-model:value="search.keyword" placeholder="名称 / Key" clearable style="width: 220px" @keyup.enter="handleSearch" />
        <n-select v-model:value="search.status" placeholder="状态" clearable style="width: 130px" :options="statusOptions" />
        <n-button size="small" type="primary" secondary @click="handleSearch">
          <template #icon><n-icon :component="Search" /></template>
          查询
        </n-button>
        <n-button size="small" quaternary @click="handleSearch">
          <template #icon><n-icon :component="RefreshCw" /></template>
          刷新
        </n-button>
      </n-space>

      <n-data-table :loading="loading" :columns="columns" :data="agents" :bordered="false" :scroll-x="tableScrollX" />
      <n-pagination
        :page="pagination.page"
        :page-size="pagination.pageSize"
        :item-count="pagination.itemCount"
        :page-sizes="[10, 20, 50, 100]"
        show-size-picker
        style="justify-content: flex-end; margin-top: 12px"
        @update:page="handlePageChange"
        @update:page-size="handlePageSizeChange"
      />
    </n-card>

    <!-- 新建向导：准备 → 生成 → 确认 -->
    <n-modal
      :show="showModal"
      preset="card"
      title="新建智能体"
      style="width: 860px"
      :bordered="false"
      :mask-closable="false"
      @update:show="(v: boolean) => (showModal = v)"
    >
      <n-steps :current="wizardStepIndex" style="margin-bottom: 20px">
        <n-step title="准备" description="描述你想要的智能体" />
        <n-step title="生成" description="AI 生成配置" />
        <n-step title="确认" description="确认创建" />
      </n-steps>

      <!-- 步骤一：准备 -->
      <div v-if="wizardStep === 'prepare'" class="wizard-panel">
        <n-form label-placement="top">
          <n-form-item label="描述你想要的智能体">
            <n-input
              v-model:value="prepareForm.description"
              type="textarea"
              :autosize="{ minRows: 5, maxRows: 10 }"
              placeholder="例如：帮我设计一个电商售后客服，能解答退换货、物流、发票问题，语气亲切专业"
            />
          </n-form-item>
        </n-form>
        <n-alert v-if="!modelOptions.length" type="warning" :bordered="false" style="margin-bottom: 12px">
          尚未配置可用的对话模型，请先在「模型管理」中配置并启用模型。创建将使用默认对话模型。
        </n-alert>
        <n-space justify="end">
          <n-button @click="showModal = false">取消</n-button>
          <n-button type="primary" :disabled="!modelOptions.length" @click="handleGenerate">生成</n-button>
        </n-space>
      </div>

      <!-- 步骤二：生成中 / 生成失败 -->
      <div v-else-if="wizardStep === 'generating'" class="wizard-panel">
        <div v-if="!generatingError" class="wizard-center">
          <n-spin size="large" description="AI 正在根据描述生成智能体配置…" />
        </div>
        <template v-else>
          <n-alert type="error" :bordered="false" title="生成失败" style="margin-bottom: 12px">
            {{ generatingError }}
          </n-alert>
          <n-space justify="end">
            <n-button @click="showModal = false">取消</n-button>
            <n-button type="primary" :loading="saving" @click="handleGenerate">重新生成</n-button>
          </n-space>
        </template>
        <n-space v-if="!generatingError" justify="end" style="margin-top: 16px">
          <n-button @click="showModal = false">取消</n-button>
        </n-space>
      </div>

      <!-- 步骤三：确认（各字段均可编辑） -->
      <div v-else class="wizard-panel">
        <n-form label-placement="top">
          <n-form-item label="Agent Key">
            <n-input v-model:value="draft.agentKey" placeholder="平台内唯一标识，默认由名称派生" />
          </n-form-item>
          <n-form-item label="名称">
            <n-input v-model:value="draft.name" />
          </n-form-item>
          <n-form-item label="描述">
            <n-input v-model:value="draft.description" type="textarea" :autosize="{ minRows: 3, maxRows: 8 }" />
          </n-form-item>
          <n-form-item label="欢迎语">
            <n-input v-model:value="draft.greeting" type="textarea" :autosize="{ minRows: 2, maxRows: 5 }" />
          </n-form-item>
          <n-form-item label="预设问题">
            <div class="question-list">
              <div v-for="(question, index) in draft.presetQuestions" :key="index" class="question-row">
                <n-input v-model:value="draft.presetQuestions[index]" size="small" />
                <n-button size="small" quaternary type="error" @click="removeDraftQuestion(index)">删除</n-button>
              </div>
              <div class="question-row">
                <n-button size="small" secondary @click="addDraftQuestion">添加预设问题</n-button>
              </div>
            </div>
          </n-form-item>
          <n-form-item label="系统指令">
            <n-input v-model:value="draft.systemPrompt" type="textarea" :autosize="{ minRows: 8, maxRows: 18 }" />
          </n-form-item>
        </n-form>
        <n-space justify="end">
          <n-button @click="showModal = false">取消</n-button>
          <n-button secondary @click="backToPrepare">返回修改描述</n-button>
          <n-button secondary @click="backToGenerate" :loading="saving">重新生成</n-button>
          <n-button type="primary" :loading="saving" @click="handleConfirmCreate">确认创建</n-button>
        </n-space>
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

    <n-modal
      :show="showQuotaModal"
      preset="card"
      title="Agent配额（0 = 不限）"
      style="width: 560px"
      :bordered="false"
      @update:show="(v: boolean) => (showQuotaModal = v)"
    >
      <n-space vertical :size="16">
        <n-alert type="info" :show-icon="false">
          Agent：{{ quotaForm.agentKey }}。各项设置为 0 时表示不限制。
        </n-alert>
        <n-form label-placement="left" label-width="120">
          <n-form-item label="每日调用次数">
            <n-input-number v-model:value="quotaForm.dailyModelCalls" :min="0" style="width: 100%" />
          </n-form-item>
          <n-form-item label="每日 Token">
            <n-input-number v-model:value="quotaForm.dailyTokens" :min="0" style="width: 100%" />
          </n-form-item>
          <n-form-item label="每分钟限流">
            <n-input-number v-model:value="quotaForm.ratePerMinute" :min="0" style="width: 100%" />
          </n-form-item>
        </n-form>
        <n-tag v-if="dailyUsage" type="info" size="small" :bordered="false">{{ dailyUsage }}</n-tag>
      </n-space>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showQuotaModal = false">取消</n-button>
          <n-button type="primary" :loading="savingQuota" @click="handleSaveQuota">保存配额</n-button>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>

<style scoped>
.wizard-panel {
  min-height: 260px;
  padding: 8px 4px;
}
.wizard-center {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 240px;
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
</style>
