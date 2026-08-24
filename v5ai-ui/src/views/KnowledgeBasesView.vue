<script setup lang="ts">
import { h, onMounted, reactive, ref } from 'vue'
import {
  NButton,
  NCard,
  NDataTable,
  NEmpty,
  NForm,
  NFormItem,
  NIcon,
  NInput,
  NModal,
  NPagination,
  NSpace,
  NTag,
  NUpload,
  useDialog,
  useMessage,
  type DataTableColumns,
  type UploadFileInfo
} from 'naive-ui'
import { Plus } from 'lucide-vue-next'
import PageHeader from '../components/PageHeader.vue'
import StatusTag from '../components/StatusTag.vue'
import {
  createKnowledgeBase,
  deleteDocument,
  disableKnowledgeBase,
  importUrl,
  listDocuments,
  listKnowledgeBases,
  listTasks,
  retryDocument,
  uploadDocument,
  type DocumentResponse,
  type KnowledgeBaseResponse,
  type TaskResponse
} from '../api/client'
import { adminToken } from '../stores/session'

const message = useMessage()
const dialog = useDialog()
const loading = ref(false)
const knowledgeBases = ref<KnowledgeBaseResponse[]>([])
const documents = ref<DocumentResponse[]>([])
const tasks = ref<TaskResponse[]>([])
const selectedKb = ref<KnowledgeBaseResponse | null>(null)
const uploadFile = ref<File | null>(null)
const kbPagination = reactive({ page: 1, pageSize: 10, itemCount: 0 })
const docPagination = reactive({ page: 1, pageSize: 10, itemCount: 0 })

const showCreateModal = ref(false)
const saving = ref(false)
const kbForm = reactive({ name: '', description: '' })
const urlForm = reactive({ url: '', title: '' })

const documentColumns: DataTableColumns<DocumentResponse> = [
  { title: 'ID', key: 'id', width: 70 },
  { title: '标题', key: 'title' },
  { title: '类型', key: 'fileType', width: 90 },
  { title: '状态', key: 'status', width: 110, render: (row) => h(StatusTag, { status: row.status }) },
  { title: '错误', key: 'errorMessage', ellipsis: { tooltip: true } },
  {
    title: '操作',
    key: 'actions',
    width: 130,
    render: (row) =>
      h(NSpace, { size: 6 }, { default: () => [
        h(NButton, { size: 'small', secondary: true, onClick: () => handleRetry(row.id) }, { default: () => '重试' }),
        h(NButton, { size: 'small', secondary: true, type: 'error', onClick: () => handleDeleteDoc(row.id) }, { default: () => '删除' })
      ] })
  }
]

const taskColumns: DataTableColumns<TaskResponse> = [
  { title: 'ID', key: 'id', width: 70 },
  { title: '文档 ID', key: 'documentId', width: 90 },
  { title: '状态', key: 'status', width: 110, render: (row) => h(StatusTag, { status: row.status }) },
  { title: '尝试', key: 'attemptCount', width: 80, render: (row) => `${row.attemptCount}/${row.maxAttempts}` },
  { title: '错误', key: 'errorMessage', ellipsis: { tooltip: true } }
]

async function reload() {
  loading.value = true
  try {
    const kbRows = await listKnowledgeBases(adminToken.value, { pageNum: kbPagination.page, pageSize: kbPagination.pageSize })
    knowledgeBases.value = kbRows.rows
    kbPagination.itemCount = kbRows.total
    if (selectedKb.value == null && knowledgeBases.value.length > 0) {
      selectedKb.value = knowledgeBases.value[0]
    }
    if (selectedKb.value != null) {
      await reloadDocuments(selectedKb.value.id)
    }
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载知识库失败')
  } finally {
    loading.value = false
  }
}

function handleKbPageChange(page: number) {
  kbPagination.page = page
  void reload()
}

function handleKbPageSizeChange(pageSize: number) {
  kbPagination.pageSize = pageSize
  kbPagination.page = 1
  void reload()
}

function handleDocPageChange(page: number) {
  docPagination.page = page
  if (selectedKb.value) void reloadDocuments(selectedKb.value.id)
}

function handleDocPageSizeChange(pageSize: number) {
  docPagination.pageSize = pageSize
  docPagination.page = 1
  if (selectedKb.value) void reloadDocuments(selectedKb.value.id)
}

async function reloadDocuments(kbId: number) {
  const [docRows, taskRows] = await Promise.all([
    listDocuments(adminToken.value, kbId, { pageNum: docPagination.page, pageSize: docPagination.pageSize }),
    listTasks(adminToken.value, kbId)
  ])
  documents.value = docRows.rows
  docPagination.itemCount = docRows.total
  tasks.value = taskRows.rows
}

async function selectKb(kb: KnowledgeBaseResponse) {
  selectedKb.value = kb
  await reloadDocuments(kb.id)
}

function handleFileChange(options: { file: UploadFileInfo }) {
  const raw = options.file.file
  if (raw instanceof File) {
    uploadFile.value = raw
  }
}

async function handleCreateKb() {
  saving.value = true
  try {
    await createKnowledgeBase(adminToken.value, {
      name: kbForm.name,
      description: kbForm.description || undefined
    })
    message.success('知识库已创建')
    showCreateModal.value = false
    kbForm.name = ''
    kbForm.description = ''
    await reload()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '创建知识库失败')
  } finally {
    saving.value = false
  }
}

async function handleUpload() {
  if (!selectedKb.value) {
    message.warning('请先选择知识库')
    return
  }
  if (!uploadFile.value) {
    message.warning('请选择文件')
    return
  }
  try {
    await uploadDocument(adminToken.value, selectedKb.value.id, uploadFile.value)
    message.success('文档已上传，Worker 将异步索引')
    uploadFile.value = null
    await reloadDocuments(selectedKb.value.id)
  } catch (e) {
    message.error(e instanceof Error ? e.message : '上传文档失败')
  }
}

async function handleImportUrl() {
  if (!selectedKb.value) {
    message.warning('请先选择知识库')
    return
  }
  try {
    await importUrl(adminToken.value, selectedKb.value.id, urlForm)
    message.success('URL 导入任务已创建')
    urlForm.url = ''
    urlForm.title = ''
    await reloadDocuments(selectedKb.value.id)
  } catch (e) {
    message.error(e instanceof Error ? e.message : 'URL 导入失败')
  }
}

async function handleRetry(documentId: number) {
  try {
    await retryDocument(adminToken.value, documentId)
    message.success('已重置任务为 PENDING')
    if (selectedKb.value) await reloadDocuments(selectedKb.value.id)
  } catch (e) {
    message.error(e instanceof Error ? e.message : '重试失败')
  }
}

async function handleDeleteDoc(documentId: number) {
  dialog.warning({
    title: '删除确认',
    content: `确认删除文档 #${documentId}？`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await deleteDocument(adminToken.value, documentId)
        message.success('文档已删除')
        if (selectedKb.value) await reloadDocuments(selectedKb.value.id)
      } catch (e) {
        message.error(e instanceof Error ? e.message : '删除失败')
      }
    }
  })
}

async function handleDisableKb(row: KnowledgeBaseResponse) {
  dialog.warning({
    title: '禁用确认',
    content: `确认禁用知识库「${row.name}」？`,
    positiveText: '禁用',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await disableKnowledgeBase(adminToken.value, row.id)
        message.success('知识库已禁用')
        await reload()
      } catch (e) {
        message.error(e instanceof Error ? e.message : '禁用失败')
      }
    }
  })
}

onMounted(reload)
</script>

<template>
  <div class="page">
    <PageHeader title="知识库管理" description="创建知识库并导入文档，由后台 Worker 解析、切片并写入向量库。">
      <template #actions>
        <n-button size="small" type="primary" @click="showCreateModal = true">
          <template #icon><n-icon :component="Plus" /></template>
          新建知识库
        </n-button>
      </template>
    </PageHeader>

    <n-card title="知识库列表" :bordered="true">
      <n-data-table
        :loading="loading"
        :columns="[
          { title: 'ID', key: 'id', width: 90 },
          { title: '名称', key: 'name' },
          { title: '描述', key: 'description', ellipsis: { tooltip: true } },
          { title: '状态', key: 'status', width: 100, render: (row: KnowledgeBaseResponse) => h(StatusTag, { status: row.status }) },
          {
            title: '操作',
            key: 'actions',
            width: 130,
            render: (row: KnowledgeBaseResponse) => h(NSpace, { size: 6 }, { default: () => [
              h(NButton, { size: 'small', type: 'primary', secondary: true, onClick: () => selectKb(row) }, { default: () => '管理文档' }),
              h(NButton, { size: 'small', type: 'error', secondary: true, disabled: row.status !== 'ACTIVE', onClick: () => handleDisableKb(row) }, { default: () => '禁用' })
            ] })
          }
        ]"
        :data="knowledgeBases"
        :bordered="false"
      />
      <n-pagination
        :page="kbPagination.page"
        :page-size="kbPagination.pageSize"
        :item-count="kbPagination.itemCount"
        :page-sizes="[10, 20, 50, 100]"
        show-size-picker
        style="justify-content: flex-end; margin-top: 12px"
        @update:page="handleKbPageChange"
        @update:page-size="handleKbPageSizeChange"
      />
    </n-card>

    <n-card v-if="selectedKb" :title="`文档管理 · ${selectedKb.name}`" :bordered="true">
      <n-space vertical :size="16">
        <div class="doc-toolbar">
          <n-upload :default-upload="false" :show-file-list="false" accept=".txt,.md,.markdown,.pdf,.docx" @change="handleFileChange">
            <n-button secondary>选择文件（TXT/MD/PDF/DOCX）</n-button>
          </n-upload>
          <n-button type="primary" @click="handleUpload">上传文档</n-button>
          <div class="url-toolbar">
            <n-input v-model:value="urlForm.url" placeholder="https://example.com/doc" style="width: 280px" />
            <n-input v-model:value="urlForm.title" placeholder="标题（可选）" style="width: 160px" />
            <n-button secondary type="info" @click="handleImportUrl">导入 URL</n-button>
          </div>
        </div>
        <n-empty v-if="documents.length === 0" description="暂无文档" />
        <n-data-table v-else :loading="loading" :columns="documentColumns" :data="documents" :bordered="false" />
        <n-pagination
          :page="docPagination.page"
          :page-size="docPagination.pageSize"
          :item-count="docPagination.itemCount"
          :page-sizes="[10, 20, 50, 100]"
          show-size-picker
          style="justify-content: flex-end; margin-top: 12px"
          @update:page="handleDocPageChange"
          @update:page-size="handleDocPageSizeChange"
        />
      </n-space>
    </n-card>

    <n-card v-if="selectedKb" title="索引任务" :bordered="true">
      <n-empty v-if="tasks.length === 0" description="暂无索引任务" />
      <n-data-table v-else :loading="loading" :columns="taskColumns" :data="tasks" :bordered="false" />
      <div class="muted" style="margin-top: 12px; font-size: 12px">
        Worker 每 5 秒扫描一次待处理任务，执行 解析 → 切片 → Embedding → 写入 pgvector。
      </div>
    </n-card>

    <n-modal
      v-model:show="showCreateModal"
      preset="card"
      title="新建知识库"
      style="width: 440px"
      :bordered="false"
    >
      <n-form label-placement="top">
        <n-form-item label="名称">
          <n-input v-model:value="kbForm.name" placeholder="如：产品文档" />
        </n-form-item>
        <n-form-item label="描述">
          <n-input v-model:value="kbForm.description" type="textarea" :autosize="{ minRows: 2, maxRows: 4 }" />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showCreateModal = false">取消</n-button>
          <n-button type="primary" :loading="saving" @click="handleCreateKb">创建</n-button>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>

<style scoped>
.doc-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.url-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-left: auto;
}

@media (max-width: 720px) {
  .url-toolbar {
    margin-left: 0;
    width: 100%;
  }
}
</style>
