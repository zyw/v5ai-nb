<script setup lang="ts">
/**
 * 智能体编辑页：从「智能体管理」列表的编辑操作或新建向导创建成功后进入（站内子路由，保留平台框架，
 * 与知识库详情页一致）。左栏为基本信息与配置，右栏为预览与调试。
 */
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  NAlert,
  NButton,
  NCard,
  NForm,
  NFormItem,
  NIcon,
  NInput,
  NSkeleton,
  NSpace,
  NSwitch,
  NTag,
  NText, NTooltip,
  useMessage
} from 'naive-ui'
import {ArrowLeft, Bot, CircleQuestionMark, MessagesSquare, Plus, X} from 'lucide-vue-next'
import {
  getAgent,
  getKnowledgeBindings,
  getMcpServerBindings,
  getSkillBindings,
  listKnowledgeBases,
  listMcpServers,
  listModels,
  listSkills,
  loadImagePreview,
  updateAgent,
  bindKnowledgeBases,
  bindMcpServers,
  bindSkills,
  type AgentResponse,
  type ModelResponse,
  type ResourceOption
} from '../../api/client'
import { adminToken } from '../../stores/session'
import { formatDateTime } from '../../utils/dateUtils'
import StatusTag from '../../components/StatusTag.vue'
import PreviewPanel from './PreviewPanel.vue'
import IconPicker from '../../components/IconPicker.vue'
import ResourceIcon from '../../components/ResourceIcon.vue'
import ResourcePickerList from '../../components/ResourcePickerList.vue'

const route = useRoute()
const router = useRouter()
const message = useMessage()

const agentKey = computed(() => String(route.params.agentKey ?? ''))
const loading = ref(true)
const saving = ref(false)
const missing = ref(false)
const detail = ref<AgentResponse | null>(null)
const previewRef = ref<InstanceType<typeof PreviewPanel> | null>(null)
const newQuestion = ref('')
const avatarPreview = ref('')
const showAvatarPicker = ref(false)

const modelCatalog = ref<ResourceOption[]>([])
const skillCatalog = ref<ResourceOption[]>([])
const mcpCatalog = ref<ResourceOption[]>([])
const kbCatalog = ref<ResourceOption[]>([])

type PickerKind = 'model' | 'secondaryModel' | 'skill' | 'mcp' | 'rag'
const pickerOpen = ref(false)
const pickerKind = ref<PickerKind>('skill')
const pickerMultiple = ref(true)
const pickerTitle = ref('')
const pickerItems = ref<ResourceOption[]>([])
const pickerSelected = ref<number[]>([])

const form = reactive({
  name: '',
  description: '',
  avatar: '',
  greeting: '',
  systemPrompt: '',
  presetQuestions: [] as string[],
  modelId: null as number | null,
  /** 次要模型：供会话标题改写与会话摘要压缩调用；null = 留空回退对话模型（提交 null 即清除） */
  secondaryModelId: null as number | null,
  skillIds: [] as number[],
  mcpServerIds: [] as number[],
  knowledgeBaseIds: [] as number[],
  memoryEnabled: false,
  mcpEnabled: false,
  skillEnabled: false,
  webSearchEnabled: false,
  ragEnabled: false,
  ragCallMode: 2,
  showCitations: true
})

const selectedModel = computed(() => modelCatalog.value.find((m) => m.id === form.modelId) ?? null)
const selectedSecondaryModel = computed(
  () => modelCatalog.value.find((m) => m.id === form.secondaryModelId) ?? null
)
/**
 * 选择器卡片上的图标类别。
 *
 * pickerKind 既决定「选中后落到哪个表单字段」也决定图标；次要模型是另一个字段，但图标仍是模型，
 * 所以在这里收敛成图标侧的四类，避免让选择器组件多认一个与它无关的 kind。
 */
const pickerIconKind = computed<'model' | 'skill' | 'mcp' | 'rag'>(() =>
  pickerKind.value === 'secondaryModel' ? 'model' : pickerKind.value
)
const selectedSkills = computed(() =>
  form.skillIds.map((id) => skillCatalog.value.find((s) => s.id === id)).filter((s): s is ResourceOption => !!s)
)
const selectedMcps = computed(() =>
  form.mcpServerIds.map((id) => mcpCatalog.value.find((s) => s.id === id)).filter((s): s is ResourceOption => !!s)
)
const selectedKbs = computed(() =>
  form.knowledgeBaseIds.map((id) => kbCatalog.value.find((s) => s.id === id)).filter((s): s is ResourceOption => !!s)
)

/** MCP 传输类型展示标签：STREAMABLE_HTTP → HTTP，其余原样。 */
function mcpTransportLabel(t: string): string {
  return t === 'STREAMABLE_HTTP' ? 'HTTP' : t
}

function openPicker(kind: PickerKind, title: string, multiple: boolean, items: ResourceOption[], selected: number[]) {
  pickerKind.value = kind
  pickerTitle.value = title
  pickerMultiple.value = multiple
  pickerItems.value = items
  pickerSelected.value = selected
  pickerOpen.value = true
}

function openModelPicker() {
  openPicker('model', '选择对话模型', false, modelCatalog.value, form.modelId != null ? [form.modelId] : [])
}
function openSecondaryModelPicker() {
  openPicker('secondaryModel', '选择次要模型', false, modelCatalog.value,
    form.secondaryModelId != null ? [form.secondaryModelId] : [])
}
/**
 * 清除次要模型（回到「复用对话模型」）。
 *
 * 后端把「提交 null」当作清除（与其它字段的 null=不改不同），所以要能回到空值：
 * 只在选择器里换模型是回不去的，必须显式置空。
 */
function clearSecondaryModel() {
  form.secondaryModelId = null
}
function openSkillPicker() {
  openPicker('skill', '添加 Skill', true, skillCatalog.value, form.skillIds)
}
function openMcpPicker() {
  openPicker('mcp', '添加 MCP 服务', true, mcpCatalog.value, form.mcpServerIds)
}
function openKbPicker() {
  openPicker('rag', '添加知识库', true, kbCatalog.value, form.knowledgeBaseIds)
}

function onPickerSelect(id: number) {
  if (pickerKind.value === 'model') form.modelId = id
  else if (pickerKind.value === 'secondaryModel') form.secondaryModelId = id
}
function onPickerConfirm(ids: number[]) {
  if (pickerKind.value === 'skill') form.skillIds = ids
  else if (pickerKind.value === 'mcp') form.mcpServerIds = ids
  else if (pickerKind.value === 'rag') form.knowledgeBaseIds = ids
}

function removeSkill(id: number) {
  form.skillIds = form.skillIds.filter((x) => x !== id)
}
function removeMcp(id: number) {
  form.mcpServerIds = form.mcpServerIds.filter((x) => x !== id)
}
function removeKb(id: number) {
  form.knowledgeBaseIds = form.knowledgeBaseIds.filter((x) => x !== id)
}

// 没有绑定项时运行时开关必须保持关闭（前端开关也因此不可打开）
watch(selectedSkills, (list) => {
  if (!list.length) form.skillEnabled = false
})
watch(selectedMcps, (list) => {
  if (!list.length) form.mcpEnabled = false
})
watch(selectedKbs, (list) => {
  if (!list.length) form.ragEnabled = false
})

const updatedAtLabel = computed(() => formatDateTime(detail.value?.updatedAt) ?? '—')

// ---- 图片输入能力：取决于「模型管理」里当前所选模型有没有开启「支持图片输入」----
const agentModel = ref<ModelResponse | null>(null)
const imageSupported = computed(
  () => !!agentModel.value && modelImageSupported(agentModel.value.config)
)
/** 模型 config（ConfigExtAttrsDTO JSON）里 capabilities 含 image 即视为支持图片输入。 */
function modelImageSupported(config?: string | null): boolean {
  if (!config) return false
  try {
    const parsed = JSON.parse(config) as { capabilities?: unknown }
    return Array.isArray(parsed.capabilities) && (parsed.capabilities as string[]).includes('image')
  } catch {
    return false
  }
}

/** 按当前选的模型刷新能力标记（切换模型后按钮状态随之变化）。 */
let imageSupportSeq = 0
async function refreshImageSupport() {
  const modelId = form.modelId
  const seq = ++imageSupportSeq
  if (modelId == null) {
    agentModel.value = null
    return
  }
  try {
    const rows = await listModels(adminToken.value, { pageNum: 1, pageSize: 200 })
    if (seq !== imageSupportSeq) return // 已被更新的选择覆盖，丢弃本次结果
    agentModel.value = rows.rows.find((row) => row.id === modelId) ?? null
  } catch {
    if (seq === imageSupportSeq) agentModel.value = null
  }
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
  const question = newQuestion.value.trim()
  if (!question) return
  form.presetQuestions.push(question)
  newQuestion.value = ''
}

function removeQuestion(index: number) {
  form.presetQuestions.splice(index, 1)
}

async function refreshAvatarPreview() {
  if (avatarPreview.value.startsWith('blob:')) URL.revokeObjectURL(avatarPreview.value)
  avatarPreview.value = ''
  if (!form.avatar) return
  try {
    avatarPreview.value = await loadImagePreview(adminToken.value, form.avatar)
  } catch {
    avatarPreview.value = ''
  }
}

function openAvatarPicker() {
  showAvatarPicker.value = true
}

function handleAvatarPicked(value: string) {
  form.avatar = value
  void refreshAvatarPreview()
}

function clearAvatar() {
  form.avatar = ''
  void refreshAvatarPreview()
}

function applyAgent(row: AgentResponse) {
  detail.value = row
  form.name = row.name
  form.description = row.description ?? ''
  form.avatar = row.avatar ?? ''
  void refreshAvatarPreview()
  form.greeting = row.greeting ?? ''
  form.systemPrompt = row.systemPrompt ?? ''
  form.presetQuestions = parseQuestions(row.presetQuestions)
  form.modelId = row.modelId ?? null
  form.secondaryModelId = row.secondaryModelId ?? null
  form.memoryEnabled = row.memoryEnabled ?? false
  form.mcpEnabled = row.mcpEnabled ?? false
  form.skillEnabled = row.skillEnabled ?? false
  form.webSearchEnabled = row.webSearchEnabled ?? false
  form.ragEnabled = row.ragEnabled ?? false
  form.ragCallMode = row.ragCallMode ?? 2
  form.showCitations = row.showCitations ?? true
}

async function load() {
  if (!agentKey.value) {
    missing.value = true
    loading.value = false
    return
  }
  loading.value = true
  missing.value = false
  try {
    // 详情与四类资源列表并行拉取；绑定查询失败不阻断整页渲染。
    const [row, models, kbs, mcps, skills] = await Promise.all([
      getAgent(adminToken.value, agentKey.value),
      listModels(adminToken.value, { pageNum: 1, pageSize: 200, modelType: 'CHAT', enabled: true }),
      listKnowledgeBases(adminToken.value, { pageNum: 1, pageSize: 200 }),
      listMcpServers(adminToken.value, { pageNum: 1, pageSize: 200, status: 'ACTIVE' }),
      listSkills(adminToken.value, { pageNum: 1, pageSize: 200, status: 'ACTIVE' })
    ])
    modelCatalog.value = models.rows.map((m) => ({
      id: m.id,
      name: m.modelName ?? m.modelKey,
      description: m.description ?? null,
      icon: null,
      default: m.isDefault,
      meta: null
    }))
    kbCatalog.value = kbs.rows.map((k) => ({ id: k.id, name: k.name, description: k.description ?? null, icon: k.icon ?? null }))
    mcpCatalog.value = mcps.rows.map((s) => ({
      id: s.id,
      name: s.name,
      description: null,
      icon: null,
      meta: mcpTransportLabel(s.transportType)
    }))
    skillCatalog.value = skills.rows
      .filter((s) => s.currentVersionId != null)
      .map((s) => ({ id: s.id, name: s.name, description: s.description ?? null, icon: null }))
    applyAgent(row)
    if (form.modelId == null) {
      form.modelId = modelCatalog.value.find((m) => m.default)?.id ?? modelCatalog.value[0]?.id ?? null
    }
    await refreshImageSupport()
    const [skillIds, mcpIds, kbIds] = await Promise.all([
      getSkillBindings(adminToken.value, agentKey.value),
      getMcpServerBindings(adminToken.value, agentKey.value),
      getKnowledgeBindings(adminToken.value, agentKey.value)
    ])
    form.skillIds = skillIds
    form.mcpServerIds = mcpIds
    form.knowledgeBaseIds = kbIds
  } catch (e) {
    if (e instanceof Error && e.message.includes('不存在')) {
      missing.value = true
    } else {
      message.error(e instanceof Error ? e.message : '加载智能体失败')
    }
  } finally {
    loading.value = false
  }
}

async function save() {
  if (!form.name.trim()) {
    message.warning('请填写名称')
    return
  }
  if (form.modelId == null) {
    message.warning('请选择对话模型')
    return
  }
  saving.value = true
  try {
    // 更新接口对 null 字段是「保留原值」语义，所以这里始终显式提交全部字段。
    // 例外是 secondaryModelId：那里的 null 表示**清除**（回退对话模型），
    // 正因如此才必须「整表单提交」——漏传会被当成清除。
    await updateAgent(adminToken.value, agentKey.value, {
      name: form.name,
      description: form.description,
      systemPrompt: form.systemPrompt,
      avatar: form.avatar,
      greeting: form.greeting,
      presetQuestions: JSON.stringify(form.presetQuestions),
      memoryEnabled: form.memoryEnabled,
      mcpEnabled: form.mcpEnabled,
      skillEnabled: form.skillEnabled,
      webSearchEnabled: form.webSearchEnabled,
      ragEnabled: form.ragEnabled,
      ragCallMode: form.ragEnabled ? form.ragCallMode : 2,
      showCitations: form.showCitations,
      modelId: form.modelId,
      secondaryModelId: form.secondaryModelId
    })
    await Promise.all([
      bindSkills(adminToken.value, agentKey.value, form.skillIds),
      bindMcpServers(adminToken.value, agentKey.value, form.mcpServerIds),
      bindKnowledgeBases(adminToken.value, agentKey.value, form.knowledgeBaseIds)
    ])
    message.success('智能体已保存')
    await load()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    saving.value = false
  }
}

function goBack() {
  void router.push({ name: 'agents' })
}

// 用户换了模型，图片按钮的可用状态要跟着变
watch(() => form.modelId, () => {
  void refreshImageSupport()
})

watch(agentKey, () => {
  previewRef.value?.reset()
  newQuestion.value = ''
  void load()
})

onMounted(load)
</script>

<template>
  <div class="page agent-edit-page">
    <n-spin :show="loading">
      <div v-if="missing" class="detail-missing">
        <n-text depth="2">智能体不存在或已被删除</n-text>
        <n-button size="small" secondary style="margin-top: 10px" @click="goBack">返回智能体列表</n-button>
      </div>

      <template v-else>
        <div class="agent-head">
          <div class="agent-head-main">
            <n-button quaternary circle aria-label="返回智能体列表" @click="goBack">
              <template #icon><n-icon :component="ArrowLeft" /></template>
            </n-button>
            <n-icon :component="Bot" size="22" class="agent-icon" />
            <div class="agent-title-wrap">
              <div class="agent-title">{{ detail?.name || form.name || '智能体' }}</div>
              <div class="agent-meta">
                <span>{{ agentKey }}</span>
                <span class="sep">·</span>
                <span>更新时间 {{ updatedAtLabel }}</span>
              </div>
            </div>
          </div>
          <n-space :size="8" align="center">
            <StatusTag v-if="detail" :status="detail.status" />
            <n-tag :bordered="false" type="default">
              版本 {{ detail?.publishedVersion ?? '未发布' }}
            </n-tag>
            <n-button size="small" type="primary" :loading="saving" @click="save">保存</n-button>
          </n-space>
        </div>

        <n-skeleton v-if="loading && !detail" text :repeat="6" style="max-width: 600px" />

        <div v-else class="agent-edit-grid">
          <!-- 左栏：基本信息与配置 -->
          <div class="agent-edit-left">
            <n-card title="基本信息" size="small" :bordered="true">
              <n-form label-placement="top" size="small">
                <div class="basic-info-row">
                  <div class="basic-info-avatar">
                    <n-form-item class="avatar-item">
                      <div class="avatar-wrap">
                        <div class="avatar-picker" title="点击选择头像" @click="openAvatarPicker">
                          <img v-if="avatarPreview" :src="avatarPreview" alt="头像" class="avatar-picker-img" />
                          <span v-else class="avatar-picker-placeholder">选择头像</span>
                        </div>
                        <button
                          v-if="form.avatar"
                          type="button"
                          class="avatar-remove-badge"
                          title="移除头像"
                          aria-label="移除头像"
                          @click.stop="clearAvatar"
                        >
                          <n-icon :component="X" size="12" />
                        </button>
                      </div>
                      <div class="avatar-actions">
                        <span class="field-hint">资源/上传/URL</span>
                      </div>
                    </n-form-item>
                  </div>
                  <div class="basic-info-fields">
<!--                    <n-input :value="agentKey" type="hidden" disabled />-->
                    <n-form-item label="名称">
                      <n-input v-model:value="form.name" placeholder="智能体名称" />
                    </n-form-item>
                    <n-form-item label="描述">
                      <n-input
                        v-model:value="form.description"
                        type="textarea"
                        :autosize="{ minRows: 3, maxRows: 8 }"
                        placeholder="一句话说明这个智能体是做什么的"
                      />
                    </n-form-item>
                  </div>
                </div>
              </n-form>
            </n-card>

            <n-card title="配置信息" size="small" :bordered="true">
              <n-alert v-if="!modelCatalog.length" type="warning" :bordered="false" style="margin-bottom: 12px">
                尚未配置可用的对话模型，请先在「模型管理」中配置并启用模型。
              </n-alert>

              <!-- Chat 模型（单选） -->
              <div class="config-group">
                <div class="config-group-head">
                  <div class="config-group-title">对话模型</div>
                  <n-tag size="small" :bordered="false" type="info" style="margin-left: 8px">单选</n-tag>
                  <div class="config-group-actions">
                    <n-button size="tiny" secondary @click="openModelPicker">更换模型</n-button>
                  </div>
                </div>
                <div class="resource-list">
                  <div v-if="selectedModel" class="resource-item">
                    <ResourceIcon kind="model" :size="36" />
                    <div class="resource-item-main">
                      <div class="resource-item-name">{{ selectedModel.name }}</div>
                      <div v-if="selectedModel.description" class="resource-item-desc">{{ selectedModel.description }}</div>
                    </div>
                    <n-tag v-if="selectedModel.default" size="small" :bordered="false" type="success">默认</n-tag>
                  </div>
                  <n-empty v-else description="未选择对话模型" size="small" style="padding: 8px 0" />
                </div>
              </div>

              <!-- 次要模型（单选，可留空）：只服务平台内部调用，不参与对话回复 -->
              <div class="config-group">
                <div class="config-group-head">
                  <div class="config-group-title">
                    次要模型
                    <n-tooltip placement="top">
                      <template #trigger><n-icon size="13" class="q-icon"><CircleQuestionMark /></n-icon></template>
                      <span style="font-size: 12px;">
                        供两处平台内部调用使用：生成会话标题、压缩过长的历史会话。<br />
                        不参与对话回复。留空表示复用上面的对话模型。
                      </span>
                    </n-tooltip>
                  </div>
                  <n-tag size="small" :bordered="false" type="info" style="margin-left: 8px">可选</n-tag>
                  <div class="config-group-actions">
                    <n-button v-if="selectedSecondaryModel" size="tiny" tertiary @click="clearSecondaryModel">清除</n-button>
                    <n-button size="tiny" secondary @click="openSecondaryModelPicker">
                      {{ selectedSecondaryModel ? '更换模型' : '选择模型' }}
                    </n-button>
                  </div>
                </div>
                <div class="resource-list">
                  <div v-if="selectedSecondaryModel" class="resource-item">
                    <ResourceIcon kind="model" :size="36" />
                    <div class="resource-item-main">
                      <div class="resource-item-name">{{ selectedSecondaryModel.name }}</div>
                      <div v-if="selectedSecondaryModel.description" class="resource-item-desc">{{ selectedSecondaryModel.description }}</div>
                    </div>
                  </div>
                  <n-empty v-else description="未配置：复用对话模型" size="small" style="padding: 8px 0" />
                </div>
                <div class="field-hint" style="margin-top: 6px">
                  改动需重新发布后对门户生效（右侧调试面板即时生效）
                </div>
              </div>

              <!-- Skill -->
              <div class="config-group">
                <div class="config-group-head">
                  <div class="config-group-title">
                    Skill
                    <n-tooltip placement="top">
                      <template #trigger><n-icon size="13" class="q-icon"><CircleQuestionMark /></n-icon></template>
                      配置 SKILL，赋予智能体更多能力
                    </n-tooltip>
                  </div>
                  <div class="config-group-actions">
                    <n-switch v-model:value="form.skillEnabled" size="small" :disabled="!selectedSkills.length">
                      <template #checked>开</template>
                      <template #unchecked>关</template>
                    </n-switch>
                    <n-button size="tiny" secondary @click="openSkillPicker">+ 添加 Skill</n-button>
                  </div>
                </div>
                <div class="resource-list">
                  <div v-for="s in selectedSkills" :key="s.id" class="resource-item">
                    <ResourceIcon kind="skill" :icon="s.icon" :size="36" />
                    <div class="resource-item-main">
                      <div class="resource-item-name">{{ s.name }}</div>
                      <div v-if="s.description" class="resource-item-desc">{{ s.description }}</div>
                    </div>
                    <n-button size="tiny" quaternary type="error" @click="removeSkill(s.id)">移除</n-button>
                  </div>
                  <n-empty v-if="!selectedSkills.length" description="未添加 Skill" size="small" style="padding: 8px 0" />
                </div>
              </div>

              <!-- MCP -->
              <div class="config-group">
                <div class="config-group-head">
                  <div class="config-group-title">
                    MCP 服务
                    <n-tooltip placement="top">
                      <template #trigger><n-icon size="13" class="q-icon"><CircleQuestionMark /></n-icon></template>
                      配置 MCP 服务，赋予智能体更多能力
                    </n-tooltip>
                  </div>
                  <div class="config-group-actions">
                    <n-switch v-model:value="form.mcpEnabled" size="small" :disabled="!selectedMcps.length">
                      <template #checked>开</template>
                      <template #unchecked>关</template>
                    </n-switch>
                    <n-button size="tiny" secondary @click="openMcpPicker">+ 添加 MCP</n-button>
                  </div>
                </div>
                <div class="resource-list">
                  <div v-for="s in selectedMcps" :key="s.id" class="resource-item">
                    <ResourceIcon kind="mcp" :icon="s.icon" :size="36" />
                    <div class="resource-item-main">
                      <div class="resource-item-name">{{ s.name }}</div>
                    </div>
                    <n-tag v-if="s.meta" size="small" :bordered="false" type="info">{{ s.meta }}</n-tag>
                    <n-button size="tiny" quaternary type="error" @click="removeMcp(s.id)">移除</n-button>
                  </div>
                  <n-empty v-if="!selectedMcps.length" description="未添加 MCP 服务" size="small" style="padding: 8px 0" />
                </div>
              </div>

              <!-- RAG 知识库 -->
              <div class="config-group">
                <div class="config-group-head">
                  <div class="config-group-title">
                    RAG 知识库
                    <n-tooltip placement="top">
                      <template #trigger><n-icon size="13" class="q-icon"><CircleQuestionMark /></n-icon></template>
                      配置 RAG 知识库服务，赋予智能体更多能力
                    </n-tooltip>
                  </div>
                  <div class="config-group-actions">
                    <n-switch v-model:value="form.ragEnabled" size="small" :disabled="!selectedKbs.length">
                      <template #checked>开</template>
                      <template #unchecked>关</template>
                    </n-switch>
                    <n-button size="tiny" secondary @click="openKbPicker">+ 添加知识库</n-button>
                  </div>
                </div>
                <div v-if="!!selectedKbs.length" class="rag-call-mode-wrapper">
                  <span>
                    调用方式
                    <n-tooltip placement="top">
                      <template #trigger><n-icon size="13" class="q-icon"><CircleQuestionMark /></n-icon></template>
                      <div style="font-size: 14px;font-weight: bold;">智能调用</div>
                      <span style="font-size: 12px;">由 LLM 自主判断是否需要检索知识库。模型会根据用户问题的上下文和已有信息决定何时调用 RAG。</span>
                      <div style="font-size: 14px;font-weight: bold;">强制调用</div>
                      <span style="font-size: 12px;">每次对话都强制检索知识库。适用于需要确保每次回答都基于知识库内容的场景。</span>
                    </n-tooltip>
                  </span>
                  <n-radio-group v-model:value="form.ragCallMode" size="small">
                    <n-radio-button :value="2">强制调用</n-radio-button>
                    <n-radio-button :value="1">智能调用</n-radio-button>
                  </n-radio-group>
                  <span>{{ form.ragCallMode === 1 ? '由 LLM 自主判断是否检索知识库' : '每次对话都强制检索知识库' }}</span>
                </div>
                <div v-if="!!selectedKbs.length" class="rag-call-mode-wrapper">
                  <span>
                    引用展示
                    <n-tooltip placement="top">
                      <template #trigger><n-icon size="13" class="q-icon"><CircleQuestionMark /></n-icon></template>
                      <span style="font-size: 12px;">
                        仅控制聊天窗口是否显示「引用（N 条）」折叠块。<br />
                        知识库检索、引用记录都不受影响——关掉只是不展示，回答依据仍会照常记录。
                      </span>
                    </n-tooltip>
                  </span>
                  <n-switch v-model:value="form.showCitations" size="small" :disabled="!form.ragEnabled">
                    <template #checked>开</template>
                    <template #unchecked>关</template>
                  </n-switch>
                  <span>{{ form.showCitations ? '回答下方展示引用的来源切片' : '不展示引用折叠块（仍照常检索与记录）' }}</span>
                </div>
                <div class="resource-list">
                  <div v-for="s in selectedKbs" :key="s.id" class="resource-item">
                    <ResourceIcon kind="rag" :icon="s.icon" :size="36" />
                    <div class="resource-item-main">
                      <div class="resource-item-name">{{ s.name }}</div>
                      <div v-if="s.description" class="resource-item-desc">{{ s.description }}</div>
                    </div>
                    <n-button size="tiny" quaternary type="error" @click="removeKb(s.id)">移除</n-button>
                  </div>
                  <n-empty v-if="!selectedKbs.length" description="未添加知识库" size="small" style="padding: 8px 0" />
                </div>
              </div>

              <!-- 其他能力（无列表） -->
              <div class="config-group config-group--last">
                <div class="config-group-head">
                  <div class="config-group-title">其他能力</div>
                </div>
                <div class="resource-list other-switches">
                  <n-space align="center" :size="8">
                    <n-switch v-model:value="form.memoryEnabled" size="small">
                      <template #checked>开</template>
                      <template #unchecked>关</template>
                    </n-switch>
                    <span>记忆库</span>
                  </n-space>
                  <n-space align="center" :size="8">
                    <n-switch v-model:value="form.webSearchEnabled" size="small">
                      <template #checked>开</template>
                      <template #unchecked>关</template>
                    </n-switch>
                    <span>联网搜索</span>
                  </n-space>
                </div>
              </div>
            </n-card>

            <n-card title="系统指令" size="small" :bordered="true">
              <n-form label-placement="top" size="small">
                <n-form-item label="系统提示词">
                  <n-input
                    v-model:value="form.systemPrompt"
                    type="textarea"
                    :autosize="{ minRows: 8, maxRows: 20 }"
                    placeholder="定义智能体的角色、语气与行为边界"
                  />
                </n-form-item>
              </n-form>
            </n-card>

            <n-card title="对话开场白" size="small" :bordered="true">
              <n-form label-placement="top" size="small">
                <n-form-item label="欢迎语">
                  <n-input
                      v-model:value="form.greeting"
                      type="textarea"
                      :autosize="{ minRows: 2, maxRows: 5 }"
                      placeholder="对话开始时展示的欢迎语"
                  />
                </n-form-item>
                <n-form-item label="预设问题">
                  <div class="question-list">
                    <div v-for="(question, index) in form.presetQuestions" :key="index" class="question-row">
                      <n-input v-model:value="form.presetQuestions[index]" size="small" />
                      <n-button size="small" quaternary type="error" @click="removeQuestion(index)">删除</n-button>
                    </div>
                    <div class="question-row">
                      <n-input
                          v-model:value="newQuestion"
                          size="small"
                          placeholder="输入新问题后回车或点击添加"
                          @keyup.enter="addQuestion"
                      />
                      <n-button size="small" secondary @click="addQuestion">
                        <template #icon><n-icon :component="Plus" /></template>
                        添加
                      </n-button>
                    </div>
                  </div>
                </n-form-item>
              </n-form>
            </n-card>
          </div>

          <!-- 右栏：预览与调试 -->
          <div class="agent-edit-right">
            <PreviewPanel
              ref="previewRef"
              :agent-key="agentKey"
              :name="form.name"
              :description="form.description"
              :avatar="form.avatar"
              :greeting="form.greeting"
              :preset-questions="form.presetQuestions"
              :image-supported="imageSupported"
              :show-citations="form.showCitations"
            />
          </div>
        </div>
      </template>
    </n-spin>
  </div>

  <IconPicker
    v-model:show="showAvatarPicker"
    :model-value="form.avatar"
    :admin-token="adminToken"
    @update:model-value="handleAvatarPicked"
  />

  <ResourcePickerList
    v-model:show="pickerOpen"
    :title="pickerTitle"
    :kind="pickerIconKind"
    :multiple="pickerMultiple"
    :items="pickerItems"
    :selected-ids="pickerSelected"
    @select="onPickerSelect"
    @confirm="onPickerConfirm"
  />
</template>

<style scoped>
/* 编辑页占满「顶栏下沿 → 视口底部」的整块可用高度：不出现整页滚动条，
   改为「左栏内部滚动 + 右栏会话区内部滚动」，两栏底部与视口底部齐平。
   父级取高方式与知识库详情页一致（n-layout-content 那条链是 display:block，只能靠 :has() 补高度）。
   注意 height 必须是 100%（= .n-scrollbar-content 的内容盒高度，AppLayout 的内边距已含在其中）：
   若再写成 calc(100% - var(--app-topbar-height))，页面会被压矮 64px，底部留出空白。 */
.agent-edit-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  gap: 0;
}

/* n-spin 根元素是 display:inline-block + 未定高：inline-block 的 height:auto 不是「确定高度」，
   子元素的 height:100% 会退回 auto，下面整条高度链就断在这里（左右两栏都撑不满）。
   改成块级 flex 容器并占满剩余空间，容器高度即是确定值，链才通。
   注意：一个选择器里只能有一个 :deep()，所以下面每条都要单独写。 */
.agent-edit-page :deep(.n-spin) {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  width: 100%;
}

.agent-edit-page :deep(.n-spin-container) {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.agent-edit-page :deep(.n-spin-content) {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.detail-missing {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  padding: 40px 8px;
}

.agent-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 4px 0 14px;
  border-bottom: 1px solid rgba(128, 128, 128, 0.2);
  margin-bottom: 14px;
  flex-wrap: wrap;
}

.agent-head-main {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.agent-icon {
  color: #2f6bff;
}

.agent-title-wrap {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.agent-title {
  font-size: 19px;
  font-weight: 700;
  line-height: 1.3;
}

.agent-meta {
  font-size: 12px;
  color: rgba(120, 120, 120, 0.95);
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.agent-edit-grid {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 16px;
  /* 不用 align-items:start —— 那会让网格项按内容高（fit-content），
     右栏高度链断掉，预览面板就撑不满可用高度。两列都撑满，各自内部滚动。 */
  align-items: stretch;
}

/* 左栏：表单很长，滚动条只出现在这一栏内部。 */
.agent-edit-left {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
  min-height: 0;
  overflow-y: auto;
  padding-right: 4px;
}

.agent-edit-right {
  min-width: 0;
  min-height: 0;
  display: flex;
}

.agent-edit-right > * {
  flex: 1;
  min-height: 0;
}

/* 极窄窗口：单列堆叠，高度交还文档流（左右各自自然高度）。 */
@media (max-width: 1200px) {
  .agent-edit-page {
    height: auto;
  }

  .agent-edit-grid {
    grid-template-columns: minmax(0, 1fr);
    align-items: start;
  }

  .agent-edit-left {
    overflow-y: visible;
    padding-right: 0;
  }

  .agent-edit-right {
    min-height: 560px;
  }
}

/* 让 n-layout-content 这条链拿到确定高度，详细原因见知识库详情页同名注释：
   .n-layout-scroll-container 的子级是块级元素，flex 无效，块级也不会自动取父高，
   于是随内容长高 → 父级 overflow-y:auto 冒出整页滚动条。
   用 :has() 限定只作用在编辑页，其它页面完全不受影响。 */
:global(.n-layout-content:has(.agent-edit-page)) {
  height: calc(100% - var(--app-topbar-height, 64px));
}

/* naive 自带的 .n-scrollbar / .n-scrollbar-container 是 height:100%，
   真正断在 height:auto 的 .n-scrollbar-content（它是 .agent-edit-page 的祖先，只能用 :global()）。 */
:global(.n-layout-content:has(.agent-edit-page) > .n-scrollbar > .n-scrollbar-container > .n-scrollbar-content) {
  height: 100%;
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

.field-hint {
  font-size: 12px;
  color: rgba(120, 120, 120, 0.95);
}

/* 基本信息：头像居左，Agent Key / 名称 / 描述三项居右。 */
.basic-info-row {
  display: flex;
  gap: 10px;
  align-items: flex-start;
}

.basic-info-avatar {
  flex: 0 0 auto;
  width: 104px;
}

.basic-info-fields {
  flex: 1;
  min-width: 0;
}

.avatar-item :deep(.n-form-item-blank) {
  display: block;
}

.avatar-wrap {
  position: relative;
  width: 80px;
  height: 80px;
}

.avatar-picker {
  width: 80px;
  height: 80px;
  border: 1px dashed #d9d9d9;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  overflow: hidden;
  background: #fafafa;
}

/* 右上角 × 角标删除：hover 头像框时显现，点击即移除头像。角标放在 .avatar-wrap（无 overflow 裁剪）里，可跨出头像框约半个身位。 */
.avatar-remove-badge {
  position: absolute;
  top: -4px;
  right: -4px;
  width: 18px;
  height: 18px;
  padding: 0;
  border: none;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.2s, background-color 0.2s;
  z-index: 999;
}

.avatar-wrap:hover .avatar-remove-badge,
.avatar-remove-badge:focus-visible {
  opacity: 1;
}

.avatar-remove-badge:hover {
  background: rgba(220, 38, 38, 0.9);
}

.avatar-picker-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.avatar-picker-placeholder {
  font-size: 12px;
  color: #999;
}

.avatar-actions {
  margin-top: 8px;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
}
/* 配置信息：列表式资源选择器 */
.config-group {
  padding: 4px 0 12px;
  border-bottom: 1px solid rgba(128, 128, 128, 0.14);
  margin-bottom: 10px;
}
.config-group--last {
  border-bottom: none;
  margin-bottom: 0;
  padding-bottom: 0;
}
.config-group-head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.config-group-title {
  font-size: 14px;
  font-weight: 600;
}
.config-group-actions {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 10px;
}
.resource-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-top: 8px;
}
.resource-item {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 6px 8px;
  border-radius: 8px;
}
.resource-item:hover {
  background: rgba(128, 128, 128, 0.06);
}
.resource-item-main {
  flex: 1;
  min-width: 0;
}
.resource-item-name {
  font-size: 14px;
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.resource-item-desc {
  font-size: 12px;
  color: rgba(120, 120, 120, 0.95);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.other-switches {
  flex-direction: row;
  flex-wrap: wrap;
  gap: 20px;
  padding-left: 8px;
}
.rag-call-mode-wrapper {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 2px 0 6px;
  font-size: 12px;
  margin-top: 6px;
  color: var(--n-text-color-3);
}
</style>
