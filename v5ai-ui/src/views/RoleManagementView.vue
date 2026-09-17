<script setup lang="ts">
import { h, onMounted, reactive, ref } from 'vue'
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
  NPagination,
  NRadioButton,
  NRadioGroup,
  NSelect,
  NSwitch,
  NTree,
  useDialog,
  useMessage,
  type DataTableColumns,
  type TreeOption
} from 'naive-ui'
import { Plus, RefreshCw, Search, ShieldCheck } from 'lucide-vue-next'
import PageHeader from '../components/PageHeader.vue'
import RowActions from '../components/RowActions.vue'
import {
  changeRoleStatus,
  createRole,
  deleteRoles,
  listRoles,
  roleMenuTreeSelect,
  updateRole,
  updateRolePermission,
  type MenuTreeNode,
  type Role,
  type RoleForm
} from '../api/client'
import { adminToken } from '../stores/session'

const message = useMessage()
const dialog = useDialog()
const loading = ref(false)
const rows = ref<Role[]>([])
const pagination = reactive({ page: 1, pageSize: 10, itemCount: 0 })
const search = reactive({ roleName: '', roleKey: '', status: null as string | null })

const DATA_SCOPE_LABELS: Record<string, string> = {
  '1': '全部数据权限',
  '2': '自定义数据权限',
  '3': '本部门数据权限',
  '4': '本部门及以下数据权限',
  '5': '仅本人数据权限'
}

const columns: DataTableColumns<Role> = [
  { type: 'selection' },
  { title: 'ID', key: 'id', width: 64 },
  { title: '角色名称', key: 'roleName', width: 150 },
  { title: '权限字符', key: 'roleKey', width: 130 },
  { title: '显示顺序', key: 'roleSort', width: 90 },
  { title: '数据范围', key: 'dataScope', width: 150, render: (r) => DATA_SCOPE_LABELS[r.dataScope ?? ''] ?? r.dataScope ?? '-' },
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
  { title: '备注', key: 'remark', render: (r) => r.remark ?? '-' },
  {
    title: '操作',
    key: 'actions',
    width: 180,
    render: (r) =>
      h(RowActions, {
        maxInline: 1,
        actions: [
          { key: 'permission', label: '菜单权限', quaternary: true, onClick: () => openPermission(r) },
          { key: 'edit', label: '编辑', quaternary: true, onClick: () => openEdit(r) },
          {
            key: 'delete',
            label: '删除',
            quaternary: true,
            type: 'error',
            confirm: `确认删除角色「${r.roleName}」？`,
            onClick: () => handleDelete([r])
          }
        ]
      })
  }
]

const selectedIds = ref<number[]>([])
const togglingId = ref<number | null>(null)

async function reload() {
  loading.value = true
  try {
    const res = await listRoles(adminToken.value, {
      pageNum: pagination.page,
      pageSize: pagination.pageSize,
      roleName: search.roleName || undefined,
      roleKey: search.roleKey || undefined,
      status: search.status ?? undefined
    })
    rows.value = res.rows
    pagination.itemCount = res.total
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载角色失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pagination.page = 1
  void reload()
}

function handlePageChange(page: number) {
  pagination.page = page
  void reload()
}

function handlePageSizeChange(pageSize: number) {
  pagination.pageSize = pageSize
  pagination.page = 1
  void reload()
}

// ---- 新增/编辑 ----

const showModal = ref(false)
const saving = ref(false)
const isEdit = ref(false)
const form = reactive<RoleForm>({ roleName: '', roleKey: '', roleSort: 1, dataScope: '1', menuCheckStrictly: true, status: '0', remark: '' })

function openCreate() {
  isEdit.value = false
  Object.assign(form, { id: undefined, roleName: '', roleKey: '', roleSort: 1, dataScope: '1', menuCheckStrictly: true, status: '0', remark: '' })
  showModal.value = true
}

function openEdit(row: Role) {
  isEdit.value = true
  Object.assign(form, {
    id: row.id,
    roleName: row.roleName,
    roleKey: row.roleKey,
    roleSort: row.roleSort,
    dataScope: row.dataScope ?? '1',
    menuCheckStrictly: row.menuCheckStrictly ?? true,
    status: row.status ?? '0',
    remark: row.remark ?? ''
  })
  showModal.value = true
}

async function handleSave() {
  if (!form.roleName || !form.roleKey) {
    message.warning('请填写角色名称与权限字符')
    return
  }
  saving.value = true
  try {
    if (isEdit.value) {
      await updateRole(adminToken.value, { ...form })
      message.success('角色已更新')
    } else {
      await createRole(adminToken.value, { ...form })
      message.success('角色已创建')
    }
    showModal.value = false
    await reload()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    saving.value = false
  }
}

// ---- 菜单权限分配 ----

const showPermModal = ref(false)
const permSaving = ref(false)
const permRole = ref<Role | null>(null)
const menuTreeOptions = ref<TreeOption[]>([])
const checkedKeys = ref<Array<string | number>>([])
const permDataScope = ref('1')
const menuTreeRef = ref<{ getIndeterminateData: () => { keys: Array<string | number> } } | null>(null)

/**
 * 菜单树数据：只给真正有子节点的节点挂 children。
 *
 * Naive UI 的 isLeaf 由 treemate 判定——children 字段「存在」即视为非叶子（空数组也算），
 * 于是叶子节点也会画出折叠箭头且点击无效果。所以叶子节点必须不带 children 字段。
 */
function toTreeOptions(nodes: MenuTreeNode[] | undefined): TreeOption[] {
  return (nodes ?? []).map((n) => {
    const children = toTreeOptions(n.children)
    return {
      key: n.id ?? -1,
      label: n.label ?? '',
      ...(children.length > 0 ? { children } : {})
    }
  })
}

async function openPermission(row: Role) {
  permRole.value = row
  permDataScope.value = row.dataScope ?? '1'
  try {
    const res = await roleMenuTreeSelect(adminToken.value, row.id)
    menuTreeOptions.value = toTreeOptions(res.menus)
    checkedKeys.value = res.checkedKeys
    showPermModal.value = true
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载菜单树失败')
  }
}

async function handleSavePermission() {
  if (!permRole.value) return
  permSaving.value = true
  try {
    // 合并全选与半选节点，避免父节点勾选丢失
    const half = menuTreeRef.value?.getIndeterminateData().keys ?? []
    const menuIds = [...new Set([...checkedKeys.value, ...half])].filter((k) => typeof k === 'number') as number[]
    await updateRolePermission(adminToken.value, {
      id: permRole.value.id,
      menuIds,
      dataScope: permDataScope.value,
      menuCheckStrictly: permRole.value.menuCheckStrictly ?? true
    })
    message.success('权限已更新')
    showPermModal.value = false
  } catch (e) {
    message.error(e instanceof Error ? e.message : '保存权限失败')
  } finally {
    permSaving.value = false
  }
}

// ---- 状态 / 删除 ----

async function toggleStatus(row: Role) {
  const next = row.status === '0' ? '1' : '0'
  togglingId.value = row.id
  try {
    await changeRoleStatus(adminToken.value, row.id, next)
    message.success(next === '0' ? '已启用' : '已停用')
    await reload()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '操作失败')
  } finally {
    togglingId.value = null
  }
}

async function handleDelete(targets: Role[]) {
  try {
    await deleteRoles(adminToken.value, targets.map((t) => t.id))
    message.success('已删除')
    await reload()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '删除失败')
  }
}

function handleBatchDelete() {
  dialog.warning({
    title: '批量删除确认',
    content: `确认删除选中的 ${selectedIds.value.length} 个角色？`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await deleteRoles(adminToken.value, selectedIds.value)
        message.success('已删除所选角色')
        selectedIds.value = []
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
    <PageHeader title="角色管理" description="管理角色与菜单权限分配。">
      <template #actions>
        <n-space size="small">
          <n-button size="small" type="primary" @click="openCreate">
            <template #icon><n-icon :component="Plus" /></template>
            新增角色
          </n-button>
        </n-space>
      </template>
    </PageHeader>

    <n-card :bordered="true">
      <n-space style="margin-bottom: 12px" :size="8" align="center">
        <n-input v-model:value="search.roleName" placeholder="角色名称" clearable style="width: 160px" @keyup.enter="handleSearch" />
        <n-input v-model:value="search.roleKey" placeholder="权限字符" clearable style="width: 140px" @keyup.enter="handleSearch" />
        <n-select
          v-model:value="search.status"
          placeholder="状态"
          clearable
          style="width: 110px"
          :options="[{ label: '正常', value: '0' }, { label: '停用', value: '1' }]"
        />
        <n-button size="small" type="primary" secondary @click="handleSearch">
          <template #icon><n-icon :component="Search" /></template>
          查询
        </n-button>
        <n-button size="small" quaternary @click="handleSearch">
          <template #icon><n-icon :component="RefreshCw" /></template>
          刷新
        </n-button>
        <n-button
          v-if="selectedIds.length > 0"
          size="small"
          type="error"
          secondary
          @click="handleBatchDelete"
        >
          删除所选（{{ selectedIds.length }}）
        </n-button>
      </n-space>

      <n-data-table
        :loading="loading"
        :columns="columns"
        :data="rows"
        :row-key="(r: Role) => r.id"
        :checked-row-keys="selectedIds"
        @update:checked-row-keys="(keys: Array<string | number>) => (selectedIds = keys as number[])"
        :bordered="false"
      />
      <n-pagination
        :page="pagination.page"
        :page-size="pagination.pageSize"
        :item-count="pagination.itemCount"
        :page-sizes="[10, 20, 50, 100]"
        show-size-picker
        style="justify-content: flex-end; margin-top: 12px"
        @update:page="handlePageChange"
        @update:page-size="handlePageSizeChange"
      />
    </n-card>

    <n-modal v-model:show="showModal" preset="card" :title="isEdit ? '编辑角色' : '新增角色'" style="width: 520px" :bordered="false">
      <n-form label-placement="top">
        <n-divider title-placement="left" style="margin: 0 0 16px">基础信息</n-divider>
        <n-grid :cols="2" :x-gap="16">
          <n-form-item-gi label="角色名称" required>
            <n-input v-model:value="form.roleName" placeholder="如：运营人员" />
          </n-form-item-gi>
          <n-form-item-gi label="权限字符" required>
            <n-input v-model:value="form.roleKey" placeholder="如：operator（字母数字）" />
          </n-form-item-gi>
        </n-grid>
        <n-form-item label="显示顺序">
          <n-input-number v-model:value="form.roleSort" :min="0" style="width: 100%" />
        </n-form-item>

        <n-divider title-placement="left" style="margin: 8px 0 16px">数据权限</n-divider>
        <n-form-item label="数据范围">
          <n-select
            v-model:value="form.dataScope"
            :options="Object.entries(DATA_SCOPE_LABELS).map(([value, label]) => ({ label, value }))"
          />
        </n-form-item>
        <n-form-item label="菜单树选择项是否关联显示">
          <n-radio-group v-model:value="form.menuCheckStrictly">
            <n-radio-button :value="true">关联显示</n-radio-button>
            <n-radio-button :value="false">不关联</n-radio-button>
          </n-radio-group>
        </n-form-item>
        <n-form-item label="备注">
          <n-input v-model:value="form.remark" type="textarea" placeholder="备注" />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showModal = false">取消</n-button>
          <n-button type="primary" :loading="saving" @click="handleSave">保存</n-button>
        </n-space>
      </template>
    </n-modal>

    <n-modal v-model:show="showPermModal" preset="card" :title="`菜单权限 - ${permRole?.roleName ?? ''}`" style="width: 480px" :bordered="false">
      <n-form label-placement="top">
        <n-form-item label="数据范围">
          <n-select
            v-model:value="permDataScope"
            :options="Object.entries(DATA_SCOPE_LABELS).map(([value, label]) => ({ label, value }))"
          />
        </n-form-item>
        <n-form-item label="菜单权限">
          <div style="max-height: 360px; overflow: auto; width: 100%; border: 1px solid rgba(128,128,128,.2); border-radius: 8px; padding: 8px">
            <n-tree
              ref="menuTreeRef"
              :data="menuTreeOptions"
              :checked-keys="checkedKeys"
              checkable
              selectable
              cascade
              default-expand-all
              @update:checked-keys="(keys: Array<string | number>) => (checkedKeys = keys)"
            />
          </div>
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showPermModal = false">取消</n-button>
          <n-button type="primary" :loading="permSaving" @click="handleSavePermission">保存</n-button>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>
