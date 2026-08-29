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
  NPopconfirm,
  NSelect,
  NSpace,
  NSwitch,
  NTag,
  useDialog,
  useMessage,
  type DataTableColumns
} from 'naive-ui'
import { Plus, RefreshCw, Search } from 'lucide-vue-next'
import PageHeader from '../components/PageHeader.vue'
import {
  createMenu,
  deleteMenu,
  deleteMenusCascade,
  listMenus,
  updateMenu,
  type Menu,
  type MenuForm
} from '../api/client'
import { adminToken } from '../stores/session'

type MenuRow = Menu & { children?: MenuRow[] }

const message = useMessage()
const dialog = useDialog()
const loading = ref(false)
const menus = ref<Menu[]>([])
const search = reactive({ menuName: '', status: null as string | null })

const MENU_TYPE_LABELS: Record<string, string> = { M: '目录', C: '菜单', F: '按钮' }

/** 由扁平列表构建树。 */
function buildTree(list: Menu[]): MenuRow[] {
  const map = new Map<number, MenuRow>()
  for (const m of list) map.set(m.id, { ...m, children: [] })
  const roots: MenuRow[] = []
  for (const m of map.values()) {
    if (m.parentId && map.has(m.parentId)) {
      map.get(m.parentId)!.children!.push(m)
    } else {
      roots.push(m)
    }
  }
  return roots
}

const treeData = computed<MenuRow[]>(() => buildTree(menus.value))

/** 父菜单下拉选项（树拍平，排除按钮类型）。 */
const parentOptions = computed(() => {
  const opts: { label: string; value: number }[] = [{ label: '根目录', value: 0 }]
  const walk = (list: MenuRow[], prefix: string) => {
    for (const m of list) {
      if (m.menuType !== 'F') {
        opts.push({ label: `${prefix}${m.menuName}`, value: m.id })
      }
      if (m.children) walk(m.children, `${prefix}${m.menuName} / `)
    }
  }
  walk(treeData.value, '')
  return opts
})

const columns: DataTableColumns<MenuRow> = [
  { title: '菜单名称', key: 'menuName', width: 220 },
  {
    title: '类型',
    key: 'menuType',
    width: 80,
    render: (r) => h(NTag, { size: 'small', bordered: false, round: true }, { default: () => MENU_TYPE_LABELS[r.menuType] ?? r.menuType })
  },
  { title: '图标', key: 'icon', width: 80, render: (r) => r.icon ?? '-' },
  { title: '排序', key: 'orderNum', width: 70, render: (r) => r.orderNum ?? 0 },
  { title: '路由地址', key: 'path', width: 150, render: (r) => r.path ?? '-' },
  { title: '组件路径', key: 'component', width: 170, render: (r) => r.component ?? '-' },
  { title: '权限标识', key: 'perms', width: 170, render: (r) => r.perms ?? '-' },
  {
    title: '可见',
    key: 'visible',
    width: 70,
    render: (r) => (r.visible === '1' ? h(NTag, { size: 'small', bordered: false }, { default: () => '隐藏' }) : h(NTag, { type: 'success', size: 'small', bordered: false }, { default: () => '显示' }))
  },
  {
    title: '状态',
    key: 'status',
    width: 70,
    render: (r) => (r.status === '0'
      ? h(NTag, { type: 'success', size: 'small', bordered: false, round: true }, { default: () => '正常' })
      : h(NTag, { type: 'default', size: 'small', bordered: false, round: true }, { default: () => '停用' }))
  },
  {
    title: '操作',
    key: 'actions',
    width: 200,
    render: (r) =>
      h(NSpace, { size: 4 }, {
        default: () => [
          h(NButton, { size: 'small', quaternary: true, onClick: () => openCreate(r) }, { default: () => '新增' }),
          h(NButton, { size: 'small', quaternary: true, onClick: () => openEdit(r) }, { default: () => '编辑' }),
          h(
            NPopconfirm,
            { onPositiveClick: () => handleDelete(r) },
            {
              trigger: () => h(NButton, { size: 'small', quaternary: true, type: 'error' }, { default: () => '删除' }),
              default: () => `确认删除菜单「${r.menuName}」？`
            }
          )
        ]
      })
  }
]

async function reload() {
  loading.value = true
  try {
    menus.value = await listMenus(adminToken.value, {
      menuName: search.menuName || undefined,
      status: search.status ?? undefined
    })
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载菜单失败')
  } finally {
    loading.value = false
  }
}

// ---- 新增/编辑 ----

const showModal = ref(false)
const saving = ref(false)
const isEdit = ref(false)
const form = reactive<MenuForm>({
  menuName: '',
  parentId: 0,
  orderNum: 0,
  path: '',
  component: '',
  queryParam: '',
  isFrame: 0,
  isCache: 0,
  menuType: 'C',
  visible: '0',
  status: '0',
  perms: '',
  icon: '',
  remark: ''
})

function openCreate(parent?: MenuRow) {
  isEdit.value = false
  Object.assign(form, {
    id: undefined,
    menuName: '',
    parentId: parent?.id ?? 0,
    orderNum: 0,
    path: '',
    component: '',
    queryParam: '',
    isFrame: 0,
    isCache: 0,
    menuType: parent?.menuType === 'M' ? 'C' : 'M',
    visible: '0',
    status: '0',
    perms: '',
    icon: '',
    remark: ''
  })
  showModal.value = true
}

function openEdit(row: Menu) {
  isEdit.value = true
  Object.assign(form, {
    id: row.id,
    menuName: row.menuName,
    parentId: row.parentId ?? 0,
    orderNum: row.orderNum ?? 0,
    path: row.path ?? '',
    component: row.component ?? '',
    queryParam: row.queryParam ?? '',
    isFrame: row.isFrame ?? 0,
    isCache: row.isCache ?? 0,
    menuType: row.menuType,
    visible: row.visible ?? '0',
    status: row.status ?? '0',
    perms: row.perms ?? '',
    icon: row.icon ?? '',
    remark: row.remark ?? ''
  })
  showModal.value = true
}

async function handleSave() {
  if (!form.menuName || !form.menuType) {
    message.warning('请填写菜单名称与类型')
    return
  }
  saving.value = true
  try {
    if (isEdit.value) {
      await updateMenu(adminToken.value, { ...form })
      message.success('菜单已更新')
    } else {
      await createMenu(adminToken.value, { ...form })
      message.success('菜单已创建')
    }
    showModal.value = false
    await reload()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    saving.value = false
  }
}

// ---- 删除 ----

async function handleDelete(row: MenuRow) {
  try {
    await deleteMenu(adminToken.value, row.id)
    message.success('已删除')
    await reload()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '删除失败')
  }
}

/** 级联删除整棵子树（含子菜单）。 */
async function handleCascadeDelete(row: MenuRow) {
  const ids: number[] = []
  const collect = (r: MenuRow) => {
    ids.push(r.id)
    r.children?.forEach(collect)
  }
  collect(row)
  dialog.warning({
    title: '级联删除',
    content: `确认删除「${row.menuName}」及其全部子菜单（共 ${ids.length} 项）？`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await deleteMenusCascade(adminToken.value, ids)
        message.success('已删除')
        await reload()
      } catch (e) {
        message.error(e instanceof Error ? e.message : '删除失败')
      }
    }
  })
}

onMounted(reload)
</script>

<template>
  <div class="page">
    <PageHeader title="菜单管理" description="维护侧边导航与按钮权限标识（M 目录 / C 菜单 / F 按钮）。">
      <template #actions>
        <n-button size="small" type="primary" @click="openCreate()">
          <template #icon><n-icon :component="Plus" /></template>
          新增菜单
        </n-button>
      </template>
    </PageHeader>

    <n-card :bordered="true">
      <n-space style="margin-bottom: 12px" :size="8" align="center">
        <n-input v-model:value="search.menuName" placeholder="菜单名称" clearable style="width: 180px" @keyup.enter="reload" />
        <n-select
          v-model:value="search.status"
          placeholder="状态"
          clearable
          style="width: 120px"
          :options="[{ label: '正常', value: '0' }, { label: '停用', value: '1' }]"
        />
        <n-button size="small" type="primary" secondary @click="reload">
          <template #icon><n-icon :component="Search" /></template>
          查询
        </n-button>
        <n-button size="small" quaternary @click="reload">
          <template #icon><n-icon :component="RefreshCw" /></template>
          刷新
        </n-button>
      </n-space>

      <n-data-table
        :loading="loading"
        :columns="columns"
        :data="treeData"
        :row-key="(r: MenuRow) => r.id"
        :default-expanded-keys="[]"
        :bordered="false"
        :indent="20"
      />
    </n-card>

    <n-modal v-model:show="showModal" preset="card" :title="isEdit ? '编辑菜单' : '新增菜单'" style="width: 560px" :bordered="false">
      <n-form label-placement="top">
        <n-form-item label="上级菜单">
          <n-select v-model:value="form.parentId" :options="parentOptions" />
        </n-form-item>
        <n-space style="width: 100%">
          <n-form-item label="菜单类型" style="flex: 1">
            <n-select
              v-model:value="form.menuType"
              :options="[
                { label: '目录 M', value: 'M' },
                { label: '菜单 C', value: 'C' },
                { label: '按钮 F', value: 'F' }
              ]"
            />
          </n-form-item>
          <n-form-item label="显示排序" style="flex: 1">
            <n-input-number v-model:value="form.orderNum" :min="0" style="width: 100%" />
          </n-form-item>
        </n-space>
        <n-form-item label="菜单名称" required>
          <n-input v-model:value="form.menuName" placeholder="菜单显示名称" />
        </n-form-item>
        <n-form-item v-if="form.menuType !== 'F'" label="路由地址">
          <n-input v-model:value="form.path" placeholder="如 system/users" />
        </n-form-item>
        <n-form-item v-if="form.menuType === 'C'" label="组件路径">
          <n-input v-model:value="form.component" placeholder="如 system/users/index" />
        </n-form-item>
        <n-form-item v-if="form.menuType !== 'F'" label="权限标识">
          <n-input v-model:value="form.perms" placeholder="如 system:user:list" />
        </n-form-item>
        <n-form-item v-else label="权限标识" required>
          <n-input v-model:value="form.perms" placeholder="按钮权限码，如 system:user:add" />
        </n-form-item>
        <n-space style="width: 100%">
          <n-form-item label="图标" style="flex: 1">
            <n-input v-model:value="form.icon" placeholder="图标名（lucide）" />
          </n-form-item>
          <n-form-item label="路由参数" style="flex: 1">
            <n-input v-model:value="form.queryParam" placeholder="可选" />
          </n-form-item>
        </n-space>
        <n-space style="width: 100%">
          <n-form-item label="显示状态" style="flex: 1">
            <n-select v-model:value="form.visible" :options="[{ label: '显示', value: '0' }, { label: '隐藏', value: '1' }]" />
          </n-form-item>
          <n-form-item label="菜单状态" style="flex: 1">
            <n-select v-model:value="form.status" :options="[{ label: '正常', value: '0' }, { label: '停用', value: '1' }]" />
          </n-form-item>
        </n-space>
        <n-space style="width: 100%">
          <n-form-item label="外链">
            <n-switch :value="form.isFrame === 1" @update:value="(v: boolean) => (form.isFrame = v ? 1 : 0)" />
          </n-form-item>
          <n-form-item label="缓存">
            <n-switch :value="form.isCache === 1" @update:value="(v: boolean) => (form.isCache = v ? 1 : 0)" />
          </n-form-item>
          <n-form-item label="备注" style="flex: 1">
            <n-input v-model:value="form.remark" placeholder="可选" />
          </n-form-item>
        </n-space>
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
