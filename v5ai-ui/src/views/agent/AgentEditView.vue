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
  NSelect,
  NSkeleton,
  NSpace,
  NSwitch,
  NTag,
  NText,
  useMessage
} from 'naive-ui'
import { ArrowLeft, Bot, Plus } from 'lucide-vue-next'
import {
  getAgent,
  getKnowledgeBindings,
  getMcpServerBindings,
  getSkillBindings,
  listKnowledgeBaseOptions,
  listMcpServerOptions,
  listModelOptions,
  listModels,
  listSkillOptions,
  updateAgent,
  bindKnowledgeBases,
  bindMcpServers,
  bindSkills,
  type AgentResponse,
  type ModelResponse,
  type OptionResponse
} from '../../api/client'
import { adminToken } from '../../stores/session'
import { formatDateTime } from '../../utils/dateUtils'
import StatusTag from '../../components/StatusTag.vue'
import PreviewPanel from './PreviewPanel.vue'

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

const modelOptions = ref<OptionResponse[]>([])
const knowledgeBaseOptions = ref<OptionResponse[]>([])
const mcpServerOptions = ref<OptionResponse[]>([])
const skillOptions = ref<OptionResponse[]>([])

const form = reactive({
  name: '',
  description: '',
  avatar: '',
  greeting: '',
  systemPrompt: '',
  presetQuestions: [] as string[],
  modelId: null as number | null,
  skillIds: [] as number[],
  mcpServerIds: [] as number[],
  knowledgeBaseIds: [] as number[],
  memoryEnabled: false,
  mcpEnabled: false,
  skillEnabled: false,
  webSearchEnabled: false,
  ragEnabled: false
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

function applyAgent(row: AgentResponse) {
  detail.value = row
  form.name = row.name
  form.description = row.description ?? ''
  form.avatar = row.avatar ?? ''
  form.greeting = row.greeting ?? ''
  form.systemPrompt = row.systemPrompt ?? ''
  form.presetQuestions = parseQuestions(row.presetQuestions)
  form.modelId = row.modelId ?? null
  form.memoryEnabled = row.memoryEnabled ?? false
  form.mcpEnabled = row.mcpEnabled ?? false
  form.skillEnabled = row.skillEnabled ?? false
  form.webSearchEnabled = row.webSearchEnabled ?? false
  form.ragEnabled = row.ragEnabled ?? false
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
    // 详情与下拉选项并行拉取；绑定查询失败不阻断整页渲染。
    const [row, models, kbs, mcps, skills] = await Promise.all([
      getAgent(adminToken.value, agentKey.value),
      listModelOptions(adminToken.value, 'CHAT'),
      listKnowledgeBaseOptions(adminToken.value),
      listMcpServerOptions(adminToken.value),
      listSkillOptions(adminToken.value)
    ])
    modelOptions.value = models
    knowledgeBaseOptions.value = kbs
    mcpServerOptions.value = mcps
    skillOptions.value = skills
    applyAgent(row)
    if (form.modelId == null) {
      form.modelId = models.find((o) => o.isDefault)?.value ?? models[0]?.value ?? null
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
      modelId: form.modelId
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
                <n-form-item label="Agent Key">
                  <n-input :value="agentKey" disabled />
                </n-form-item>
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
                <n-form-item label="头像 URL">
                  <n-input v-model:value="form.avatar" placeholder="可选：智能体头像图片地址" />
                </n-form-item>
              </n-form>
            </n-card>

            <n-card title="配置信息" size="small" :bordered="true">
              <n-form label-placement="top" size="small">
                <n-form-item label="CHAT 模型">
                  <n-select v-model:value="form.modelId" filterable :options="modelOptions" placeholder="选择已配置的对话模型" />
                </n-form-item>
                <n-form-item>
                  <template #label>SKILL</template>
                  <n-select v-model:value="form.skillIds" multiple clearable :options="skillOptions" placeholder="选择已发布的 Skill" />
                  <template #feedback>
                    <span class="field-hint">添加 Skill 后，智能体将具备更多专项能力</span>
                  </template>
                </n-form-item>
                <n-form-item label="MCP 服务">
                  <n-select v-model:value="form.mcpServerIds" multiple clearable :options="mcpServerOptions" placeholder="选择已发现的 MCP Server" />
                  <template #feedback>
                    <span class="field-hint">添加 MCP 服务后，对话中即可使用这些工具能力</span>
                  </template>
                </n-form-item>
                <n-form-item label="知识库">
                  <n-select v-model:value="form.knowledgeBaseIds" multiple clearable :options="knowledgeBaseOptions" placeholder="选择知识库（RAG）" />
                  <template #feedback>
                    <span class="field-hint">绑定知识库后，回答会检索库内文档并给出引用</span>
                  </template>
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

            <n-card title="能力开关" size="small" :bordered="true">
              <n-alert v-if="!modelOptions.length" type="warning" :bordered="false" style="margin-bottom: 10px">
                尚未配置可用的对话模型，请先在「模型管理」中配置并启用模型。
              </n-alert>
              <n-space size="large" wrap>
                <n-space align="center" :size="8"><n-switch v-model:value="form.memoryEnabled" /><span>记忆库</span></n-space>
                <n-space align="center" :size="8"><n-switch v-model:value="form.mcpEnabled" /><span>MCP</span></n-space>
                <n-space align="center" :size="8"><n-switch v-model:value="form.skillEnabled" /><span>Skill</span></n-space>
                <n-space align="center" :size="8"><n-switch v-model:value="form.webSearchEnabled" /><span>联网搜索</span></n-space>
                <n-space align="center" :size="8"><n-switch v-model:value="form.ragEnabled" /><span>RAG</span></n-space>
              </n-space>
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
            />
          </div>
        </div>
      </template>
    </n-spin>
  </div>
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
</style>
