<script setup lang="ts">
/**
 * 知识库详情页「原始文档」tab：库内文档管理。
 * 行操作：预览（pdf/docx/md/txt）、下载源文档、重新解析、查看切片、删除；支持多选批量操作。
 */
import { defineAsyncComponent, h, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
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
  NSpin,
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
import MarkdownPreview from '../../components/MarkdownPreview.vue'
import MonacoTextView from '../../components/MonacoTextView.vue'
import RowActions from '../../components/RowActions.vue'
import {
  batchDeleteDocuments,
  batchReparseDocuments,
  deleteDocument,
  downloadDocument,
  importUrl,
  listDocuments,
  previewDocument,
  reparseDocument,
  uploadDocument,
  type DocumentBatchResult,
  type DocumentResponse
} from '../../api/client'
import { adminToken } from '../../stores/session'
import { formatDateTime } from '../../utils/dateUtils'

// vue-office：docx / pdf / excel / pptx 在线预览组件（懒加载，仅在预览对应类型时拉取解析库）
// 注：@vue-office/pdf、@vue-office/pptx 不导出独立 CSS，无需额外样式引入
import '@vue-office/docx/lib/index.css'
import '@vue-office/excel/lib/index.css'
const VueOfficeDocx = defineAsyncComponent(() => import('@vue-office/docx'))
const VueOfficePdf = defineAsyncComponent(() => import('@vue-office/pdf'))
const VueOfficeExcel = defineAsyncComponent(() => import('@vue-office/excel'))
const VueOfficePptx = defineAsyncComponent(() => import('@vue-office/pptx'))

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
const checkedRowKeys = ref<Array<string | number>>([])

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
  XLSX: 'XLSX',
  PPTX: 'PPTX',
  HTML: 'HTML',
  CSV: 'CSV',
  URL: '网页'
}

const columns: DataTableColumns<DocumentResponse> = [
  { type: 'selection' },
  { title: '文档名称', key: 'title', minWidth: 100, maxWidth: 180, ellipsis: { tooltip: true } },
  {
    title: '文件状态',
    key: 'status',
    width: 100,
    render: (row) => renderStatus(row.status)
  },
  { title: '切片数', key: 'chunkCount', width: 80, render: (row) => row.chunkCount ?? '—' },
  { title: '解析引擎', key: 'parseEngine', width: 100, render: (row) => row.parseEngine ?? 'default' },
  { title: '页数', key: 'pageCount', width: 70, render: () => '—' },
  { title: '文件大小', key: 'fileSize', width: 100, render: (row) => fileSizeText(row.fileSize ?? 0) },
  {
    title: '导入方式',
    key: 'sourceType',
    width: 100,
    render: (row) => h(NTag, { size: 'small', bordered: false, round: true, type: row.sourceType === 'URL' ? 'info' : 'default' }, { default: () => (row.sourceType === 'URL' ? 'URL 导入' : '本地上传') })
  },
  { title: '文件类型', key: 'fileType', width: 90, render: (row) => FILE_TYPE_TEXT[row.fileType] ?? row.fileType },
  { title: '上传时间', key: 'createdAt', width: 185, render: (row) => formatDateTime(row.createdAt) ?? '—' },
  {
    title: '操作',
    key: 'actions',
    width: 150,
    render: (row) => h(RowActions, { maxInline: 1, actions: buildActions(row) })
  }
]

// ---- 导入弹窗 ----

const importMethod = ref<'upload' | 'url' | 'cloud'>('upload')
const showImport = ref(false)
const selectedFile = ref<File | null>(null)
const urlForm = reactive({ url: '', title: '' })
const importing = ref(false)

const ACCEPT_EXTS = '.txt,.md,.markdown,.pdf,.docx,.xlsx,.pptx,.html,.htm,.csv'

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

// ---- 预览 / 下载 / 重新解析 / 删除 ----

const showPreviewModal = ref(false)
const previewName = ref('')
/** 预览加载中：拉取文件 + 文档渲染（office 渲染完成或出错后置 false） */
const previewLoading = ref(false)
/** 预览类型：docx/pdf/excel/pptx 用 vue-office，markdown 用 vditor，text 用 monaco */
const previewKind = ref<'pdf' | 'docx' | 'excel' | 'pptx' | 'markdown' | 'text' | null>(null)
const previewBlob = ref<Blob | null>(null)
const previewText = ref('')

/** 按 fileType 判定预览类型；文档仅 pdf/docx/xlsx/pptx/markdown/text 类。 */
function previewKindOf(row: DocumentResponse): 'pdf' | 'docx' | 'excel' | 'pptx' | 'markdown' | 'text' | null {
  const ft = (row.fileType ?? '').toUpperCase()
  if (ft === 'PDF') return 'pdf'
  if (ft === 'DOCX') return 'docx'
  if (ft === 'XLSX') return 'excel'
  if (ft === 'PPTX') return 'pptx'
  if (ft === 'MARKDOWN') return 'markdown'
  if (ft === 'TXT' || ft === 'URL' || ft === 'HTML' || ft === 'CSV') return 'text'
  return null
}

async function handlePreview(row: DocumentResponse) {
  const kind = previewKindOf(row)
  if (!kind) {
    message.info('该类型不支持在线预览，已转为下载')
    await handleDownload(row)
    return
  }
  previewLoading.value = true
  try {
    const blob = await previewDocument(adminToken.value, row.id)
    previewName.value = row.title
    previewKind.value = kind
    previewBlob.value = blob
    previewText.value = kind === 'markdown' || kind === 'text' ? await blob.text() : ''
    // markdown/text 内容即时可用；office 由组件 rendered/error 事件关闭 loading
    if (kind === 'markdown' || kind === 'text') {
      previewLoading.value = false
    }
    showPreviewModal.value = true
  } catch (e) {
    previewLoading.value = false
    message.error(e instanceof Error ? e.message : '预览失败')
  }
}

function closePreview() {
  showPreviewModal.value = false
  previewLoading.value = false
  previewKind.value = null
  previewBlob.value = null
  previewText.value = ''
}

async function handleDownload(row: DocumentResponse) {
  try {
    const blob = await downloadDocument(adminToken.value, row.id)
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = row.title || `document-${row.id}`
    a.click()
    setTimeout(() => URL.revokeObjectURL(url), 10_000)
  } catch (e) {
    message.error(e instanceof Error ? e.message : '下载失败')
  }
}

async function handleReparse(row: DocumentResponse) {
  try {
    const requeued = await reparseDocument(adminToken.value, row.id)
    if (requeued) {
      message.success('已提交重新解析')
      await reload()
    } else {
      message.info('该文档正在处理中，已跳过')
    }
  } catch (e) {
    message.error(e instanceof Error ? e.message : '重新解析失败')
  }
}

async function deleteOne(row: DocumentResponse) {
  try {
    await deleteDocument(adminToken.value, row.id)
    message.success('文档已删除')
    await reload()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '删除失败')
  }
}

function buildActions(row: DocumentResponse) {
  const list = [
    { key: 'preview', label: '预览', secondary: true, type: 'primary' as const, onClick: () => handlePreview(row) },
    { key: 'download', label: '下载源文档', secondary: true, onClick: () => handleDownload(row) },
    { key: 'reparse', label: '重新解析', secondary: true, disabled: row.status !== 3 && row.status !== 4, onClick: () => handleReparse(row) },
    { key: 'chunks', label: '查看切片', secondary: true, onClick: () => emit('go-chunks', row.id) },
    { key: 'delete', label: '删除', secondary: true, type: 'error' as const, confirm: `确认删除文档「${row.title}」？将同步清理其全部切片与向量。`, onClick: () => deleteOne(row) }
  ]
  // URL 导入无源文件，隐藏「下载源文档」
  if (row.sourceType === 'URL') {
    return list.filter((a) => a.key !== 'download')
  }
  return list
}

function onCheckedKeysChange(keys: Array<string | number>) {
  checkedRowKeys.value = keys
}

function formatBatchSummary(action: string, result: DocumentBatchResult): string {
  const parts = [`成功 ${result.succeeded}`]
  if (result.skipped) parts.push(`跳过 ${result.skipped}`)
  if (result.failures?.length) parts.push(`失败 ${result.failures.length}`)
  return `${action}：${parts.join('，')}`
}

async function handleBatchReparse() {
  const ids = checkedRowKeys.value.map((k) => Number(k))
  if (ids.length === 0) return
  dialog.warning({
    title: '批量重新解析确认',
    content: `确认重新解析选中的 ${ids.length} 个文档？仅「处理完成 / 处理失败」的文档会被重新解析，其余跳过。`,
    positiveText: '重新解析',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        const result = await batchReparseDocuments(adminToken.value, ids)
        message.success(formatBatchSummary('批量重新解析', result))
        checkedRowKeys.value = []
        await reload()
      } catch (e) {
        message.error(e instanceof Error ? e.message : '批量重新解析失败')
      }
    }
  })
}

async function handleBatchDelete() {
  const ids = checkedRowKeys.value.map((k) => Number(k))
  if (ids.length === 0) return
  dialog.error({
    title: '批量删除确认',
    content: `确认删除选中的 ${ids.length} 个文档？将同步清理其全部切片与向量，且不可恢复。`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        const result = await batchDeleteDocuments(adminToken.value, ids)
        message.success(formatBatchSummary('批量删除', result))
        checkedRowKeys.value = []
        await reload()
      } catch (e) {
        message.error(e instanceof Error ? e.message : '批量删除失败')
      }
    }
  })
}

async function reload(silent = false) {
  if (!silent) loading.value = true
  try {
    const rows = await listDocuments(adminToken.value, props.kbId, {
      pageNum: pagination.page,
      pageSize: pagination.pageSize
    })
    documents.value = rows.rows
    pagination.itemCount = rows.total
  } catch (e) {
    if (!silent) message.error(e instanceof Error ? e.message : '加载文档失败')
  } finally {
    if (!silent) loading.value = false
    syncPolling()
  }
}

// ---- 定时刷新：当前页存在进行中（0待处理/1解析中/2处理中）时每 5s 静默刷新；全终态则停 ----
const POLL_INTERVAL_MS = 5_000
let pollTimer: number | null = null

function hasInFlight(): boolean {
  return documents.value.some((d) => d.status === 0 || d.status === 1 || d.status === 2)
}

function stopPolling() {
  if (pollTimer != null) {
    window.clearInterval(pollTimer)
    pollTimer = null
  }
}

function syncPolling() {
  if (props.active && hasInFlight()) {
    if (pollTimer == null) {
      pollTimer = window.setInterval(() => void reload(true), POLL_INTERVAL_MS)
    }
  } else {
    stopPolling()
  }
}

watch(
  () => props.active,
  (active) => {
    if (active) void reload()
    else stopPolling()
  }
)

onMounted(() => {
  if (props.active) void reload()
})

onBeforeUnmount(stopPolling)
</script>

<template>
  <div class="doc-tab">
    <div class="toolbar">
      <n-space :size="8" align="center">
        <span class="count">共 {{ pagination.itemCount }} 个文档</span>
        <template v-if="checkedRowKeys.length > 0">
          <n-button size="small" secondary type="primary" @click="handleBatchReparse">
            批量重新解析（{{ checkedRowKeys.length }}）
          </n-button>
          <n-button size="small" secondary type="error" @click="handleBatchDelete">
            删除所选（{{ checkedRowKeys.length }}）
          </n-button>
        </template>
      </n-space>
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
      :row-key="(row: DocumentResponse) => row.id"
      :checked-row-keys="checkedRowKeys"
      :scroll-x="columns.reduce((s, c) => s + (typeof c.width === 'number' ? c.width : 240), 0)"
      @update:checked-row-keys="onCheckedKeysChange"
    >
    <template #empty>
      <n-empty description="暂无文档，点击右上角导入" style="padding: 32px 0" />
    </template>
    </n-data-table>
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
              <div class="muted">支持 TXT、Markdown、PDF、DOCX、XLSX、PPTX、HTML、CSV 格式（与后端解析器能力一致）</div>
              <div class="muted">导入后由 Worker 异步解析 → 切片 → 向量入库</div>
            </div>
          </n-upload>
          <div v-if="selectedFile" class="selected-file">
            已选择：<n-tag size="small" type="success" :bordered="false">{{ selectedFile.name }}</n-tag>
            <n-button size="tiny" quaternary type="error" @click="selectedFile = null">移除</n-button>
          </div>
          <div class="fmt-row">
            <n-tag v-for="f in ['PDF', 'DOCX', 'XLSX', 'PPTX', 'MD', 'TXT', 'HTML', 'CSV']" :key="f" size="small" :bordered="false">{{ f }}</n-tag>
          </div>
        </n-tab-pane>
        <n-tab-pane name="url" tab="URL 导入">
          <div class="url-import">
            <n-input v-model:value="urlForm.url" placeholder="输入在线文档地址，如 https://example.com/doc.html" clearable />
            <n-input v-model:value="urlForm.title" placeholder="标题（可选）" clearable />
            <div class="muted">服务端下载网页/远程文件后按类型解析，同样走异步索引任务。</div>
          </div>
        </n-tab-pane>
<!--        <n-tab-pane name="cloud" tab="云存储" :disabled="true">-->
<!--          <div class="muted cloud-hint">-->
<!--            <n-icon :component="CloudUpload" style="margin-right: 6px" />OSS / S3（即将支持）-->
<!--          </div>-->
<!--        </n-tab-pane>-->
      </n-tabs>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showImport = false">取消</n-button>
          <n-button type="primary" :loading="importing" :disabled="importMethod === 'cloud'" @click="doImport">导入</n-button>
        </n-space>
      </template>
    </n-modal>

    <!-- 预览弹窗：pdf/docx 用 vue-office，markdown 用 vditor，text 用 monaco -->
    <n-modal v-model:show="showPreviewModal" preset="card" :title="previewName" style="width: 80%" :bordered="false" @after-leave="closePreview">
      <n-spin :show="previewLoading" description="文档加载中...">
        <vue-office-pdf v-if="previewKind === 'pdf'" :src="previewBlob" style="height: 70vh" @rendered="previewLoading = false" @error="previewLoading = false" />
        <vue-office-docx v-else-if="previewKind === 'docx'" :src="previewBlob" style="height: 70vh" @rendered="previewLoading = false" @error="previewLoading = false" />
        <vue-office-excel v-else-if="previewKind === 'excel'" :src="previewBlob" style="height: 70vh" @rendered="previewLoading = false" @error="previewLoading = false" />
        <vue-office-pptx v-else-if="previewKind === 'pptx'" :src="previewBlob" style="height: 70vh" @rendered="previewLoading = false" @error="previewLoading = false" />
        <markdown-preview v-else-if="previewKind === 'markdown'" :content="previewText" @rendered="previewLoading = false" @error="previewLoading = false" />
        <monaco-text-view v-else-if="previewKind === 'text'" :content="previewText" :file-name="previewName" @rendered="previewLoading = false" @error="previewLoading = false" />
      </n-spin>
      <template #footer>
        <n-space justify="end">
          <n-button @click="closePreview">关闭</n-button>
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

/* Naive 的 n-upload 及其 trigger 默认 inline-block，改为块级占满弹窗宽度 */
.drop-zone {
  display: block;
  width: 100%;
}

.drop-zone :deep(.n-upload-trigger) {
  display: block;
  width: 100%;
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
  width: 100%;
  box-sizing: border-box;
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
