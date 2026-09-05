<script setup lang="ts">
/**
 * 知识库详情页「原始文档」tab：库内文档管理（从原列表页内联卡片迁移）。
 * 不含原文预览；行「查看切片」跳到切片 tab 并按该文档预选。
 */
import { h, onMounted, reactive, ref, watch } from 'vue'
import {
  NButton,
  NDataTable,
  NEmpty,
  NIcon,
  NInput,
  NModal,
  NPagination,
  NSelect,
  NSpace,
  NTag,
  NTabs,
  NTabPane,
  NUpload,
  useDialog,
  useMessage,
  type DataTableColumns,
  type UploadFileInfo
} from 'naive-ui'
import { CloudUpload, Link as LinkIcon, Plus, Upload as UploadIcon } from 'lucide-vue-next'
import {
  deleteDocument,
  importUrl,
  listDocuments,
  retryDocument,
  uploadDocument,
  type DocumentResponse
} from '../../api/client'
import { adminToken } from '../../stores/session'
import { formatDateTime } from '../../utils/dateUtils'

const props = defineProps<{
  kbId: number
  /** tab 激活时自动加载 */
  active: boolean
}>()

const emit = defineEmits<{ (e: 'go-chunks', documentId: number): void }>()

const message = useMessage()
const dialog = useDialog()
const loading = ref(false)
const documents = ref<DocumentResponse[]>([])
const pagination = reactive({ page: 1, pageSize: 10, itemCount: 0 })

const DOC_STATUS_MAP: Record<number, { type: 'default' | 'success' | 'info' | 'warning' | 'error'; label: string }> = {
  0: { type: 'warning', label: '待处理' },
  1: { type: 'info', label: '解析中' },
  2: { type: 'info', label: '处理中' },
  3: { type: 'success', label: '处理完成' },
  4: { type: 'error', label: '处理失败' }
}

function fileSizeText(bytes?: number): string {
  if (bytes == null || bytes <= 0) return '—'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

function renderStatus(status: number) {
  const entry = DOC_STATUS_MAP[status]
  return h(NTag, { size: 'small', type: entry?.type ?? 'default', bordered: false, round: true }, { default: () => entry?.label ?? String(status) })
}

const FILE_TYPE_TEXT: Record<string, string> = {
  TXT: 'TXT',
  MARKDOWN: 'MD',
  PDF: 'PDF',
  DOCX: 'DOCX',
  URL: '网页'
}

const columns: DataTableColumns<DocumentResponse> = [
  { title: '文档名称', key: 'title', minWidth: 220, ellipsis: { tooltip: true } },
  {
    title: '文件状态',
    key: 'status',
    width: 100,
    render: (row) => renderStatus(row.status)
  },
  { title: '切片数', key: 'chunkCount', width: 80, render: (row) => row.chunkCount ?? '—' },
  { title: '页数', key: 'pageCount', width: 70, render: () => '—' },
  { title: '文件大小', key: 'fileSize', width: 100, render: (row) => fileSizeText(row.fileSize ?? 0) },
  {
    title: '导入方式',
    key: 'sourceType',
    width: 100,
    render: (row) => h(NTag, { size: 'small', bordered: false, round: true, type: row.sourceType === 'URL' ? 'info' : 'default' }, { default: () => (row.sourceType === 'URL' ? 'URL 导入' : '本地上传') })
  },
  { title: '文件类型', key: 'fileType', width: 90, render: (row) => FILE_TYPE_TEXT[row.fileType] ?? row.fileType },
  { title: '上传时间', key: 'createdAt', width: 170, render: (row) => formatDateTime(row.createdAt) ?? '—' },
  {
    title: '操作',
    key: 'actions',
    width: 220,
    render: (row) =>
      h(NSpace, { size: 6 }, { default: () => [
        h(NButton, { size: 'small', type: 'primary', secondary: true, onClick: () => emit('go-chunks', row.id) }, { default: () => '查看切片' }),
        row.status === 4
          ? h(NButton, { size: 'small', secondary: true, onClick: () => handleRetry(row.id) }, { default: () => '重试' })
          : null,
        h(NButton, { size: 'small', secondary: true, type: 'error', onClick: () => handleDelete(row) }, { default: () => '删除' })
      ] })
  }
]

// ---- 导入弹窗 ----

const importMethod = ref<'upload' | 'url' | 'cloud'>('upload')
const showImport = ref(false)
const selectedFile = ref<File | null>(null)
const urlForm = reactive({ url: '', title: '' })
const importing = ref(false)

const ACCEPT_EXTS = '.txt,.md,.markdown,.pdf,.docx'

function onFileChange(options: { file: UploadFileInfo }) {
  const raw = options.file.file
  if (raw instanceof File) selectedFile.value = raw
}

function openImport() {
  importMethod.value = 'upload'
  selectedFile.value = null
  urlForm.url = ''
  urlForm.title = ''
  showImport.value = true
}

async function doImport() {
  if (importMethod.value === 'upload') {
    if (!selectedFile.value) {
      message.warning('请选择要上传的文件')
      return
    }
    importing.value = true
    try {
      await uploadDocument(adminToken.value, props.kbId, selectedFile.value)
      message.success('文档已上传，Worker 将异步索引')
      selectedFile.value = null
      showImport.value = false
      await reload()
    } catch (e) {
      message.error(e instanceof Error ? e.message : '上传失败')
    } finally {
      importing.value = false
    }
    return
  }
  if (importMethod.value === 'url') {
    if (!urlForm.url.trim()) {
      message.warning('请输入在线文档地址')
      return
    }
    importing.value = true
    try {
      await importUrl(adminToken.value, props.kbId, { url: urlForm.url.trim(), title: urlForm.title.trim() || undefined })
      message.success('URL 导入任务已创建')
      showImport.value = false
      await reload()
    } catch (e) {
      message.error(e instanceof Error ? e.message : 'URL 导入失败')
    } finally {
      importing.value = false
    }
  }
}

function handlePage(page: number) {
  pagination.page = page
  void reload()
}

function handlePageSize(pageSize: number) {
  pagination.pageSize = pageSize
  pagination.page = 1
  void reload()
}

async function handleRetry(documentId: number) {
  try {
    await retryDocument(adminToken.value, documentId)
    message.success('已重置任务为待处理')
    await reload()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '重试失败')
  }
}

function handleDelete(row: DocumentResponse) {
  dialog.warning({
    title: '删除确认',
    content: `确认删除文档「${row.title}」（#${row.id}）？将同步清理其全部切片与向量。`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await deleteDocument(adminToken.value, row.id)
        message.success('文档已删除')
        await reload()
      } catch (e) {
        message.error(e instanceof Error ? e.message : '删除失败')
      }
    }
  })
}

async function reload() {
  loading.value = true
  try {
    const rows = await listDocuments(adminToken.value, props.kbId, {
      pageNum: pagination.page,
      pageSize: pagination.pageSize
    })
    documents.value = rows.rows
    pagination.itemCount = rows.total
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载文档失败')
  } finally {
    loading.value = false
  }
}

watch(
  () => props.active,
  (active) => {
    if (active) void reload()
  }
)

onMounted(() => {
  if (props.active) void reload()
})
</script>

<template>
  <div class="doc-tab">
    <div class="toolbar">
      <span class="count">共 {{ pagination.itemCount }} 个文档</span>
      <n-button type="primary" size="small" @click="openImport">
        <template #icon><n-icon :component="Plus" /></template>
        导入文档
      </n-button>
    </div>

    <n-data-table
      :loading="loading"
      :columns="columns"
      :data="documents"
      :bordered="false"
      :scroll-x="columns.reduce((s, c) => s + (typeof c.width === 'number' ? c.width : 240), 0)"
    />
    <n-empty v-if="!loading && documents.length === 0" description="暂无文档，点击右上角导入" style="padding: 32px 0" />
    <n-pagination
      v-if="pagination.itemCount > pagination.pageSize"
      :page="pagination.page"
      :page-size="pagination.pageSize"
      :item-count="pagination.itemCount"
      :page-sizes="[10, 20, 50, 100]"
      show-size-picker
      style="justify-content: flex-end; margin-top: 12px"
      @update:page="handlePage"
      @update:page-size="handlePageSize"
    />

    <!-- 导入文档弹窗 -->
    <n-modal v-model:show="showImport" preset="card" title="导入文档" style="width: 640px" :bordered="false">
      <n-tabs v-model:value="importMethod" type="line">
        <n-tab-pane name="upload" tab="本地上传">
          <n-upload
            :default-upload="false"
            :show-file-list="false"
            :accept="ACCEPT_EXTS"
            class="drop-zone"
            @change="onFileChange"
          >
            <div class="upload-box">
              <n-icon :component="UploadIcon" size="28" />
              <div>点击或拖拽文件到此区域上传</div>
              <div class="muted">支持 TXT、Markdown、PDF、DOCX 格式（与后端解析器能力一致）</div>
              <div class="muted">导入后由 Worker 异步解析 → 切片 → 向量入库</div>
            </div>
          </n-upload>
          <div v-if="selectedFile" class="selected-file">
            已选择：<n-tag size="small" type="success" :bordered="false">{{ selectedFile.name }}</n-tag>
            <n-button size="tiny" quaternary type="error" @click="selectedFile = null">移除</n-button>
          </div>
          <div class="fmt-row">
            <n-tag v-for="f in ['PDF', 'DOCX', 'MD', 'TXT']" :key="f" size="small" :bordered="false">{{ f }}</n-tag>
          </div>
        </n-tab-pane>
        <n-tab-pane name="url" tab="URL 导入">
          <div class="url-import">
            <n-input v-model:value="urlForm.url" placeholder="输入在线文档地址，如 https://example.com/doc.html" clearable />
            <n-input v-model:value="urlForm.title" placeholder="标题（可选）" clearable />
            <div class="muted">服务端抓取网页正文后按文档处理，同样走异步索引任务。</div>
          </div>
        </n-tab-pane>
        <n-tab-pane name="cloud" tab="云存储" :disabled="true">
          <div class="muted cloud-hint">
            <n-icon :component="CloudUpload" style="margin-right: 6px" />OSS / S3（即将支持）
          </div>
        </n-tab-pane>
      </n-tabs>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showImport = false">取消</n-button>
          <n-button type="primary" :loading="importing" :disabled="importMethod === 'cloud'" @click="doImport">导入</n-button>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>

<style scoped>
.doc-tab {
  display: flex;
  flex-direction: column;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.count {
  font-size: 13px;
  color: rgba(60, 60, 60, 0.85);
}

.upload-box {
  border: 1px dashed #c8c8c8;
  border-radius: 8px;
  padding: 28px 16px;
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  color: #333;
  cursor: pointer;
}

.selected-file {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 10px;
}

.fmt-row {
  display: flex;
  gap: 6px;
  margin-top: 12px;
  flex-wrap: wrap;
}

.url-import {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 8px 0;
}

.cloud-hint {
  display: flex;
  align-items: center;
  padding: 12px 0;
}

.muted {
  font-size: 12px;
  color: rgba(120, 120, 120, 0.9);
}
</style>
