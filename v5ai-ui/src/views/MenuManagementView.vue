<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
import {
  NButton,
  NCard,
  NDataTable,
  NForm,
  NFormItem,
  NIcon,
  NInput,
  NInputNumber,
  NModal,
  NSelect,
  NSpace,
  NSwitch,
  NTag,
  useDialog,
  useMessage,
  type DataTableColumns
} from 'naive-ui'
import { Plus } from 'lucide-vue-next'
import PageHeader from '../components/PageHeader.vue'
import { createMenu, deleteMenu, listMenus, updateMenu, type Menu } from '../api/client'
import { adminToken } from '../stores/session'

type MenuRow = Menu & { children?: MenuRow[] }

const message = useMessage()
const dialog = useDialog()
const loading = ref(false)
const menus = ref<Menu[]>([])
const showModal = ref(false)
const editingId = ref<number | null>(null)
const saving = ref(false)

const form = reactive({
  parentId: null as number | null,
  code: '',
  name: '',
  type: 'MENU' as string,
  routeName: '',
  path: '',
  icon: '',
  sortOrder: 0,
  visible: true
})

function buildTree(list: Menu[]): MenuRow[] {
  const byParent = new Map<number | null, Menu[]>()
  for (const m of list) {
    const key = m.parentId ?? null
    const bucket = byParent.get(key) ?? []
    bucket.push(m)
    byParent.set(key, bucket)
  }
  const build = (parentId: number | null): MenuRow[] =>
    (byParent.get(parentId) ?? [])
      .slice()
      .sort((a, b) => a.sortOrder - b.sortOrder)
      .map((m) => {
        const row: MenuRow = { ...m }
        const kids = build(m.id)
        if (kids.length) row.children = kids
        return row
      })
  return build(null)
}

const rows = computed(() => buildTree(menus.value))

const parentOptions = computed(() =>
  menus.value.filter((m) => m.type === 'DIR').map((d) => ({ label: d.name, value: d.id }))
)

const hasChildren = (id: number) => menus.value.some((m) => m.parentId === id)

const columns: DataTableColumns<MenuRow> = [
  { title: '名称', key: 'name' },
  {
    title: '类型',
    key: 'type',
    width: 80,
    render: (row) =>
      h(NTag, { size: 'small', type: row.type === 'DIR' ? 'warning' : 'info', bordered: false }, {
        default: () => (row.type === 'DIR' ? '分组' : '页面')
      })
  },
  { title: 'Code', key: 'code', width: 160 },
  { title: '路由名', key: 'routeName', width: 150, render: (row) => row.routeName ?? '-' },
  { title: '图标', key: 'icon', width: 130, render: (row) => row.icon ?? '-' },
  { title: '排序', key: 'sortOrder', width: 70 },
  {
    title: '可见',
    key: 'visible',
    width: 80,
    render: (row) =>
      h(NTag, { size: 'small', type: row.visible ? 'success' : 'default', bordered: false }, {
        default: () => (row.visible ? '显示' : '隐藏')
      })
  },
  {
    title: '操作',
    key: 'actions',
    width: 150,
    render: (row) =>
      h(NSpace, { size: 6 }, {
        default: () => [
          h(NButton, { size: 'small', secondary: true, type: 'info', onClick: () => openEdit(row) }, { default: () => '编辑' }),
          h(NButton, { size: 'small', secondary: true, type: 'error', disabled: hasChildren(row.id), onClick: () => handleDelete(row) }, { default: () => '删除' })
        ]
      })
  }
]

async function reload() {
  loading.value = true
  try {
    menus.value = (await listMenus(adminToken.value)).rows
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载菜单失败')
  } finally {
    loading.value = false
  }
}

function resetForm() {
  form.parentId = null
  form.code = ''
  form.name = ''
  form.type = 'MENU'
  form.routeName = ''
  form.path = ''
  form.icon = ''
  form.sortOrder = 0
  form.visible = true
}

function openCreate() {
  editingId.value = null
  resetForm()
  showModal.value = true
}

function openEdit(row: Menu) {
  editingId.value = row.id
  form.parentId = row.parentId ?? null
  form.code = row.code
  form.name = row.name
  form.type = row.type
  form.routeName = row.routeName ?? ''
  form.path = row.path ?? ''
  form.icon = row.icon ?? ''
  form.sortOrder = row.sortOrder
  form.visible = row.visible
  showModal.value = true
}

async function handleSave() {
  saving.value = true
  try {
    const payload = {
      parentId: form.type === 'DIR' ? null : form.parentId,
      name: form.name,
      type: form.type,
      routeName: form.type === 'MENU' ? form.routeName || undefined : null,
      path: form.path || undefined,
      icon: form.icon || undefined,
      sortOrder: form.sortOrder,
      visible: form.visible
    }
    if (editingId.value) {
      await updateMenu(adminToken.value, editingId.value, payload)
      message.success('菜单已更新')
    } else {
      await createMenu(adminToken.value, { ...payload, code: form.code })
      message.success('菜单已创建')
    }
    showModal.value = false
    await reload()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '保存菜单失败')
  } finally {
    saving.value = false
  }
}

async function handleDelete(row: Menu) {
  dialog.warning({
    title: '删除确认',
    content: `确认删除菜单「${row.name}」？`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await deleteMenu(adminToken.value, row.id)
        message.success('菜单已删除')
        await reload()
      } catch (e) {
        message.error(e instanceof Error ? e.message : '删除菜单失败')
      }
    }
  })
}

onMounted(reload)
</script>

<template>
  <div class="page">
    <PageHeader title="菜单管理" description="管理菜单树与页面可见性。分组（DIR）位于顶级，页面（MENU）绑定路由名。">
      <template #actions>
        <n-button size="small" type="primary" @click="openCreate">
          <template #icon><n-icon :component="Plus" /></template>
          新建菜单
        </n-button>
      </template>
    </PageHeader>

    <n-card :bordered="true">
      <n-data-table
        :loading="loading"
        :columns="columns"
        :data="rows"
        :bordered="false"
        :row-key="(row: MenuRow) => row.id"
        default-expand-all
      />
    </n-card>

    <n-modal
      v-model:show="showModal"
      preset="card"
      :title="editingId ? '编辑菜单' : '新建菜单'"
      style="width: 480px"
      :bordered="false"
    >
      <n-form label-placement="top">
        <n-form-item label="类型">
          <n-select
            v-model:value="form.type"
            :options="[
              { label: '分组（DIR，顶级导航分组）', value: 'DIR' },
              { label: '页面（MENU，绑定路由）', value: 'MENU' }
            ]"
          />
        </n-form-item>
        <n-form-item v-if="form.type === 'MENU'" label="所属分组">
          <n-select v-model:value="form.parentId" clearable :options="parentOptions" placeholder="留空表示顶级页面" />
        </n-form-item>
        <n-form-item label="Code（唯一，创建后不可改）">
          <n-input v-model:value="form.code" :disabled="!!editingId" placeholder="小写字母/数字/横线，如 system-menus" />
        </n-form-item>
        <n-form-item label="名称">
          <n-input v-model:value="form.name" placeholder="如 菜单管理" />
        </n-form-item>
        <n-form-item v-if="form.type === 'MENU'" label="路由名（route name）">
          <n-input v-model:value="form.routeName" placeholder="与前端路由 name 一致，如 system-menus" />
        </n-form-item>
        <n-form-item v-if="form.type === 'MENU'" label="路径（可选，仅展示）">
          <n-input v-model:value="form.path" placeholder="如 system/menus" />
        </n-form-item>
        <n-form-item label="图标名（可选）">
          <n-input v-model:value="form.icon" placeholder="如 menus / users / settings" />
        </n-form-item>
        <n-form-item label="排序">
          <n-input-number v-model:value="form.sortOrder" :min="0" style="width: 100%" />
        </n-form-item>
        <n-form-item label="在导航中显示">
          <n-switch v-model:value="form.visible" />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showModal = false">取消</n-button>
          <n-button type="primary" :loading="saving" @click="handleSave">保存</n-button>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>
