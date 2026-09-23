<script setup lang="ts">
import { h, onMounted, reactive, ref } from 'vue'
import {
  NAlert,
  NButton,
  NCard,
  NDataTable,
  NDivider,
  NForm,
  NFormItem,
  NIcon,
  NInput,
  NInputNumber,
  NModal,
  NPagination,
  NRadioButton,
  NRadioGroup,
  NSelect,
  NSwitch,
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
  createMcpServer,
  disableMcpServer,
  discoverMcpTools,
  enableMcpServer,
  listMcpServers,
  listMcpTools,
  testMcpServerConnection,
  updateMcpServer,
  updateMcpToolPermission,
  type McpServerResponse,
  type McpToolPermission,
  type McpToolResponse,
  type McpTransportType
} from '../api/client'
import { adminToken } from '../stores/session'
import { formatDateTime } from '../utils/dateUtils'

const message = useMessage()
const dialog = useDialog()
const loading = ref(false)
const servers = ref<McpServerResponse[]>([])
const tools = ref<McpToolResponse[]>([])
const activeServer = ref<McpServerResponse | null>(null)
const pagination = reactive({ page: 1, pageSize: 10, itemCount: 0 })

const showCreateModal = ref(false)
const showToolsModal = ref(false)
const saving = ref(false)
const editingId = ref<number | null>(null)
const togglingId = ref<number | null>(null)

const form = reactive({
  name: '',
  transportType: 'STREAMABLE_HTTP' as McpTransportType,
  endpoint: '',
  args: '',
  headers: '',
  envVars: '',
  timeoutSeconds: 30
})

const transportOptions = [
  { label: 'HTTP', value: 'STREAMABLE_HTTP' },
  { label: 'SSE', value: 'SSE' },
  { label: 'Stdio', value: 'STDIO' }
]

const permissionOptions = [
  { label: 'ALLOW 允许', value: 'ALLOW' },
  { label: 'APPROVE 审批', value: 'APPROVE' },
  { label: 'DENY 拒绝', value: 'DENY' }
]

const statusOptions = [
  { label: '启用', value: 'ACTIVE' },
  { label: '禁用', value: 'DISABLED' }
]

const search = reactive({
  name: '',
  transportType: null as string | null,
  status: null as string | null
})

const serverColumns: DataTableColumns<McpServerResponse> = [
  { title: 'ID', key: 'id', width: 64 },
  { title: '名称', key: 'name', width: 160 },
  {
    title: '传输',
    key: 'transportType',
    width: 80,
    render: (row) => h(NTag, { size: 'small', type: transportTypeFormat(row), bordered: false }, { default: () => row.transportType === 'STREAMABLE_HTTP' ? "HTTP" : row.transportType })
  },
  { title: 'Endpoint / 命令', width: '260', key: 'endpoint', render: (row) => row.endpoint ?? '-' },
  {
    title: '状态',
    key: 'status',
    width: 80,
    render: (row) =>
      h(NSwitch, {
        value: row.status === 'ACTIVE',
        loading: togglingId.value === row.id,
        onUpdateValue: () => handleToggleStatus(row)
      }, {
        checked: () => h('span', '启'),
        unchecked: () => h('span', '禁')
      })
  },
  { title: '测试状态', key: 'lastTestStatus', width: 100, render: (row) => h(StatusTag, { status: row.lastTestStatus === 'ok' ? 'success' : row.lastTestStatus }) },
  { title: '测试时间', key: 'lastTestedAt', width: 182, render: (row) => fmtTime(row.lastTestedAt) },
  { title: '创建时间', key: 'createdAt', width: 182, render: (row) => fmtTime(row.createdAt) },
  { title: '更新时间', key: 'updatedAt', width: 182, render: (row) => fmtTime(row.updatedAt) },
  {
    title: '操作',
    key: 'actions',
    width: 180,
    render: (row) =>
      h(RowActions, {
        actions: [
          { key: 'edit', label: '编辑', quaternary: true, onClick: () => openEditModal(row) },
          { key: 'test', label: '测试连接', secondary: true, type: 'primary', onClick: () => handleTestConnection(row) },
          { key: 'discover', label: '发现工具', secondary: true, type: 'info', disabled: row.status !== 'ACTIVE', onClick: () => handleDiscover(row) },
          { key: 'tools', label: '工具', secondary: true, type: 'warning', onClick: () => handleShowTools(row) }
        ]
      })
  }
]

const toolColumns: DataTableColumns<McpToolResponse> = [
  { title: 'ID', key: 'id', width: 64 },
  { title: '工具名', key: 'toolName', width: 'auto' },
  { title: '描述', key: 'description', ellipsis: { tooltip: true } },
  {
    title: '只读',
    key: 'readOnly',
    width: 90,
    render: (row) => h(NTag, { size: 'small', type: row.readOnly ? 'success' : 'default', bordered: false }, { default: () => (row.readOnly ? '只读' : '可写') })
  },
  {
    title: '权限',
    key: 'permission',
    width: 160,
    render: (row) =>
      h(NSelect, {
        size: 'small',
        value: row.permission,
        options: permissionOptions,
        onUpdateValue: (value: McpToolPermission) => handlePermissionChange(row, value)
      })
  }
]

async function reload() {
  loading.value = true
  try {
    const rows = await listMcpServers(adminToken.value, {
      pageNum: pagination.page,
      pageSize: pagination.pageSize,
      name: search.name || undefined,
      transportType: search.transportType ?? undefined,
      status: search.status ?? undefined
    })
    servers.value = rows.rows
    pagination.itemCount = rows.total
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载 MCP 数据失败')
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

function resetForm() {
  editingId.value = null
  form.name = ''
  form.transportType = 'STREAMABLE_HTTP'
  form.endpoint = ''
  form.args = ''
  form.headers = ''
  form.envVars = ''
  form.timeoutSeconds = 30
}

function openCreateModal() {
  resetForm()
  showCreateModal.value = true
}

function openEditModal(row: McpServerResponse) {
  editingId.value = row.id
  form.name = row.name
  form.transportType = row.transportType
  form.endpoint = row.endpoint ?? ''
  form.args = (row.args ?? []).join(', ')
  form.headers = mapToText(row.headers)
  form.envVars = mapToText(row.envVars)
  form.timeoutSeconds = row.timeoutSeconds ?? 30
  showCreateModal.value = true
}

function transportTypeFormat(row: McpServerResponse) {
  switch (row.transportType) {
    case 'STREAMABLE_HTTP':
      return 'info'
    case 'SSE':
      return 'success'
    case 'STDIO':
      return 'warning'
  }
}

function fmtTime(value?: string | null): string {
  return value ? (formatDateTime(value) ?? '—') : '—'
}

async function handleSave() {
  saving.value = true
  try {
    if (editingId.value != null) {
      await updateMcpServer(adminToken.value, {
        id: editingId.value,
        name: form.name,
        transportType: form.transportType,
        endpoint: form.endpoint || undefined,
        args: form.args ? form.args.split(',').map((s) => s.trim()).filter(Boolean) : undefined,
        headers: parseMap(form.headers),
        envVars: parseMap(form.envVars),
        timeoutSeconds: form.timeoutSeconds
      })
      message.success('MCP Server 已更新')
    } else {
      await createMcpServer(adminToken.value, {
        name: form.name,
        transportType: form.transportType,
        endpoint: form.endpoint || undefined,
        args: form.args ? form.args.split(',').map((s) => s.trim()).filter(Boolean) : undefined,
        headers: parseMap(form.headers),
        envVars: parseMap(form.envVars),
        timeoutSeconds: form.timeoutSeconds
      })
      message.success('MCP Server 已创建')
    }
    showCreateModal.value = false
    resetForm()
    await reload()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '保存 MCP Server 失败')
  } finally {
    saving.value = false
  }
}

async function handleTestConnection(row: McpServerResponse) {
  try {
    const result = await testMcpServerConnection(adminToken.value, row.id)
    if (result.ok) {
      message.success(`连接成功，发现 ${result.toolCount} 个工具`)
    } else {
      message.error(`连接失败: ${result.message}`)
    }
    await reload()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '测试连接失败')
  }
}

async function handleDiscover(row: McpServerResponse) {
  try {
    const discovered = await discoverMcpTools(adminToken.value, row.id)
    message.success(`发现 ${discovered.length} 个工具`)
    tools.value = discovered
    activeServer.value = row
    showToolsModal.value = true
  } catch (e) {
    message.error(e instanceof Error ? e.message : '发现工具失败')
  }
}

async function handleShowTools(row: McpServerResponse) {
  try {
    tools.value = (await listMcpTools(adminToken.value, row.id)).rows
    activeServer.value = row
    showToolsModal.value = true
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载工具失败')
  }
}

async function handlePermissionChange(row: McpToolResponse, permission: McpToolPermission) {
  try {
    const updated = await updateMcpToolPermission(adminToken.value, row.serverId, row.toolName, permission)
    const index = tools.value.findIndex((tool) => tool.id === row.id)
    if (index >= 0) tools.value[index] = updated
    message.success(`${row.toolName} 权限已设为 ${permission}`)
  } catch (e) {
    message.error(e instanceof Error ? e.message : '更新权限失败')
  }
}

async function handleToggleStatus(row: McpServerResponse) {
  const next = row.status === 'ACTIVE' ? '禁用' : '启用'
  dialog.warning({
    title: `${next}确认`,
    content: `确认${next} MCP Server「${row.name}」？`,
    positiveText: next,
    negativeText: '取消',
    onPositiveClick: async () => {
      togglingId.value = row.id
      try {
        if (row.status === 'ACTIVE') {
          await disableMcpServer(adminToken.value, row.id)
        } else {
          await enableMcpServer(adminToken.value, row.id)
        }
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

function parseMap(text: string): Record<string, string> | undefined {
  const result: Record<string, string> = {}
  for (const line of text.split('\n')) {
    const trimmed = line.trim()
    if (!trimmed) continue
    const separator = trimmed.indexOf('=')
    if (separator > 0) {
      result[trimmed.slice(0, separator).trim()] = trimmed.slice(separator + 1).trim()
    }
  }
  return Object.keys(result).length > 0 ? result : undefined
}

function mapToText(map: Record<string, string> | null | undefined): string {
  return Object.entries(map ?? {})
    .map(([key, value]) => `${key}=${value}`)
    .join('\n')
}

onMounted(reload)
</script>

<template>
  <div class="page">
    <PageHeader title="MCP 管理" description="接入外部 MCP Server，发现工具并配置运行时权限。">
      <template #actions>
        <n-button size="small" type="primary" @click="openCreateModal">
          <template #icon><n-icon :component="Plus" /></template>
          新建 MCP Server
        </n-button>
      </template>
    </PageHeader>

    <n-card :bordered="true">
      <n-space style="margin-bottom: 12px" :size="8" align="center">
        <n-input v-model:value="search.name" placeholder="名称" clearable style="width: 200px" @keyup.enter="handleSearch" />
        <n-select
          v-model:value="search.transportType"
          placeholder="传输类型"
          clearable
          style="width: 150px"
          :options="transportOptions"
        />
        <n-select
          v-model:value="search.status"
          placeholder="状态"
          clearable
          style="width: 130px"
          :options="statusOptions"
        />
        <n-button size="small" type="primary" secondary @click="handleSearch">
          <template #icon><n-icon :component="Search" /></template>
          查询
        </n-button>
        <n-button size="small" quaternary @click="handleSearch">
          <template #icon><n-icon :component="RefreshCw" /></template>
          刷新
        </n-button>
      </n-space>

      <n-data-table
        :loading="loading"
        :columns="serverColumns"
        :data="servers"
        :bordered="false"
        :scroll-x="serverColumns.reduce((s, c) => s + (typeof c.width === 'number' ? c.width : 240), 0)"
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

    <n-modal
      v-model:show="showCreateModal"
      preset="card"
      :title="editingId != null ? '编辑 MCP Server' : '新建 MCP Server'"
      style="width: 560px"
      :bordered="false"
    >
      <n-form label-placement="top">
        <n-divider title-placement="left" style="margin: 0 0 16px">基本信息</n-divider>
        <n-form-item label="名称">
          <n-input v-model:value="form.name" placeholder="平台内唯一标识（也是 MCP 客户端名）" />
        </n-form-item>
        <n-form-item label="传输类型">
          <n-radio-group v-model:value="form.transportType">
            <n-radio-button value="STREAMABLE_HTTP">HTTP</n-radio-button>
            <n-radio-button value="SSE">SSE</n-radio-button>
            <n-radio-button value="STDIO">Stdio</n-radio-button>
          </n-radio-group>
        </n-form-item>

        <n-divider title-placement="left" style="margin: 8px 0 16px">连接配置</n-divider>
        <n-form-item label="Endpoint（HTTP/SSE 填 URL；Stdio 填可执行命令）">
          <n-input v-model:value="form.endpoint" :placeholder="form.transportType === 'STDIO' ? 'node 或 D:/nvm/v24.9.0/node.exe' : 'https://mcp.example.com/mcp'" />
        </n-form-item>
        <n-form-item v-if="form.transportType === 'STDIO'" label="Stdio 参数（逗号分隔）">
          <n-input v-model:value="form.args" placeholder="-m, mcp_server_git" />
        </n-form-item>
        <n-form-item v-if="form.transportType !== 'STDIO'" label="请求头（每行 key=value）">
          <n-input v-model:value="form.headers" type="textarea" :autosize="{ minRows: 2, maxRows: 5 }" placeholder="Authorization=Bearer xxx" />
        </n-form-item>
        <n-form-item v-if="form.transportType === 'STDIO'" label="环境变量（每行 key=value）">
          <n-input v-model:value="form.envVars" type="textarea" :autosize="{ minRows: 2, maxRows: 5 }" placeholder="API_KEY=xxx" />
        </n-form-item>
        <n-form-item label="超时（秒）">
          <n-input-number v-model:value="form.timeoutSeconds" :min="5" :max="300" style="width: 100%" />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showCreateModal = false">取消</n-button>
          <n-button type="primary" :loading="saving" @click="handleSave">{{ editingId != null ? '保存' : '创建' }}</n-button>
        </n-space>
      </template>
    </n-modal>

    <n-modal
      v-model:show="showToolsModal"
      preset="card"
      :title="`工具列表（${activeServer?.name ?? ''} 共${tools.length} 个工具）`"
      style="width: 70%"
      :bordered="false"
    >
      <n-alert type="info" :bordered="false" style="margin-bottom: 16px">
        运行时会根据 Agent 已发布配置动态注册这些工具；DENY 权限的工具不会被注册。
      </n-alert>
      <n-data-table
        :columns="toolColumns"
        :data="tools"
        :bordered="false"
        :scroll-x="toolColumns.reduce((s, c) => s + (typeof c.width === 'number' ? c.width : 240), 0)"
      />
    </n-modal>
  </div>
</template>
