<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
import { NAlert, NButton, NCard, NDataTable, NDivider, NForm, NFormItem, NInput, NSelect, NSpace, NTag, useMessage, type DataTableColumns } from 'naive-ui'
import {
  bindMcpServers,
  createMcpServer,
  disableMcpServer,
  discoverMcpTools,
  getMcpServerBindings,
  listAgents,
  listMcpServers,
  listMcpTools,
  testMcpServerConnection,
  updateMcpToolPermission,
  type AgentResponse,
  type McpServerResponse,
  type McpToolPermission,
  type McpToolResponse,
  type McpTransportType
} from '../api/client'
import { adminToken, isLoggedIn } from '../stores/session'

const message = useMessage()
const loading = ref(false)
const servers = ref<McpServerResponse[]>([])
const agents = ref<AgentResponse[]>([])
const selectedServerTools = ref<McpToolResponse[]>([])
const activeServerId = ref<number | null>(null)

const form = reactive({
  name: 'weather-mcp',
  transportType: 'STREAMABLE_HTTP' as McpTransportType,
  endpoint: 'https://mcp.example.com/mcp',
  args: '',
  headers: '',
  envVars: '',
  timeoutSeconds: 30
})

const bindForm = reactive({
  agentKey: 'demo',
  serverIds: [] as number[]
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
  { title: 'ID', key: 'id', width: 60 },
  { title: '名称', key: 'name' },
  {
    title: '传输',
    key: 'transportType',
    render: row => h(NTag, { size: 'small', type: row.transportType === 'STDIO' ? 'warning' : 'info' }, { default: () => row.transportType })
  },
  { title: 'Endpoint/命令', key: 'endpoint', render: row => row.endpoint ?? '-' },
  {
    title: '状态',
    key: 'status',
    render: row => h(NTag, { size: 'small', type: row.status === 'ACTIVE' ? 'success' : 'default' }, { default: () => row.status })
  },
  {
    title: '最近测试',
    key: 'lastTestStatus',
    render: row => row.lastTestStatus
      ? h(NTag, { size: 'small', type: row.lastTestStatus === 'ok' ? 'success' : 'error' }, { default: () => row.lastTestStatus })
      : '-'
  },
  {
    title: '操作',
    key: 'actions',
    width: 330,
    render: row => h(NSpace, { size: 6 }, { default: () => [
      h(NButton, { size: 'small', type: 'primary', secondary: true, disabled: !isLoggedIn.value, onClick: () => handleTestConnection(row) }, { default: () => '测试连接' }),
      h(NButton, { size: 'small', type: 'info', secondary: true, disabled: !isLoggedIn.value || row.status !== 'ACTIVE', onClick: () => handleDiscover(row) }, { default: () => '发现工具' }),
      h(NButton, { size: 'small', type: 'warning', secondary: true, disabled: !isLoggedIn.value, onClick: () => handleShowTools(row) }, { default: () => '工具' }),
      h(NButton, { size: 'small', type: 'error', secondary: true, disabled: !isLoggedIn.value || row.status !== 'ACTIVE', onClick: () => handleDisable(row) }, { default: () => '禁用' })
    ] })
  }
]

const toolColumns: DataTableColumns<McpToolResponse> = [
  { title: '工具名', key: 'toolName' },
  { title: '描述', key: 'description', ellipsis: { tooltip: true } },
  {
    title: '只读',
    key: 'readOnly',
    render: row => h(NTag, { size: 'small', type: row.readOnly ? 'success' : 'default' }, { default: () => row.readOnly ? '只读' : '可写' })
  },
  {
    title: '权限',
    key: 'permission',
    render: row => h(NSelect, {
      size: 'small',
      value: row.permission,
      options: permissionOptions,
      disabled: !isLoggedIn.value,
      onUpdateValue: (value: McpToolPermission) => handlePermissionChange(row, value)
    })
  }
]

async function reload() {
  if (!isLoggedIn.value) return
  loading.value = true
  try {
    const [serverRows, appRows] = await Promise.all([
      listMcpServers(adminToken.value),
      listAgents(adminToken.value)
    ])
    servers.value = serverRows
    agents.value = appRows
  } catch (error) {
    message.error(error instanceof Error ? error.message : '加载 MCP 数据失败')
  } finally {
    loading.value = false
  }
}

async function handleCreate() {
  try {
    await createMcpServer(adminToken.value, {
      name: form.name,
      transportType: form.transportType,
      endpoint: form.endpoint || undefined,
      args: form.args ? form.args.split(',').map((item: string) => item.trim()).filter(Boolean) : undefined,
      headers: parseMap(form.headers),
      envVars: parseMap(form.envVars),
      timeoutSeconds: form.timeoutSeconds
    })
    message.success('MCP Server 已创建')
    await reload()
  } catch (error) {
    message.error(error instanceof Error ? error.message : '创建 MCP Server 失败')
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
  } catch (error) {
    message.error(error instanceof Error ? error.message : '测试连接失败')
  }
}

async function handleDiscover(row: McpServerResponse) {
  try {
    const tools = await discoverMcpTools(adminToken.value, row.id)
    message.success(`发现 ${tools.length} 个工具`)
    activeServerId.value = row.id
    selectedServerTools.value = tools
  } catch (error) {
    message.error(error instanceof Error ? error.message : '发现工具失败')
  }
}

async function handleShowTools(row: McpServerResponse) {
  try {
    selectedServerTools.value = await listMcpTools(adminToken.value, row.id)
    activeServerId.value = row.id
  } catch (error) {
    message.error(error instanceof Error ? error.message : '加载工具失败')
  }
}

async function handlePermissionChange(row: McpToolResponse, permission: McpToolPermission) {
  try {
    const updated = await updateMcpToolPermission(adminToken.value, row.serverId, row.toolName, permission)
    const index = selectedServerTools.value.findIndex(tool => tool.id === row.id)
    if (index >= 0) {
      selectedServerTools.value[index] = updated
    }
    message.success(`${row.toolName} 权限已设为 ${permission}`)
  } catch (error) {
    message.error(error instanceof Error ? error.message : '更新权限失败')
  }
}

async function handleDisable(row: McpServerResponse) {
  try {
    await disableMcpServer(adminToken.value, row.id)
    message.success(`${row.name} 已禁用`)
    await reload()
  } catch (error) {
    message.error(error instanceof Error ? error.message : '禁用失败')
  }
}

async function reloadBindings() {
  try {
    bindForm.serverIds = await getMcpServerBindings(adminToken.value, bindForm.agentKey)
  } catch {
    bindForm.serverIds = []
  }
}

async function handleBind() {
  try {
    await bindMcpServers(adminToken.value, bindForm.agentKey, bindForm.serverIds)
    message.success(`已更新 ${bindForm.agentKey} 的 MCP 绑定`)
    await reloadBindings()
  } catch (error) {
    message.error(error instanceof Error ? error.message : '绑定失败')
  }
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

const serverOptions = computed(() =>
  servers.value.map(server => ({ label: `${server.name} (#${server.id})`, value: server.id }))
)

onMounted(async () => {
  await reload()
  if (agents.value.length > 0) {
    bindForm.agentKey = agents.value[0].agentKey
  }
  await reloadBindings()
})
</script>

<template>
  <div class="grid">
    <n-alert v-if="!isLoggedIn" class="wide" type="warning" title="需要管理员 Token">
      请先在 Dashboard 登录，再管理 MCP Server。
    </n-alert>

    <n-card title="创建 MCP Server" class="panel">
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
          <n-input-number v-model:value="form.timeoutSeconds" :min="5" :max="300" />
        </n-form-item>
        <n-button type="primary" :disabled="!isLoggedIn" @click="handleCreate">创建 MCP Server</n-button>
      </n-form>
    </n-card>

    <n-card title="Agent 绑定 MCP" class="panel">
      <n-form label-placement="top">
        <n-form-item label="Agent App Key">
          <n-select
            v-model:value="bindForm.agentKey"
            filterable
            tag
            :options="agents.map(item => ({ label: item.agentKey, value: item.agentKey }))"
            @update:value="reloadBindings"
          />
        </n-form-item>
        <n-form-item label="绑定 MCP Server">
          <n-select
            v-model:value="bindForm.serverIds"
            multiple
            clearable
            :options="serverOptions"
            placeholder="选择该 Agent 可用的 MCP Server（发布前请先完成 Tool 发现）"
          />
        </n-form-item>
        <n-button secondary type="info" :disabled="!isLoggedIn" @click="handleBind">保存 MCP 绑定</n-button>
      </n-form>
    </n-card>

    <n-card title="MCP Server 列表" class="panel wide">
      <n-space vertical>
        <n-button secondary :disabled="!isLoggedIn" :loading="loading" @click="reload">刷新列表</n-button>
        <n-data-table :loading="loading" :columns="serverColumns" :data="servers" />
      </n-space>
    </n-card>

    <n-card v-if="activeServerId !== null" :title="`工具列表（Server #${activeServerId}）`" class="panel wide">
      <n-space vertical>
        <n-alert type="info" :show-icon="true">
          运行时会根据 Agent 已发布配置动态注册这些工具；DENY 权限的工具不会被注册。
        </n-alert>
        <n-data-table :columns="toolColumns" :data="selectedServerTools" />
      </n-space>
    </n-card>
  </div>
</template>
