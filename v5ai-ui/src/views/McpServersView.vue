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
  NInputNumber,
  NModal,
  NSelect,
  NSpace,
  NTag,
  useDialog,
  useMessage,
  type DataTableColumns
} from 'naive-ui'
import { Plus } from 'lucide-vue-next'
import PageHeader from '../components/PageHeader.vue'
import StatusTag from '../components/StatusTag.vue'
import {
  createMcpServer,
  disableMcpServer,
  discoverMcpTools,
  listMcpServers,
  listMcpTools,
  testMcpServerConnection,
  updateMcpToolPermission,
  type McpServerResponse,
  type McpToolPermission,
  type McpToolResponse,
  type McpTransportType
} from '../api/client'
import { adminToken } from '../stores/session'

const message = useMessage()
const dialog = useDialog()
const loading = ref(false)
const servers = ref<McpServerResponse[]>([])
const tools = ref<McpToolResponse[]>([])
const activeServer = ref<McpServerResponse | null>(null)

const showCreateModal = ref(false)
const showToolsModal = ref(false)
const saving = ref(false)

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
  { label: 'Streamable HTTP', value: 'STREAMABLE_HTTP' },
  { label: 'SSE', value: 'SSE' },
  { label: 'Stdio', value: 'STDIO' }
]

const permissionOptions = [
  { label: 'ALLOW 允许', value: 'ALLOW' },
  { label: 'APPROVE 审批', value: 'APPROVE' },
  { label: 'DENY 拒绝', value: 'DENY' }
]

const serverColumns: DataTableColumns<McpServerResponse> = [
  { title: 'ID', key: 'id', width: 64 },
  { title: '名称', key: 'name' },
  {
    title: '传输',
    key: 'transportType',
    width: 150,
    render: (row) => h(NTag, { size: 'small', type: row.transportType === 'STDIO' ? 'warning' : 'info', bordered: false }, { default: () => row.transportType })
  },
  { title: 'Endpoint / 命令', key: 'endpoint', render: (row) => row.endpoint ?? '-' },
  { title: '状态', key: 'status', width: 100, render: (row) => h(StatusTag, { status: row.status }) },
  {
    title: '操作',
    key: 'actions',
    width: 280,
    render: (row) =>
      h(
        NSpace,
        { size: 6 },
        {
          default: () => [
            h(NButton, { size: 'small', secondary: true, type: 'primary', onClick: () => handleTestConnection(row) }, { default: () => '测试连接' }),
            h(NButton, { size: 'small', secondary: true, type: 'info', disabled: row.status !== 'ACTIVE', onClick: () => handleDiscover(row) }, { default: () => '发现工具' }),
            h(NButton, { size: 'small', secondary: true, type: 'warning', onClick: () => handleShowTools(row) }, { default: () => '工具' }),
            h(NButton, { size: 'small', secondary: true, type: 'error', disabled: row.status !== 'ACTIVE', onClick: () => handleDisable(row) }, { default: () => '禁用' })
          ]
        }
      )
  }
]

const toolColumns: DataTableColumns<McpToolResponse> = [
  { title: '工具名', key: 'toolName' },
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
    servers.value = await listMcpServers(adminToken.value)
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载 MCP 数据失败')
  } finally {
    loading.value = false
  }
}

function resetForm() {
  form.name = ''
  form.transportType = 'STREAMABLE_HTTP'
  form.endpoint = ''
  form.args = ''
  form.headers = ''
  form.envVars = ''
  form.timeoutSeconds = 30
}

async function handleCreate() {
  saving.value = true
  try {
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
    showCreateModal.value = false
    resetForm()
    await reload()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '创建 MCP Server 失败')
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
    tools.value = await listMcpTools(adminToken.value, row.id)
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

async function handleDisable(row: McpServerResponse) {
  dialog.warning({
    title: '禁用确认',
    content: `确认禁用 MCP Server「${row.name}」？`,
    positiveText: '禁用',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await disableMcpServer(adminToken.value, row.id)
        message.success(`${row.name} 已禁用`)
        await reload()
      } catch (e) {
        message.error(e instanceof Error ? e.message : '禁用失败')
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

onMounted(reload)
</script>

<template>
  <div class="page">
    <PageHeader title="MCP 管理" description="接入外部 MCP Server，发现工具并配置运行时权限。">
      <template #actions>
        <n-button size="small" type="primary" @click="showCreateModal = true">
          <template #icon><n-icon :component="Plus" /></template>
          新建 MCP Server
        </n-button>
      </template>
    </PageHeader>

    <n-card :bordered="true">
      <n-data-table :loading="loading" :columns="serverColumns" :data="servers" :bordered="false" />
    </n-card>

    <n-modal
      v-model:show="showCreateModal"
      preset="card"
      title="新建 MCP Server"
      style="width: 540px"
      :bordered="false"
    >
      <n-form label-placement="top">
        <n-form-item label="名称">
          <n-input v-model:value="form.name" placeholder="平台内唯一标识（也是 MCP 客户端名）" />
        </n-form-item>
        <n-form-item label="传输类型">
          <n-select v-model:value="form.transportType" :options="transportOptions" />
        </n-form-item>
        <n-form-item label="Endpoint（HTTP/SSE 填 URL；Stdio 填可执行命令）">
          <n-input v-model:value="form.endpoint" />
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
          <n-button type="primary" :loading="saving" @click="handleCreate">创建</n-button>
        </n-space>
      </template>
    </n-modal>

    <n-modal
      v-model:show="showToolsModal"
      preset="card"
      :title="`工具列表（${activeServer?.name ?? ''}）`"
      style="width: 640px"
      :bordered="false"
    >
      <n-alert type="info" :bordered="false" style="margin-bottom: 16px">
        运行时会根据 Agent 已发布配置动态注册这些工具；DENY 权限的工具不会被注册。
      </n-alert>
      <n-data-table :columns="toolColumns" :data="tools" :bordered="false" />
    </n-modal>
  </div>
</template>
