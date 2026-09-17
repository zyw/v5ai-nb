<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
import {
  NButton,
  NCard,
  NDataTable,
  NDivider,
  NForm,
  NFormItem,
  NFormItemGi,
  NGrid,
  NIcon,
  NInput,
  NInputNumber,
  NModal,
  NRadioButton,
  NRadioGroup,
  NSelect,
  NSwitch,
  NTag,
  NText,
  useDialog,
  useMessage,
  type DataTableColumns
} from 'naive-ui'
import { Plus, RefreshCw, Search } from 'lucide-vue-next'
import PageHeader from '../components/PageHeader.vue'
import RowActions, { type RowAction } from '../components/RowActions.vue'
import {
  changeMenuStatus,
  createMenu,
  deleteMenu,
  deleteMenusCascade,
  listMenus,
  updateMenu,
  type Menu,
  type MenuForm
} from '../api/client'
import { adminToken } from '../stores/session'
import { MENU_ICON_MAP, MENU_ICON_OPTIONS, resolveMenuIcon } from '../utils/menuIcons'

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

/** 图标 + slug 的展示节点：下拉选项、已选值、表格列共用。 */
function iconNode(slug: string) {
  return h('span', { style: 'display:inline-flex;align-items:center;gap:8px' }, [
    h(NIcon, { component: resolveMenuIcon(slug) }),
    h('span', slug)
  ])
}

/** 下拉选项：左侧画真实图标，右侧显示 slug（与侧边栏同一份注册表）。 */
const renderIconLabel = (option: { value?: unknown }) => iconNode(String(option.value ?? ''))

/** 已选值同样带图标预览。 */
const renderIconTag = ({ option }: { option: { value?: unknown } }) => iconNode(String(option.value ?? ''))

/**
 * 表格图标列：按钮菜单（F）不展示图标；空值与 '#' 占位按「无图标」展示；
 * 未注册的 slug 按纯文本展示，避免显示成兜底图标造成误判。
 */
function renderIconCell(row: MenuRow) {
  if (row.menuType === 'F' || !row.icon || row.icon === '#') return '-'
  return MENU_ICON_MAP[row.icon] ? iconNode(row.icon) : row.icon
}

const columns: DataTableColumns<MenuRow> = [
  { title: '菜单名称', key: 'menuName', width: 220 },
  {
    title: '类型',
    key: 'menuType',
    width: 80,
    render: (r) => h(NTag, { size: 'small', bordered: false, round: true }, { default: () => MENU_TYPE_LABELS[r.menuType] ?? r.menuType })
  },
  { title: '图标', key: 'icon', width: 150, render: (r) => renderIconCell(r) },
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
    width: 90,
    render: (r) =>
      h(NSwitch, {
        value: r.status === '0',
        loading: togglingId.value === r.id,
        onUpdateValue: () => toggleStatus(r)
      }, {
        checked: () => h('span', '启'),
        unchecked: () => h('span', '停')
      })
  },
  {
    title: '操作',
    key: 'actions',
    width: 120,
    render: (r) => {
      const actions: RowAction[] = [
        { key: 'edit', label: '编辑', quaternary: true, onClick: () => openEdit(r) },
        {
          key: 'delete',
          label: '删除',
          quaternary: true,
          type: 'error',
          confirm: `确认删除菜单「${r.menuName}」？`,
          onClick: () => handleDelete(r)
        }
      ]
      // 按钮（F）是最末级，不能再挂子菜单，故不提供「新增」
      if (r.menuType !== 'F') {
        actions.unshift({ key: 'add', label: '新增', quaternary: true, onClick: () => openCreate(r) })
      }
      return h(RowActions, { maxInline: 1, actions })
    }
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
  isFrame: 'N',
  isCache: 'Y',
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
    isFrame: 'N',
    isCache: 'Y',
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
    isFrame: row.isFrame ?? 'N',
    isCache: row.isCache ?? 'Y',
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

// ---- 状态 / 删除 ----

const togglingId = ref<number | null>(null)

async function toggleStatus(row: Menu) {
  const next = row.status === '0' ? '1' : '0'
  togglingId.value = row.id
  try {
    await changeMenuStatus(adminToken.value, row.id, next)
    message.success(next === '0' ? '已启用' : '已停用')
    await reload()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '操作失败')
  } finally {
    togglingId.value = null
  }
}

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

    <n-modal v-model:show="showModal" preset="card" :title="isEdit ? '编辑菜单' : '新增菜单'" style="width: 640px" :bordered="false">
      <n-form label-placement="top">
        <n-divider title-placement="left" style="margin: 0 0 16px">基础信息</n-divider>
        <n-grid :cols="2" :x-gap="16">
          <n-form-item-gi label="上级菜单">
            <n-select v-model:value="form.parentId" :options="parentOptions" />
          </n-form-item-gi>
          <n-form-item-gi label="菜单名称" required>
            <n-input v-model:value="form.menuName" placeholder="菜单显示名称" />
          </n-form-item-gi>
          <n-form-item-gi label="菜单类型">
            <n-radio-group v-model:value="form.menuType">
              <n-radio-button value="M">目录</n-radio-button>
              <n-radio-button value="C">菜单</n-radio-button>
              <n-radio-button value="F">按钮</n-radio-button>
            </n-radio-group>
          </n-form-item-gi>
          <n-form-item-gi label="显示排序">
            <n-input-number v-model:value="form.orderNum" :min="0" style="width: 100%" />
          </n-form-item-gi>
        </n-grid>

        <n-divider title-placement="left" style="margin: 8px 0 16px">路由与权限</n-divider>
        <n-form-item v-if="form.menuType !== 'F'" label="路由地址">
          <n-input v-model:value="form.path" placeholder="如 system/users" />
        </n-form-item>
        <n-form-item v-if="form.menuType === 'C'" label="组件路径">
          <n-input v-model:value="form.component" placeholder="如 system/users/index" />
        </n-form-item>
        <n-grid :cols="2" :x-gap="16">
          <n-form-item-gi label="路由参数">
            <n-input v-model:value="form.queryParam" placeholder="可选" />
          </n-form-item-gi>
          <n-form-item-gi v-if="form.menuType !== 'F'" label="权限标识">
            <n-input v-model:value="form.perms" placeholder="如 system:user:list" />
          </n-form-item-gi>
          <n-form-item-gi v-else label="权限标识" required>
            <n-input v-model:value="form.perms" placeholder="按钮权限码，如 system:user:add" />
          </n-form-item-gi>
        </n-grid>
        <n-form-item v-if="form.menuType !== 'F'" label="图标">
          <n-select
            :value="form.icon ?? ''"
            :options="MENU_ICON_OPTIONS"
            :render-label="renderIconLabel"
            :render-tag="renderIconTag"
            filterable
            clearable
            placeholder="从图标库中选择"
            @update:value="(v: string | null) => (form.icon = v ?? '')"
          />
        </n-form-item>

        <n-divider title-placement="left" style="margin: 8px 0 16px">显示与行为</n-divider>
        <n-form-item label="显示状态">
          <n-radio-group v-model:value="form.visible">
            <n-radio-button value="0">显示</n-radio-button>
            <n-radio-button value="1">隐藏</n-radio-button>
          </n-radio-group>
        </n-form-item>
        <n-grid :cols="2" :x-gap="16">
          <n-form-item-gi label="外链">
            <n-space align="center" :size="10" :wrap="false">
              <n-switch :value="form.isFrame === 'Y'" @update:value="(v: boolean) => (form.isFrame = v ? 'Y' : 'N')">
                <template #checked>是</template>
                <template #unchecked>否</template>
              </n-switch>
              <n-text depth="3" style="font-size: 12px">按外部地址打开</n-text>
            </n-space>
          </n-form-item-gi>
          <n-form-item-gi label="缓存">
            <n-space align="center" :size="10" :wrap="false">
              <n-switch :value="form.isCache === 'Y'" @update:value="(v: boolean) => (form.isCache = v ? 'Y' : 'N')">
                <template #checked>是</template>
                <template #unchecked>否</template>
              </n-switch>
              <n-text depth="3" style="font-size: 12px">保留页面状态</n-text>
            </n-space>
          </n-form-item-gi>
        </n-grid>

        <n-form-item label="备注">
          <n-input v-model:value="form.remark" type="textarea" :rows="3" placeholder="可选" />
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
