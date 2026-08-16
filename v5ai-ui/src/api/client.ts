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
}

export interface CreateAgentRequest {
  agentKey: string
  name: string
  description?: string
  modelId: number
  systemPrompt?: string
}

export interface UpdateAgentRequest {
  name?: string
  description?: string
  modelId?: number
  systemPrompt?: string
}

export interface AgentVersionResponse {
  agentKey: string
  version: number
  snapshotJson: string
  description?: string | null
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

async function requestJson<T>(path: string, init: RequestInit = {}, adminToken?: string): Promise<T> {
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
  return JSON.parse(text) as T
}

export async function login(username: string, password: string): Promise<LoginResponse> {
  return requestJson<LoginResponse>('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username, password })
  })
}

// ---- Provider ----

export function listProviders(adminToken: string): Promise<ProviderResponse[]> {
  return requestJson<ProviderResponse[]>('/api/admin/providers', {}, adminToken)
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

export function listModels(adminToken: string): Promise<ModelResponse[]> {
  return requestJson<ModelResponse[]>('/api/admin/models', {}, adminToken)
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

export function listAgents(adminToken: string): Promise<AgentResponse[]> {
  return requestJson<AgentResponse[]>('/api/admin/agents', {}, adminToken)
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
  return requestJson<void>(`/api/admin/agents/${encodeURIComponent(agentKey)}`, {
    method: 'DELETE'
  }, adminToken)
}

export function listAgentVersions(adminToken: string, agentKey: string): Promise<AgentVersionResponse[]> {
  return requestJson<AgentVersionResponse[]>(`/api/admin/agents/${encodeURIComponent(agentKey)}/versions`, {}, adminToken)
}

export function publishAgent(adminToken: string, agentKey: string, description: string): Promise<AgentResponse> {
  return requestJson<AgentResponse>(`/api/admin/agents/${encodeURIComponent(agentKey)}/publish`, {
    method: 'POST',
    body: JSON.stringify({ description })
  }, adminToken)
}

// ---- Runtime chat ----

export function chat(agentKey: string, apiKey: string, request: ChatRequest): Promise<AgentRunResult> {
  return requestJson<AgentRunResult>(`/api/v1/agents/${encodeURIComponent(agentKey)}/chat`, {
    method: 'POST',
    headers: {
      Authorization: normalizeBearer(apiKey)
    },
    body: JSON.stringify(request)
  })
}

export async function streamChat(
  agentKey: string,
  apiKey: string,
  request: ChatRequest,
  onEvent: (event: string, data: string) => void
): Promise<void> {
  const response = await fetch(`${apiBase}/api/v1/agents/${agentKey}/chat/stream`, {
    method: 'POST',
    headers: {
      Authorization: normalizeBearer(apiKey),
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(request)
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

export function getConversationMessages(agentKey: string, apiKey: string, conversationId: string): Promise<ConversationMessageResponse[]> {
  return requestJson<ConversationMessageResponse[]>(
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

export function listKnowledgeBases(adminToken: string): Promise<KnowledgeBaseResponse[]> {
  return requestJson<KnowledgeBaseResponse[]>('/api/admin/knowledge-bases', {}, adminToken)
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

export function listDocuments(adminToken: string, kbId: number): Promise<DocumentResponse[]> {
  return requestJson<DocumentResponse[]>(`/api/admin/knowledge-bases/${kbId}/documents`, {}, adminToken)
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

export function listTasks(adminToken: string, kbId: number): Promise<TaskResponse[]> {
  return requestJson<TaskResponse[]>(`/api/admin/knowledge-bases/${kbId}/tasks`, {}, adminToken)
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

export function listApiKeys(adminToken: string): Promise<ApiKeyRecord[]> {
  return requestJson<ApiKeyRecord[]>('/api/admin/api-keys', {}, adminToken)
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

export function listMcpServers(adminToken: string): Promise<McpServerResponse[]> {
  return requestJson<McpServerResponse[]>('/api/admin/mcp-servers', {}, adminToken)
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

export function listMcpTools(adminToken: string, id: number): Promise<McpToolResponse[]> {
  return requestJson<McpToolResponse[]>(`/api/admin/mcp-servers/${id}/tools`, {}, adminToken)
}

export function updateMcpToolPermission(adminToken: string, id: number, toolName: string, permission: McpToolPermission): Promise<McpToolResponse> {
  return requestJson<McpToolResponse>(`/api/admin/mcp-servers/${id}/tools/${encodeURIComponent(toolName)}/permission`, {
    method: 'PUT',
    body: JSON.stringify({ permission })
  }, adminToken)
}

export function listMcpToolCallsByServer(adminToken: string, id: number): Promise<McpToolCallAuditResponse[]> {
  return requestJson<McpToolCallAuditResponse[]>(`/api/admin/mcp-servers/${id}/tool-calls`, {}, adminToken)
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

export function listSkills(adminToken: string): Promise<SkillResponse[]> {
  return requestJson<SkillResponse[]>('/api/admin/skills', {}, adminToken)
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

export function listSkillVersions(adminToken: string, id: number): Promise<SkillVersionResponse[]> {
  return requestJson<SkillVersionResponse[]>(`/api/admin/skills/${id}/versions`, {}, adminToken)
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
  name: string
  description?: string | null
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
  enabled: boolean
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

export function listTenants(adminToken: string): Promise<Tenant[]> {
  return requestJson<Tenant[]>('/api/admin/tenants', {}, adminToken)
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

export function listUsers(adminToken: string): Promise<UserAccount[]> {
  return requestJson<UserAccount[]>('/api/admin/users', {}, adminToken)
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

export function updateUserEnabled(adminToken: string, id: number, enabled: boolean): Promise<UserAccount> {
  return requestJson<UserAccount>(`/api/admin/users/${id}/enabled`, {
    method: 'PUT',
    body: JSON.stringify({ enabled })
  }, adminToken)
}

// ---- Roles ----

export function listRoles(adminToken: string): Promise<Role[]> {
  return requestJson<Role[]>('/api/admin/roles', {}, adminToken)
}

export function createRole(adminToken: string, request: { roleKey: string; name: string; description?: string }): Promise<Role> {
  return requestJson<Role>('/api/admin/roles', {
    method: 'POST',
    body: JSON.stringify(request)
  }, adminToken)
}

export function updateRole(adminToken: string, roleKey: string, request: { name: string; description?: string }): Promise<Role> {
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

export function listMenus(adminToken: string): Promise<Menu[]> {
  return requestJson<Menu[]>('/api/admin/menus', {}, adminToken)
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

export function listUsage(adminToken: string, params: { agentKey?: string; from?: string; to?: string; limit?: number } = {}): Promise<UsageRecord[]> {
  const query = new URLSearchParams()
  if (params.agentKey) query.set('agentKey', params.agentKey)
  if (params.from) query.set('from', params.from)
  if (params.to) query.set('to', params.to)
  if (params.limit) query.set('limit', String(params.limit))
  const qs = query.toString()
  return requestJson<UsageRecord[]>(`/api/admin/usage${qs ? `?${qs}` : ''}`, {}, adminToken)
}

export function listAuditLogs(adminToken: string, params: { actor?: string; action?: string; limit?: number } = {}): Promise<AuditLog[]> {
  const query = new URLSearchParams()
  if (params.actor) query.set('actor', params.actor)
  if (params.action) query.set('action', params.action)
  if (params.limit) query.set('limit', String(params.limit))
  const qs = query.toString()
  return requestJson<AuditLog[]>(`/api/admin/audit-logs${qs ? `?${qs}` : ''}`, {}, adminToken)
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

export function listWorkflows(adminToken: string): Promise<WorkflowResponse[]> {
  return requestJson<WorkflowResponse[]>('/api/admin/workflows', {}, adminToken)
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

export function listWorkflowRuns(adminToken: string, workflowKey: string): Promise<WorkflowRunResponse[]> {
  return requestJson<WorkflowRunResponse[]>(`/api/admin/workflows/${encodeURIComponent(workflowKey)}/runs`, {}, adminToken)
}

export function getWorkflowRun(adminToken: string, runId: string): Promise<WorkflowRunDetailResponse> {
  return requestJson<WorkflowRunDetailResponse>(`/api/admin/workflows/runs/${encodeURIComponent(runId)}`, {}, adminToken)
}
