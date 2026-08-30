<script setup lang="ts">
import { h, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  NButton,
  NCard,
  NDataTable,
  NForm,
  NFormItem,
  NIcon,
  NInput,
  NModal,
  NPagination,
  useDialog,
  useMessage,
  type DataTableColumns
} from 'naive-ui'
import { Plus } from 'lucide-vue-next'
import PageHeader from '../components/PageHeader.vue'
import RowActions from '../components/RowActions.vue'
import StatusTag from '../components/StatusTag.vue'
import {
  createWorkflow,
  disableWorkflow,
  getWorkflowRun,
  listWorkflowRuns,
  listWorkflows,
  publishWorkflow,
  type WorkflowNodeRunResponse,
  type WorkflowResponse,
  type WorkflowRunResponse
} from '../api/client'
import { adminToken } from '../stores/session'

const router = useRouter()
const message = useMessage()
const dialog = useDialog()
const loading = ref(false)
const workflows = ref<WorkflowResponse[]>([])
const pagination = reactive({ page: 1, pageSize: 10, itemCount: 0 })

const showCreateModal = ref(false)
const showRunsModal = ref(false)
const showRunDetailModal = ref(false)
const saving = ref(false)
const activeWorkflow = ref<WorkflowResponse | null>(null)
const runs = ref<WorkflowRunResponse[]>([])
const runDetail = ref<{ run: WorkflowRunResponse; nodeRuns: WorkflowNodeRunResponse[] } | null>(null)

const form = reactive({
  workflowKey: '',
  name: '',
  description: ''
})

const workflowColumns: DataTableColumns<WorkflowResponse> = [
  { title: '名称', key: 'name' },
  { title: 'Key', key: 'workflowKey' },
  {
    title: '状态',
    key: 'status',
    width: 100,
    render: (row) => h(StatusTag, { status: row.status })
  },
  {
    title: '已发布版本',
    key: 'publishedVersion',
    width: 110,
    render: (row) => (row.publishedVersion ? `v${row.publishedVersion}` : '—')
  },
  {
    title: '操作',
    key: 'actions',
    width: 220,
    render: (row) =>
      h(RowActions, {
        actions: [
          { key: 'edit', label: '编排', secondary: true, type: 'primary', onClick: () => handleEdit(row) },
          { key: 'publish', label: '发布', secondary: true, type: 'info', disabled: row.status === 'DISABLED', onClick: () => handlePublish(row) },
          { key: 'runs', label: '运行记录', secondary: true, type: 'warning', onClick: () => openRuns(row) },
          { key: 'disable', label: '禁用', secondary: true, type: 'error', disabled: row.status === 'DISABLED', onClick: () => handleDisable(row) }
        ]
      })
  }
]

const runColumns: DataTableColumns<WorkflowRunResponse> = [
  {
    title: '运行ID',
    key: 'runId',
    width: 120,
    render: (row) => row.runId.slice(0, 8)
  },
  { title: '状态', key: 'status', width: 90, render: (row) => h(StatusTag, { status: row.status }) },
  { title: '版本', key: 'workflowVersion', width: 70, render: (row) => (row.workflowVersion ? `v${row.workflowVersion}` : '—') },
  { title: '开始时间', key: 'startedAt', width: 170, render: (row) => fmtTime(row.startedAt) },
  { title: '结束时间', key: 'finishedAt', width: 170, render: (row) => fmtTime(row.finishedAt) },
  {
    title: '操作',
    key: 'actions',
    width: 90,
    render: (row) =>
      h(NButton, { size: 'small', secondary: true, type: 'primary', onClick: () => openRunDetail(row.runId) }, { default: () => '详情' })
  }
]

const nodeRunColumns: DataTableColumns<WorkflowNodeRunResponse> = [
  { title: '节点', key: 'nodeId', width: 130 },
  { title: '类型', key: 'nodeType', width: 90 },
  { title: '状态', key: 'status', width: 90, render: (row) => h(StatusTag, { status: row.status }) },
  {
    title: '输出',
    key: 'outputs',
    ellipsis: { tooltip: true },
    render: (row) => (row.outputs ? JSON.stringify(row.outputs) : '—')
  },
  {
    title: '错误',
    key: 'error',
    ellipsis: { tooltip: true },
    render: (row) => row.error ?? '—'
  }
]

function fmtTime(value?: string | null): string {
  return value ? new Date(value).toLocaleString() : '—'
}

async function reload() {
  loading.value = true
  try {
    const rows = await listWorkflows(adminToken.value, { pageNum: pagination.page, pageSize: pagination.pageSize })
    workflows.value = rows.rows
    pagination.itemCount = rows.total
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载工作流失败')
  } finally {
    loading.value = false
  }
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
  form.workflowKey = ''
  form.name = ''
  form.description = ''
}

async function handleCreate() {
  saving.value = true
  try {
    const created = await createWorkflow(adminToken.value, {
      workflowKey: form.workflowKey,
      name: form.name,
      description: form.description || undefined
    })
    message.success('工作流已创建')
    showCreateModal.value = false
    resetForm()
    await reload()
    router.push({ name: 'workflow-editor', params: { key: created.workflowKey } })
  } catch (e) {
    message.error(e instanceof Error ? e.message : '创建工作流失败')
  } finally {
    saving.value = false
  }
}

function handleEdit(row: WorkflowResponse) {
  router.push({ name: 'workflow-editor', params: { key: row.workflowKey } })
}

async function handlePublish(row: WorkflowResponse) {
  try {
    const published = await publishWorkflow(adminToken.value, row.workflowKey)
    message.success(`已发布 v${published.publishedVersion}`)
    await reload()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '发布失败')
  }
}

function handleDisable(row: WorkflowResponse) {
  dialog.warning({
    title: '禁用确认',
    content: `确认禁用工作流「${row.name}」？禁用后无法运行。`,
    positiveText: '禁用',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await disableWorkflow(adminToken.value, row.workflowKey)
        message.success(`${row.name} 已禁用`)
        await reload()
      } catch (e) {
        message.error(e instanceof Error ? e.message : '禁用失败')
      }
    }
  })
}

async function openRuns(row: WorkflowResponse) {
  activeWorkflow.value = row
  try {
    runs.value = (await listWorkflowRuns(adminToken.value, row.workflowKey)).rows
  } catch (e) {
    runs.value = []
    message.error(e instanceof Error ? e.message : '加载运行记录失败')
  }
  showRunsModal.value = true
}

async function openRunDetail(runId: string) {
  try {
    const detail = await getWorkflowRun(adminToken.value, runId)
    runDetail.value = detail
    showRunDetailModal.value = true
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载运行详情失败')
  }
}

onMounted(reload)
</script>

<template>
  <div class="page">
    <PageHeader title="工作流" description="将多个已发布 Agent 编排为有向图：节点、变量、条件分支与运行记录。">
      <template #actions>
        <n-button size="small" type="primary" @click="showCreateModal = true">
          <template #icon><n-icon :component="Plus" /></template>
          新建工作流
        </n-button>
      </template>
    </PageHeader>

    <n-card :bordered="true">
      <n-data-table :loading="loading" :columns="workflowColumns" :data="workflows" :bordered="false" />
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

    <n-modal v-model:show="showCreateModal" preset="card" title="新建工作流" style="width: 520px" :bordered="false">
      <n-form label-placement="top">
        <n-form-item label="名称">
          <n-input v-model:value="form.name" placeholder="工作流显示名称" />
        </n-form-item>
        <n-form-item label="Key（唯一标识）">
          <n-input v-model:value="form.workflowKey" placeholder="如 support-ticket" />
        </n-form-item>
        <n-form-item label="描述">
          <n-input v-model:value="form.description" type="textarea" :autosize="{ minRows: 2, maxRows: 4 }" placeholder="可选" />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showCreateModal = false">取消</n-button>
          <n-button type="primary" :loading="saving" @click="handleCreate">创建并编排</n-button>
        </n-space>
      </template>
    </n-modal>

    <n-modal v-model:show="showRunsModal" preset="card" :title="`运行记录（${activeWorkflow?.name ?? ''}）`" style="width: 760px" :bordered="false">
      <n-data-table :columns="runColumns" :data="runs" :bordered="false" />
    </n-modal>

    <n-modal v-model:show="showRunDetailModal" preset="card" :title="`运行详情（${runDetail?.run.runId.slice(0, 8) ?? ''}）`" style="width: 780px" :bordered="false">
      <n-data-table v-if="runDetail" :columns="nodeRunColumns" :data="runDetail.nodeRuns" :bordered="false" />
    </n-modal>
  </div>
</template>
