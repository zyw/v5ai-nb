<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
import {
  NButton,
  NCard,
  NDataTable,
  NDivider,
  NForm,
  NGridItem,
  NIcon,
  NInput,
  NInputNumber,
  NModal,
  NPagination,
  NRadioButton,
  NRadioGroup,
  NSelect,
  NSpace,
  NSwitch,
  NTag,
  NText,
  useDialog,
  useMessage,
  type DataTableColumns,
  type FormInst,
  type FormRules
} from 'naive-ui'
import { BadgeCheck, Plus, RefreshCw, Search } from 'lucide-vue-next'
import PageHeader from '../components/PageHeader.vue'
import RowActions from '../components/RowActions.vue'
import {
  createStoreInstance,
  deleteStoreInstances,
  listStoreInstances,
  testStoreInstanceConnection,
  updateStoreInstance,
  updateStoreInstanceDefault,
  type StoreInstanceRequest,
  type StoreInstanceResponse
} from '../api/client'
import { adminToken } from '../stores/session'
import { formatDateTime } from '../utils/dateUtils'

const message = useMessage()
const dialog = useDialog()
const loading = ref(false)
const instances = ref<StoreInstanceResponse[]>([])
const pagination = reactive({ page: 1, pageSize: 10, itemCount: 0 })
const search = reactive({
  name: '',
  category: null as number | null,
  type: null as number | null,
  status: null as number | null
})
const checkedRowKeys = ref<number[]>([])

const categoryOptions = [
  { label: '向量库', value: 1 },
  { label: '搜索引擎', value: 2 }
]

const typeOptions = [
  { label: 'PGVector', value: 1 },
  { label: 'Milvus', value: 2 },
  { label: 'ElasticSearch', value: 3 },
  // 业务库原生 BM25（历史名 PGFullText）：分词存在业务库切片行上，无需任何连接参数
  { label: '业务库 BM25', value: 4 }
]

const statusOptions = [
  { label: '启用', value: 1 },
  { label: '停用', value: 0 }
]

const formRef = ref<FormInst | null>(null)

/** n-form 校验按 path 读取值：form 与 configForm 合并为模型 */
const formModel = computed(() => ({ ...form, configForm }))

function validatePort(_rule: unknown, value: unknown): boolean | Error {
  if (typeof value !== 'number' || value < 1 || value > 65535) {
    return new Error('端口范围为 1-65535')
  }
  return true
}

/** 校验规则：name/主机/端口必填；PG 需数据库与用户名，Milvus 需数据库，ES 用户名可留空 */
const rules = computed<FormRules>(() => {
  const common: FormRules = {
    name: [{ required: true, whitespace: true, message: '请输入实例名称', trigger: 'blur' }],
    'configForm.host': [{ required: true, message: '请输入主机地址', trigger: 'blur' }],
    'configForm.port': [
      { required: true, type: 'number', message: '请输入端口', trigger: 'blur' },
      { validator: validatePort, trigger: ['blur', 'change'] }
    ]
  }
  // 类型 4（业务库 BM25）不渲染任何连接参数项，也就不参与校验（Naive 只校验已挂载的 FormItem）
  if (form.type === 1) {
    // common['configForm.database'] = [{ required: true, message: '请输入数据库名', trigger: 'blur' }]
    common['configForm.username'] = [{ required: true, message: '请输入用户名', trigger: 'blur' }]
  } else if (form.type === 2) {
    // common['configForm.database'] = [{ required: true, message: '请输入数据库名', trigger: 'blur' }]
  }
  return common
})


function categoryLabel(category?: number): string {
  return categoryOptions.find((o) => o.value === category)?.label ?? '—'
}

function typeLabel(type?: number): string {
  return typeOptions.find((o) => o.value === type)?.label ?? '—'
}

const columns: DataTableColumns<StoreInstanceResponse> = [
  { type: 'selection' },
  { title: 'ID', key: 'id', width: 64 },
  {
    title: '名称',
    key: 'name',
    width: 180,
    render: (row) =>
      h('div', { style: 'display:flex;align-items:center;gap:6px;min-width:0' }, [
        h('span', { style: 'min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap', title: row.name }, row.name),
        row.isDefault
          ? h(BadgeCheck, {
              size: 16,
              'aria-label': '默认存储实例',
              title: '默认存储实例',
              style: 'color:var(--primary-color);flex:0 0 auto'
            })
          : null
      ])
  },
  { title: '描述', key: 'description', width: 200 },
  {
    title: '分类',
    key: 'category',
    width: 90,
    render: (row) => h(NTag, { size: 'small', type: row.category === 1 ? 'success' : 'info', bordered: false }, { default: () => categoryLabel(row.category) })
  },
  {
    title: '类型',
    key: 'type',
    width: 140,
    render: (row) => h(NTag, { size: 'small', type: 'info', bordered: false }, { default: () => typeLabel(row.type) })
  },
  {
    title: '状态',
    key: 'status',
    width: 90,
    render: (row) =>
      h(NSwitch, {
        value: row.status === 1,
        loading: togglingId.value === row.id,
        onUpdateValue: () => handleToggleStatus(row)
      }, {
        checked: () => h('span', '启'),
        unchecked: () => h('span', '停')
      })
  },
  // { title: '创建时间', key: 'createdAt', width: 182, render: (row) => fmtTime(row.createdAt) },
  { title: '更新时间', key: 'updatedAt', width: 182, render: (row) => fmtTime(row.updatedAt) },
  {
    title: '操作',
    key: 'actions',
    width: 220,
    render: (row) =>
      h(RowActions, {
        maxInline: 1,
        actions: [
          { key: 'edit', label: '编辑', secondary: true, type: 'primary', onClick: () => openEdit(row) },
          { key: 'default', label: row.isDefault ? '取消默认' : '设为默认', type: row.isDefault ? 'warning' : 'primary', secondary: true, loading: defaultId.value === row.id, onClick: () => handleToggleDefault(row) },
          { key: 'delete', label: '删除', secondary: true, type: 'error', confirm: `确认删除存储实例「${row.name}」？`, onClick: () => handleDelete(row) }
        ]
      })
  }
]

function fmtTime(value?: string | null): string {
  return value ? (formatDateTime(value) ?? '—') : '—'
}

async function reload() {
  loading.value = true
  try {
    const rows = await listStoreInstances(adminToken.value, {
      pageNum: pagination.page,
      pageSize: pagination.pageSize,
      name: search.name || undefined,
      category: search.category ?? undefined,
      type: search.type ?? undefined,
      status: search.status ?? undefined
    })
    instances.value = rows.rows
    pagination.itemCount = rows.total
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载存储实例失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pagination.page = 1
  void reload()
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

// ---- 新增 / 编辑 ----

const showModal = ref(false)
const saving = ref(false)
const testing = ref(false)
const editingId = ref<number | null>(null)
const defaultId = ref<number | null>(null)
const togglingId = ref<number | null>(null)
// const testResult = ref<{ ok: boolean; message: string } | null>(null)

const form = reactive<StoreInstanceRequest>({
  name: '',
  description: '',
  category: 1,
  type: 1,
  status: 1,
  isDefault: false
})

/** 类型选项随分类联动：向量库支持 PG_VECTOR/MILVUS/ELASTICSEARCH，搜索引擎支持 ELASTICSEARCH/业务库 BM25 */
const formTypeOptions = computed(() =>
  form.category === 1
    ? typeOptions.filter((o) => o.value === 1 || o.value === 2 || o.value === 3)
    : form.category === 2
      ? typeOptions.filter((o) => o.value === 3 || o.value === 4)
      : typeOptions
)

/** 连接参数表单字段：键名与后端 XxxVectorConfigDO 字段一一对应 */
interface ConfigForm {
  host: string
  port: number | null
  database: string
  username: string
  password: string
  token: string
  scheme: 'http' | 'https'
  sslEnabled: boolean
  sslVerificationDisabled: boolean
}

/** 各类型连接参数默认值（与后端 DO 默认值一致） */
const CONFIG_DEFAULTS: Record<number, ConfigForm> = {
  // PG_VECTOR → PgVectorConfigDO（类型 4 无连接参数，故不在表内）
  1: { host: 'localhost', port: 5432, database: 'v5ai_ai', username: 'postgres', password: '', token: '', scheme: 'http', sslEnabled: false, sslVerificationDisabled: false },
  // MILVUS → MilvusVectorConfigDO
  2: { host: 'localhost', port: 19530, database: 'default', username: '', password: '', token: '', scheme: 'http', sslEnabled: false, sslVerificationDisabled: false },
  // ELASTICSEARCH → ElasticsearchVectorConfigDO
  3: { host: 'localhost', port: 9200, database: '', username: '', password: '', token: '', scheme: 'http', sslEnabled: false, sslVerificationDisabled: false }
}

const configForm = reactive<ConfigForm>({ ...CONFIG_DEFAULTS[1] })

/** 编辑时解析出的、表单不认识的 config 字段（indexPrefix、similarity 等），保存时保留避免丢数据 */
const preservedConfig = ref<Record<string, unknown>>({})

/** 表单已覆盖的字段：生成 JSON 时以表单为准，避免旧值残留 */
const KNOWN_CONFIG_KEYS = new Set([
  'host', 'port', 'database', 'username', 'password', 'token',
  'scheme', 'sslEnabled', 'sslMode', 'sslVerificationDisabled'
])

function resetConfigForType(type: number) {
  Object.assign(configForm, CONFIG_DEFAULTS[type] ?? CONFIG_DEFAULTS[1])
  preservedConfig.value = {}
  // testResult.value = null
}

function asStr(v: unknown, fallback: string): string {
  return typeof v === 'string' ? v : fallback
}

function asNum(v: unknown, fallback: number | null): number | null {
  return typeof v === 'number' && Number.isFinite(v) ? v : fallback
}

function asBool(v: unknown, fallback: boolean): boolean {
  return typeof v === 'boolean' ? v : fallback
}

function parseConfig(json?: string | null): Record<string, unknown> {
  if (!json) return {}
  try {
    const parsed: unknown = JSON.parse(json)
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed)
      ? (parsed as Record<string, unknown>)
      : {}
  } catch {
    return {}
  }
}

function fillConfigFromParsed(parsed: Record<string, unknown>) {
  const d = CONFIG_DEFAULTS[form.type] ?? CONFIG_DEFAULTS[1]
  configForm.host = asStr(parsed.host, d.host)
  configForm.port = asNum(parsed.port, d.port)
  configForm.database = asStr(parsed.database, d.database)
  configForm.username = asStr(parsed.username, d.username)
  configForm.password = asStr(parsed.password, d.password)
  configForm.token = asStr(parsed.token, d.token)
  configForm.scheme = parsed.scheme === 'https' ? 'https' : 'http'
  configForm.sslEnabled = asBool(parsed.sslEnabled, false)
  configForm.sslVerificationDisabled = asBool(parsed.sslVerificationDisabled, false)
  preservedConfig.value = Object.fromEntries(
    Object.entries(parsed).filter(([key]) => !KNOWN_CONFIG_KEYS.has(key))
  )
}

/** 按类型生成与对应 DO 字段名一致的 config JSON */
function buildConfigJson(type: number): string {
  const text = (v: string): string | undefined => {
    const t = v.trim()
    return t || undefined
  }
  // 业务库 BM25：没有外部连接可配，直接落空对象
  if (type === 4) {
    return '{}'
  }
  const defaults = CONFIG_DEFAULTS[type] ?? CONFIG_DEFAULTS[1]
  const base = { host: text(configForm.host) ?? 'localhost', port: configForm.port ?? defaults.port }
  let cfg: Record<string, unknown>
  if (type === 1) {
    // PgVectorConfigDO
    cfg = {
      ...base,
      database: text(configForm.database),
      username: text(configForm.username),
      password: text(configForm.password),
      sslEnabled: configForm.sslEnabled,
      sslMode: configForm.sslEnabled ? 'require' : 'disable'
    }
  } else if (type === 2) {
    // MilvusVectorConfigDO
    cfg = {
      ...base,
      // token: text(configForm.token),
      username: text(configForm.username),
      password: text(configForm.password),
      database: text(configForm.database) ?? 'default'
    }
  } else {
    // ElasticsearchVectorConfigDO
    cfg = {
      ...base,
      scheme: configForm.scheme,
      username: text(configForm.username),
      password: text(configForm.password),
      sslVerificationDisabled: configForm.sslVerificationDisabled
    }
  }
  const generated = Object.fromEntries(Object.entries(cfg).filter(([, v]) => v !== undefined))
  return JSON.stringify({ ...preservedConfig.value, ...generated })
}

function handleCategoryChange() {
  form.type = form.category === 1 ? 1 : 3
  resetConfigForType(form.type)
}

function handleTypeChange() {
  resetConfigForType(form.type)
}

function resetForm() {
  editingId.value = null
  form.id = undefined
  form.name = ''
  form.description = ''
  form.category = 1
  form.type = 1
  form.status = 1
  form.isDefault = false
  resetConfigForType(form.type)
}

function openCreate() {
  resetForm()
  showModal.value = true
}

function openEdit(row: StoreInstanceResponse) {
  editingId.value = row.id
  form.id = row.id
  form.name = row.name
  form.description = row.description ?? ''
  form.category = row.category
  form.type = row.type
  form.status = row.status ?? 1
  form.isDefault = !!row.isDefault
  resetConfigForType(form.type)
  fillConfigFromParsed(parseConfig(row.config))
  showModal.value = true
}

async function handleSave() {
  try {
    await formRef.value?.validate()
  } catch {
    return
  }
  saving.value = true
  try {
    const payload: StoreInstanceRequest = {
      ...form,
      name: form.name.trim(),
      config: buildConfigJson(form.type)
    }
    if (editingId.value != null) {
      await updateStoreInstance(adminToken.value, payload)
      message.success('存储实例已更新')
    } else {
      await createStoreInstance(adminToken.value, payload)
      message.success('存储实例已创建')
    }
    showModal.value = false
    await reload()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    saving.value = false
  }
}

async function handleTestConnection() {
  testing.value = true
  // testResult.value = null
  try {
    const result = await testStoreInstanceConnection(adminToken.value, {
      type: form.type,
      config: buildConfigJson(form.type),
      id: editingId.value ?? undefined
    })
    // testResult.value = result
    if (result.ok) {
      message.success('连接成功')
    } else {
      message.error(`连接失败: ${result.message}`)
    }
  } catch (e) {
    const msg = e instanceof Error ? e.message : '测试连接失败'
    // testResult.value = { ok: false, message: msg }
    message.error(msg)
  } finally {
    testing.value = false
  }
}

function handleToggleDefault(row: StoreInstanceResponse) {
  const next = row.isDefault ? '取消默认' : '设为默认'
  dialog.warning({
    title: `${next}确认`,
    content: `确认${next}存储实例「${row.name}」？`,
    positiveText: next,
    negativeText: '取消',
    onPositiveClick: async () => {
      defaultId.value = row.id
      try {
        await updateStoreInstanceDefault(adminToken.value, row.id, !row.isDefault)
        message.success(`${row.name} 已${next}`)
        await reload()
      } catch (e) {
        message.error(e instanceof Error ? e.message : `${next}失败`)
      } finally {
        defaultId.value = null
      }
    }
  })
}

function handleToggleStatus(row: StoreInstanceResponse) {
  const next = row.status === 1 ? '停用' : '启用'
  dialog.warning({
    title: `${next}确认`,
    content: `确认${next}存储实例「${row.name}」？`,
    positiveText: next,
    negativeText: '取消',
    onPositiveClick: async () => {
      togglingId.value = row.id
      try {
        const payload: StoreInstanceRequest = {
          id: row.id,
          name: row.name,
          category: row.category,
          type: row.type,
          status: row.status === 1 ? 0 : 1
        }
        await updateStoreInstance(adminToken.value, payload)
        message.success(`${row.name} 已${next}`)
        await reload()
      } catch (e) {
        message.error(e instanceof Error ? e.message : `${next}失败`)
      } finally {
        togglingId.value = null
      }
    }
  })
}

function handleDelete(row: StoreInstanceResponse) {
  void deleteStoreInstances(adminToken.value, [row.id]).then(() => {
    message.success(`「${row.name}」已删除`)
    void reload()
  }).catch((e) => {
    message.error(e instanceof Error ? e.message : '删除失败')
  })
}

function handleBatchDelete() {
  dialog.warning({
    title: '批量删除确认',
    content: `确认删除选中的 ${checkedRowKeys.value.length} 个存储实例？`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await deleteStoreInstances(adminToken.value, checkedRowKeys.value)
        message.success('已删除所选存储实例')
        checkedRowKeys.value = []
        await reload()
      } catch (e) {
        message.error(e instanceof Error ? e.message : '删除失败')
      }
    }
  })
}

onMounted(reload)
</script>

<template>
  <div class="page">
    <PageHeader title="存储实例" description="配置向量库 / 搜索引擎实例：连接参数与默认实例设置。">
      <template #actions>
        <n-button size="small" type="primary" @click="openCreate">
          <template #icon><n-icon :component="Plus" /></template>
          新建存储实例
        </n-button>
      </template>
    </PageHeader>

    <n-card :bordered="true">
      <n-space style="margin-bottom: 12px" :size="8" align="center">
        <n-input v-model:value="search.name" placeholder="实例名称" clearable style="width: 180px" @keyup.enter="handleSearch" />
        <n-select v-model:value="search.category" placeholder="分类" clearable style="width: 130px" :options="categoryOptions" />
        <n-select v-model:value="search.type" placeholder="类型" clearable style="width: 150px" :options="typeOptions" />
        <n-select v-model:value="search.status" placeholder="状态" clearable style="width: 110px" :options="statusOptions" />
        <n-button size="small" type="primary" secondary @click="handleSearch">
          <template #icon><n-icon :component="Search" /></template>
          查询
        </n-button>
        <n-button size="small" quaternary @click="handleSearch">
          <template #icon><n-icon :component="RefreshCw" /></template>
          刷新
        </n-button>
        <n-button
          v-if="checkedRowKeys.length > 0"
          size="small"
          type="error"
          secondary
          @click="handleBatchDelete"
        >
          删除所选（{{ checkedRowKeys.length }}）
        </n-button>
      </n-space>

      <n-data-table
        :loading="loading"
        :columns="columns"
        :data="instances"
        :bordered="false"
        :row-key="(row: StoreInstanceResponse) => row.id"
        :checked-row-keys="checkedRowKeys"
        @update:checked-row-keys="(keys: Array<string | number>) => (checkedRowKeys = keys.map(Number))"
      />
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

    <n-modal v-model:show="showModal" preset="card" :title="editingId != null ? '编辑存储实例' : '新建存储实例'" style="width: 720px" :bordered="false">
      <n-form ref="formRef" label-placement="top" :model="formModel" :rules="rules">
        <n-grid :cols="24" :x-gap="24">
          <n-grid-item :span="24">
            <n-divider title-placement="left" style="margin: 0 0 16px">基本信息</n-divider>
          </n-grid-item>
          <n-form-item-gi :span="24" label="实例名称" path="name">
            <n-input v-model:value="form.name" placeholder="实例显示名称" />
          </n-form-item-gi>
          <n-form-item-gi :span="24" label="实例描述">
            <n-input v-model:value="form.description" type="textarea" :autosize="{ minRows: 2, maxRows: 4 }" placeholder="实例用途 / 备注（可选）" />
          </n-form-item-gi>
<!--          <n-form-item-gi :span="12" label="状态">-->
<!--            <n-select v-model:value="form.status" :options="statusOptions" />-->
<!--          </n-form-item-gi>-->
<!--          <n-form-item-gi :span="12" label="设为该分类下默认实例">-->
<!--            <n-switch v-model:value="form.isDefault" />-->
<!--          </n-form-item-gi>-->
          <n-form-item-gi :span="12" label="分类">
            <n-select v-model:value="form.category" :options="categoryOptions" @update:value="handleCategoryChange" />
          </n-form-item-gi>
          <n-form-item-gi :span="12" label="类型">
            <n-select v-model:value="form.type" :options="formTypeOptions" @update:value="handleTypeChange" />
          </n-form-item-gi>
          <n-grid-item :span="24">
            <n-divider title-placement="left" style="margin: 8px 0 16px">连接参数</n-divider>
          </n-grid-item>
          <!-- 连接参数：按类型动态渲染，保存时自动生成对应 DO 的 JSON -->
          <template v-if="form.type === 4">
            <n-grid-item :span="24">
              <n-text depth="3" style="font-size: 12px">
                该类型不连外部服务：分词与 BM25 统计都走应用自身的业务库（PostgreSQL / MySQL 均可），
                因此无需连接参数，保存后 config 为空对象。
              </n-text>
            </n-grid-item>
          </template>
          <template v-else-if="form.type === 1">
            <n-form-item-gi :span="12" label="主机地址" path="configForm.host">
              <n-input v-model:value="configForm.host" placeholder="localhost" />
            </n-form-item-gi>
            <n-form-item-gi :span="12" label="端口" path="configForm.port">
              <n-input-number v-model:value="configForm.port" :min="1" :max="65535" style="width: 100%" />
            </n-form-item-gi>
            <n-form-item-gi :span="12" label="用户名" path="configForm.username">
              <n-input v-model:value="configForm.username" placeholder="postgres" />
            </n-form-item-gi>
            <n-form-item-gi :span="12" label="密码">
              <n-input v-model:value="configForm.password" type="password" show-password-on="mousedown" placeholder="留空表示不启用密码认证" />
            </n-form-item-gi>
            <n-form-item-gi :span="12" label="数据库名" path="configForm.database">
              <n-input v-model:value="configForm.database" placeholder="v5ai_ai" />
            </n-form-item-gi>
            <n-form-item-gi :span="12" label="启用 SSL">
              <n-space align="center" :size="10" :wrap="false">
                <n-switch v-model:value="configForm.sslEnabled">
                  <template #checked>是</template>
                  <template #unchecked>否</template>
                </n-switch>
                <n-text depth="3" style="font-size: 12px">加密连接</n-text>
              </n-space>
            </n-form-item-gi>
          </template>
          <template v-else-if="form.type === 2">
            <n-form-item-gi :span="12" label="主机地址" path="configForm.host">
              <n-input v-model:value="configForm.host" placeholder="localhost" />
            </n-form-item-gi>
            <n-form-item-gi :span="12" label="端口" path="configForm.port">
              <n-input-number v-model:value="configForm.port" :min="1" :max="65535" style="width: 100%" />
            </n-form-item-gi>
            <!--          <n-form-item label="Token">-->
            <!--            <n-input v-model:value="configForm.token" type="password" show-password-on="mousedown" placeholder="留空表示不启用鉴权" />-->
            <!--          </n-form-item>-->
            <n-form-item-gi :span="12" label="用户名">
              <n-input v-model:value="configForm.username" placeholder="root" />
            </n-form-item-gi>
            <n-form-item-gi :span="12" label="密码">
              <n-input v-model:value="configForm.password" type="password" show-password-on="mousedown" placeholder="留空表示不启用密码认证" />
            </n-form-item-gi>
            <n-form-item-gi :span="12" label="数据库名" path="configForm.database">
              <n-input v-model:value="configForm.database" placeholder="default" />
            </n-form-item-gi>
          </template>
          <template v-else>
            <n-form-item-gi :span="8" label="协议">
              <n-radio-group v-model:value="configForm.scheme">
                <n-radio-button value="http">http</n-radio-button>
                <n-radio-button value="https">https</n-radio-button>
              </n-radio-group>
            </n-form-item-gi>
            <n-form-item-gi :span="8" label="主机地址" path="configForm.host">
              <n-input v-model:value="configForm.host" placeholder="localhost" />
            </n-form-item-gi>
            <n-form-item-gi :span="8" label="端口" path="configForm.port">
              <n-input-number v-model:value="configForm.port" :min="1" :max="65535" style="width: 100%" />
            </n-form-item-gi>
            <n-form-item-gi :span="12" label="用户名">
              <n-input v-model:value="configForm.username" placeholder="留空表示不启用 Basic 认证" />
            </n-form-item-gi>
            <n-form-item-gi :span="12" label="密码">
              <n-input v-model:value="configForm.password" type="password" show-password-on="mousedown" placeholder="留空表示不启用 Basic 认证" />
            </n-form-item-gi>
            <n-form-item-gi :span="12" label="关闭 SSL 证书校验">
              <n-space align="center" :size="10" :wrap="false">
                <n-switch v-model:value="configForm.sslVerificationDisabled">
                  <template #checked>是</template>
                  <template #unchecked>否</template>
                </n-switch>
                <n-text depth="3" style="font-size: 12px">自签证书场景</n-text>
              </n-space>
            </n-form-item-gi>
          </template>
        </n-grid>
      </n-form>
      <template #footer>
<!--        <n-alert v-if="testResult" :type="testResult.ok ? 'success' : 'error'" :bordered="false" style="margin-bottom: 8px">
          {{ testResult.message }}
        </n-alert>-->
        <n-space justify="space-between">
          <n-button secondary :loading="testing" @click="handleTestConnection">测试连接</n-button>
          <div style="display: flex; align-items: center; gap: 8px">
            <n-button @click="showModal = false">取消</n-button>
            <n-button type="primary" :loading="saving" @click="handleSave">保存</n-button>
          </div>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>
