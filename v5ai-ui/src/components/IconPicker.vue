<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import {
  NButton,
  NEmpty,
  NInput,
  NModal,
  NPagination,
  NSpace,
  NSpin,
  NUpload,
  useMessage,
  type UploadFileInfo
} from 'naive-ui'
import { listResources, loadImagePreview, uploadResource, type ResourceResponse } from '../api/client'

const props = defineProps<{
  show: boolean
  modelValue: string
  adminToken: string
}>()

const emit = defineEmits<{
  (e: 'update:show', value: boolean): void
  (e: 'update:modelValue', value: string): void
}>()

const message = useMessage()

/** 待确认的图标 URL：/api/ 应用内路径 或 http(s) 外部地址；空串表示清除。 */
const pending = ref('')
/** 待确认值的可预览地址（blob: objectURL 或直接 http URL）。 */
const preview = ref('')
const uploading = ref(false)

// ---- 资源库（bizType=AVATAR 图片，单选） ----
const library = ref<ResourceResponse[]>([])
const libraryPreviews = reactive<Record<number, string>>({})
const libPage = ref(1)
const libPageSize = ref(12)
const libItemCount = ref(0)
const libLoading = ref(false)

// ---- 网络 URL 输入 ----
const urlInput = ref('')
const urlError = ref('')

const HTTP_URL_RE = /^https?:\/\/\S+$/i

function revokeBlobs() {
  if (preview.value.startsWith('blob:')) URL.revokeObjectURL(preview.value)
  for (const url of Object.values(libraryPreviews)) {
    if (url.startsWith('blob:')) URL.revokeObjectURL(url)
  }
}

async function resolvePreview(value: string): Promise<string> {
  if (!value) return ''
  try {
    return await loadImagePreview(props.adminToken, value)
  } catch {
    return ''
  }
}

async function refreshPreview() {
  if (preview.value.startsWith('blob:')) URL.revokeObjectURL(preview.value)
  preview.value = ''
  if (pending.value) {
    preview.value = await resolvePreview(pending.value)
  }
}

async function loadLibrary() {
  libLoading.value = true
  try {
    const res = await listResources(props.adminToken, {
      bizType: 'AVATAR',
      pageNum: libPage.value,
      pageSize: libPageSize.value
    })
    // 客户端过滤：仅展示图片资源
    library.value = (res.rows ?? []).filter((r) => r.accessUrl && r.mimeType?.startsWith('image/'))
    libItemCount.value = res.total ?? 0
    for (const url of Object.values(libraryPreviews)) {
      if (url.startsWith('blob:')) URL.revokeObjectURL(url)
    }
    for (const key of Object.keys(libraryPreviews)) delete libraryPreviews[Number(key)]
    await Promise.all(
      library.value.map(async (r) => {
        try {
          libraryPreviews[r.id] = await loadImagePreview(props.adminToken, r.accessUrl!)
        } catch {
          libraryPreviews[r.id] = ''
        }
      })
    )
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载图标列表失败')
  } finally {
    libLoading.value = false
  }
}

async function handleUploadChange(options: { file: UploadFileInfo }) {
  const raw = options.file.file
  if (!(raw instanceof File)) return
  uploading.value = true
  try {
    const saved = await uploadResource(props.adminToken, raw, { bizType: 'AVATAR' })
    const url = saved.accessUrl ?? ''
    pending.value = url
    urlInput.value = ''
    urlError.value = ''
    await refreshPreview()
    libPage.value = 1
    await loadLibrary()
    if (url) message.success('图标已上传并选中')
  } catch (e) {
    message.error(e instanceof Error ? e.message : '上传图标失败')
  } finally {
    uploading.value = false
  }
}

function selectFromLibrary(item: ResourceResponse) {
  if (!item.accessUrl) return
  pending.value = item.accessUrl
  urlInput.value = ''
  urlError.value = ''
  void refreshPreview()
}

function handleUrlChange() {
  const value = urlInput.value.trim()
  if (!value) {
    urlError.value = ''
    return
  }
  if (!HTTP_URL_RE.test(value)) {
    urlError.value = '请输入合法的 http(s) 地址'
    return
  }
  urlError.value = ''
  pending.value = value
  void refreshPreview()
}

function clearIcon() {
  pending.value = ''
  urlInput.value = ''
  urlError.value = ''
  void refreshPreview()
}

function handleLibPage(page: number) {
  libPage.value = page
  void loadLibrary()
}

function handleLibPageSize(size: number) {
  libPageSize.value = size
  libPage.value = 1
  void loadLibrary()
}

function handleModalShow(show: boolean) {
  emit('update:show', show)
}

function handleConfirm() {
  const value = urlInput.value.trim()
  if (value && !HTTP_URL_RE.test(value)) {
    urlError.value = '请输入合法的 http(s) 地址'
    return
  }
  emit('update:modelValue', pending.value)
  emit('update:show', false)
}

function handleCancel() {
  emit('update:show', false)
}

watch(
  () => props.show,
  (open) => {
    if (open) {
      pending.value = props.modelValue
      // 当前图标是网络图标（http(s) 绝对地址）时回填到地址框；相对路径（/api/、/icons/）不回填
      urlInput.value = HTTP_URL_RE.test(props.modelValue) ? props.modelValue : ''
      urlError.value = ''
      libPage.value = 1
      void refreshPreview()
      void loadLibrary()
    } else {
      revokeBlobs()
      preview.value = ''
      library.value = []
    }
  }
)
</script>

<template>
  <n-modal :show="show" preset="card" title="选择图标" style="width: 640px" :bordered="false" @update:show="handleModalShow">
    <n-space vertical :size="16">
      <!-- 统一预览 -->
      <div style="display: flex; align-items: center; gap: 12px; min-height: 48px">
        <img
          v-if="preview"
          :src="preview"
          alt="选中图标"
          style="width: 48px; height: 48px; border-radius: 6px; object-fit: cover; border: 1px solid #eee; background: #fafafa"
        />
        <span v-else style="color: #999; font-size: 13px">尚未选择图标</span>
        <n-button v-if="pending" size="small" quaternary type="error" @click="clearIcon">清除</n-button>
      </div>

      <!-- 上传图标 -->
      <div>
        <div style="font-weight: 600; margin-bottom: 8px">上传图标</div>
        <n-upload :default-upload="false" :show-file-list="false" accept="image/*" :disabled="uploading" @change="handleUploadChange">
          <n-button size="small" :loading="uploading">选择图片上传</n-button>
        </n-upload>
      </div>

      <!-- 资源列表单选 -->
      <div>
        <div style="font-weight: 600; margin-bottom: 8px">选择已上传图标</div>
        <n-spin :show="libLoading">
          <div v-if="library.length" style="display: flex; flex-wrap: wrap; gap: 8px">
            <div
              v-for="item in library"
              :key="item.id"
              :title="item.originalName ?? ''"
              :style="{
                width: '56px',
                height: '56px',
                borderRadius: '6px',
                cursor: 'pointer',
                overflow: 'hidden',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                background: '#fafafa',
                border: `2px solid ${pending === item.accessUrl ? '#18a058' : 'transparent'}`
              }"
              @click="selectFromLibrary(item)"
            >
              <img v-if="libraryPreviews[item.id]" :src="libraryPreviews[item.id]" alt="" style="width: 100%; height: 100%; object-fit: cover" />
            </div>
          </div>
          <n-empty v-else-if="!libLoading" description="暂无已上传的图标，可先在上方上传" style="padding: 12px 0" />
        </n-spin>
        <n-pagination
          v-if="libItemCount > libPageSize"
          :page="libPage"
          :page-size="libPageSize"
          :item-count="libItemCount"
          style="justify-content: flex-end; margin-top: 8px"
          @update:page="handleLibPage"
          @update:page-size="handleLibPageSize"
        />
      </div>

      <!-- 网络 URL -->
      <div>
        <div style="font-weight: 600; margin-bottom: 8px">网络图标URL</div>
        <n-input v-model:value="urlInput" placeholder="https://example.com/logo.png" clearable @update:value="handleUrlChange" />
        <div v-if="urlError" style="color: #d03050; font-size: 12px; margin-top: 4px">{{ urlError }}</div>
      </div>
    </n-space>

    <template #footer>
      <n-space justify="end">
        <n-button @click="handleCancel">取消</n-button>
        <n-button type="primary" @click="handleConfirm">确定</n-button>
      </n-space>
    </template>
  </n-modal>
</template>
