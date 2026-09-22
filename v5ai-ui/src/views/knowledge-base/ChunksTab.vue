<script setup lang="ts">
/**
 * 知识库详情页「切片详情」tab：库内切片卡片流。
 * 顶部：共 N 切片 / +新增切片 / 按文档ID筛选 / 搜索切片ID / 搜索片段内容；
 * 卡片：序号 + 切片ID + 内容摘要 + 所属文档·字符数·更新时间；悬停操作：编辑 / 详情 / 删除。
 * 新增切片：所属文档的归属由工具栏筛选决定——筛选选中具体文档时弹窗内锁定为静态文本，
 * 选中「全部文档」时才在弹窗内手选；文档列表为空/加载失败时分别禁用入口与给出重试。
 */
import { computed, onMounted, reactive, ref, watch } from 'vue'
import {
  NButton,
  NEllipsis,
  NEmpty,
  NIcon,
  NInput,
  NModal,
  NPagination,
  NSelect,
  NSpace,
  NSpin,
  NText,
  useDialog,
  useMessage,
  type SelectOption
} from 'naive-ui'
import { Eye, FileText, Pencil, Plus, Trash2 } from 'lucide-vue-next'
import {
  addChunk,
  deleteChunk,
  listChunks,
  listDocuments,
  updateChunk,
  type DocumentResponse,
  type KnowledgeChunkResponse
} from '../../api/client'
import { adminToken } from '../../stores/session'
import { formatDateTime } from '../../utils/dateUtils'

const props = defineProps<{
  kbId: number
  active: boolean
  /** 从「原始文档」行跳转时的预选文档 */
  presetDocumentId: number | null
}>()

const message = useMessage()
const dialog = useDialog()
const loading = ref(false)
const chunks = ref<KnowledgeChunkResponse[]>([])
const documents = ref<DocumentResponse[]>([])
/** 文档列表是否加载失败（与「真的没有文档」区分开，避免误禁用新增入口） */
const documentsFailed = ref(false)
/** 0 = 「全部文档」哨兵值（不是 null）；非 0 代表归属已由筛选上下文决定 */
const filterDocId = ref<number | null>(0)
const searchChunkId = ref('')
const searchContent = ref('')
const pagination = reactive({ page: 1, pageSize: 10, itemCount: 0 })

watch(
  () => props.presetDocumentId,
  (id) => {
    if (id != null) {
      filterDocId.value = id
      pagination.page = 1
      if (props.active) void reload()
    }
  }
)

watch(
  () => props.active,
  (active) => {
    if (active) void reload()
  }
)

onMounted(() => {
  void loadDocuments()
  if (props.active) void reload()
})

async function loadDocuments() {
  try {
    const rows = await listDocuments(adminToken.value, props.kbId, { pageNum: 1, pageSize: 200 })
    documents.value = rows.rows
    documentsFailed.value = false
  } catch {
    documents.value = []
    documentsFailed.value = true
  }
}

const docOptions = computed<SelectOption[]>(() => [
  { label: '全部文档', value: 0 } as SelectOption,
  ...documents.value.map((d) => ({ label: d.title, value: d.id }) as SelectOption)
])

const docTitleOf = (chunk: KnowledgeChunkResponse) => chunk.documentTitle || documents.value.find((d) => d.id === chunk.documentId)?.title || `文档 #${chunk.documentId}`

const docTitleById = (id: number | null) =>
  (id == null ? undefined : documents.value.find((d) => d.id === id)?.title) ?? (id == null ? '—' : `文档 #${id}`)

/** 工具栏筛选选中了具体文档：新增切片的归属随之确定，弹窗内不再提供选择 */
const documentLocked = computed(() => filterDocId.value !== 0)
/** 确实一份文档都没有（加载失败不算）：不存在合法的所属文档，新增入口应关闭 */
const noDocuments = computed(() => !documentsFailed.value && documents.value.length === 0)

async function reload() {
  loading.value = true
  try {
    const chunkId = searchChunkId.value.trim()
    const rows = await listChunks(adminToken.value, props.kbId, {
      pageNum: pagination.page,
      pageSize: pagination.pageSize,
      documentId: filterDocId.value || undefined,
      chunkId: chunkId ? Number(chunkId) || undefined : undefined,
      content: searchContent.value.trim() || undefined
    })
    chunks.value = rows.rows
    pagination.itemCount = rows.total
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载切片失败')
  } finally {
    loading.value = false
  }
}

function applyFilters() {
  pagination.page = 1
  void reload()
}

function excerpt(content: string, max = 240): string {
  const collapsed = content.replace(/\s+/g, ' ').trim()
  return collapsed.length > max ? `${collapsed.slice(0, max)}…` : collapsed
}

// ---- 新增 / 编辑 / 详情 ----

const modalMode = ref<'add' | 'edit' | 'detail' | null>(null)
const current = ref<KnowledgeChunkResponse | null>(null)
const addDocId = ref<number | null>(null)
const editContent = ref('')
const saving = ref(false)

function handlePage(page: number) {
  pagination.page = page
  void reload()
}

function handlePageSize(pageSize: number) {
  pagination.pageSize = pageSize
  pagination.page = 1
  void reload()
}

function openAdd() {
  modalMode.value = 'add'
  addDocId.value = filterDocId.value || null
  editContent.value = ''
}

function openEdit(chunk: KnowledgeChunkResponse) {
  modalMode.value = 'edit'
  current.value = chunk
  editContent.value = chunk.content
}

function openDetail(chunk: KnowledgeChunkResponse) {
  modalMode.value = 'detail'
  current.value = chunk
}

const addDocOptions = computed(() => documents.value.map((d) => ({ label: d.title, value: d.id })))

async function saveChunk() {
  if (modalMode.value === 'add' && addDocId.value == null) {
    message.warning('请选择所属文档')
    return
  }
  if (!editContent.value.trim()) {
    message.warning('请输入切片内容')
    return
  }
  saving.value = true
  try {
    if (modalMode.value === 'add') {
      await addChunk(adminToken.value, props.kbId, { documentId: addDocId.value!, content: editContent.value })
      message.success('切片已新增并向量入库')
      // 新切片按 ID 倒序落在第 1 页顶端，停在第 2 页及以后会「保存成功却看不见」
      pagination.page = 1
    } else if (modalMode.value === 'edit' && current.value) {
      await updateChunk(adminToken.value, props.kbId, current.value.id, { content: editContent.value })
      message.success('切片已更新并重新嵌入')
    }
    modalMode.value = null
    await reload()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    saving.value = false
  }
}

function handleDelete(chunk: KnowledgeChunkResponse) {
  dialog.warning({
    title: '删除确认',
    content: `确认删除切片 #${chunk.id}？删除后不可恢复，将同步清理其向量。`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await deleteChunk(adminToken.value, props.kbId, chunk.id)
        message.success('切片已删除')
        await reload()
      } catch (e) {
        message.error(e instanceof Error ? e.message : '删除失败')
      }
    }
  })
}

const charCount = computed(() => editContent.value.length)
const showEditor = computed(() => modalMode.value === 'add' || modalMode.value === 'edit')
const showDetail = computed(() => modalMode.value === 'detail')

function closeEditor() {
  if (!saving.value) modalMode.value = null
}

function closeDetail() {
  modalMode.value = null
}

function handleEditorShow(show: boolean) {
  if (!show) closeEditor()
}

function handleDetailShow(show: boolean) {
  if (!show) closeDetail()
}
</script>

<template>
  <div class="chunk-tab">
    <div class="toolbar">
      <span class="count">共 {{ pagination.itemCount }} 切片</span>
      <n-space :size="8" align="center">
        <n-select v-model:value="filterDocId" :options="docOptions" size="small" style="width: 180px" placeholder="按文档ID筛选" @update:value="applyFilters" />
        <n-input v-model:value="searchChunkId" size="small" placeholder="搜索切片 ID" style="width: 150px" clearable @keyup.enter="applyFilters" @clear="applyFilters" />
        <n-input v-model:value="searchContent" size="small" placeholder="搜索片段内容" style="width: 180px" clearable @keyup.enter="applyFilters" @clear="applyFilters" />
        <n-button size="small" secondary @click="applyFilters">查询</n-button>
        <n-button size="small" type="primary" :disabled="noDocuments" @click="openAdd">
          <template #icon><n-icon :component="Plus" /></template>
          新增切片
        </n-button>
        <!-- 禁用控件收不到鼠标事件，提示只能常驻 -->
        <span v-if="noDocuments" class="form-hint">请先导入文档</span>
      </n-space>
    </div>

    <n-spin class="chunk-spin" :show="loading">
      <div v-if="chunks.length === 0 && !loading" class="empty-box">
        <n-empty :description="noDocuments ? '该知识库还没有文档，请先在「原始文档」页导入' : '暂无切片数据'">
          <template #extra>
            <n-button v-if="!noDocuments" size="small" secondary @click="openAdd">新增切片</n-button>
          </template>
        </n-empty>
      </div>
      <div v-else class="chunk-grid">
        <div v-for="(chunk, i) in chunks" :key="chunk.id" class="chunk-card">
          <div class="chunk-head">
            <span class="chunk-seq">#{{ (pagination.page - 1) * pagination.pageSize + i + 1 }}</span>
            <span class="chunk-id">ID {{ chunk.id }}</span>
            <div class="chunk-actions">
              <n-button size="tiny" quaternary @click="openDetail(chunk)">
                <template #icon><n-icon :component="Eye" /></template>
                详情
              </n-button>
              <n-button size="tiny" quaternary @click="openEdit(chunk)">
                <template #icon><n-icon :component="Pencil" /></template>
                编辑
              </n-button>
              <n-button size="tiny" quaternary type="error" @click="handleDelete(chunk)">
                <template #icon><n-icon :component="Trash2" /></template>
                删除
              </n-button>
            </div>
          </div>
          <div class="chunk-title">{{ docTitleOf(chunk) }}</div>
          <p class="chunk-content">{{ excerpt(chunk.content) }}</p>
          <div class="chunk-foot">
            <n-icon :component="FileText" size="14" />
            <n-ellipsis style="max-width: 320px">{{ docTitleOf(chunk) }}</n-ellipsis>
            <span>· 字符 {{ chunk.content.length }}</span>
            <span>· 更新于 {{ formatDateTime(chunk.updatedAt) ?? formatDateTime(chunk.createdAt) ?? '—' }}</span>
          </div>
        </div>
      </div>
    </n-spin>

    <n-pagination
      v-if="pagination.itemCount > pagination.pageSize"
      :page="pagination.page"
      :page-size="pagination.pageSize"
      :item-count="pagination.itemCount"
      :page-sizes="[10, 20, 50, 100]"
      show-size-picker
      style="justify-content: flex-end; margin-top: 14px"
      @update:page="handlePage"
      @update:page-size="handlePageSize"
    />

    <!-- 新增 / 编辑切片 -->
    <n-modal :show="showEditor" @update:show="handleEditorShow" preset="card" :title="modalMode === 'add' ? '新增切片' : '编辑切片'" style="width: 640px" :bordered="false">
      <n-space vertical :size="14">
        <div>
          <div class="form-label">所属文档</div>
          <div v-if="modalMode === 'edit'" class="doc-static">{{ current ? docTitleOf(current) : '' }}</div>
          <!-- 归属已由列表筛选决定：渲染静态文本，而不是一个「灰着的」可点控件 -->
          <template v-else-if="documentLocked">
            <div class="doc-static">{{ docTitleById(addDocId) }}</div>
            <div class="form-hint">由列表筛选决定；如需更换，请先在上方筛选器中切换文档</div>
          </template>
          <!-- 文档列表没加载出来：给重试入口，而不是让人面对一个空下拉 -->
          <template v-else-if="documentsFailed">
            <div class="form-hint">
              文档列表加载失败
              <n-button text size="tiny" @click="loadDocuments">重试</n-button>
            </div>
          </template>
          <n-select
            v-else
            v-model:value="addDocId"
            :options="addDocOptions"
            placeholder="选择所属文档"
            filterable
          />
        </div>
        <div>
          <div class="form-label-row">
            <span class="form-label">切片内容</span>
            <span class="char-count">{{ charCount }} 字符</span>
          </div>
          <n-input
            v-model:value="editContent"
            type="textarea"
            :autosize="{ minRows: 8, maxRows: 16 }"
            placeholder="输入切片内容，保存后自动重新嵌入向量"
          />
        </div>
      </n-space>
      <template #footer>
        <n-space justify="end">
          <n-button @click="closeEditor">取消</n-button>
          <n-button type="primary" :loading="saving" @click="saveChunk">保存</n-button>
        </n-space>
      </template>
    </n-modal>

    <!-- 切片详情（只读） -->
    <n-modal :show="showDetail" @update:show="handleDetailShow" preset="card" title="切片详情" style="width: 720px" :bordered="false">
      <template v-if="current">
        <n-space vertical :size="12">
          <div class="detail-meta">
            <n-text depth="3">切片 ID：#{{ current.id }} · 所属文档：{{ docTitleOf(current) }}（文档 ID {{ current.documentId }}）</n-text>
            <n-text depth="3">序号：{{ current.chunkIndex }} · 字符：{{ current.content.length }} · 来源：{{ current.sourceType ?? 'TEXT' }}</n-text>
            <n-text depth="3">创建：{{ formatDateTime(current.createdAt) ?? '—' }} · 更新：{{ formatDateTime(current.updatedAt) ?? '—' }}</n-text>
          </div>
          <div class="detail-content">{{ current.content }}</div>
        </n-space>
      </template>
      <template #footer>
        <n-space justify="end">
          <n-button v-if="current" @click="openEdit(current)">编辑</n-button>
          <n-button type="primary" @click="closeDetail">关闭</n-button>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>

<style scoped>
.chunk-tab {
  display: flex;
  flex-direction: column;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
  flex-wrap: wrap;
  gap: 8px;
}

.count {
  font-size: 13px;
  color: rgba(60, 60, 60, 0.85);
}

.chunk-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(360px, 1fr));
  gap: 12px;
}

/* 首次加载时内容为空，给 spinner 一个稳定的内容区，避免贴在筛选栏下方。 */
.chunk-spin {
  min-height: 260px;
}

.chunk-spin :deep(.n-spin-container) {
  min-height: 260px;
}

.chunk-card {
  border: 1px solid rgba(128, 128, 128, 0.22);
  border-radius: 10px;
  padding: 12px 14px;
  background: rgba(255, 255, 255, 0.6);
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.chunk-card:hover {
  border-color: rgba(64, 128, 255, 0.6);
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.06);
}

.chunk-head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.chunk-seq {
  color: #2f7bff;
  font-weight: 600;
  font-size: 13px;
}

.chunk-id {
  font-size: 12px;
  color: rgba(90, 90, 90, 0.9);
  background: rgba(128, 128, 128, 0.12);
  padding: 1px 6px;
  border-radius: 4px;
}

.chunk-actions {
  margin-left: auto;
  opacity: 0;
  transition: opacity 0.15s;
  display: flex;
}

.chunk-card:hover .chunk-actions {
  opacity: 1;
}

.chunk-title {
  font-weight: 600;
  font-size: 13px;
}

.chunk-content {
  margin: 0;
  font-size: 12.5px;
  line-height: 1.7;
  color: rgba(50, 50, 50, 0.92);
  max-height: 132px;
  overflow: hidden;
  white-space: pre-line;
}

.chunk-foot {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: rgba(120, 120, 120, 0.95);
  border-top: 1px dashed rgba(128, 128, 128, 0.25);
  padding-top: 8px;
}

.empty-box {
  padding: 40px 0;
}

.form-label-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}

.form-label {
  font-size: 13px;
  font-weight: 500;
  margin-bottom: 6px;
}

.char-count {
  font-size: 12px;
  color: rgba(120, 120, 120, 0.9);
}

.form-hint {
  font-size: 12px;
  color: rgba(120, 120, 120, 0.9);
  margin-top: 4px;
}

.doc-static {
  font-size: 13px;
  color: rgba(60, 60, 60, 0.95);
  padding: 6px 0;
}

.detail-meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.detail-content {
  border: 1px solid rgba(128, 128, 128, 0.25);
  border-radius: 8px;
  padding: 12px;
  white-space: pre-wrap;
  max-height: 46vh;
  overflow-y: auto;
  font-size: 13px;
  line-height: 1.8;
  background: rgba(245, 245, 245, 0.5);
}
</style>
