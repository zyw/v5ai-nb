<script setup lang="ts">
import { computed, markRaw, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useVueFlow, VueFlow } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { MiniMap } from '@vue-flow/minimap'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import '@vue-flow/minimap/dist/style.css'
import {
  NButton,
  NForm,
  NFormItem,
  NIcon,
  NInput,
  NModal,
  NSelect,
  NSpace,
  NTag,
  useMessage
} from 'naive-ui'
import {
  ArrowLeft, Bot, CirclePlay, CircleStop, Code2, Eye, Focus, GitBranch, Globe,
  LayoutGrid, Map as MapIcon, Play, Save, Send, ShieldCheck, Trash2,
  Variable as VariableIcon, ZoomIn, ZoomOut
} from 'lucide-vue-next'
import WorkflowNodeCard from '../components/workflow/WorkflowNodeCard.vue'
import {
  getWorkflow,
  getWorkflowRun,
  listAgents,
  publishWorkflow,
  runWorkflow,
  validateWorkflow,
  listWorkflowVersions,
  restoreWorkflowVersion,
  updateWorkflow,
  type AgentResponse,
  type WorkflowDefinition,
  type WorkflowNode,
  type WorkflowNodeRunResponse,
  type WorkflowNodeType,
  type WorkflowValidationResult,
  type WorkflowResponse,
  type WorkflowRunResponse
} from '../api/client'
import { adminToken } from '../stores/session'

const route = useRoute()
const router = useRouter()
const message = useMessage()
const workflowKey = String(route.params.key ?? '')

interface WFNodeData {
  nodeType: WorkflowNodeType
  name: string
  config: Record<string, any>
}

const TYPE_LABELS: Record<WorkflowNodeType, string> = {
  START: '开始',
  AGENT: 'Agent',
  CONDITION: '条件',
  HTTP: 'HTTP 请求',
  PYTHON: 'Python 脚本',
  VARIABLE: '变量赋值',
  END: '结束'
}

const NODE_GROUPS: Array<{ title: string; types: WorkflowNodeType[] }> = [
  { title: '基础', types: ['START', 'END'] },
  { title: 'AI', types: ['AGENT'] },
  { title: '执行器', types: ['HTTP', 'PYTHON', 'VARIABLE'] },
  { title: '控制', types: ['CONDITION'] }
]

const NODE_ICONS: Record<WorkflowNodeType, any> = {
  START: CirclePlay,
  END: CircleStop,
  AGENT: Bot,
  CONDITION: GitBranch,
  HTTP: Globe,
  PYTHON: Code2,
  VARIABLE: VariableIcon
}

const OPERATOR_OPTIONS = [
  { label: '等于 ==', value: '==' },
  { label: '不等于 !=', value: '!=' },
  { label: '包含 contains', value: 'contains' },
  { label: '不包含 not_contains', value: 'not_contains' },
  { label: '为空 is_empty', value: 'is_empty' },
  { label: '非空 is_not_empty', value: 'is_not_empty' },
  { label: '大于 >', value: '>' },
  { label: '大于等于 >=', value: '>=' },
  { label: '小于 <', value: '<' },
  { label: '小于等于 <=', value: '<=' }
]

function defaultConfig(type: WorkflowNodeType): Record<string, any> {
  switch (type) {
    case 'START':
      return { variables: [] }
    case 'AGENT':
      return { agentKey: '', prompt: '', outputVar: '' }
    case 'CONDITION':
      return { left: '', operator: '==', right: '' }
    case 'END':
      return { outputs: [] }
    case 'HTTP':
      return { method: 'GET', url: '', headers: {}, body: '', outputVar: '' }
    case 'PYTHON':
      return { code: 'result = inputs', outputVar: 'result' }
    case 'VARIABLE':
      return { name: '', value: '' }
  }
}

let seq = 0
function uid(prefix: string): string {
  seq += 1
  return `${prefix}_${Date.now().toString(36)}_${seq}`
}

const loading = ref(false)
const saving = ref(false)
const workflow = ref<WorkflowResponse | null>(null)
const agents = ref<AgentResponse[]>([])

const nodes = ref<any[]>([])
const edges = ref<any[]>([])
const selectedNodeId = ref<string | null>(null)
const nodeIdDraft = ref('')
const nodeIdError = ref('')

const nodeTypes: Record<string, any> = { workflow: markRaw(WorkflowNodeCard) }
const { zoomIn, zoomOut, fitView, screenToFlowCoordinate, viewport } = useVueFlow()
const showMiniMap = ref(true)
const initialViewportFitted = ref(false)

const showRunModal = ref(false)
const runInputs = reactive<Record<string, string>>({})
const running = ref(false)
const lastRun = ref<WorkflowRunResponse | null>(null)
const lastNodeRuns = ref<WorkflowNodeRunResponse[]>([])
const validation = ref<WorkflowValidationResult | null>(null)
const versions = ref<Array<{ label: string; value: number }>>([])
const selectedVersion = ref<number | null>(null)

const selectedNode = computed<any>(() => nodes.value.find((n) => n.id === selectedNodeId.value))
const selectedData = computed<WFNodeData | null>(() => (selectedNode.value?.data as WFNodeData) ?? null)
watch(selectedNodeId, (id) => {
  nodeIdDraft.value = id ?? ''
  nodeIdError.value = ''
}, { immediate: true })
const selectedVariables = computed(
  () => (Array.isArray(selectedData.value?.config?.variables) ? selectedData.value.config.variables : []) as Array<{ name: string; type: string; default: string }>
)
const selectedOutputs = computed(
  () => (Array.isArray(selectedData.value?.config?.outputs) ? selectedData.value.config.outputs : []) as Array<{ name: string; value: string }>
)

const agentOptions = computed(() =>
  agents.value.map((a) => ({ label: `${a.name} (${a.agentKey})`, value: a.agentKey }))
)

function wfData(node: any): WFNodeData {
  return node.data as WFNodeData
}

function runNodeLabel(nodeId: string): string {
  const node = nodes.value.find((item) => item.id === nodeId)
  const name = node ? wfData(node).name?.trim() : ''
  return name ? `${name} (${nodeId})` : nodeId
}

function buildDefinition(): WorkflowDefinition {
  const positions = new Map(nodes.value.map((node) => [node.id, node.position]))
  return {
    schemaVersion: 2,
    nodes: nodes.value.map((n) => {
      const d = wfData(n)
      return { id: n.id, type: d.nodeType, name: d.name, config: d.config, position: positions.get(n.id) }
    }),
    edges: edges.value.map((e) => ({
      id: e.id,
      source: e.source,
      target: e.target,
      sourceHandle: e.sourceHandle && e.sourceHandle !== 'out' ? e.sourceHandle : null
    }))
  }
}

function layout(domainNodes: Array<{ id: string }>, domainEdges: { source: string; target: string }[]): Map<string, { x: number; y: number }> {
  const incoming = new Map<string, number>()
  const adj = new Map<string, string[]>()
  for (const e of domainEdges) {
    incoming.set(e.target, (incoming.get(e.target) ?? 0) + 1)
    const list = adj.get(e.source) ?? []
    list.push(e.target)
    adj.set(e.source, list)
  }
  const depth = new Map<string, number>()
  const queue: string[] = []
  for (const n of domainNodes) {
    if ((incoming.get(n.id) ?? 0) === 0) {
      depth.set(n.id, 0)
      queue.push(n.id)
    }
  }
  if (queue.length === 0 && domainNodes.length) {
    depth.set(domainNodes[0].id, 0)
    queue.push(domainNodes[0].id)
  }
  while (queue.length) {
    const cur = queue.shift() as string
    for (const next of adj.get(cur) ?? []) {
      const nd = Math.max(depth.get(next) ?? 0, (depth.get(cur) ?? 0) + 1)
      depth.set(next, nd)
      queue.push(next)
    }
  }
  const byDepth = new Map<number, string[]>()
  for (const n of domainNodes) {
    const d = depth.get(n.id) ?? 0
    const list = byDepth.get(d) ?? []
    list.push(n.id)
    byDepth.set(d, list)
  }
  const pos = new Map<string, { x: number; y: number }>()
  for (const [d, ids] of byDepth) {
    ids.forEach((id, i) => pos.set(id, { x: d * 280 + 40, y: i * 150 + 60 }))
  }
  return pos
}

async function load() {
  loading.value = true
  try {
    workflow.value = await getWorkflow(adminToken.value, workflowKey)
    const def = workflow.value.draftDefinition ?? workflow.value.publishedDefinition
    const domainNodes = def?.nodes ?? []
    const domainEdges = def?.edges ?? []
    const pos = layout(domainNodes, domainEdges)
    nodes.value = domainNodes.map((n) => {
      const p = n.position ?? pos.get(n.id) ?? { x: 40, y: 60 }
      return {
        id: n.id,
        type: 'workflow',
        position: p,
        data: { nodeType: n.type, name: n.name, config: n.config ?? {} } as WFNodeData
      }
    })
    edges.value = domainEdges.map((e) => ({
      id: e.id,
      source: e.source,
      target: e.target,
      sourceHandle: e.sourceHandle ?? 'out',
      targetHandle: 'in'
    }))
    agents.value = (await listAgents(adminToken.value)).rows
    versions.value = (await listWorkflowVersions(adminToken.value, workflowKey)).map((v) => ({ label: `v${v.version}`, value: v.version }))
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载工作流失败')
  } finally {
    loading.value = false
  }
}

function addNode(type: WorkflowNodeType, position?: { x: number; y: number }) {
  const count = nodes.value.filter((n) => wfData(n).nodeType === type).length
  const id = uid('node')
  nodes.value = [
    ...nodes.value,
    {
      id,
      type: 'workflow',
      position: position ?? { x: 120 + (count % 3) * 220, y: 80 + count * 48 },
      data: { nodeType: type, name: `${TYPE_LABELS[type]} ${count + 1}`, config: defaultConfig(type) } as WFNodeData
    }
  ]
  selectedNodeId.value = id
}

const NODE_DRAG_MIME = 'application/x-v5ai-workflow-node'

function onNodeDragStart(event: DragEvent, type: WorkflowNodeType) {
  if (!event.dataTransfer) return
  event.dataTransfer.setData(NODE_DRAG_MIME, type)
  event.dataTransfer.effectAllowed = 'copy'
}

function onCanvasDragOver(event: DragEvent) {
  if (event.dataTransfer?.types.includes(NODE_DRAG_MIME)) {
    event.preventDefault()
    event.dataTransfer.dropEffect = 'copy'
  }
}

function onCanvasDrop(event: DragEvent) {
  const target = event.target
  if (!(target instanceof HTMLElement) || !target.closest('.vue-flow')) return

  const type = event.dataTransfer?.getData(NODE_DRAG_MIME) as WorkflowNodeType | undefined
  if (!type || !Object.hasOwn(TYPE_LABELS, type)) return

  event.preventDefault()
  const position = screenToFlowCoordinate({ x: event.clientX, y: event.clientY })
  addNode(type, position)
}

async function fitInitialViewport() {
  if (initialViewportFitted.value || !nodes.value.length) return
  await fitView({ padding: 0.18, maxZoom: 0.9 })
  initialViewportFitted.value = true
}

function onConnect(connection: any) {
  edges.value = [
    ...edges.value,
    {
      id: uid('edge'),
      source: connection.source,
      target: connection.target,
      sourceHandle: connection.sourceHandle,
      targetHandle: connection.targetHandle
    }
  ]
}

function onNodeClick(event: any) {
  selectedNodeId.value = event.node.id
}

function onPaneClick() {
  selectedNodeId.value = null
}

function applyNodeId() {
  const currentId = selectedNodeId.value
  const newId = nodeIdDraft.value.trim()
  if (!currentId || !selectedNode.value || newId === currentId) {
    nodeIdError.value = ''
    return
  }
  if (!/^[A-Za-z][A-Za-z0-9_-]{0,63}$/.test(newId)) {
    nodeIdError.value = 'ID 须以字母开头，且只能包含字母、数字、_、-，长度不超过 64。'
    return
  }
  if (nodes.value.some((node) => node.id === newId)) {
    nodeIdError.value = '该节点 ID 已存在。'
    return
  }

  nodes.value = nodes.value.map((node) => node.id === currentId ? { ...node, id: newId } : node)
  edges.value = edges.value.map((edge) => ({
    ...edge,
    source: edge.source === currentId ? newId : edge.source,
    target: edge.target === currentId ? newId : edge.target
  }))
  selectedNodeId.value = newId
  nodeIdDraft.value = newId
  nodeIdError.value = ''
}

function deleteSelected() {
  if (!selectedNodeId.value) return
  const id = selectedNodeId.value
  nodes.value = nodes.value.filter((n) => n.id !== id)
  edges.value = edges.value.filter((e) => e.source !== id && e.target !== id)
  selectedNodeId.value = null
}

function addVariable() {
  const config = selectedData.value?.config
  if (!config) return
  if (!Array.isArray(config.variables)) config.variables = []
  config.variables.push({ name: '', type: 'string', default: '' })
}

function removeVariable(index: number) {
  const config = selectedData.value?.config
  if (config && Array.isArray(config.variables)) config.variables.splice(index, 1)
}

function addOutput() {
  const config = selectedData.value?.config
  if (!config) return
  if (!Array.isArray(config.outputs)) config.outputs = []
  config.outputs.push({ name: '', value: '' })
}

function removeOutput(index: number) {
  const config = selectedData.value?.config
  if (config && Array.isArray(config.outputs)) config.outputs.splice(index, 1)
}

function setHttpHeaders(value: string) {
  if (!selectedData.value) return
  try {
    selectedData.value.config.headers = value.trim() ? JSON.parse(value) : {}
  } catch {
    // Keep the last valid value; publishing will be blocked by server-side validation.
  }
}

async function saveDraft() {
  saving.value = true
  try {
    workflow.value = await updateWorkflow(adminToken.value, workflowKey, {
      name: workflow.value?.name ?? undefined,
      description: workflow.value?.description ?? undefined,
      definition: buildDefinition(),
      expectedRevision: workflow.value?.draftRevision ?? 0
    })
    message.success('草稿已保存')
  } catch (e) {
    message.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    saving.value = false
  }
}

async function validateDraft() {
  saving.value = true
  try {
    workflow.value = await updateWorkflow(adminToken.value, workflowKey, {
      definition: buildDefinition(),
      expectedRevision: workflow.value?.draftRevision ?? 0
    })
    validation.value = await validateWorkflow(adminToken.value, workflowKey)
    if (validation.value.valid) message.success('校验通过')
    else message.error(`校验未通过：${validation.value.diagnostics.map((d) => d.message).join('；')}`)
  } catch (e) {
    message.error(e instanceof Error ? e.message : '校验失败')
  } finally {
    saving.value = false
  }
}

async function autoLayout() {
  const positions = layout(
    nodes.value.map((node) => ({ id: node.id })),
    edges.value.map((edge) => ({ source: edge.source, target: edge.target }))
  )
  nodes.value = nodes.value.map((node) => ({ ...node, position: positions.get(node.id) ?? node.position }))
  await nextTick()
  await fitView({ padding: 0.18, duration: 250 })
}

async function publish() {
  saving.value = true
  try {
    workflow.value = await updateWorkflow(adminToken.value, workflowKey, {
      definition: buildDefinition(),
      expectedRevision: workflow.value?.draftRevision ?? 0
    })
    const result = await validateWorkflow(adminToken.value, workflowKey)
    validation.value = result
    if (!result.valid) {
      message.error(`校验未通过：${result.diagnostics.map((d) => d.message).join('；')}`)
      return
    }
    workflow.value = await publishWorkflow(adminToken.value, workflowKey)
    message.success(`已发布 v${workflow.value.publishedVersion}`)
  } catch (e) {
    message.error(e instanceof Error ? e.message : '发布失败')
  } finally {
    saving.value = false
  }
}

function openRunModal() {
  Object.keys(runInputs).forEach((k) => delete runInputs[k])
  const start = nodes.value.find((n) => wfData(n).nodeType === 'START')
  const vars = start ? wfData(start).config?.variables : undefined
  if (Array.isArray(vars)) {
    for (const v of vars) {
      if (v && v.name) runInputs[v.name] = v.default ?? ''
    }
  }
  showRunModal.value = true
}

async function executeRun() {
  running.value = true
  lastRun.value = null
  lastNodeRuns.value = []
  try {
    const inputs: Record<string, unknown> = {}
    const start = nodes.value.find((n) => wfData(n).nodeType === 'START')
    const declarations = Array.isArray(start?.data?.config?.variables) ? start.data.config.variables : []
    for (const [k, v] of Object.entries(runInputs)) {
      if (!k) continue
      const type = declarations.find((item: any) => item.name === k)?.type
      if (type === 'number' && v.trim() !== '' && Number.isFinite(Number(v))) inputs[k] = Number(v)
      else if (type === 'boolean') inputs[k] = v === 'true'
      else inputs[k] = v
    }
    const run = await runWorkflow(adminToken.value, workflowKey, inputs, true)
    lastRun.value = run
    try {
      const detail = await getWorkflowRun(adminToken.value, run.runId)
      lastNodeRuns.value = detail.nodeRuns
    } catch {
      /* 节点级详情非必需 */
    }
    message.success(run.status === 'SUCCEEDED' ? '运行成功' : '运行结束（失败）')
  } catch (e) {
    message.error(e instanceof Error ? e.message : '运行失败')
  } finally {
    running.value = false
  }
}

async function restoreVersion() {
  if (selectedVersion.value == null) return
  try {
    workflow.value = await restoreWorkflowVersion(adminToken.value, workflowKey, selectedVersion.value, workflow.value?.draftRevision ?? undefined)
    message.success(`已将 v${selectedVersion.value} 恢复为草稿`)
    await load()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '恢复版本失败')
  }
}

function fmt(obj?: Record<string, unknown> | null): string {
  return obj ? JSON.stringify(obj, null, 2) : '—'
}

onMounted(load)
</script>

<template>
  <div class="editor">
    <div class="editor-topbar">
      <div class="editor-title">
        <n-button quaternary circle size="small" @click="router.push({ name: 'workflows' })">
          <template #icon><n-icon :component="ArrowLeft" /></template>
        </n-button>
        <span class="editor-name">{{ workflow?.name ?? '工作流' }}</span>
        <n-tag v-if="workflow?.status === 'PUBLISHED'" size="small" type="success" :bordered="false">已发布 v{{ workflow?.publishedVersion }}</n-tag>
        <n-tag v-else-if="workflow?.status === 'DRAFT'" size="small" type="info" :bordered="false">草稿</n-tag>
      </div>
      <n-space :size="8">
        <n-select v-model:value="selectedVersion" :options="versions" placeholder="版本历史" size="small" clearable style="width: 110px" />
        <n-button size="small" :disabled="selectedVersion == null" @click="restoreVersion">恢复版本</n-button>
        <n-button size="small" type="primary" :loading="saving" @click="publish">
          <template #icon><n-icon :component="Send" /></template>
          发布
        </n-button>
      </n-space>
    </div>

    <div class="editor-body">
      <aside class="palette">
        <div class="panel-title">节点类型</div>
        <section v-for="group in NODE_GROUPS" :key="group.title" class="palette-group" :aria-label="group.title">
          <div class="palette-group-title">{{ group.title }}</div>
          <button
            v-for="type in group.types"
            :key="type"
            type="button"
            class="palette-item"
            draggable="true"
            @click="addNode(type)"
            @dragstart="onNodeDragStart($event, type)"
          >
            <n-icon :component="NODE_ICONS[type]" class="palette-icon" aria-hidden="true" />
            <span class="palette-item-copy">
              <span>{{ TYPE_LABELS[type] }}</span>
              <span class="palette-type">{{ type }}</span>
            </span>
          </button>
        </section>
        <div class="panel-tip">拖拽或点击节点可添加；连线：从右侧圆点拖到下一节点左侧圆点。</div>
      </aside>

      <div class="canvas" @dragover="onCanvasDragOver" @drop="onCanvasDrop">
        <VueFlow
          v-model:nodes="nodes"
          v-model:edges="edges"
          :node-types="nodeTypes"
          :default-viewport="{ zoom: 0.9, x: 0, y: 0 }"
          @connect="onConnect"
          @node-click="onNodeClick"
          @pane-click="onPaneClick"
          @nodes-initialized="fitInitialViewport"
        >
          <Background />
          <MiniMap v-if="showMiniMap" />
        </VueFlow>
        <div v-if="!nodes.length" class="canvas-hint">从左侧点击添加节点，开始编排工作流。</div>
        <div class="canvas-toolbar" role="toolbar" aria-label="画布工具栏" @click.stop>
          <div class="toolbar-cluster" aria-label="缩放与视图">
            <n-button quaternary circle size="small" title="缩小" aria-label="缩小" @click="zoomOut()">
              <template #icon><n-icon :component="ZoomOut" /></template>
            </n-button>
            <span class="zoom-level" aria-live="polite">{{ Math.round(viewport.zoom * 100) }}%</span>
            <n-button quaternary circle size="small" title="放大" aria-label="放大" @click="zoomIn()">
              <template #icon><n-icon :component="ZoomIn" /></template>
            </n-button>
            <n-button quaternary circle size="small" title="适应画布" aria-label="适应画布" @click="fitView({ padding: 0.18, duration: 200 })">
              <template #icon><n-icon :component="Focus" /></template>
            </n-button>
            <n-button quaternary circle size="small" title="自动布局" aria-label="自动布局" @click="autoLayout">
              <template #icon><n-icon :component="LayoutGrid" /></template>
            </n-button>
            <n-button quaternary circle size="small" :title="showMiniMap ? '隐藏小地图' : '显示小地图'" :aria-label="showMiniMap ? '隐藏小地图' : '显示小地图'" :aria-pressed="showMiniMap" @click="showMiniMap = !showMiniMap">
              <template #icon><n-icon :component="MapIcon" /></template>
            </n-button>
          </div>
          <span class="toolbar-divider" aria-hidden="true" />
          <div class="toolbar-cluster toolbar-actions">
            <n-button size="small" :loading="saving" title="校验工作流" @click="validateDraft">
              <template #icon><n-icon :component="ShieldCheck" /></template>
              校验
            </n-button>
            <n-button size="small" :loading="saving" title="保存草稿" @click="saveDraft">
              <template #icon><n-icon :component="Save" /></template>
              保存
            </n-button>
            <n-button size="small" title="预览并填写试运行参数" @click="openRunModal">
              <template #icon><n-icon :component="Eye" /></template>
              预览
            </n-button>
            <n-button size="small" type="primary" :loading="running" title="填写参数并运行草稿" @click="openRunModal">
              <template #icon><n-icon :component="Play" /></template>
              运行
            </n-button>
          </div>
        </div>
      </div>

      <aside class="inspector">
        <template v-if="selectedData">
          <div class="panel-title">属性面板</div>
          <n-form label-placement="top" size="small">
            <n-form-item label="节点 ID">
              <n-input
                v-model:value="nodeIdDraft"
                placeholder="字母开头，支持字母、数字、_、-"
                maxlength="64"
                @blur="applyNodeId"
                @keyup.enter="applyNodeId"
              />
              <div v-if="nodeIdError" class="run-error">{{ nodeIdError }}</div>
            </n-form-item>
            <n-form-item label="节点名称">
              <n-input v-model:value="selectedData.name" />
            </n-form-item>

            <template v-if="selectedData.nodeType === 'START'">
              <div class="panel-section">输入变量</div>
              <div v-for="(v, i) in selectedVariables" :key="i" class="var-block">
                <n-input v-model:value="v.name" placeholder="变量名" />
                <div class="row-item">
                  <n-select v-model:value="v.type" :options="[{ label: 'string', value: 'string' }, { label: 'number', value: 'number' }, { label: 'boolean', value: 'boolean' }]" style="width: 96px; flex-shrink: 0" />
                  <n-input v-model:value="v.default" placeholder="默认值" style="flex: 1; min-width: 0" />
                  <n-button quaternary circle size="tiny" type="error" @click="removeVariable(i)">
                    <template #icon><n-icon :component="Trash2" /></template>
                  </n-button>
                </div>
              </div>
              <n-button size="tiny" dashed block @click="addVariable">+ 添加变量</n-button>
            </template>

            <template v-else-if="selectedData.nodeType === 'AGENT'">
              <n-form-item label="Agent">
                <n-select v-model:value="selectedData.config.agentKey" :options="agentOptions" filterable placeholder="选择已发布 Agent" />
              </n-form-item>
              <n-form-item label="Prompt 模板（支持 {{变量}}）">
                <n-input v-model:value="selectedData.config.prompt" type="textarea" :autosize="{ minRows: 3, maxRows: 8 }" placeholder="请分析 {{topic}} 并给出结论" />
              </n-form-item>
              <n-form-item label="输出变量名（缺省为节点 id）">
                <n-input v-model:value="selectedData.config.outputVar" placeholder="summary" />
              </n-form-item>
            </template>

            <template v-else-if="selectedData.nodeType === 'CONDITION'">
              <n-form-item label="左值（支持 {{变量}}）">
                <n-input v-model:value="selectedData.config.left" placeholder="{{result}} 或字面量" />
              </n-form-item>
              <n-form-item label="运算符">
                <n-select v-model:value="selectedData.config.operator" :options="OPERATOR_OPTIONS" />
              </n-form-item>
              <n-form-item label="右值（支持 {{变量}}）">
                <n-input v-model:value="selectedData.config.right" placeholder="{{threshold}} 或字面量" />
              </n-form-item>
            </template>

            <template v-else-if="selectedData.nodeType === 'HTTP'">
              <n-form-item label="HTTP 方法"><n-select v-model:value="selectedData.config.method" :options="['GET','POST','PUT','PATCH','DELETE'].map(v => ({ label: v, value: v }))" /></n-form-item>
              <n-form-item label="URL"><n-input v-model:value="selectedData.config.url" placeholder="https://api.example.com/resource" /></n-form-item>
              <n-form-item label="请求头 JSON"><n-input :value="JSON.stringify(selectedData.config.headers ?? {}, null, 2)" type="textarea" placeholder="{}" @update:value="setHttpHeaders" /></n-form-item>
              <n-form-item label="请求体模板"><n-input v-model:value="selectedData.config.body" type="textarea" /></n-form-item>
              <n-form-item label="输出变量"><n-input v-model:value="selectedData.config.outputVar" /></n-form-item>
            </template>

            <template v-else-if="selectedData.nodeType === 'PYTHON'">
              <n-form-item label="Python 代码（需配置隔离 Runner）"><n-input v-model:value="selectedData.config.code" type="textarea" :autosize="{ minRows: 8, maxRows: 16 }" /></n-form-item>
              <n-form-item label="输出变量"><n-input v-model:value="selectedData.config.outputVar" /></n-form-item>
            </template>

            <template v-else-if="selectedData.nodeType === 'VARIABLE'">
              <n-form-item label="变量名"><n-input v-model:value="selectedData.config.name" /></n-form-item>
              <n-form-item label="值模板"><n-input v-model:value="selectedData.config.value" /></n-form-item>
            </template>

            <template v-else-if="selectedData.nodeType === 'END'">
              <div class="panel-section">输出</div>
              <div v-for="(o, i) in selectedOutputs" :key="i" class="var-block">
                <n-input v-model:value="o.name" placeholder="输出名" />
                <div class="row-item">
                  <n-input v-model:value="o.value" placeholder="{{变量}} 或字面量" style="flex: 1; min-width: 0" />
                  <n-button quaternary circle size="tiny" type="error" @click="removeOutput(i)">
                    <template #icon><n-icon :component="Trash2" /></template>
                  </n-button>
                </div>
              </div>
              <n-button size="tiny" dashed block @click="addOutput">+ 添加输出</n-button>
            </template>
          </n-form>
          <div v-if="validation && !validation.valid" class="run-error">{{ validation.diagnostics.map(d => d.message).join('；') }}</div>

          <n-button size="small" type="error" secondary block style="margin-top: 12px" @click="deleteSelected">
            <template #icon><n-icon :component="Trash2" /></template>
            删除节点
          </n-button>
        </template>
        <div v-else class="inspector-empty">点击画布中的节点以编辑其属性。</div>
      </aside>
    </div>

    <n-modal v-model:show="showRunModal" preset="card" title="测试运行" style="width: 680px" :bordered="false">
      <div v-if="!lastRun">
        <n-form label-placement="top" size="small">
          <n-form-item v-for="(value, key) in runInputs" :key="key" :label="key">
            <n-input v-model:value="runInputs[key]" />
          </n-form-item>
        </n-form>
        <div v-if="!Object.keys(runInputs).length" class="muted">该工作流没有输入变量。</div>
      </div>
      <div v-else>
        <div class="run-status">
          <span>状态：</span>
          <n-tag size="small" :type="lastRun.status === 'SUCCEEDED' ? 'success' : 'error'" :bordered="false">{{ lastRun.status }}</n-tag>
        </div>
        <div v-if="lastRun.error" class="run-error">错误：{{ lastRun.error }}</div>
        <div class="panel-section">输出</div>
        <pre class="run-json">{{ fmt(lastRun.outputs) }}</pre>
        <div v-if="lastNodeRuns.length" class="panel-section">节点执行情况</div>
        <div v-for="nr in lastNodeRuns" :key="nr.id" class="node-run-row">
          <span class="nr-node">{{ runNodeLabel(nr.nodeId) }}</span>
          <n-tag size="tiny" :bordered="false" :type="nr.status === 'SUCCEEDED' ? 'success' : nr.status === 'SKIPPED' ? 'default' : nr.status === 'FAILED' ? 'error' : 'info'">{{ nr.status }}</n-tag>
        </div>
      </div>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showRunModal = false">关闭</n-button>
          <n-button v-if="!lastRun" type="primary" :loading="running" @click="executeRun">运行</n-button>
          <n-button v-else type="primary" @click="lastRun = null">再次运行</n-button>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>

<style scoped>
.editor {
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
  margin-bottom: 12px;
}

.editor-title {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.editor-name {
  font-size: 16px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.editor-body {
  display: flex;
  flex: 1;
  gap: 12px;
  min-height: 0;
}

.palette {
  width: 164px;
  flex-shrink: 0;
  padding: 10px;
  border: 1px solid var(--n-border-color, rgba(128, 128, 128, 0.16));
  border-radius: 10px;
  overflow-y: auto;
}

.inspector {
  width: 300px;
  flex-shrink: 0;
  padding: 12px;
  border: 1px solid var(--n-border-color, rgba(128, 128, 128, 0.16));
  border-radius: 10px;
  overflow-y: auto;
}

.canvas {
  position: relative;
  flex: 1;
  min-width: 0;
  border: 1px solid var(--n-border-color, rgba(128, 128, 128, 0.16));
  border-radius: 10px;
  overflow: hidden;
}

.canvas-toolbar {
  position: absolute;
  z-index: 6;
  left: 50%;
  bottom: 16px;
  display: flex;
  align-items: center;
  gap: 10px;
  max-width: calc(100% - 24px);
  padding: 7px 9px;
  border: 1px solid var(--n-border-color, rgba(128, 128, 128, 0.2));
  border-radius: 12px;
  background: var(--n-color, #fff);
  box-shadow: 0 6px 24px rgba(15, 23, 42, 0.14);
  transform: translateX(-50%);
}

.toolbar-cluster {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}

.toolbar-actions {
  gap: 6px;
}

.zoom-level {
  min-width: 42px;
  text-align: center;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  opacity: 0.75;
}

.toolbar-divider {
  width: 1px;
  height: 24px;
  flex-shrink: 0;
  background: var(--n-border-color, rgba(128, 128, 128, 0.2));
}

.canvas-hint {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  padding: 8px 16px;
  border-radius: 8px;
  background: rgba(128, 128, 128, 0.08);
  font-size: 13px;
  opacity: 0.7;
  pointer-events: none;
}

.panel-title {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 10px;
}

.palette-group {
  margin-top: 11px;
}

.palette-group-title {
  margin: 0 0 7px 2px;
  color: var(--n-text-color-3, #7b7b86);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.04em;
}

.panel-section {
  font-size: 12px;
  font-weight: 600;
  margin: 14px 0 8px;
  opacity: 0.8;
}

.panel-tip {
  margin-top: 14px;
  font-size: 12px;
  opacity: 0.55;
  line-height: 1.5;
}

.palette-item {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 7px;
  width: 100%;
  min-height: 40px;
  margin-bottom: 5px;
  padding: 5px 7px;
  border: 1px solid var(--n-border-color, rgba(128, 128, 128, 0.16));
  border-radius: 8px;
  background: transparent;
  cursor: pointer;
  font: inherit;
  color: inherit;
}

.palette-icon {
  width: 18px;
  height: 18px;
  flex-shrink: 0;
  padding: 5px;
  border-radius: 7px;
  color: var(--n-primary-color, #6d5ce8);
  background: var(--n-primary-color-suppl, rgba(109, 92, 232, 0.1));
  box-sizing: content-box;
}

.palette-item-copy {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  min-width: 0;
  text-align: left;
  font-size: 13px;
  line-height: 1.35;
}

.palette-item:hover {
  border-color: #7c3aed;
  background: rgba(124, 58, 237, 0.06);
}

.palette-type {
  font-size: 10px;
  opacity: 0.5;
  letter-spacing: 0.5px;
}

.row-item {
  display: flex;
  align-items: center;
  gap: 6px;
}

.var-block {
  margin-bottom: 12px;
  padding: 8px;
  border: 1px solid rgba(128, 128, 128, 0.14);
  border-radius: 8px;
}

.var-block > :first-child {
  margin-bottom: 6px;
}

.inspector-empty {
  padding: 24px 8px;
  text-align: center;
  font-size: 13px;
  opacity: 0.5;
}

.run-status {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.run-error {
  color: var(--n-error-color, #d03050);
  font-size: 13px;
  margin-bottom: 8px;
  white-space: pre-wrap;
}

.run-json {
  margin: 0;
  padding: 10px;
  border-radius: 8px;
  background: rgba(128, 128, 128, 0.08);
  font-size: 12px;
  overflow: auto;
  max-height: 220px;
}

.node-run-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 0;
  border-bottom: 1px solid rgba(128, 128, 128, 0.08);
}

.nr-node {
  font-size: 13px;
  font-family: monospace;
}

.muted {
  font-size: 13px;
  opacity: 0.55;
}

@media (max-width: 900px) {
  .canvas-toolbar {
    gap: 6px;
    padding: 6px;
  }

  .toolbar-actions {
    gap: 4px;
  }
}

@media (max-width: 700px) {
  .canvas-toolbar {
    left: 8px;
    right: 8px;
    bottom: 8px;
    justify-content: center;
    max-width: none;
    flex-wrap: wrap;
    transform: none;
  }
}
</style>
