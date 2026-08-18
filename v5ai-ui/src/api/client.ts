export interface LoginResponse {
  tokenType: string
  token: string
}

export interface ProviderResponse {
  id: number
  providerKey: string
  name: string
  providerType: string
  enabled: boolean
}

export interface CreateProviderRequest {
  providerKey: string
  name: string
  providerType: string
  enabled: boolean
}

export interface UpdateProviderRequest {
  name: string
  providerType: string
  enabled: boolean
}

export interface ModelResponse {
  id: number
  providerKey: string
  modelKey: string
  type: string
  credentialsCiphertext: string
  enabled: boolean
}

export interface CreateModelRequest {
  providerKey: string
  modelKey: string
  type: string
  credentials: string
}

export interface UpdateModelRequest {
  modelKey: string
  type: string
  credentials?: string
  enabled: boolean
}

export interface TestConnectionResponse {
  modelId: number
  ok: boolean
  message: string
}

export interface AgentResponse {
  agentKey: string
  name: string
  description?: string | null
  status: string
  modelId: number
  publishedVersion?: number | null
  systemPrompt?: string | null
  avatar?: string | null
  greeting?: string | null
  presetQuestions?: string | null
  memoryEnabled?: boolean
  mcpEnabled?: boolean
  skillEnabled?: boolean
  webSearchEnabled?: boolean
  ragEnabled?: boolean
}

export interface CreateAgentRequest {
  agentKey: string
  name: string
  description?: string
  modelId: number
  systemPrompt?: string
  avatar?: string
  greeting?: string
  presetQuestions?: string
  memoryEnabled?: boolean
  mcpEnabled?: boolean
  skillEnabled?: boolean
  webSearchEnabled?: boolean
  ragEnabled?: boolean
}

export interface UpdateAgentRequest {
  name?: string
  description?: string
  modelId?: number
  systemPrompt?: string
  avatar?: string
  greeting?: string
  presetQuestions?: string
  memoryEnabled?: boolean
  mcpEnabled?: boolean
  skillEnabled?: boolean
  webSearchEnabled?: boolean
  ragEnabled?: boolean
}

export interface AgentVersionResponse {
  agentKey: string
  version: number
  snapshotJson: string
  description?: string | null
}

export interface GenerateAgentConfigRequest {
  modelId: number
  description: string
}

export interface GenerateAgentConfigResponse {
  name: string
  description?: string | null
  greeting?: string | null
  presetQuestions?: string[]
  systemPrompt?: string | null
}

export interface ChatRequest {
  conversationId?: string
  query: string
}

export interface AgentRunResult {
  runId: string
  answer: string
}

export interface ConversationMessageResponse {
  role: string
  content: string
}

export interface KnowledgeBaseResponse {
  id: number
  name: string
  description?: string | null
  status: string
}

export interface CreateKnowledgeBaseRequest {
  name: string
  description?: string
}

export interface DocumentResponse {
  id: number
  knowledgeBaseId: number
  title: string
  fileType: string
  source?: string | null
  status: string
  errorMessage?: string | null
}

export interface ImportUrlRequest {
  url: string
  title?: string
}

export interface TaskResponse {
  id: number
  knowledgeBaseId: number
  documentId: number
  status: string
  attemptCount: number
  maxAttempts: number
  errorMessage?: string | null
}

export interface CreateApiKeyResult {
  agentKey: string
  apiKey: string
}

export interface ApiKeyRecord {
  agentKey: string
  enabled: boolean
}

const apiBase = import.meta.env.VITE_API_BASE ?? ''

/** 统一响应包装（后端 Controller 一律返回 R<T>） */
export interface R<T> {
  code: number
  msg: string
  data: T
}

/** 统一分页结果（列表接口出参） */
export interface PageResult<T> {
  total: number
  rows: T[]
}

/** 分页入参（与后端 PageQuery 对应） */
export interface PageQuery {
  pageNum?: number
  pageSize?: number
  orderByColumn?: string
  isAsc?: string
}

function normalizeBearer(token: string): string {
  return token.trim().toLowerCase().startsWith('bearer ') ? token.trim() : `Bearer ${token.trim()}`
}

async function readError(response: Response): Promise<string> {
  const text = await response.text()
  if (!text) return `${response.status} ${response.statusText}`.trim()
  try {
    const body = JSON.parse(text) as { message?: string; error?: string; code?: string }
    return body.message ?? body.error ?? body.code ?? text
  } catch {
    return text
  }
}

async function requestJson<T>(path: string, init: RequestInit = {}, adminToken?: string, raw = false): Promise<T> {
  const headers: Record<string, string> = {
    Accept: 'application/json',
    ...(init.body && !(init.body instanceof FormData) ? { 'Content-Type': 'application/json' } : {}),
    ...(init.headers as Record<string, string> | undefined)
  }

  if (adminToken) {
    headers.Authorization = normalizeBearer(adminToken)
  }

  const response = await fetch(`${apiBase}${path}`, { ...init, headers })
  if (!response.ok) {
    throw new Error(await readError(response))
  }
  if (response.status === 204) {
    return undefined as T
  }
  const text = await response.text()
  if (!text) {
    return undefined as T
  }
  const body = JSON.parse(text)
  if (raw) {
    return body as T
  }
  const r = body as R<T>
  if (r.code !== 200) {
    throw new Error(r.msg || '请求失败')
  }
  return r.data
}

export async function login(username: string, password: string): Promise<LoginResponse> {
  return requestJson<LoginResponse>('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username, password })
  })
}

function pageQueryString(query?: PageQuery): string {
  if (!query) return ''
  const params = new URLSearchParams()
  if (query.pageNum !== undefined) params.set('pageNum', String(query.pageNum))
  if (query.pageSize !== undefined) params.set('pageSize', String(query.pageSize))
  if (query.orderByColumn) params.set('orderByColumn', query.orderByColumn)
  if (query.isAsc) params.set('isAsc', query.isAsc)
  const s = params.toString()
  return s ? `?${s}` : ''
}

// ---- Provider ----

export function listProviders(adminToken: string, query?: PageQuery): Promise<PageResult<ProviderResponse>> {
  return requestJson<PageResult<ProviderResponse>>(`/api/admin/providers${pageQueryString(query)}`, {}, adminToken)
}

export function createProvider(adminToken: string, request: CreateProviderRequest): Promise<ProviderResponse> {
  return requestJson<ProviderResponse>('/api/admin/providers', {
    method: 'POST',
    body: JSON.stringify(request)
  }, adminToken)
}

export function updateProvider(adminToken: string, providerKey: string, request: UpdateProviderRequest): Promise<ProviderResponse> {
  return requestJson<ProviderResponse>(`/api/admin/providers/${encodeURIComponent(providerKey)}`, {
    method: 'PUT',
    body: JSON.stringify(request)
  }, adminToken)
}

export function deleteProvider(adminToken: string, providerKey: string): Promise<void> {
  return requestJson<void>(`/api/admin/providers/${encodeURIComponent(providerKey)}`, {
    method: 'DELETE'
  }, adminToken)
}

// ---- Model ----

export function listModels(adminToken: string, query?: PageQuery): Promise<PageResult<ModelResponse>> {
  return requestJson<PageResult<ModelResponse>>(`/api/admin/models${pageQueryString(query)}`, {}, adminToken)
}

export function createModel(adminToken: string, request: CreateModelRequest): Promise<ModelResponse> {
  return requestJson<ModelResponse>('/api/admin/models', {
    method: 'POST',
    body: JSON.stringify(request)
  }, adminToken)
}

export function updateModel(adminToken: string, id: number, request: UpdateModelRequest): Promise<ModelResponse> {
  return requestJson<ModelResponse>(`/api/admin/models/${id}`, {
    method: 'PUT',
    body: JSON.stringify(request)
  }, adminToken)
}

export function deleteModel(adminToken: string, id: number): Promise<void> {
  return requestJson<void>(`/api/admin/models/${id}`, {
    method: 'DELETE'
  }, adminToken)
}

export function testModelConnection(adminToken: string, id: number): Promise<TestConnectionResponse> {
  return requestJson<TestConnectionResponse>(`/api/admin/models/${id}/test`, {
    method: 'POST'
  }, adminToken)
}

// ---- Agent ----

export function listAgents(adminToken: string): Promise<PageResult<AgentResponse>> {
  return requestJson<PageResult<AgentResponse>>('/api/admin/agents', {}, adminToken)
}

export function createAgent(adminToken: string, request: CreateAgentRequest): Promise<AgentResponse> {
  return requestJson<AgentResponse>('/api/admin/agents', {
    method: 'POST',
    body: JSON.stringify(request)
  }, adminToken)
}

export function updateAgent(adminToken: string, agentKey: string, request: UpdateAgentRequest): Promise<AgentResponse> {
  return requestJson<AgentResponse>(`/api/admin/agents/${encodeURIComponent(agentKey)}`, {
    method: 'PUT',
    body: JSON.stringify(request)
  }, adminToken)
}

export function disableAgent(adminToken: string, agentKey: string): Promise<void> {
  return requestJson<void>(`/api/admin/agents/${encodeURIComponent(agentKey)}/disable`, {
    method: 'POST'
  }, adminToken)
}

export function deleteAgent(adminToken: string, agentKey: string): Promise<void> {
  return requestJson<void>(`/api/admin/agents/${encodeURIComponent(agentKey)}`, {
    method: 'DELETE'
  }, adminToken)
}

export function listAgentVersions(adminToken: string, agentKey: string): Promise<PageResult<AgentVersionResponse>> {
  return requestJson<PageResult<AgentVersionResponse>>(`/api/admin/agents/${encodeURIComponent(agentKey)}/versions`, {}, adminToken)
}

export function publishAgent(adminToken: string, agentKey: string, description: string): Promise<AgentResponse> {
  return requestJson<AgentResponse>(`/api/admin/agents/${encodeURIComponent(agentKey)}/publish`, {
    method: 'POST',
    body: JSON.stringify({ description })
  }, adminToken)
}

export function generateAgentConfig(
  adminToken: string,
  request: GenerateAgentConfigRequest
): Promise<GenerateAgentConfigResponse> {
  return requestJson<GenerateAgentConfigResponse>('/api/admin/agents/generate', {
    method: 'POST',
    body: JSON.stringify(request)
  }, adminToken)
}

// ---- Runtime chat ----

export function chat(agentKey: string, apiKey: string, request: ChatRequest): Promise<AgentRunResult> {
  // 运行 API 的 call 为 Mono 响应，不包 R，raw 模式直接返回
  return requestJson<AgentRunResult>(`/api/v1/agents/${encodeURIComponent(agentKey)}/chat`, {
    method: 'POST',
    headers: {
      Authorization: normalizeBearer(apiKey)
    },
    body: JSON.stringify(request)
  }, undefined, true)
}

export async function streamChat(
  agentKey: string,
  apiKey: string,
  request: ChatRequest,
  onEvent: (event: string, data: string) => void
): Promise<void> {
  await streamSse(
    `/api/v1/agents/${encodeURIComponent(agentKey)}/chat/stream`,
    { Authorization: normalizeBearer(apiKey) },
    request,
    onEvent
  )
}

/** 管理端调试：使用管理 Sa-Token 鉴权（免 API Key），供后台预览/调试面板使用。 */
export async function adminDebugStream(
  agentKey: string,
  adminToken: string,
  request: ChatRequest,
  onEvent: (event: string, data: string) => void
): Promise<void> {
  await streamSse(
    `/api/admin/agents/${encodeURIComponent(agentKey)}/chat/stream`,
    { Authorization: normalizeBearer(adminToken) },
    request,
    onEvent
  )
}

async function streamSse(
  path: string,
  headers: Record<string, string>,
  body: unknown,
  onEvent: (event: string, data: string) => void
): Promise<void> {
  const response = await fetch(`${apiBase}${path}`, {
    method: 'POST',
    headers: {
      ...headers,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(body)
  })

  if (!response.ok || !response.body) {
    throw new Error(await readError(response))
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    const chunks = buffer.split('\n\n')
    buffer = chunks.pop() ?? ''
    for (const chunk of chunks) {
      const event = chunk.match(/^event:\s*(.*)$/m)?.[1] ?? 'message'
      const data = chunk.match(/^data:\s*(.*)$/m)?.[1] ?? ''
      onEvent(event, data)
    }
  }
}

export function getConversationMessages(agentKey: string, apiKey: string, conversationId: string): Promise<PageResult<ConversationMessageResponse>> {
  return requestJson<PageResult<ConversationMessageResponse>>(
    `/api/v1/agents/${encodeURIComponent(agentKey)}/chat/conversations/${encodeURIComponent(conversationId)}`,
    { headers: { Authorization: normalizeBearer(apiKey) } }
  )
}

export async function resumeConversation(
  agentKey: string,
  apiKey: string,
  conversationId: string,
  query: string,
  onEvent: (event: string, data: string) => void
): Promise<void> {
  const response = await fetch(`${apiBase}/api/v1/agents/${agentKey}/chat/conversations/${conversationId}/resume`, {
    method: 'POST',
    headers: {
      Authorization: normalizeBearer(apiKey),
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({ query })
  })
  if (!response.ok || !response.body) {
    throw new Error(await readError(response))
  }
  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    const chunks = buffer.split('\n\n')
    buffer = chunks.pop() ?? ''
    for (const chunk of chunks) {
      const event = chunk.match(/^event:\s*(.*)$/m)?.[1] ?? 'message'
      const data = chunk.match(/^data:\s*(.*)$/m)?.[1] ?? ''
      onEvent(event, data)
    }
  }
}

// ---- Knowledge bases ----

export function listKnowledgeBases(adminToken: string): Promise<PageResult<KnowledgeBaseResponse>> {
  return requestJson<PageResult<KnowledgeBaseResponse>>('/api/admin/knowledge-bases', {}, adminToken)
}

export function createKnowledgeBase(adminToken: string, request: CreateKnowledgeBaseRequest): Promise<KnowledgeBaseResponse> {
  return requestJson<KnowledgeBaseResponse>('/api/admin/knowledge-bases', {
    method: 'POST',
    body: JSON.stringify(request)
  }, adminToken)
}

export function disableKnowledgeBase(adminToken: string, id: number): Promise<void> {
  return requestJson<void>(`/api/admin/knowledge-bases/${id}`, { method: 'DELETE' }, adminToken)
}

export function listDocuments(adminToken: string, kbId: number): Promise<PageResult<DocumentResponse>> {
  return requestJson<PageResult<DocumentResponse>>(`/api/admin/knowledge-bases/${kbId}/documents`, {}, adminToken)
}

export async function uploadDocument(adminToken: string, kbId: number, file: File, title?: string): Promise<DocumentResponse> {
  const formData = new FormData()
  formData.append('file', file)
  if (title) {
    formData.append('title', title)
  }
  return requestJson<DocumentResponse>(`/api/admin/knowledge-bases/${kbId}/documents`, {
    method: 'POST',
    body: formData
  }, adminToken)
}

export function importUrl(adminToken: string, kbId: number, request: ImportUrlRequest): Promise<DocumentResponse> {
  return requestJson<DocumentResponse>(`/api/admin/knowledge-bases/${kbId}/documents/url`, {
    method: 'POST',
    body: JSON.stringify(request)
  }, adminToken)
}

export function deleteDocument(adminToken: string, documentId: number): Promise<void> {
  return requestJson<void>(`/api/admin/documents/${documentId}`, { method: 'DELETE' }, adminToken)
}

export function retryDocument(adminToken: string, documentId: number): Promise<void> {
  return requestJson<void>(`/api/admin/documents/${documentId}/retry`, { method: 'POST' }, adminToken)
}

export function listTasks(adminToken: string, kbId: number): Promise<PageResult<TaskResponse>> {
  return requestJson<PageResult<TaskResponse>>(`/api/admin/knowledge-bases/${kbId}/tasks`, {}, adminToken)
}

export function bindKnowledgeBases(adminToken: string, agentKey: string, knowledgeBaseIds: number[]): Promise<void> {
  return requestJson<void>(`/api/admin/agents/${encodeURIComponent(agentKey)}/knowledge-bindings`, {
    method: 'POST',
    body: JSON.stringify({ knowledgeBaseIds })
  }, adminToken)
}

export function getKnowledgeBindings(adminToken: string, agentKey: string): Promise<number[]> {
  return requestJson<number[]>(`/api/admin/agents/${encodeURIComponent(agentKey)}/knowledge-bindings`, {}, adminToken)
}

// ---- API keys ----

export function createApiKey(adminToken: string, agentKey: string): Promise<CreateApiKeyResult> {
  return requestJson<CreateApiKeyResult>(`/api/admin/agents/${encodeURIComponent(agentKey)}/api-keys`, {
    method: 'POST'
  }, adminToken)
}

export function listApiKeys(adminToken: string): Promise<PageResult<ApiKeyRecord>> {
  return requestJson<PageResult<ApiKeyRecord>>('/api/admin/api-keys', {}, adminToken)
}

export function deleteApiKey(adminToken: string, agentKey: string): Promise<void> {
  return requestJson<void>(`/api/admin/agents/${encodeURIComponent(agentKey)}/api-keys`, {
    method: 'DELETE'
  }, adminToken)
}

// ---- MCP servers ----

export type McpTransportType = 'STREAMABLE_HTTP' | 'SSE' | 'STDIO'

export interface McpServerResponse {
  id: number
  name: string
  transportType: McpTransportType
  endpoint?: string | null
  args?: string[] | null
  headers?: Record<string, string> | null
  envVars?: Record<string, string> | null
  timeoutSeconds: number
  status: string
  lastTestStatus?: string | null
  lastTestMessage?: string | null
  lastTestedAt?: string | null
}

export interface CreateMcpServerRequest {
  name: string
  transportType: McpTransportType
  endpoint?: string
  args?: string[]
  headers?: Record<string, string>
  envVars?: Record<string, string>
  timeoutSeconds?: number
}

export interface UpdateMcpServerRequest {
  name?: string
  transportType?: McpTransportType
  endpoint?: string
  args?: string[]
  headers?: Record<string, string>
  envVars?: Record<string, string>
  timeoutSeconds?: number
}

export interface McpConnectionTestResponse {
  serverId: number
  ok: boolean
  message: string
  toolCount: number
}

export type McpToolPermission = 'ALLOW' | 'APPROVE' | 'DENY'

export interface McpToolResponse {
  id: number
  serverId: number
  toolName: string
  description?: string | null
  inputSchema: Record<string, unknown>
  readOnly: boolean
  permission: McpToolPermission
  lastDiscoveredAt?: string | null
}

export interface McpToolCallAuditResponse {
  id: number
  runId: string
  serverId: number
  serverName: string
  toolName: string
  argumentsSummary?: string | null
  decision: McpToolPermission
  status: string
  durationMs?: number | null
  message?: string | null
  createdAt?: string | null
}

export function listMcpServers(adminToken: string): Promise<PageResult<McpServerResponse>> {
  return requestJson<PageResult<McpServerResponse>>('/api/admin/mcp-servers', {}, adminToken)
}

export function createMcpServer(adminToken: string, request: CreateMcpServerRequest): Promise<McpServerResponse> {
  return requestJson<McpServerResponse>('/api/admin/mcp-servers', {
    method: 'POST',
    body: JSON.stringify(request)
  }, adminToken)
}

export function updateMcpServer(adminToken: string, id: number, request: UpdateMcpServerRequest): Promise<McpServerResponse> {
  return requestJson<McpServerResponse>(`/api/admin/mcp-servers/${id}`, {
    method: 'PUT',
    body: JSON.stringify(request)
  }, adminToken)
}

export function disableMcpServer(adminToken: string, id: number): Promise<void> {
  return requestJson<void>(`/api/admin/mcp-servers/${id}`, { method: 'DELETE' }, adminToken)
}

export function testMcpServerConnection(adminToken: string, id: number): Promise<McpConnectionTestResponse> {
  return requestJson<McpConnectionTestResponse>(`/api/admin/mcp-servers/${id}/test-connection`, {
    method: 'POST'
  }, adminToken)
}

export function discoverMcpTools(adminToken: string, id: number): Promise<McpToolResponse[]> {
  return requestJson<McpToolResponse[]>(`/api/admin/mcp-servers/${id}/discover-tools`, {
    method: 'POST'
  }, adminToken)
}

export function listMcpTools(adminToken: string, id: number): Promise<PageResult<McpToolResponse>> {
  return requestJson<PageResult<McpToolResponse>>(`/api/admin/mcp-servers/${id}/tools`, {}, adminToken)
}

export function updateMcpToolPermission(adminToken: string, id: number, toolName: string, permission: McpToolPermission): Promise<McpToolResponse> {
  return requestJson<McpToolResponse>(`/api/admin/mcp-servers/${id}/tools/${encodeURIComponent(toolName)}/permission`, {
    method: 'PUT',
    body: JSON.stringify({ permission })
  }, adminToken)
}

export function listMcpToolCallsByServer(adminToken: string, id: number): Promise<PageResult<McpToolCallAuditResponse>> {
  return requestJson<PageResult<McpToolCallAuditResponse>>(`/api/admin/mcp-servers/${id}/tool-calls`, {}, adminToken)
}

export function bindMcpServers(adminToken: string, agentKey: string, mcpServerIds: number[]): Promise<void> {
  return requestJson<void>(`/api/admin/agents/${encodeURIComponent(agentKey)}/mcp-bindings`, {
    method: 'POST',
    body: JSON.stringify({ mcpServerIds })
  }, adminToken)
}

export function getMcpServerBindings(adminToken: string, agentKey: string): Promise<number[]> {
  return requestJson<number[]>(`/api/admin/agents/${encodeURIComponent(agentKey)}/mcp-bindings`, {}, adminToken)
}

// ---- Skills ----

export interface SkillResponse {
  id: number
  name: string
  description?: string | null
  status: string
  currentVersionId?: number | null
  currentVersion?: number | null
  createdAt?: string | null
  updatedAt?: string | null
}

export interface SkillVersionResponse {
  id: number
  skillId: number
  version: number
  status: string
  description?: string | null
  publishedAt?: string | null
  createdAt?: string | null
}

export function listSkills(adminToken: string): Promise<PageResult<SkillResponse>> {
  return requestJson<PageResult<SkillResponse>>('/api/admin/skills', {}, adminToken)
}

export async function uploadSkill(adminToken: string, file: File, description?: string): Promise<SkillResponse> {
  const formData = new FormData()
  formData.append('file', file)
  if (description) {
    formData.append('description', description)
  }
  return requestJson<SkillResponse>('/api/admin/skills', {
    method: 'POST',
    body: formData
  }, adminToken)
}

export function disableSkill(adminToken: string, id: number): Promise<void> {
  return requestJson<void>(`/api/admin/skills/${id}`, { method: 'DELETE' }, adminToken)
}

export function listSkillVersions(adminToken: string, id: number): Promise<PageResult<SkillVersionResponse>> {
  return requestJson<PageResult<SkillVersionResponse>>(`/api/admin/skills/${id}/versions`, {}, adminToken)
}

export function publishSkillVersion(adminToken: string, id: number, versionId: number): Promise<SkillResponse> {
  return requestJson<SkillResponse>(`/api/admin/skills/${id}/versions/${versionId}/publish`, {
    method: 'POST'
  }, adminToken)
}

export function rollbackSkill(adminToken: string, id: number, versionId: number): Promise<SkillResponse> {
  return requestJson<SkillResponse>(`/api/admin/skills/${id}/rollback`, {
    method: 'POST',
    body: JSON.stringify({ versionId })
  }, adminToken)
}

export function bindSkills(adminToken: string, agentKey: string, skillIds: number[]): Promise<void> {
  return requestJson<void>(`/api/admin/agents/${encodeURIComponent(agentKey)}/skill-bindings`, {
    method: 'POST',
    body: JSON.stringify({ skillIds })
  }, adminToken)
}

export function getSkillBindings(adminToken: string, agentKey: string): Promise<number[]> {
  return requestJson<number[]>(`/api/admin/agents/${encodeURIComponent(agentKey)}/skill-bindings`, {}, adminToken)
}

// ---- 下拉选项（轻量 options，供 Agent 编辑/绑定弹窗按需加载） ----

export interface OptionResponse {
  value: number
  label: string
}

export function listModelOptions(adminToken: string): Promise<OptionResponse[]> {
  return requestJson<OptionResponse[]>('/api/admin/models/options', {}, adminToken)
}

export function listKnowledgeBaseOptions(adminToken: string): Promise<OptionResponse[]> {
  return requestJson<OptionResponse[]>('/api/admin/knowledge-bases/options', {}, adminToken)
}

export function listMcpServerOptions(adminToken: string): Promise<OptionResponse[]> {
  return requestJson<OptionResponse[]>('/api/admin/mcp-servers/options', {}, adminToken)
}

export function listSkillOptions(adminToken: string): Promise<OptionResponse[]> {
  return requestJson<OptionResponse[]>('/api/admin/skills/options', {}, adminToken)
}

// ---- Platform (Phase 5) ----

export interface Tenant {
  id: number
  tenantKey: string
  name: string
  status: string
  createdAt?: string | null
  updatedAt?: string | null
}

export interface Role {
  roleKey: string
  roleName: string
  description?: string | null
  dataScope?: string | null
  builtin: boolean
  createdAt?: string | null
  updatedAt?: string | null
}

export interface UserAccount {
  id: number
  username: string
  displayName?: string | null
  tenantId?: number | null
  roles: string[]
  status?: string | null
}

export interface AppQuota {
  agentKey: string
  dailyModelCalls: number
  dailyTokens: number
  ratePerMinute: number
  updatedAt?: string | null
}

export interface AppUsage {
  agentKey: string
  usageDate: string
  modelCalls: number
  tokens: number
}

export interface UsageRecord {
  id: number
  runId?: string | null
  agentKey?: string | null
  modelKey?: string | null
  promptTokens: number
  completionTokens: number
  totalTokens: number
  durationMs?: number | null
  status: string
  createdAt?: string | null
}

export interface AuditLog {
  id: number
  tenantId?: number | null
  actor?: string | null
  action: string
  targetType?: string | null
  targetId?: string | null
  detail?: string | null
  ip?: string | null
  createdAt?: string | null
}

export interface OverviewResponse {
  agents: number
  models: number
  knowledgeBases: number
  skills: number
  totalRuns: number
  todayRuns: number
  todayModelCalls: number
  todayTokens: number
}

export interface LoginResponseFull {
  tokenType: string
  token: string
  userId?: number | null
  username?: string | null
  displayName?: string | null
  tenantId?: number | null
  roles?: string[]
}

export function listTenants(adminToken: string): Promise<PageResult<Tenant>> {
  return requestJson<PageResult<Tenant>>('/api/admin/tenants', {}, adminToken)
}

export function createTenant(adminToken: string, tenantKey: string, name: string): Promise<Tenant> {
  return requestJson<Tenant>('/api/admin/tenants', {
    method: 'POST',
    body: JSON.stringify({ tenantKey, name })
  }, adminToken)
}

export function disableTenant(adminToken: string, id: number): Promise<void> {
  return requestJson<void>(`/api/admin/tenants/${id}`, { method: 'DELETE' }, adminToken)
}

export function listUsers(adminToken: string): Promise<PageResult<UserAccount>> {
  return requestJson<PageResult<UserAccount>>('/api/admin/users', {}, adminToken)
}

export function createUser(adminToken: string, request: { username: string; password: string; displayName?: string; tenantId?: number; roles?: string[] }): Promise<UserAccount> {
  return requestJson<UserAccount>('/api/admin/users', {
    method: 'POST',
    body: JSON.stringify(request)
  }, adminToken)
}

export function updateUserRoles(adminToken: string, id: number, roles: string[]): Promise<UserAccount> {
  return requestJson<UserAccount>(`/api/admin/users/${id}/roles`, {
    method: 'PUT',
    body: JSON.stringify({ roles })
  }, adminToken)
}

export function updateUserStatus(adminToken: string, id: number, status: string): Promise<UserAccount> {
  return requestJson<UserAccount>(`/api/admin/users/${id}/status`, {
    method: 'PUT',
    body: JSON.stringify({ status })
  }, adminToken)
}

// ---- Roles ----

export function listRoles(adminToken: string): Promise<PageResult<Role>> {
  return requestJson<PageResult<Role>>('/api/admin/roles', {}, adminToken)
}

export function createRole(adminToken: string, request: { roleKey: string; roleName: string; description?: string; dataScope?: string }): Promise<Role> {
  return requestJson<Role>('/api/admin/roles', {
    method: 'POST',
    body: JSON.stringify(request)
  }, adminToken)
}

export function updateRole(adminToken: string, roleKey: string, request: { roleName: string; description?: string; dataScope?: string }): Promise<Role> {
  return requestJson<Role>(`/api/admin/roles/${encodeURIComponent(roleKey)}`, {
    method: 'PUT',
    body: JSON.stringify(request)
  }, adminToken)
}

export function deleteRole(adminToken: string, roleKey: string): Promise<void> {
  return requestJson<void>(`/api/admin/roles/${encodeURIComponent(roleKey)}`, {
    method: 'DELETE'
  }, adminToken)
}

// ---- Menus ----

export interface Menu {
  id: number
  parentId?: number | null
  code: string
  name: string
  type: string // 'DIR' | 'MENU'
  routeName?: string | null
  path?: string | null
  icon?: string | null
  sortOrder: number
  visible: boolean
  createdAt?: string | null
  updatedAt?: string | null
}

export interface MenuRequest {
  parentId?: number | null
  name: string
  type: string
  routeName?: string | null
  path?: string | null
  icon?: string | null
  sortOrder?: number
  visible?: boolean
}

export function listMenus(adminToken: string): Promise<PageResult<Menu>> {
  return requestJson<PageResult<Menu>>('/api/admin/menus', {}, adminToken)
}

export function createMenu(adminToken: string, request: MenuRequest & { code: string }): Promise<Menu> {
  return requestJson<Menu>('/api/admin/menus', {
    method: 'POST',
    body: JSON.stringify(request)
  }, adminToken)
}

export function updateMenu(adminToken: string, id: number, request: MenuRequest): Promise<Menu> {
  return requestJson<Menu>(`/api/admin/menus/${id}`, {
    method: 'PUT',
    body: JSON.stringify(request)
  }, adminToken)
}

export function deleteMenu(adminToken: string, id: number): Promise<void> {
  return requestJson<void>(`/api/admin/menus/${id}`, {
    method: 'DELETE'
  }, adminToken)
}

export function listRoleMenus(adminToken: string, roleKey: string): Promise<number[]> {
  return requestJson<number[]>(`/api/admin/roles/${encodeURIComponent(roleKey)}/menus`, {}, adminToken)
}

export function updateRoleMenus(adminToken: string, roleKey: string, menuIds: number[]): Promise<void> {
  return requestJson<void>(`/api/admin/roles/${encodeURIComponent(roleKey)}/menus`, {
    method: 'PUT',
    body: JSON.stringify({ menuIds })
  }, adminToken)
}

export function fetchMyMenus(adminToken: string): Promise<Menu[]> {
  return requestJson<Menu[]>('/api/auth/menus', {}, adminToken)
}

export function getAppQuota(adminToken: string, agentKey: string): Promise<AppQuota> {
  return requestJson<AppQuota>(`/api/admin/apps/${encodeURIComponent(agentKey)}/quota`, {}, adminToken)
}

export function updateAppQuota(adminToken: string, agentKey: string, request: { dailyModelCalls?: number; dailyTokens?: number; ratePerMinute?: number }): Promise<AppQuota> {
  return requestJson<AppQuota>(`/api/admin/apps/${encodeURIComponent(agentKey)}/quota`, {
    method: 'PUT',
    body: JSON.stringify(request)
  }, adminToken)
}

export function getAppDailyUsage(adminToken: string, agentKey: string): Promise<AppUsage> {
  return requestJson<AppUsage>(`/api/admin/apps/${encodeURIComponent(agentKey)}/usage/daily`, {}, adminToken)
}

export function listUsage(adminToken: string, params: { agentKey?: string; from?: string; to?: string; pageSize?: number } = {}): Promise<PageResult<UsageRecord>> {
  const query = new URLSearchParams()
  if (params.agentKey) query.set('agentKey', params.agentKey)
  if (params.from) query.set('from', params.from)
  if (params.to) query.set('to', params.to)
  if (params.pageSize) query.set('pageSize', String(params.pageSize))
  const qs = query.toString()
  return requestJson<PageResult<UsageRecord>>(`/api/admin/usage${qs ? `?${qs}` : ''}`, {}, adminToken)
}

export function listAuditLogs(adminToken: string, params: { actor?: string; action?: string; pageSize?: number } = {}): Promise<PageResult<AuditLog>> {
  const query = new URLSearchParams()
  if (params.actor) query.set('actor', params.actor)
  if (params.action) query.set('action', params.action)
  if (params.pageSize) query.set('pageSize', String(params.pageSize))
  const qs = query.toString()
  return requestJson<PageResult<AuditLog>>(`/api/admin/audit-logs${qs ? `?${qs}` : ''}`, {}, adminToken)
}

export function getStatsOverview(adminToken: string): Promise<OverviewResponse> {
  return requestJson<OverviewResponse>('/api/admin/stats/overview', {}, adminToken)
}

export function fetchMe(adminToken: string): Promise<LoginResponseFull> {
  return requestJson<LoginResponseFull>('/api/auth/me', {}, adminToken)
}

// ---- Workflows ----

export type WorkflowNodeType = 'START' | 'AGENT' | 'CONDITION' | 'END'

export interface WorkflowNode {
  id: string
  type: WorkflowNodeType
  name: string
  config?: Record<string, unknown> | null
}

export interface WorkflowEdge {
  id: string
  source: string
  target: string
  sourceHandle?: string | null
}

export interface WorkflowDefinition {
  nodes: WorkflowNode[]
  edges: WorkflowEdge[]
}

export interface WorkflowResponse {
  id: number
  workflowKey: string
  name: string
  description?: string | null
  status: string // DRAFT | PUBLISHED | DISABLED
  draftDefinition?: WorkflowDefinition | null
  publishedDefinition?: WorkflowDefinition | null
  publishedVersion?: number | null
  publishedAt?: string | null
  createdAt?: string | null
  updatedAt?: string | null
}

export interface CreateWorkflowRequest {
  workflowKey: string
  name: string
  description?: string
}

export interface UpdateWorkflowRequest {
  name?: string
  description?: string
  definition?: WorkflowDefinition
}

export interface WorkflowRunResponse {
  runId: string
  workflowKey: string
  workflowVersion?: number | null
  status: string // RUNNING | SUCCEEDED | FAILED
  inputs?: Record<string, unknown> | null
  outputs?: Record<string, unknown> | null
  error?: string | null
  startedAt?: string | null
  finishedAt?: string | null
  createdAt?: string | null
}

export interface WorkflowNodeRunResponse {
  id: number
  runId: string
  nodeId: string
  nodeType: string
  status: string // PENDING | RUNNING | SUCCEEDED | FAILED | SKIPPED
  inputs?: Record<string, unknown> | null
  outputs?: Record<string, unknown> | null
  error?: string | null
  startedAt?: string | null
  finishedAt?: string | null
}

export interface WorkflowRunDetailResponse {
  run: WorkflowRunResponse
  nodeRuns: WorkflowNodeRunResponse[]
}

export function listWorkflows(adminToken: string): Promise<PageResult<WorkflowResponse>> {
  return requestJson<PageResult<WorkflowResponse>>('/api/admin/workflows', {}, adminToken)
}

export function getWorkflow(adminToken: string, workflowKey: string): Promise<WorkflowResponse> {
  return requestJson<WorkflowResponse>(`/api/admin/workflows/${encodeURIComponent(workflowKey)}`, {}, adminToken)
}

export function createWorkflow(adminToken: string, request: CreateWorkflowRequest): Promise<WorkflowResponse> {
  return requestJson<WorkflowResponse>('/api/admin/workflows', {
    method: 'POST',
    body: JSON.stringify(request)
  }, adminToken)
}

export function updateWorkflow(adminToken: string, workflowKey: string, request: UpdateWorkflowRequest): Promise<WorkflowResponse> {
  return requestJson<WorkflowResponse>(`/api/admin/workflows/${encodeURIComponent(workflowKey)}`, {
    method: 'PUT',
    body: JSON.stringify(request)
  }, adminToken)
}

export function publishWorkflow(adminToken: string, workflowKey: string): Promise<WorkflowResponse> {
  return requestJson<WorkflowResponse>(`/api/admin/workflows/${encodeURIComponent(workflowKey)}/publish`, {
    method: 'POST'
  }, adminToken)
}

export function disableWorkflow(adminToken: string, workflowKey: string): Promise<void> {
  return requestJson<void>(`/api/admin/workflows/${encodeURIComponent(workflowKey)}`, {
    method: 'DELETE'
  }, adminToken)
}

export function runWorkflow(adminToken: string, workflowKey: string, inputs?: Record<string, unknown>): Promise<WorkflowRunResponse> {
  return requestJson<WorkflowRunResponse>(`/api/admin/workflows/${encodeURIComponent(workflowKey)}/run`, {
    method: 'POST',
    body: JSON.stringify({ inputs: inputs ?? {} })
  }, adminToken)
}

export function listWorkflowRuns(adminToken: string, workflowKey: string): Promise<PageResult<WorkflowRunResponse>> {
  return requestJson<PageResult<WorkflowRunResponse>>(`/api/admin/workflows/${encodeURIComponent(workflowKey)}/runs`, {}, adminToken)
}

export function getWorkflowRun(adminToken: string, runId: string): Promise<WorkflowRunDetailResponse> {
  return requestJson<WorkflowRunDetailResponse>(`/api/admin/workflows/runs/${encodeURIComponent(runId)}`, {}, adminToken)
}
