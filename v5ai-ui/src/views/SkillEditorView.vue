<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import {
  NButton,
  NForm,
  NFormItem,
  NIcon,
  NInput,
  NModal,
  NSpace,
  NSpin,
  NTag,
  NTree,
  useDialog,
  useMessage,
  type TreeOption
} from 'naive-ui'
import { ArrowLeft, FilePlus2, FolderOpen, Save, Trash2 } from 'lucide-vue-next'
import MarkdownEditor from '../components/MarkdownEditor.vue'
import MonacoTextView from '../components/MonacoTextView.vue'
import {
  createSkillFile,
  deleteSkillFile,
  getSkillEditor,
  updateSkillFile,
  type SkillEditorResponse
} from '../api/client'
import { adminToken } from '../stores/session'

const route = useRoute()
const router = useRouter()
const message = useMessage()
const dialog = useDialog()

const skillId = Number(route.params.id)

const loading = ref(true)
const editorData = ref<SkillEditorResponse | null>(null)
/** 本地工作副本：path -> content */
const filesMap = reactive<Record<string, string>>({})
/** 未保存的文件路径集合（reactive Set 以驱动 UI） */
const dirtyPaths = reactive(new Set<string>())
const currentPath = ref<string | null>(null)
const saving = ref(false)

const showNewFile = ref(false)
const newFile = reactive({ path: '', content: '' })
const creating = ref(false)

const filePaths = computed(() => Object.keys(filesMap).sort())
const treeData = computed<TreeOption[]>(() => buildTree(filePaths.value))
const isDirty = computed(() => (currentPath.value ? dirtyPaths.has(currentPath.value) : false))
const currentKind = computed<'markdown' | 'code' | null>(() => {
  if (!currentPath.value || !(currentPath.value in filesMap)) return null
  return isMarkdown(currentPath.value) ? 'markdown' : 'code'
})

onMounted(async () => {
  try {
    const data = await getSkillEditor(adminToken.value, skillId)
    editorData.value = data
    for (const f of data.files) {
      filesMap[f.filePath] = f.content
    }
    currentPath.value = data.files[0]?.filePath ?? null
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载 Skill 编辑器失败')
  } finally {
    loading.value = false
  }
})

/** 离开页面且有未保存修改时二次确认，避免数据丢失。 */
onBeforeRouteLeave(() => {
  if (dirtyPaths.size === 0) return true
  return new Promise<boolean>((resolve) => {
    dialog.warning({
      title: '未保存的修改',
      content: '有未保存的修改，离开将丢失，是否继续？',
      positiveText: '离开',
      negativeText: '取消',
      onPositiveClick: () => resolve(true),
      onNegativeClick: () => resolve(false),
      onClose: () => resolve(false)
    })
  })
})

function handleSelect(keys: Array<string | number>) {
  const key = keys[0]
  if (typeof key === 'string' && key in filesMap) {
    switchTo(key)
  }
}

function switchTo(path: string) {
  if (path === currentPath.value) return
  if (currentPath.value && dirtyPaths.has(currentPath.value)) {
    dialog.warning({
      title: '未保存的修改',
      content: `「${currentPath.value}」有未保存的修改，切换后将丢失，是否继续？`,
      positiveText: '继续切换',
      negativeText: '取消',
      onPositiveClick: () => {
        currentPath.value = path
      }
    })
    return
  }
  currentPath.value = path
}

function handleChange(value: string) {
  if (!currentPath.value) return
  filesMap[currentPath.value] = value
  dirtyPaths.add(currentPath.value)
}

async function handleSave() {
  const path = currentPath.value
  if (!path) return
  saving.value = true
  try {
    await updateSkillFile(adminToken.value, skillId, path, filesMap[path])
    dirtyPaths.delete(path)
    message.success(`已保存 ${path}`)
  } catch (e) {
    message.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    saving.value = false
  }
}

function handleOpenNewFile() {
  newFile.path = ''
  newFile.content = ''
  showNewFile.value = true
}

async function handleCreateFile() {
  const path = newFile.path.trim()
  if (!path) {
    message.warning('请输入文件路径')
    return
  }
  creating.value = true
  try {
    await createSkillFile(adminToken.value, skillId, path, newFile.content)
    filesMap[path] = newFile.content
    showNewFile.value = false
    message.success(`已创建 ${path}`)
    switchTo(path)
  } catch (e) {
    message.error(e instanceof Error ? e.message : '创建失败')
  } finally {
    creating.value = false
  }
}

function handleDelete() {
  const path = currentPath.value
  if (!path) return
  if (path === 'SKILL.md') {
    message.warning('SKILL.md 是技能入口文件，不能删除')
    return
  }
  dialog.warning({
    title: '删除确认',
    content: `确认删除文件「${path}」？该操作不可恢复。`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await deleteSkillFile(adminToken.value, skillId, path)
        dirtyPaths.delete(path)
        delete filesMap[path]
        currentPath.value = filePaths.value[0] ?? null
        message.success(`已删除 ${path}`)
      } catch (e) {
        message.error(e instanceof Error ? e.message : '删除失败')
      }
    }
  })
}

function isMarkdown(path: string): boolean {
  return /\.(md|markdown)$/i.test(path)
}

/** 把平铺文件路径构造成 NTree 的层级数据。 */
function buildTree(paths: string[]): TreeOption[] {
  const root: TreeOption[] = []
  const childrenOf = new Map<string, TreeOption[]>()
  for (const path of paths) {
    const parts = path.split('/')
    let siblings = root
    let dirPath = ''
    for (let i = 0; i < parts.length - 1; i++) {
      dirPath = dirPath ? `${dirPath}/${parts[i]}` : parts[i]
      let dir = siblings.find((n) => n.key === dirPath)
      if (!dir) {
        const children: TreeOption[] = []
        dir = { key: dirPath, label: parts[i], children }
        childrenOf.set(dirPath, children)
        siblings.push(dir)
      }
      siblings = childrenOf.get(dirPath)!
    }
    siblings.push({ key: path, label: parts[parts.length - 1], isLeaf: true })
  }
  return root
}
</script>

<template>
  <div class="skill-editor">
    <div class="editor-topbar">
      <div class="editor-title">
        <n-button quaternary circle size="small" aria-label="返回 Skill 管理" @click="router.push({ name: 'skills' })">
          <template #icon><n-icon :component="ArrowLeft" /></template>
        </n-button>
        <span class="title-text">{{ editorData?.skill.name ?? 'Skill 编辑器' }}</span>
        <n-tag v-if="editorData" size="small" type="warning">v{{ editorData.version }} 草稿</n-tag>
        <span v-if="editorData?.skill.description" class="desc">{{ editorData.skill.description }}</span>
      </div>
      <div class="editor-actions">
        <n-alert v-if="editorData" type="info" :bordered="false" class="publish-hint">
          编辑内容保存到草稿版本，发布请在 Skill 管理的「版本」中操作
        </n-alert>
      </div>
    </div>

    <div class="editor-body">
      <div v-if="loading" class="editor-loading">
        <n-spin size="small" />
      </div>
      <template v-else-if="editorData">
        <div class="file-panel">
          <div class="file-panel-header">
            <span class="file-panel-title">文件（{{ filePaths.length }}）</span>
            <n-button size="tiny" type="primary" secondary @click="handleOpenNewFile">
              <template #icon><n-icon :component="FilePlus2" /></template>
              新建文件
            </n-button>
          </div>
          <div class="file-tree">
            <n-tree
              :data="treeData"
              :selected-keys="currentPath ? [currentPath] : []"
              block-line
              default-expand-all
              @update:selected-keys="handleSelect"
            />
          </div>
        </div>

        <div class="code-panel">
          <div v-if="currentPath" class="code-topbar">
            <span class="code-path">{{ currentPath }}</span>
            <n-tag v-if="isDirty" size="small" type="warning">已修改</n-tag>
            <n-space :size="8" class="code-actions">
              <n-button size="small" type="primary" :disabled="!isDirty" :loading="saving" @click="handleSave">
                <template #icon><n-icon :component="Save" /></template>
                保存
              </n-button>
              <n-button size="small" secondary type="error" :disabled="currentPath === 'SKILL.md'" @click="handleDelete">
                <template #icon><n-icon :component="Trash2" /></template>
                删除
              </n-button>
            </n-space>
          </div>
          <div v-if="currentKind === 'markdown'" class="editor-host">
            <markdown-editor
              :content="filesMap[currentPath!]"
              @change="handleChange"
              @error="message.error($event ?? 'Markdown 编辑器加载失败')"
            />
          </div>
          <div v-else-if="currentKind === 'code'" class="editor-host">
            <monaco-text-view
              :content="filesMap[currentPath!]"
              :file-name="currentPath!"
              :read-only="false"
              height="100%"
              @change="handleChange"
              @error="message.error($event ?? '代码编辑器加载失败')"
            />
          </div>
          <div v-else class="editor-empty">
            <n-icon :component="FolderOpen" size="28" />
            <p>请选择左侧文件，或点击「新建文件」开始编辑</p>
          </div>
        </div>
      </template>
    </div>

    <n-modal v-model:show="showNewFile" preset="card" title="新建文件" style="width: 480px" :bordered="false">
      <n-form label-placement="top">
        <n-form-item label="文件路径">
          <n-input v-model:value="newFile.path" placeholder="如 prompts/guide.md（支持多级目录）" @keyup.enter="handleCreateFile" />
        </n-form-item>
        <n-form-item label="初始内容（可选）">
          <n-input v-model:value="newFile.content" type="textarea" :rows="6" placeholder="文件初始内容" />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showNewFile = false">取消</n-button>
          <n-button type="primary" :loading="creating" @click="handleCreateFile">创建</n-button>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>

<style scoped>
.skill-editor {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 116px);
  min-height: 480px;
}

.editor-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}

.editor-title {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.title-text {
  font-size: 15px;
  font-weight: 600;
}

.desc {
  color: var(--n-text-color-3, #999);
  font-size: 12px;
  max-width: 40ch;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.publish-hint {
  padding: 4px 12px;
  font-size: 12px;
}

.editor-body {
  flex: 1;
  min-height: 0;
  display: flex;
  gap: 12px;
}

.editor-loading {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.file-panel {
  width: 240px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  border: 1px solid var(--n-border-color, #e0e0e6);
  border-radius: 6px;
  overflow: hidden;
}

.file-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 10px;
  border-bottom: 1px solid var(--n-border-color, #e0e0e6);
}

.file-panel-title {
  font-size: 13px;
  font-weight: 600;
}

.file-tree {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 6px 0;
}

.code-panel {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  border: 1px solid var(--n-border-color, #e0e0e6);
  border-radius: 6px;
  overflow: hidden;
}

.code-topbar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  border-bottom: 1px solid var(--n-border-color, #e0e0e6);
}

.code-path {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.code-actions {
  flex-shrink: 0;
}

.editor-host {
  flex: 1;
  min-height: 0;
}

.editor-empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: var(--n-text-color-3, #999);
}
</style>
