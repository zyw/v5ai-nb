<script setup lang="ts">
import { h, onMounted, reactive, ref } from 'vue'
import { NAlert, NButton, NCard, NDataTable, NForm, NFormItem, NInput, NSelect, NTag, useMessage, type DataTableColumns, type UploadFileInfo } from 'naive-ui'
import { createKnowledgeBase, deleteDocument, disableKnowledgeBase, importUrl, listDocuments, listKnowledgeBases, listTasks, retryDocument, uploadDocument, type DocumentResponse, type KnowledgeBaseResponse, type TaskResponse } from '../api/client'
import { adminToken, isLoggedIn } from '../stores/session'

const message = useMessage()
const loading = ref(false)
const knowledgeBases = ref<KnowledgeBaseResponse[]>([])
const documents = ref<DocumentResponse[]>([])
const tasks = ref<TaskResponse[]>([])
const selectedKbId = ref<number | null>(null)
const uploadFile = ref<UploadFileInfo | null>(null)

const kbForm = reactive({
  name: '产品文档',
  description: ''
})

const urlForm = reactive({
  url: '',
  title: ''
})

const documentColumns: DataTableColumns<DocumentResponse> = [
  { title: 'ID', key: 'id', width: 60 },
  { title: '标题', key: 'title' },
  { title: '类型', key: 'fileType' },
  {
    title: '状态',
    key: 'status',
    render: row => h(NTag, {
      type: row.status === 'COMPLETED' ? 'success' : row.status === 'FAILED' ? 'error' : row.status === 'PROCESSING' ? 'info' : 'default',
      size: 'small'
    }, { default: () => row.status })
  },
  { title: '错误', key: 'errorMessage', ellipsis: { tooltip: true } },
  {
    title: '操作',
    key: 'actions',
    render: row => h('div', { style: 'display:flex;gap:8px' }, [
      h(NButton, { size: 'small', secondary: true, disabled: !isLoggedIn.value, onClick: () => handleRetry(row.id) }, { default: () => '重试' }),
      h(NButton, { size: 'small', type: 'error', secondary: true, disabled: !isLoggedIn.value, onClick: () => handleDeleteDoc(row.id) }, { default: () => '删除' })
    ])
  }
]

const taskColumns: DataTableColumns<TaskResponse> = [
  { title: 'ID', key: 'id', width: 60 },
  { title: '文档 ID', key: 'documentId', width: 90 },
  {
    title: '状态',
    key: 'status',
    render: row => h(NTag, {
      type: row.status === 'COMPLETED' ? 'success' : row.status === 'FAILED' ? 'error' : row.status === 'PROCESSING' ? 'info' : 'default',
      size: 'small'
    }, { default: () => row.status })
  },
  { title: '尝试', key: 'attemptCount', render: row => `${row.attemptCount}/${row.maxAttempts}` },
  { title: '错误', key: 'errorMessage', ellipsis: { tooltip: true } }
]

async function reload() {
  if (!isLoggedIn.value) return
  loading.value = true
  try {
    knowledgeBases.value = await listKnowledgeBases(adminToken.value)
    if (knowledgeBases.value.length > 0 && selectedKbId.value == null) {
      selectedKbId.value = knowledgeBases.value[0].id
    }
    if (selectedKbId.value != null) {
      await reloadDocuments(selectedKbId.value)
    }
  } catch (error) {
    message.error(error instanceof Error ? error.message : '加载知识库失败')
  } finally {
    loading.value = false
  }
}

async function reloadDocuments(kbId: number) {
  const [docRows, taskRows] = await Promise.all([
    listDocuments(adminToken.value, kbId),
    listTasks(adminToken.value, kbId)
  ])
  documents.value = docRows
  tasks.value = taskRows
}

async function handleSelectKb(kbId: number) {
  selectedKbId.value = kbId
  await reloadDocuments(kbId)
}

function handleFileList(files: UploadFileInfo[]) {
  uploadFile.value = files[0] ?? null
}

async function handleCreateKb() {
  try {
    await createKnowledgeBase(adminToken.value, kbForm)
    message.success('知识库已创建')
    await reload()
  } catch (error) {
    message.error(error instanceof Error ? error.message : '创建知识库失败')
  }
}

async function handleUpload() {
  if (selectedKbId.value == null) {
    message.warning('请先选择知识库')
    return
  }
  const rawFile = uploadFile.value?.file
  if (!rawFile) {
    message.warning('请选择文件')
    return
  }
  const file = rawFile instanceof File ? rawFile : new File([rawFile], uploadFile.value?.name ?? 'upload')
  try {
    await uploadDocument(adminToken.value, selectedKbId.value, file)
    message.success('文档已上传，Worker 将异步索引')
    uploadFile.value = null
    await reloadDocuments(selectedKbId.value)
  } catch (error) {
    message.error(error instanceof Error ? error.message : '上传文档失败')
  }
}

async function handleImportUrl() {
  if (selectedKbId.value == null) {
    message.warning('请先选择知识库')
    return
  }
  try {
    await importUrl(adminToken.value, selectedKbId.value, urlForm)
    message.success('URL 导入任务已创建')
    await reloadDocuments(selectedKbId.value)
  } catch (error) {
    message.error(error instanceof Error ? error.message : 'URL 导入失败')
  }
}

async function handleRetry(documentId: number) {
  try {
    await retryDocument(adminToken.value, documentId)
    message.success('已重置任务为 PENDING')
    if (selectedKbId.value != null) {
      await reloadDocuments(selectedKbId.value)
    }
  } catch (error) {
    message.error(error instanceof Error ? error.message : '重试失败')
  }
}

async function handleDeleteDoc(documentId: number) {
  try {
    await deleteDocument(adminToken.value, documentId)
    message.success('文档已删除')
    if (selectedKbId.value != null) {
      await reloadDocuments(selectedKbId.value)
    }
  } catch (error) {
    message.error(error instanceof Error ? error.message : '删除失败')
  }
}

async function handleDisableKb(id: number) {
  try {
    await disableKnowledgeBase(adminToken.value, id)
    message.success('知识库已禁用')
    await reload()
  } catch (error) {
    message.error(error instanceof Error ? error.message : '禁用失败')
  }
}

onMounted(reload)
</script>

<template>
  <div class="grid">
    <n-alert v-if="!isLoggedIn" class="wide" type="warning" title="需要管理员 Token">
      请先在 Dashboard 登录，再管理知识库。
    </n-alert>

    <n-card title="创建知识库" class="panel">
      <n-form label-placement="top">
        <n-form-item label="名称">
          <n-input v-model:value="kbForm.name" />
        </n-form-item>
        <n-form-item label="描述">
          <n-input v-model:value="kbForm.description" type="textarea" :autosize="{ minRows: 2, maxRows: 4 }" />
        </n-form-item>
        <n-button type="primary" :disabled="!isLoggedIn" @click="handleCreateKb">创建知识库</n-button>
      </n-form>
    </n-card>

    <n-card title="知识库列表" class="panel wide">
      <n-data-table
        :loading="loading"
        :columns="[
          { title: 'ID', key: 'id', width: 200, render: (row: KnowledgeBaseResponse) => h('span', { style: 'display:flex;gap:6px;align-items:center' }, [
            String(row.id),
            !Number.isSafeInteger(row.id)
              ? h(NTag, { type: 'warning', size: 'small' }, { default: () => '旧数据（ID 超 JS 安全整数，无法上传，请新建）' })
              : null
          ]) },
          { title: '名称', key: 'name' },
          { title: '描述', key: 'description' },
          { title: '状态', key: 'status' },
          { title: '操作', key: 'actions', render: (row: KnowledgeBaseResponse) => h('div', { style: 'display:flex;gap:8px' }, [
            h(NButton, { size: 'small', type: 'primary', secondary: true, onClick: () => handleSelectKb(row.id) }, { default: () => '选择' }),
            h(NButton, { size: 'small', type: 'error', secondary: true, disabled: !isLoggedIn, onClick: () => handleDisableKb(row.id) }, { default: () => '禁用' })
          ]) }
        ]"
        :data="knowledgeBases"
      />
    </n-card>

    <n-card :title="`文档管理 ${selectedKbId != null ? `（知识库 #${selectedKbId}）` : ''}`" class="panel wide">
      <n-space vertical>
        <n-space>
          <n-upload :default-upload="false" :show-file-list="false" accept=".txt,.md,.markdown,.pdf,.docx" @update:file-list="handleFileList">
            <n-button secondary>选择文件（TXT/MD/PDF/DOCX）</n-button>
          </n-upload>
          <n-button type="primary" :disabled="!isLoggedIn || !selectedKbId" @click="handleUpload">上传文档</n-button>
        </n-space>
        <n-form inline>
          <n-form-item label="URL">
            <n-input v-model:value="urlForm.url" placeholder="https://example.com/doc" style="width: 320px" />
          </n-form-item>
          <n-form-item label="标题">
            <n-input v-model:value="urlForm.title" placeholder="可选" style="width: 180px" />
          </n-form-item>
          <n-button secondary type="info" :disabled="!isLoggedIn || !selectedKbId" @click="handleImportUrl">导入 URL</n-button>
        </n-form>
        <n-data-table :loading="loading" :columns="documentColumns" :data="documents" />
      </n-space>
    </n-card>

    <n-card :title="`索引任务 ${selectedKbId != null ? `（知识库 #${selectedKbId}）` : ''}`" class="panel wide">
      <n-data-table :loading="loading" :columns="taskColumns" :data="tasks" />
      <div class="hint" style="margin-top: 8px">Worker 每 5 秒扫描一次 PENDING/可重试任务并执行 解析 → 切片 → Embedding → 写入 pgvector。</div>
    </n-card>
  </div>
</template>
