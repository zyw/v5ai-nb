<script setup lang="ts">
import { computed, h, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  NAlert,
  NButton,
  NCard,
  NCheckbox,
  NDataTable,
  NEmpty,
  NForm,
  NFormItem,
  NIcon,
  NInput,
  NModal,
  NPagination,
  NScrollbar,
  NSelect,
  NSpace,
  NSwitch,
  NTag,
  NTooltip,
  useDialog,
  useMessage,
  useThemeVars,
  type DataTableColumns,
  type SelectOption
} from 'naive-ui'
import { Bot, Copy, Plus, RefreshCw, Search } from 'lucide-vue-next'
import PageHeader from '../components/PageHeader.vue'
import RowActions from '../components/RowActions.vue'
import {
  changeApiKeyEnabled,
  createApiKey,
  deleteApiKeys,
  listAgents,
  listApiKeys,
  loadImagePreview,
  updateApiKey,
  type ApiKeyRecord
} from '../api/client'
import { adminToken } from '../stores/session'
import { formatDateTime } from '../utils/dateUtils'

const message = useMessage()
const dialog = useDialog()
const router = useRouter()

const loading = ref(false)
const rows = ref<ApiKeyRecord[]>([])
const pagination = reactive({ page: 1, pageSize: 10, itemCount: 0 })
const search = reactive({ name: '', agentKey: null as string | null, enabled: null as string | null })

/** Agent 目录项（一次请求取全量：已发布的可被绑定，非发布状态的用于展示失效绑定）。 */
interface AgentCatalogItem {
  agentKey: string
  name: string
  description: string
  avatar: string
  status: string
}

const agentCatalog = ref<AgentCatalogItem[]>([])
// 可绑定项：只有已发布（PUBLISHED）的 Agent 能被绑定（服务端会再校验一次）
const publishedAgents = computed(() => agentCatalog.value.filter((a) => a.status === 'PUBLISHED'))
const publishedKeys = computed(() => new Set(publishedAgents.value.map((a) => a.agentKey)))
const agentOptions = computed<SelectOption[]>(() =>
  publishedAgents.value.map((a) => ({ label: `${a.name}（${a.agentKey}）`, value: a.agentKey }))
)
const agentNameOf = (agentKey: string) =>
  agentCatalog.value.find((a) => a.agentKey === agentKey)?.name ?? agentKey

const themeVars = useThemeVars()
/** 弹窗多选列表的语义颜色令牌（跟随主题，不逐屏硬编码 hex）。 */
const pickerVars = computed<Record<string, string>>(() => ({
  '--agent-border': themeVars.value.borderColor,
  '--agent-hover': themeVars.value.actionColor,
  '--agent-muted': themeVars.value.textColor3,
  '--agent-accent': themeVars.value.primaryColor
}))

/** 已解析的 Agent 图标地址：avatar 可能是受鉴权保护的 /api/ 路径，按 avatar 原值缓存避免重复请求。 */
const agentIconUrls = reactive<Record<string, string>>({})
/** avatar 指向的图片加载失败（例如存的是 emoji 文本）时回退到矢量图标。 */
const brokenAvatars = reactive<Set<string>>(new Set())
let agentIconsLoading = false

/** 取可用的图标地址；未加载完成或加载失败返回空串，模板回退到 Bot 矢量图标。 */
function agentIconUrl(rawAvatar: string): string {
  if (!rawAvatar || brokenAvatars.has(rawAvatar)) return ''
  return agentIconUrls[rawAvatar] ?? ''
}

function markAvatarBroken(rawAvatar: string) {
  if (rawAvatar) brokenAvatars.add(rawAvatar)
}

/** 打开弹窗时预取图标；已缓存的 avatar 不重复请求，失败不阻塞选择。 */
async function ensureAgentIcons() {
  const pending = agentCatalog.value.filter(
    (a) => a.avatar && !(a.avatar in agentIconUrls) && !brokenAvatars.has(a.avatar)
  )
  if (pending.length === 0 || agentIconsLoading) return
  agentIconsLoading = true
  try {
    await Promise.all(
      pending.map(async (agent) => {
        try {
          agentIconUrls[agent.avatar] = await loadImagePreview(adminToken.value, agent.avatar)
        } catch {
          // 单个图标失败不影响列表可用性
        }
      })
    )
  } finally {
    agentIconsLoading = false
  }
}

onBeforeUnmount(() => {
  for (const url of Object.values(agentIconUrls)) {
    if (url.startsWith('blob:')) URL.revokeObjectURL(url)
  }
})

const togglingId = ref<number | null>(null)

/** 批量删除选中的 Key 主键（与「角色管理」的批量删除一致）。 */
const selectedIds = ref<number[]>([])

const columns: DataTableColumns<ApiKeyRecord> = [
  { type: 'selection', width: 40 },
  { title: 'ID', key: 'id', width: 64 },
  {
    title: '名称',
    key: 'name',
    width: 120,
    // 不用列级 ellipsis.tooltip：内容里是按钮，Naive 的省略 tooltip 会把按钮复制进浮层；
    // 截断与悬浮全名交给单元格内的 span（tooltip 由时间列承担，表格仍是 fixed 布局 + scroll-x）。
    render: (r) =>
      h(
        NButton,
        { text: true, type: 'primary', title: '点击查看可访问的 Agent', onClick: () => openAgentsModal(r) },
        {
          default: () =>
            h(
              'span',
              {
                title: r.name,
                style:
                  'display:inline-block;max-width:100%;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;vertical-align:bottom'
              },
              r.name
            )
        }
      )
  },
  {
    title: '跟踪 ID',
    key: 'trackingId',
    width: 'auto',
    render: (r) =>
      h(NSpace, { size: 4, align: 'center', wrap: false }, {
        default: () => [
          h('span', { style: 'font-family: ui-monospace, SFMono-Regular, Menlo, monospace' }, r.trackingId),
          h(
            NTooltip,
            { trigger: 'hover' },
            {
              trigger: () => h(NButton, {
                size: 'tiny',
                quaternary: true,
                onClick: () => copyText(r.trackingId, '跟踪 ID')
              }, { icon: () => h(NIcon, { component: Copy, size: 14 }) }),
              default: () => '复制跟踪 ID（用于日志/审计对账）'
            }
          )
        ]
      })
  },
  {
    title: '状态',
    key: 'enabled',
    width: 110,
    render: (r) =>
      h(
        NSwitch,
        {
          value: r.enabled,
          loading: togglingId.value === r.id,
          onUpdateValue: (value: boolean) => handleToggle(r, value)
        },
        { checked: () => h('span', '启'), unchecked: () => h('span', '停') }
      )
  },
  {
    title: '最新使用时间',
    key: 'lastUsedAt',
    width: 185,
    ellipsis: { tooltip: true },
    render: (r) => (r.lastUsedAt ? (formatDateTime(r.lastUsedAt) ?? '—') : '从未使用')
  },
  { title: '创建时间', key: 'createdAt', width: 185, render: (r) => formatDateTime(r.createdAt) ?? '—' },
  {
    title: '操作',
    key: 'actions',
    width: 125,
    render: (r) =>
      h(RowActions, {
        actions: [
          { key: 'edit', label: '编辑', quaternary: true, onClick: () => openEdit(r) },
          {
            key: 'delete',
            label: '删除',
            quaternary: true,
            type: 'error',
            confirm: `确认删除「${r.name}」？删除后使用该 Key 的调用将立即失败，且不可恢复。`,
            onClick: () => handleDelete([r])
          }
        ]
      })
  }
]

/** 列宽合计：任一带 ellipsis 的表格必须显式给 scroll-x，否则固定布局下列会被压缩而不滚动。 */
const scrollX = columns.reduce((sum, col) => sum + (typeof col.width === 'number' ? col.width : 240), 0)

/** Agent 名称已知时显示「名称（agentKey）」，已非发布状态追加失效标记。 */
function agentLabel(agentKey: string): string {
  const name = agentNameOf(agentKey)
  const suffix = publishedKeys.value.has(agentKey) ? '' : ' · 已失效'
  return name === agentKey ? `${agentKey}${suffix}` : `${name}（${agentKey}）${suffix}`
}

async function copyText(text: string, label: string) {
  try {
    await navigator.clipboard.writeText(text)
    message.success(`${label}已复制`)
  } catch {
    message.warning('浏览器拒绝访问剪贴板，请手动复制')
  }
}

async function loadAgents() {
  try {
    // 取全量（含 DRAFT/DISABLED）：已发布的进可绑定列表，其余仅用于展示失效绑定的名称
    const res = await listAgents(adminToken.value, { pageNum: 1, pageSize: 200 })
    agentCatalog.value = res.rows.map((a) => ({
      agentKey: a.agentKey,
      name: a.name,
      description: a.description ?? '',
      avatar: a.avatar ?? '',
      status: a.status
    }))
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载 Agent 列表失败')
  }
}

async function reload() {
  loading.value = true
  try {
    const res = await listApiKeys(adminToken.value, {
      pageNum: pagination.page,
      pageSize: pagination.pageSize,
      name: search.name || undefined,
      agentKey: search.agentKey ?? undefined,
      // n-select 的 value 只能是字符串/数字，这里把 'true'/'false' 还原成布尔查询条件
      enabled: search.enabled === null ? undefined : search.enabled === 'true'
    })
    rows.value = res.rows
    pagination.itemCount = res.total
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载 API Key 失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pagination.page = 1
  void reload()
}

// ---- 新建 / 编辑 ----

const showFormModal = ref(false)
const saving = ref(false)
const isEdit = ref(false)
const editingId = ref<number | null>(null)
const form = reactive({ name: '', agentKeys: [] as string[], enabled: true })

/** 名称重复这类字段级错误（服务端返回后挂到输入框，改动输入即清除）。 */
const nameError = ref('')

/** 多选列表的关键字过滤（名称 / agentKey / 描述）。 */
const agentFilter = ref('')

/** 可勾选行：全部已发布 Agent + 编辑时已绑定但已失效的（禁用，避免保存时被静默丢弃）。 */
const pickerRows = computed(() => {
  const keyword = agentFilter.value.trim().toLowerCase()
  return agentCatalog.value
    .filter((a) => a.status === 'PUBLISHED' || form.agentKeys.includes(a.agentKey))
    .filter((a) => !keyword || `${a.name} ${a.agentKey} ${a.description}`.toLowerCase().includes(keyword))
})

const selectableAgentCount = computed(() => publishedAgents.value.length)
const isSelectable = (agentKey: string) => publishedKeys.value.has(agentKey)
const isBound = (agentKey: string) => form.agentKeys.includes(agentKey)

/** 勾选/取消绑定；整行由 n-checkbox 的 label 承载，点行任意位置都会走到这里。 */
function toggleAgent(agentKey: string, checked: boolean) {
  if (!isSelectable(agentKey)) return
  form.agentKeys = checked ? [...form.agentKeys, agentKey] : form.agentKeys.filter((key) => key !== agentKey)
}

function resetForm() {
  editingId.value = null
  form.name = ''
  form.agentKeys = []
  form.enabled = true
  agentFilter.value = ''
  nameError.value = ''
}

function openCreate() {
  resetForm()
  isEdit.value = false
  showFormModal.value = true
  void ensureAgentIcons()
}

function openEdit(row: ApiKeyRecord) {
  isEdit.value = true
  editingId.value = row.id
  form.name = row.name
  form.agentKeys = [...(row.agentKeys ?? [])]
  form.enabled = row.enabled
  agentFilter.value = ''
  nameError.value = ''
  showFormModal.value = true
  void ensureAgentIcons()
}

async function handleSave() {
  nameError.value = ''
  const name = form.name.trim()
  if (!name) {
    message.warning('请填写 Key 名称')
    return
  }
  if (form.agentKeys.length === 0) {
    message.warning('请至少选择一个可访问的 Agent')
    return
  }
  saving.value = true
  try {
    if (isEdit.value && editingId.value !== null) {
      await updateApiKey(adminToken.value, editingId.value, {
        name,
        agentKeys: form.agentKeys,
        enabled: form.enabled
      })
      message.success('API Key 已更新')
      showFormModal.value = false
    } else {
      const created = await createApiKey(adminToken.value, { name, agentKeys: form.agentKeys })
      showFormModal.value = false
      generated.value = created
      showKeyModal.value = true
    }
    await reload()
  } catch (e) {
    const msg = e instanceof Error ? e.message : '保存失败'
    // 重名属于字段级问题：就近挂在名称输入框上，不再重复弹全局提示
    if (msg.includes('名称已存在')) {
      nameError.value = msg
    } else {
      message.error(msg)
    }
  } finally {
    saving.value = false
  }
}

// ---- 一次性明文 Key ----

const showKeyModal = ref(false)
const generated = ref<{ name: string; trackingId: string; apiKey: string; agentKeys: string[] } | null>(null)

/** 关闭一次性弹窗前必须确认：明文 Key 关闭后无法再次查看。 */
function requestCloseKeyModal() {
  dialog.warning({
    title: '确认关闭',
    content: '明文 Key 仅本次可见，关闭后无法再次查看，请确认已妥善保存。',
    positiveText: '已保存，关闭',
    negativeText: '继续查看',
    onPositiveClick: () => {
      showKeyModal.value = false
      generated.value = null
    }
  })
}

// ---- 可访问 Agent 明细弹窗（点列表里的名称打开） ----

const showAgentsModal = ref(false)
const agentsModalKey = ref<ApiKeyRecord | null>(null)

/** 弹窗里的 Agent 明细：以该 Key 的绑定 agentKey 为准，补上目录里的名称/描述/图标与当前发布状态。 */
const agentsModalRows = computed(() =>
  (agentsModalKey.value?.agentKeys ?? []).map((agentKey) => {
    const agent = agentCatalog.value.find((a) => a.agentKey === agentKey)
    return {
      agentKey,
      name: agent?.name ?? agentKey,
      description: agent?.description ?? '',
      avatar: agent?.avatar ?? '',
      status: agent?.status ?? ''
    }
  })
)

const agentsModalTitle = computed(() =>
  agentsModalKey.value
    ? `${agentsModalKey.value.name} · 可访问的 Agent（${agentsModalRows.value.length}）`
    : '可访问的 Agent'
)

function openAgentsModal(row: ApiKeyRecord) {
  agentsModalKey.value = row
  showAgentsModal.value = true
  void ensureAgentIcons()
}

// ---- 状态 / 删除 ----

function handleToggle(row: ApiKeyRecord, next: boolean) {
  if (next) {
    void applyToggle(row, true)
    return
  }
  dialog.warning({
    title: '停用确认',
    content: `确认停用「${row.name}」？停用后使用该 Key 的调用将立即返回 401。`,
    positiveText: '停用',
    negativeText: '取消',
    onPositiveClick: () => applyToggle(row, false)
  })
}

async function applyToggle(row: ApiKeyRecord, enabled: boolean) {
  togglingId.value = row.id
  try {
    await changeApiKeyEnabled(adminToken.value, row.id, enabled)
    message.success(enabled ? '已启用' : '已停用')
    await reload()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '操作失败')
  } finally {
    togglingId.value = null
  }
}

async function handleDelete(targets: ApiKeyRecord[]) {
  try {
    await deleteApiKeys(adminToken.value, targets.map((t) => t.id))
    message.success('已删除')
    selectedIds.value = []
    await reload()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '删除失败')
  }
}

function handleBatchDelete() {
  dialog.warning({
    title: '批量删除确认',
    content: `确认删除选中的 ${selectedIds.value.length} 个 API Key？删除后使用这些 Key 的调用将立即失败，且不可恢复。`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await deleteApiKeys(adminToken.value, selectedIds.value)
        message.success('已删除所选 API Key')
        selectedIds.value = []
        await reload()
      } catch (e) {
        message.error(e instanceof Error ? e.message : '删除失败')
      }
    }
  })
}

onMounted(async () => {
  await loadAgents()
  await reload()
})
</script>

<template>
  <div class="page">
    <PageHeader
      title="API Keys 管理"
      description="创建访问运行期接口（/api/v1/agents/{agentKey}/chat）的 API Key，并绑定该 Key 可以访问的已发布 Agent。Key 归属创建者本人，明文仅创建时展示一次。"
    >
      <template #actions>
        <n-space size="small">
          <n-button size="small" type="primary" @click="openCreate">
            <template #icon><n-icon :component="Plus" /></template>
            新建 API Key
          </n-button>
        </n-space>
      </template>
    </PageHeader>

    <n-card :bordered="true">
      <n-space style="margin-bottom: 12px" :size="8" align="center">
        <n-input
          v-model:value="search.name"
          placeholder="Key 名称"
          clearable
          style="width: 180px"
          @keyup.enter="handleSearch"
        />
        <n-select
          v-model:value="search.agentKey"
          placeholder="可访问 Agent"
          clearable
          filterable
          style="width: 220px"
          :options="agentOptions"
        />
        <n-select
          v-model:value="search.enabled"
          placeholder="状态"
          clearable
          style="width: 110px"
          :options="[{ label: '启用', value: 'true' }, { label: '停用', value: 'false' }]"
        />
        <n-button size="small" type="primary" secondary @click="handleSearch">
          <template #icon><n-icon :component="Search" /></template>
          查询
        </n-button>
        <n-button size="small" quaternary @click="handleSearch">
          <template #icon><n-icon :component="RefreshCw" /></template>
          刷新
        </n-button>
        <n-button
          v-if="selectedIds.length > 0"
          size="small"
          type="error"
          secondary
          @click="handleBatchDelete"
        >
          删除所选（{{ selectedIds.length }}）
        </n-button>
      </n-space>

      <n-alert v-if="publishedAgents.length === 0" type="info" :bordered="false" style="margin-bottom: 12px">
        暂无已发布的 Agent，只有已发布状态的 Agent 才能被 API Key 访问。
        <n-button text type="primary" @click="router.push({ name: 'agents' })">前往 Agent 管理</n-button>
      </n-alert>

      <n-data-table
        :loading="loading"
        :columns="columns"
        :data="rows"
        :row-key="(r: ApiKeyRecord) => r.id"
        :checked-row-keys="selectedIds"
        @update:checked-row-keys="(keys: Array<string | number>) => (selectedIds = keys as number[])"
        :scroll-x="scrollX"
        :bordered="false"
      >
        <template #empty>
          <div class="empty-hint">
            还没有 API Key。点击右上角「新建 API Key」，选择可访问的已发布 Agent 即可创建。
          </div>
        </template>
      </n-data-table>
      <n-pagination
        :page="pagination.page"
        :page-size="pagination.pageSize"
        :item-count="pagination.itemCount"
        :page-sizes="[10, 20, 50, 100]"
        show-size-picker
        style="justify-content: flex-end; margin-top: 12px"
        @update:page="(page: number) => { pagination.page = page; void reload() }"
        @update:page-size="(size: number) => { pagination.pageSize = size; pagination.page = 1; void reload() }"
      />
    </n-card>

    <!-- 新建 / 编辑 -->
    <n-modal
      v-model:show="showFormModal"
      preset="card"
      :title="isEdit ? '编辑 API Key' : '新建 API Key'"
      style="width: 560px"
      :bordered="false"
    >
      <n-form label-placement="top">
        <n-form-item
          label="Key 名称"
          required
          :validation-status="nameError ? 'error' : undefined"
          :feedback="nameError"
        >
          <n-input
            v-model:value="form.name"
            :maxlength="100"
            show-count
            placeholder="如 生产环境-客服台"
            @update:value="nameError = ''"
          />
        </n-form-item>
        <n-form-item label="可访问的 Agent" required>
          <div class="agent-picker" :style="pickerVars">
            <div class="agent-picker-head">
              <n-input
                v-model:value="agentFilter"
                size="small"
                clearable
                placeholder="搜索名称 / agentKey / 描述"
              >
                <template #prefix><n-icon :component="Search" :size="14" /></template>
              </n-input>
              <span class="agent-picker-count">已选 {{ form.agentKeys.length }} / 可绑 {{ selectableAgentCount }}</span>
            </div>

            <div v-if="selectableAgentCount === 0" class="agent-picker-empty">
              暂无已发布（PUBLISHED）的 Agent，请先发布 Agent 再绑定。
              <n-button text type="primary" @click="router.push({ name: 'agents' })">前往 Agent 管理</n-button>
            </div>

            <n-scrollbar v-else style="max-height: 248px">
              <div class="agent-picker-list">
                <div
                  v-for="agent in pickerRows"
                  :key="agent.agentKey"
                  class="agent-picker-item"
                  :class="{ 'is-selected': isBound(agent.agentKey), 'is-stale': !isSelectable(agent.agentKey) }"
                >
                  <n-checkbox
                    :checked="isBound(agent.agentKey)"
                    :disabled="!isSelectable(agent.agentKey)"
                    @update:checked="(checked: boolean) => toggleAgent(agent.agentKey, checked)"
                  >
                    <span class="agent-picker-avatar" aria-hidden="true">
                      <img
                        v-if="agentIconUrl(agent.avatar)"
                        :src="agentIconUrl(agent.avatar)"
                        alt=""
                        @error="markAvatarBroken(agent.avatar)"
                      />
                      <n-icon v-else :component="Bot" :size="16" />
                    </span>
                    <span class="agent-picker-text">
                      <span class="agent-picker-name">
                        {{ agent.name }}
                        <span class="agent-picker-key">（{{ agent.agentKey }}）</span>
                        <n-tag v-if="!isSelectable(agent.agentKey)" size="tiny" type="error" :bordered="false">已失效</n-tag>
                      </span>
                      <span class="agent-picker-desc">
                        {{
                          agent.description ||
                          (isSelectable(agent.agentKey) ? '暂无描述' : '该 Agent 已非发布状态，取消后不可再选')
                        }}
                      </span>
                    </span>
                  </n-checkbox>
                </div>
                <n-empty v-if="pickerRows.length === 0" size="small" description="没有匹配的 Agent" />
              </div>
            </n-scrollbar>
          </div>
        </n-form-item>
<!--        <n-form-item v-if="isEdit" label="启用状态">
          <n-switch v-model:value="form.enabled">
            <template #checked>启用</template>
            <template #unchecked>停用</template>
          </n-switch>
        </n-form-item>-->
        <n-alert v-if="isEdit" type="info" :bordered="false">
          取消某个 Agent 的绑定后，使用本 Key 调用该 Agent 将立即返回 403。
        </n-alert>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showFormModal = false">取消</n-button>
          <n-button type="primary" :loading="saving" @click="handleSave">保存</n-button>
        </n-space>
      </template>
    </n-modal>

    <!-- 一次性明文 Key -->
    <n-modal
      :show="showKeyModal"
      preset="card"
      title="API Key 创建成功"
      style="width: 600px"
      :bordered="false"
      :mask-closable="false"
      :close-on-esc="false"
      @update:show="(value: boolean) => { if (!value) requestCloseKeyModal() }"
    >
      <n-alert type="warning" :bordered="false" style="margin-bottom: 12px">
        明文 Key 仅此一次可见，关闭后无法再次查看。请立即复制并妥善保存。
      </n-alert>
      <n-form label-placement="top">
        <n-form-item label="名称">
          <n-input :value="generated?.name ?? ''" readonly />
        </n-form-item>
        <n-form-item label="可访问的 Agent">
          <n-space :size="4">
            <n-tag v-for="key in generated?.agentKeys ?? []" :key="key" size="small" type="info" :bordered="false">
              {{ agentLabel(key) }}
            </n-tag>
          </n-space>
        </n-form-item>
        <n-form-item label="API Key（明文）">
          <n-input
            :value="generated?.apiKey ?? ''"
            readonly
            style="font-family: ui-monospace, SFMono-Regular, Menlo, monospace"
          />
        </n-form-item>
        <n-form-item label="跟踪 ID">
          <n-input :value="generated?.trackingId ?? ''" readonly />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="copyText(generated?.apiKey ?? '', 'API Key')">
            <template #icon><n-icon :component="Copy" /></template>
            复制 API Key
          </n-button>
          <n-button type="primary" @click="requestCloseKeyModal">我已保存</n-button>
        </n-space>
      </template>
    </n-modal>

    <!-- 可访问 Agent 明细：点列表里的名称打开 -->
    <n-modal
      v-model:show="showAgentsModal"
      preset="card"
      :title="agentsModalTitle"
      style="width: 560px"
      :bordered="false"
    >
      <div v-if="agentsModalRows.length === 0" class="agent-picker-empty" :style="pickerVars">
        该 Key 还没有绑定任何 Agent，运行时会返回 403。可在「编辑」里绑定已发布的 Agent。
      </div>
      <n-scrollbar v-else :style="pickerVars" class="agent-modal-scroll">
        <div class="agent-modal-list">
          <div v-for="agent in agentsModalRows" :key="agent.agentKey" class="agent-modal-item">
            <span class="agent-picker-avatar" aria-hidden="true">
              <img
                v-if="agentIconUrl(agent.avatar)"
                :src="agentIconUrl(agent.avatar)"
                alt=""
                @error="markAvatarBroken(agent.avatar)"
              />
              <n-icon v-else :component="Bot" :size="16" />
            </span>
            <span class="agent-picker-text">
              <span class="agent-picker-name">
                {{ agent.name }}
                <span class="agent-picker-key">（{{ agent.agentKey }}）</span>
                <n-tag v-if="agent.status && agent.status !== 'PUBLISHED'" size="tiny" type="error" :bordered="false">
                  已失效
                </n-tag>
              </span>
              <span class="agent-picker-desc">
                {{ agent.description || (agent.status === 'PUBLISHED' ? '暂无描述' : '该 Agent 当前不是发布状态') }}
              </span>
            </span>
          </div>
        </div>
      </n-scrollbar>
    </n-modal>
  </div>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.empty-hint {
  padding: 24px 0 8px;
  text-align: center;
  font-size: 13px;
  opacity: 0.7;
}

/* ---- 弹窗内的 Agent 多选列表：图标 + 名称 + 描述 + 行尾复选框 ---- */

.agent-picker {
  width: 100%;
  border: 1px solid var(--agent-border);
  border-radius: 6px;
}

.agent-picker-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px;
  border-bottom: 1px solid var(--agent-border);
}

.agent-picker-count {
  flex: none;
  font-size: 12px;
  color: var(--agent-muted);
  font-variant-numeric: tabular-nums;
}

.agent-picker-empty {
  padding: 12px 8px;
  font-size: 13px;
  color: var(--agent-muted);
}

.agent-picker-list {
  padding: 4px;
}

/* 行容器：整行是 n-checkbox 的 label（点击行内任意位置都能勾选）。
   内边距与最小高度放在 label 上，保证 44px 触摸目标整块都可点。 */
.agent-picker-item {
  border-radius: 4px;
}

.agent-picker-item:hover,
.agent-picker-item.is-selected {
  background: var(--agent-hover);
}

.agent-picker-item.is-stale {
  cursor: not-allowed;
  opacity: 0.65;
}

.agent-picker-item.is-stale:hover {
  background: transparent;
}

/* n-checkbox 根节点是 div[role=checkbox]（tabindex=0），焦点环画在整行上 */
.agent-picker-item :deep(.n-checkbox:focus-visible) {
  box-shadow: 0 0 0 2px var(--agent-accent);
}

.agent-picker-item.is-stale :deep(.n-checkbox) {
  cursor: not-allowed;
}

/* row-reverse：把复选框排到文字之后 */
.agent-picker-item :deep(.n-checkbox) {
  display: flex;
  width: 100%;
  box-sizing: border-box;
  flex-direction: row-reverse;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 44px;
  padding: 6px 8px;
  border-radius: 4px;
  cursor: pointer;
}

.agent-picker-item :deep(.n-checkbox__label) {
  display: flex;
  flex: 1;
  align-items: center;
  gap: 10px;
  min-width: 0;
  padding-left: 0;
}

.agent-picker-avatar {
  display: flex;
  flex: none;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 6px;
  overflow: hidden;
  color: var(--agent-muted);
  background: var(--agent-hover);
}

.agent-picker-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.agent-picker-text {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  gap: 2px;
}

.agent-picker-name {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  line-height: 1.4;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.agent-picker-item.is-selected .agent-picker-name {
  color: var(--agent-accent);
  font-weight: 600;
}

.agent-picker-key {
  font-size: 12px;
  color: var(--agent-muted);
}

.agent-picker-desc {
  font-size: 12px;
  color: var(--agent-muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* ---- 「可访问的 Agent」明细弹窗（只读列表，复用上面的行视觉） ---- */

.agent-modal-scroll {
  max-height: 360px;
}

.agent-modal-list {
  padding: 4px;
}

.agent-modal-item {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 44px;
  padding: 6px 8px;
  border-radius: 4px;
}

.agent-modal-item:hover {
  background: var(--agent-hover);
}
</style>
