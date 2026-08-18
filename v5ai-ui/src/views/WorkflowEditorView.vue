<script setup lang="ts">
import { computed, markRaw, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { VueFlow } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import { MiniMap } from '@vue-flow/minimap'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import '@vue-flow/controls/dist/style.css'
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
import { ArrowLeft, Play, Save, Send, Trash2 } from 'lucide-vue-next'
import WorkflowNodeCard from '../components/workflow/WorkflowNodeCard.vue'
import {
  getWorkflow,
  getWorkflowRun,
  listAgents,
  publishWorkflow,
  runWorkflow,
  updateWorkflow,
  type AgentResponse,
  type WorkflowDefinition,
  type WorkflowNode,
  type WorkflowNodeRunResponse,
  type WorkflowNodeType,
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
  END: '结束'
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

const nodeTypes: Record<string, any> = { workflow: markRaw(WorkflowNodeCard) }

const showRunModal = ref(false)
const runInputs = reactive<Record<string, string>>({})
const running = ref(false)
const lastRun = ref<WorkflowRunResponse | null>(null)
const lastNodeRuns = ref<WorkflowNodeRunResponse[]>([])

const selectedNode = computed<any>(() => nodes.value.find((n) => n.id === selectedNodeId.value))
const selectedData = computed<WFNodeData | null>(() => (selectedNode.value?.data as WFNodeData) ?? null)
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

function buildDefinition(): WorkflowDefinition {
  return {
    nodes: nodes.value.map((n) => {
      const d = wfData(n)
      return { id: n.id, type: d.nodeType, name: d.name, config: d.config }
    }),
    edges: edges.value.map((e) => ({
      id: e.id,
      source: e.source,
      target: e.target,
      sourceHandle: e.sourceHandle && e.sourceHandle !== 'out' ? e.sourceHandle : null
    }))
  }
}

function layout(domainNodes: WorkflowNode[], domainEdges: { source: string; target: string }[]): Map<string, { x: number; y: number }> {
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
      const p = pos.get(n.id) ?? { x: 40, y: 60 }
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
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载工作流失败')
  } finally {
    loading.value = false
  }
}

function addNode(type: WorkflowNodeType) {
  const count = nodes.value.filter((n) => wfData(n).nodeType === type).length
  const id = uid('node')
  nodes.value = [
    ...nodes.value,
    {
      id,
      type: 'workflow',
      position: { x: 120 + (count % 3) * 220, y: 80 + count * 48 },
      data: { nodeType: type, name: `${TYPE_LABELS[type]} ${count + 1}`, config: defaultConfig(type) } as WFNodeData
    }
  ]
  selectedNodeId.value = id
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

async function saveDraft() {
  saving.value = true
  try {
    workflow.value = await updateWorkflow(adminToken.value, workflowKey, {
      name: workflow.value?.name ?? undefined,
      description: workflow.value?.description ?? undefined,
      definition: buildDefinition()
    })
    message.success('草稿已保存')
  } catch (e) {
    message.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    saving.value = false
  }
}

async function publish() {
  saving.value = true
  try {
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
    for (const [k, v] of Object.entries(runInputs)) {
      if (k) inputs[k] = v
    }
    const run = await runWorkflow(adminToken.value, workflowKey, inputs)
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
        <n-button size="small" :loading="saving" @click="saveDraft">
          <template #icon><n-icon :component="Save" /></template>
          保存草稿
        </n-button>
        <n-button size="small" type="primary" :loading="saving" @click="publish">
          <template #icon><n-icon :component="Send" /></template>
          发布
        </n-button>
        <n-button size="small" type="info" @click="openRunModal">
          <template #icon><n-icon :component="Play" /></template>
          测试运行
        </n-button>
      </n-space>
    </div>

    <div class="editor-body">
      <aside class="palette">
        <div class="panel-title">节点类型（点击添加）</div>
        <button v-for="type in (['START', 'AGENT', 'CONDITION', 'END'] as WorkflowNodeType[])" :key="type" type="button" class="palette-item" @click="addNode(type)">
          {{ TYPE_LABELS[type] }}
          <span class="palette-type">{{ type }}</span>
        </button>
        <div class="panel-tip">连线：从节点右侧圆点拖到下一节点左侧圆点；条件节点有「真/假」两个出口。</div>
      </aside>

      <div class="canvas">
        <VueFlow
          v-model:nodes="nodes"
          v-model:edges="edges"
          :node-types="nodeTypes"
          :default-viewport="{ zoom: 1, x: 0, y: 0 }"
          fit-view-on-init
          @connect="onConnect"
          @node-click="onNodeClick"
          @pane-click="onPaneClick"
        >
          <Background />
          <Controls />
          <MiniMap />
        </VueFlow>
        <div v-if="!nodes.length" class="canvas-hint">从左侧点击添加节点，开始编排工作流。</div>
      </div>

      <aside class="inspector">
        <template v-if="selectedData">
          <div class="panel-title">属性面板</div>
          <n-form label-placement="top" size="small">
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
          <span class="nr-node">{{ nr.nodeId }}</span>
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
  width: 190px;
  flex-shrink: 0;
  padding: 12px;
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
  justify-content: space-between;
  width: 100%;
  margin-bottom: 8px;
  padding: 10px 12px;
  border: 1px solid var(--n-border-color, rgba(128, 128, 128, 0.16));
  border-radius: 8px;
  background: transparent;
  cursor: pointer;
  font: inherit;
  color: inherit;
}

.palette-item:hover {
  border-color: #7c3aed;
  background: rgba(124, 58, 237, 0.06);
}

.palette-type {
  font-size: 11px;
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
</style>
