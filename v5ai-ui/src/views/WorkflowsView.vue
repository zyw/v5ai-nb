<script setup lang="ts">
import { computed, h, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  NButton,
  NCard,
  NDataTable,
  NForm,
  NFormItem,
  NFormItemGi,
  NGrid,
  NIcon,
  NInput,
  NModal,
  NPagination,
  NSelect,
  useDialog,
  useMessage,
  type DataTableColumns
} from 'naive-ui'
import { Plus, RefreshCw, Search } from 'lucide-vue-next'
import PageHeader from '../components/PageHeader.vue'
import RowActions from '../components/RowActions.vue'
import StatusTag from '../components/StatusTag.vue'
import {
  createWorkflow,
  disableWorkflow,
  enableWorkflow,
  getWorkflowRun,
  listWorkflowRuns,
  listWorkflows,
  publishWorkflow,
  updateWorkflow,
  type WorkflowNodeRunResponse,
  type WorkflowResponse,
  type WorkflowRunResponse
} from '../api/client'
import { adminToken } from '../stores/session'
import { formatDateTime } from '../utils/dateUtils'

const router = useRouter()
const message = useMessage()
const dialog = useDialog()
const loading = ref(false)
const workflows = ref<WorkflowResponse[]>([])
const selectedWorkflowIds = ref<number[]>([])
const workflowTableContainer = ref<HTMLElement | null>(null)
const workflowTableWidth = ref(0)
let workflowTableResizeObserver: ResizeObserver | null = null
const pagination = reactive({ page: 1, pageSize: 10, itemCount: 0 })
const search = reactive({ name: '', workflowKey: '', status: null as string | null })

const statusOptions = [
  { label: '草稿', value: 'DRAFT' },
  { label: '已发布', value: 'PUBLISHED' },
  { label: '已禁用', value: 'DISABLED' }
]

const showCreateModal = ref(false)
const showEditModal = ref(false)
const showRunsModal = ref(false)
const showRunDetailModal = ref(false)
const saving = ref(false)
const editSaving = ref(false)
const activeWorkflow = ref<WorkflowResponse | null>(null)
const runs = ref<WorkflowRunResponse[]>([])
const runDetail = ref<{ run: WorkflowRunResponse; nodeRuns: WorkflowNodeRunResponse[] } | null>(null)

const form = reactive({
  workflowKey: '',
  name: '',
  description: ''
})

const editForm = reactive({
  workflowKey: '',
  name: '',
  description: ''
})

const workflowColumns: DataTableColumns<WorkflowResponse> = [
  { type: 'selection' },
  { title: 'ID', key: 'id', width: 64 },
  { title: '名称', key: 'name', width: 180 },
  { title: 'Key', key: 'workflowKey', width: 180 },
  { title: '描述', key: 'description',  ellipsis: { tooltip: true }, render: (row) => row.description || '—' },
  {
    title: '状态',
    key: 'status',
    width: 90,
    render: (row) => h(StatusTag, { status: row.status })
  },
  {
    title: '发布版本',
    key: 'publishedVersion',
    width: 85,
    render: (row) => (row.publishedVersion ? `v${row.publishedVersion}` : '—')
  },
  { title: '发布时间', key: 'publishedAt', width: 182, render: (row) => fmtTime(row.publishedAt) },
  // { title: '创建时间', key: 'createdAt', width: 182, render: (row) => fmtTime(row.createdAt) },
  { title: '更新时间', key: 'updatedAt', width: 182, render: (row) => fmtTime(row.updatedAt) },
  {
    title: '操作',
    key: 'actions',
    width: 270,
    render: (row) =>
      h(RowActions, {
        actions: [
          { key: 'edit', label: '编排', secondary: true, type: 'primary', onClick: () => handleEdit(row) },
          { key: 'metadata', label: '编辑', secondary: true, type: 'default', onClick: () => openEditModal(row) },
          { key: 'publish', label: '发布', secondary: true, type: 'info', disabled: row.status === 'DISABLED', onClick: () => handlePublish(row) },
          { key: 'runs', label: '运行记录', secondary: true, type: 'warning', onClick: () => openRuns(row) },
          {
            key: 'toggle-status',
            label: row.status === 'DISABLED' ? '启用' : '禁用',
            secondary: true,
            type: row.status === 'DISABLED' ? 'success' : 'error',
            onClick: () => handleToggleStatus(row)
          }
        ]
      })
  }
]

/** 固定列的最小宽度；描述列在有足够空间时自适应填充，不参与滚动阈值计算。 */
const workflowMinTableWidth = 1300
const workflowTableScrollX = computed<number | undefined>(() => {
  if (!workflowTableWidth.value) return undefined
  return workflowTableWidth.value >= workflowMinTableWidth ? undefined : workflowMinTableWidth
})

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
  {
    title: '节点',
    key: 'nodeName',
    width: 'auto',
    render: (row) => (row.nodeName ? `${row.nodeName} (${row.nodeId})` : row.nodeId)
  },
  { title: '类型', key: 'nodeType', width: 110 },
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
  return value ? (formatDateTime(value) ?? '—') : '—'
}

async function reload() {
  loading.value = true
  try {
    const rows = await listWorkflows(adminToken.value, {
      pageNum: pagination.page,
      pageSize: pagination.pageSize,
      name: search.name || undefined,
      workflowKey: search.workflowKey || undefined,
      status: search.status ?? undefined
    })
    workflows.value = rows.rows
    pagination.itemCount = rows.total
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载工作流失败')
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

function handleWorkflowSelection(keys: Array<string | number>) {
  selectedWorkflowIds.value = keys.map(Number)
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

function openEditModal(row: WorkflowResponse) {
  editForm.workflowKey = row.workflowKey
  editForm.name = row.name
  editForm.description = row.description ?? ''
  showEditModal.value = true
}

async function handleSaveEdit() {
  const name = editForm.name.trim()
  if (!name) {
    message.warning('请输入工作流名称')
    return
  }
  editSaving.value = true
  try {
    await updateWorkflow(adminToken.value, editForm.workflowKey, {
      name,
      description: editForm.description
    })
    message.success('工作流信息已更新')
    showEditModal.value = false
    await reload()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '更新工作流失败')
  } finally {
    editSaving.value = false
  }
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

function handleToggleStatus(row: WorkflowResponse) {
  const enabling = row.status === 'DISABLED'
  dialog.warning({
    title: enabling ? '启用确认' : '禁用确认',
    content: enabling
      ? `确认启用工作流「${row.name}」？启用后将恢复为可用状态。`
      : `确认禁用工作流「${row.name}」？禁用后无法运行。`,
    positiveText: enabling ? '启用' : '禁用',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        if (enabling) {
          await enableWorkflow(adminToken.value, row.workflowKey)
        } else {
          await disableWorkflow(adminToken.value, row.workflowKey)
        }
        message.success(`${row.name} 已${enabling ? '启用' : '禁用'}`)
        await reload()
      } catch (e) {
        message.error(e instanceof Error ? e.message : `${enabling ? '启用' : '禁用'}失败`)
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

onMounted(() => {
  void reload()
  if (workflowTableContainer.value) {
    workflowTableResizeObserver = new ResizeObserver(([entry]) => {
      workflowTableWidth.value = entry.contentRect.width
    })
    workflowTableResizeObserver.observe(workflowTableContainer.value)
  }
})

onBeforeUnmount(() => {
  workflowTableResizeObserver?.disconnect()
  workflowTableResizeObserver = null
})
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
      <n-space style="margin-bottom: 12px" :size="8" align="center">
        <n-input v-model:value="search.name" placeholder="名称" clearable style="width: 180px" @keyup.enter="handleSearch" />
        <n-input v-model:value="search.workflowKey" placeholder="Key" clearable style="width: 180px" @keyup.enter="handleSearch" />
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

      <div ref="workflowTableContainer" class="workflow-table-container">
        <n-data-table
          :loading="loading"
          :columns="workflowColumns"
          :data="workflows"
          :bordered="false"
          :scroll-x="workflowTableScrollX"
          :row-key="(row: WorkflowResponse) => row.id"
          :checked-row-keys="selectedWorkflowIds"
          @update:checked-row-keys="handleWorkflowSelection"
        />
      </div>
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
        <n-grid :cols="2" :x-gap="16">
          <n-form-item-gi label="名称">
            <n-input v-model:value="form.name" placeholder="工作流显示名称" />
          </n-form-item-gi>
          <n-form-item-gi label="Key（唯一标识）">
            <n-input v-model:value="form.workflowKey" placeholder="如 support-ticket" />
          </n-form-item-gi>
        </n-grid>
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

    <n-modal v-model:show="showEditModal" preset="card" title="编辑工作流" style="width: 520px" :bordered="false">
      <n-form label-placement="top">
        <n-form-item label="工作流 Key">
          <n-input v-model:value="editForm.workflowKey" disabled />
        </n-form-item>
        <n-form-item label="名称">
          <n-input v-model:value="editForm.name" placeholder="工作流显示名称" />
        </n-form-item>
        <n-form-item label="描述">
          <n-input v-model:value="editForm.description" type="textarea" :autosize="{ minRows: 2, maxRows: 4 }" placeholder="可选" />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showEditModal = false">取消</n-button>
          <n-button type="primary" :loading="editSaving" @click="handleSaveEdit">保存</n-button>
        </n-space>
      </template>
    </n-modal>

    <n-modal v-model:show="showRunsModal" preset="card" :title="`运行记录（${activeWorkflow?.name ?? ''}）`" style="width: 50%" :bordered="false">
      <n-data-table :columns="runColumns" :data="runs" :bordered="false" />
    </n-modal>

    <n-modal v-model:show="showRunDetailModal" preset="card" :title="`运行详情（${runDetail?.run.runId.slice(0, 8) ?? ''}）`" style="width: 60%" :bordered="false">
      <n-data-table
        v-if="runDetail"
        :columns="nodeRunColumns"
        :data="runDetail.nodeRuns"
        :bordered="false"
        :scroll-x="nodeRunColumns.reduce((s, c) => s + (typeof c.width === 'number' ? c.width : 240), 0)"
      />
    </n-modal>
  </div>
</template>
