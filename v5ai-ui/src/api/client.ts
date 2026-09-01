import { encryptRequest } from '../utils/crypto'

export interface LoginResponse {
  access_token: string
  expire_in?: number
  refresh_token?: string
  refresh_expire_in?: number
  client_id?: string
}

/** 业务错误：携带后端 R.code，便于前端按错误码分支（如滑块验证码 1006）。 */
export class ApiError extends Error {
  constructor(
    message: string,
    readonly code?: number
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

export interface ProviderResponse {
  id: number
  providerKey: string
  name: string
  providerType: string
  enabled: boolean
  createdAt?: string
  updatedAt?: string
}

export interface CreateProviderRequest {
  providerKey: string
  name: string
  providerType: string
  enabled: boolean
}

export interface UpdateProviderRequest {
  id: number
  name: string
  providerType: string
  enabled: boolean
}

export interface ModelResponse {
  id: number
  providerId: number
  modelKey: string
  modelType: string
  endpoint?: string | null
  credentialsCiphertext?: string
  enabled: boolean
  modelName?: string | null
  description?: string | null
  adapterKey?: string | null
  config?: string | null
  scope?: string | null
  isDefault?: boolean
  ownerId?: number | null
  createdAt?: string
  updatedAt?: string
}

export interface CreateModelRequest {
  providerKey: string
  modelKey: string
  type: string
  endpoint?: string
  enabled?: boolean
  credentials: string
  modelName?: string
  description?: string
  adapterKey?: string
  config?: string
  scope?: string
  isDefault?: boolean
  ownerId?: number
}

export interface UpdateModelRequest {
  id: number
  providerKey?: string
  modelKey: string
  type: string
  endpoint?: string
  enabled?: boolean
  credentials?: string
  modelName?: string
  description?: string
  adapterKey?: string
  config?: string
  scope?: string
  isDefault?: boolean
  ownerId?: number
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
  icon?: string | null
  embeddingModelId: number
  vectorStoreInstanceId?: number | null
  dimensionOfVectorModel: number
  rerankModelId?: number | null
  searchEngineEnable?: boolean
  searchEngineInstanceId?: number | null
  delimiter?: string | null
  ragEnhancement?: string | null
  config?: RagConfig | null
  dedupStrategy?: number
  dedupAction?: number
  createdAt?: string
  updatedAt?: string
}

export interface RagChunkParams {
  mode?: 'length' | 'delimiter' | 'regex' | 'smart'
  maxChunkTokens?: number
  chunkOverlap?: number
  customDelimiter?: string
  chunkRegex?: string
  chunkModelId?: number
  mergeShortSegments?: boolean
  imageOcr?: boolean
}

export interface RagDoclingParams {
  doOcr?: boolean
  doTableStructure?: boolean
  imageExportMode?: string
  ocrLang?: string[]
  pdfBackend?: string
  saveImages?: boolean
  maxImageCount?: number
  maxImageBytes?: number
}

export interface RagParseParams {
  engine?: 'default' | 'docling'
  docling?: RagDoclingParams
}

export interface RagSearchParams {
  resultCount?: number
  rerankEnabled?: boolean
  rerankModelId?: number
  enterRerankCount?: number
  thresholdEnabled?: boolean
  threshold?: number
  denseWeight?: number
  fusionStrategy?: string
  rrfK?: number
  questionRewrite?: boolean
}

export interface RagModelParams {
  modelId?: number
  nearbySliceCount?: number
  prompt?: string
}

export interface RagConfig {
  searchParams?: RagSearchParams
  modelParams?: RagModelParams
  chunkParams?: RagChunkParams
  parseParams?: RagParseParams
}

export interface CreateKnowledgeBaseRequest {
  id?: number
  name: string
  description?: string
  icon?: string
  embeddingModelId: number
  vectorStoreInstanceId: number
  dimensionOfVectorModel: number
  rerankModelId?: number
  searchEngineEnable?: boolean
  searchEngineInstanceId?: number
  delimiter?: string
  ragEnhancement?: string
  config?: RagConfig
  dedupStrategy?: number
  dedupAction?: number
}

export interface StoreInstanceResponse {
  id: number
  name: string
  description?: string | null
  category: number
  type: number
  config?: string | null
  status?: number
  isDefault?: boolean
  createdAt?: string
  updatedAt?: string
}

export interface StoreInstanceRequest {
  id?: number
  name: string
  description?: string
  category: number
  type: number
  config?: string
  status?: number
  isDefault?: boolean
}

export interface DocumentResponse {
  id: number
  knowledgeBaseId: number
  title: string
  fileType: string
  sourceType?: string | null
  status: number
  errorMessage?: string | null
  storageType?: string | null
  storagePath?: string | null
  fileSize?: number
  chunkCount?: number
  parseTime?: number
  contentHash?: string | null
  resourceId?: number | null
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

const apiBase = import.meta.env.VITE_APP_BASE_API ?? import.meta.env.VITE_API_BASE ?? ''
const clientId = import.meta.env.VITE_APP_CLIENT_ID ?? ''

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

const adminTokenKey = 'v5ai.adminToken'
const refreshTokenKey = 'v5ai.adminRefreshToken'

interface SessionHooks {
  /** 读取当前刷新令牌（用于 401 自动刷新）。 */
  getRefreshToken: () => string | null
  /** 刷新成功后同步会话（更新访问令牌，轮换刷新令牌）。 */
  applySession: (accessToken: string, refreshToken: string | null) => void
  /** 刷新失败（刷新令牌失效）：清除本地会话。 */
  onExpired: () => void
}

/** 会话钩子：默认直接读写 localStorage；session store 可注册以同步响应式状态。 */
let sessionHooks: SessionHooks = {
  getRefreshToken: () => localStorage.getItem(refreshTokenKey),
  applySession: (accessToken, refreshToken) => {
    localStorage.setItem(adminTokenKey, accessToken)
    if (refreshToken) {
      localStorage.setItem(refreshTokenKey, refreshToken)
    }
  },
  onExpired: () => {
    localStorage.removeItem(adminTokenKey)
    localStorage.removeItem(refreshTokenKey)
  }
}

export function setSessionHooks(hooks: Partial<SessionHooks>): void {
  sessionHooks = { ...sessionHooks, ...hooks }
}

/** 用刷新令牌换取新的访问令牌（并发去重）。 */
let refreshPromise: Promise<string | null> | null = null
async function refreshAccessToken(): Promise<string | null> {
  if (!refreshPromise) {
    refreshPromise = (async () => {
      const refreshToken = sessionHooks.getRefreshToken()
      if (!refreshToken) {
        return null
      }
      try {
        const res = await doRequest<LoginResponse>(
          '/api/auth/refresh',
          { method: 'POST', body: JSON.stringify({ refreshToken }) },
          undefined,
          false,
          true,
          true
        )
        sessionHooks.applySession(res.access_token, res.refresh_token ?? null)
        return res.access_token
      } catch {
        sessionHooks.onExpired()
        return null
      } finally {
        refreshPromise = null
      }
    })()
  }
  return refreshPromise
}

async function requestJson<T>(
  path: string,
  init: RequestInit = {},
  adminToken?: string,
  raw = false,
  encrypt = false
): Promise<T> {
  return doRequest<T>(path, init, adminToken, raw, encrypt, false)
}

async function doRequest<T>(
  path: string,
  init: RequestInit,
  adminToken: string | undefined,
  raw: boolean,
  encrypt: boolean,
  retried: boolean
): Promise<T> {
  let body = init.body
  const extraHeaders: Record<string, string> = {}
  if (encrypt && typeof body === 'string') {
    const encrypted = encryptRequest(JSON.parse(body))
    body = encrypted.body
    extraHeaders['encrypt-key'] = encrypted.encryptKey
  }
  const headers: Record<string, string> = {
    Accept: 'application/json',
    // 后端 SecurityConfig 校验 token 内 clientId 与请求头/参数 clientid 一致
    ...(clientId ? { clientid: clientId } : {}),
    ...(body && !(body instanceof FormData) ? { 'Content-Type': 'application/json' } : {}),
    ...extraHeaders,
    ...(init.headers as Record<string, string> | undefined)
  }

  if (adminToken) {
    headers.Authorization = normalizeBearer(adminToken)
  }

  const response = await fetch(`${apiBase}${path}`, { ...init, body, headers })
  // 访问令牌过期：尝试刷新令牌后重试一次
  if (response.status === 401 && adminToken && !retried) {
    const newToken = await refreshAccessToken()
    if (newToken) {
      return doRequest<T>(path, init, newToken, raw, encrypt, true)
    }
    throw new ApiError((await readError(response)) || '登录已过期，请重新登录', 401)
  }
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
  const bodyJson = JSON.parse(text)
  if (raw) {
    return bodyJson as T
  }
  const r = bodyJson as R<T>
  if (r.code !== 200) {
    if (r.code === 401 && adminToken && !retried) {
      const newToken = await refreshAccessToken()
      if (newToken) {
        return doRequest<T>(path, init, newToken, raw, encrypt, true)
      }
    }
    throw new ApiError(r.msg || '请求失败', r.code)
  }
  return r.data
}

/** 退出登录：吊销服务端会话与刷新令牌。 */
export async function logout(adminToken: string, refreshToken?: string): Promise<void> {
  await requestJson<void>(
    '/api/auth/logout',
    {
      method: 'POST',
      body: refreshToken ? JSON.stringify({ refreshToken }) : undefined
    },
    adminToken
  )
}

/** 登录：请求体 RSA+AES 混合加密后发送（对应后端 @ApiEncrypt）。 */
export async function login(username: string, password: string, sliderUuid?: string): Promise<LoginResponse> {
  return requestJson<LoginResponse>(
    '/api/auth/login',
    {
      method: 'POST',
      body: JSON.stringify({
        clientId: import.meta.env.VITE_APP_CLIENT_ID,
        grantType: 'password',
        username,
        password,
        uuid: sliderUuid
      })
    },
    undefined,
    false,
    true
  )
}

/** 滑块拼图（背景图/拼图块为 PNG base64，y 为拼图块纵向位置）。 */
export interface SliderCaptcha {
  uuid: string
  background: string
  puzzle: string
  y: number
}

/** 获取滑块拼图。 */
export function getSliderCaptcha(): Promise<SliderCaptcha> {
  return requestJson<SliderCaptcha>('/api/auth/captcha')
}

/** 校验滑块拼图位置（x 为拼图块横向像素位置），失败抛 ApiError。 */
export async function verifySliderCaptcha(uuid: string, x: number): Promise<void> {
  await requestJson<void>('/api/auth/captcha/verify', {
    method: 'POST',
    body: JSON.stringify({ uuid, x: Math.round(x) })
  })
}

/** 登录后用户信息（getInfo）：用户基本信息 + 角色 + 权限集合。 */
export interface UserInfo {
  user: {
    id?: number
    userName?: string
    nickName?: string
    userType?: string
    email?: string
    phoneNumber?: string
    status?: string
    loginIp?: string
    loginDate?: string
    remark?: string
  }
  permissions: string[]
  roles: string[]
}

/** 权限内路由的 meta 信息。 */
export interface RouterMeta {
  title?: string
  icon?: string
}

/** 权限内路由节点（getRouters 返回的菜单树）。 */
export interface RouterVo {
  name: string
  path: string
  hidden?: boolean
  redirect?: string
  component?: string
  alwaysShow?: boolean
  meta?: RouterMeta
  children?: RouterVo[]
}

/** 获取当前登录用户信息、角色与权限集合。 */
export function getInfo(adminToken: string): Promise<UserInfo> {
  return requestJson<UserInfo>('/api/auth/getInfo', {}, adminToken)
}

/** 获取当前用户权限允许的路由菜单树。 */
export function getRouters(adminToken: string): Promise<RouterVo[]> {
  return requestJson<RouterVo[]>('/api/auth/getRouters', {}, adminToken)
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

/** 拼接查询参数（忽略空值）。 */
function paramString(params?: object): string {
  if (!params) return ''
  const usp = new URLSearchParams()
  for (const [k, v] of Object.entries(params)) {
    if (v !== undefined && v !== null && v !== '') {
      usp.set(k, String(v))
    }
  }
  const s = usp.toString()
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

export function updateProvider(adminToken: string, request: UpdateProviderRequest): Promise<ProviderResponse> {
  return requestJson<ProviderResponse>('/api/admin/providers', {
    method: 'PUT',
    body: JSON.stringify(request)
  }, adminToken)
}

export function deleteProvider(adminToken: string, ids: number[]): Promise<void> {
  return requestJson<void>(`/api/admin/providers/${ids.join(',')}`, {
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

export function updateModel(adminToken: string, request: UpdateModelRequest): Promise<ModelResponse> {
  return requestJson<ModelResponse>('/api/admin/models', {
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

export function listAgents(adminToken: string, query?: PageQuery): Promise<PageResult<AgentResponse>> {
  return requestJson<PageResult<AgentResponse>>(`/api/admin/agents${pageQueryString(query)}`, {}, adminToken)
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

export function listKnowledgeBases(adminToken: string, query?: PageQuery): Promise<PageResult<KnowledgeBaseResponse>> {
  return requestJson<PageResult<KnowledgeBaseResponse>>(`/api/admin/knowledge-bases${pageQueryString(query)}`, {}, adminToken)
}

export function createKnowledgeBase(adminToken: string, request: CreateKnowledgeBaseRequest): Promise<KnowledgeBaseResponse> {
  return requestJson<KnowledgeBaseResponse>('/api/admin/knowledge-bases', {
    method: 'POST',
    body: JSON.stringify(request)
  }, adminToken)
}

export function updateKnowledgeBase(adminToken: string, request: CreateKnowledgeBaseRequest): Promise<KnowledgeBaseResponse> {
  return requestJson<KnowledgeBaseResponse>('/api/admin/knowledge-bases', {
    method: 'PUT',
    body: JSON.stringify(request)
  }, adminToken)
}

export function disableKnowledgeBase(adminToken: string, id: number): Promise<void> {
  return requestJson<void>(`/api/admin/knowledge-bases/${id}`, { method: 'DELETE' }, adminToken)
}

export function listDocuments(adminToken: string, kbId: number, query?: PageQuery): Promise<PageResult<DocumentResponse>> {
  return requestJson<PageResult<DocumentResponse>>(`/api/admin/knowledge-bases/${kbId}/documents${pageQueryString(query)}`, {}, adminToken)
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

// ---- Store instances ----

export function listStoreInstances(
  adminToken: string,
  query?: PageQuery & { name?: string; category?: number; type?: number; status?: number }
): Promise<PageResult<StoreInstanceResponse>> {
  return requestJson<PageResult<StoreInstanceResponse>>(`/api/admin/store-instances${paramString(query)}`, {}, adminToken)
}

export function getStoreInstance(adminToken: string, id: number): Promise<StoreInstanceResponse> {
  return requestJson<StoreInstanceResponse>(`/api/admin/store-instances/${id}`, {}, adminToken)
}

export function createStoreInstance(adminToken: string, request: StoreInstanceRequest): Promise<void> {
  return requestJson<void>('/api/admin/store-instances', {
    method: 'POST',
    body: JSON.stringify(request)
  }, adminToken)
}

export function updateStoreInstance(adminToken: string, request: StoreInstanceRequest): Promise<void> {
  return requestJson<void>('/api/admin/store-instances', {
    method: 'PUT',
    body: JSON.stringify(request)
  }, adminToken)
}

export function deleteStoreInstances(adminToken: string, ids: number[]): Promise<void> {
  return requestJson<void>(`/api/admin/store-instances/${ids.join(',')}`, { method: 'DELETE' }, adminToken)
}

// ---- 资源存储（通用资源存储 plm_resource） ----

export type ResourceBizType = 'GENERAL' | 'AVATAR' | 'ATTACHMENT' | 'DOCUMENT'

export interface ResourceResponse {
  id: number
  storageKey: string
  originalName: string
  fileSize: number
  mimeType?: string | null
  storageType?: string
  accessUrl?: string | null
  bizType?: string
  bizId?: number | null
  createdBy?: number | null
  createDt?: string | null
  updateDt?: string | null
}

export interface ResourceRequest {
  id: number
  bizType?: string
  bizId?: number
}

/** 分页查询资源列表：文件名模糊 + 业务类型 + 创建时间区间（params[beginTime/endTime]）。 */
export function listResources(
  adminToken: string,
  query?: PageQuery & { originalName?: string; bizType?: string; beginTime?: string; endTime?: string }
): Promise<PageResult<ResourceResponse>> {
  const usp = new URLSearchParams()
  if (query) {
    for (const [k, v] of Object.entries(query)) {
      if (v === undefined || v === null || v === '' || k === 'beginTime' || k === 'endTime') continue
      usp.set(k, String(v))
    }
    if (query.beginTime) usp.set('params[beginTime]', query.beginTime)
    if (query.endTime) usp.set('params[endTime]', query.endTime)
  }
  const qs = usp.toString()
  return requestJson<PageResult<ResourceResponse>>(`/api/admin/resources${qs ? `?${qs}` : ''}`, {}, adminToken)
}

/** 上传文件（multipart：file + 可选 bizType/bizId）。 */
export async function uploadResource(
  adminToken: string,
  file: File,
  options?: { bizType?: ResourceBizType; bizId?: number }
): Promise<ResourceResponse> {
  const formData = new FormData()
  formData.append('file', file)
  if (options?.bizType) formData.append('bizType', options.bizType)
  if (options?.bizId !== undefined) formData.append('bizId', String(options.bizId))
  return requestJson<ResourceResponse>('/api/admin/resources/upload', {
    method: 'POST',
    body: formData
  }, adminToken)
}

/** 修改资源元数据（业务类型 / 关联业务ID）。 */
export function updateResource(adminToken: string, request: ResourceRequest): Promise<void> {
  return requestJson<void>('/api/admin/resources', {
    method: 'PUT',
    body: JSON.stringify(request)
  }, adminToken)
}

/** 批量删除资源（物理删除文件与记录）。 */
export function deleteResources(adminToken: string, ids: number[]): Promise<void> {
  return requestJson<void>(`/api/admin/resources/${ids.join(',')}`, { method: 'DELETE' }, adminToken)
}

/** 带鉴权头拉取资源文件内容（预览/下载共用），返回 Blob。 */
async function fetchResourceBlob(adminToken: string, path: string): Promise<Blob> {
  const headers: Record<string, string> = {
    // 后端 SecurityConfig 校验 token 内 clientId 与请求头/参数 clientid 一致
    ...(clientId ? { clientid: clientId } : {}),
    Authorization: normalizeBearer(adminToken)
  }
  const response = await fetch(`${apiBase}${path}`, { headers })
  // 访问令牌过期：尝试刷新令牌后重试一次
  if (response.status === 401 && adminToken) {
    const newToken = await refreshAccessToken()
    if (newToken) {
      return fetchResourceBlob(newToken, path)
    }
  }
  if (!response.ok) {
    throw new Error(await readError(response))
  }
  // 后端业务异常（登录失效/权限不足等）以 R JSON + HTTP 200 返回，
  // 识别后抛出，避免把错误体当作文件内容（预览新标签页/下载得到 JSON）
  if ((response.headers.get('content-type') ?? '').includes('application/json')) {
    const text = await response.text()
    let r: R<unknown> | null = null
    try {
      r = JSON.parse(text) as R<unknown>
    } catch {
      r = null
    }
    if (r && typeof r.code === 'number' && r.code !== 200) {
      throw new ApiError(r.msg || '请求失败', r.code)
    }
    return new Blob([text], { type: response.headers.get('content-type') ?? 'application/json' })
  }
  return response.blob()
}

/** 预览文件（inline 内容）。 */
export function previewResource(adminToken: string, id: number): Promise<Blob> {
  return fetchResourceBlob(adminToken, `/api/admin/resources/${id}/preview`)
}

/** 下载文件（attachment 内容）。 */
export function downloadResource(adminToken: string, id: number): Promise<Blob> {
  return fetchResourceBlob(adminToken, `/api/admin/resources/${id}/download`)
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
  createdAt?: string | null
  updatedAt?: string | null
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
  id: number
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

export function listMcpServers(
  adminToken: string,
  query?: PageQuery & { name?: string; transportType?: string; status?: string }
): Promise<PageResult<McpServerResponse>> {
  return requestJson<PageResult<McpServerResponse>>(`/api/admin/mcp-servers${paramString(query)}`, {}, adminToken)
}

export function createMcpServer(adminToken: string, request: CreateMcpServerRequest): Promise<McpServerResponse> {
  return requestJson<McpServerResponse>('/api/admin/mcp-servers', {
    method: 'POST',
    body: JSON.stringify(request)
  }, adminToken)
}

export function updateMcpServer(adminToken: string, request: UpdateMcpServerRequest): Promise<McpServerResponse> {
  return requestJson<McpServerResponse>('/api/admin/mcp-servers', {
    method: 'PUT',
    body: JSON.stringify(request)
  }, adminToken)
}

export function disableMcpServer(adminToken: string, id: number): Promise<void> {
  return requestJson<void>(`/api/admin/mcp-servers/${id}/disable`, { method: 'PUT' }, adminToken)
}

export function enableMcpServer(adminToken: string, id: number): Promise<void> {
  return requestJson<void>(`/api/admin/mcp-servers/${id}/enable`, { method: 'PUT' }, adminToken)
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

export function listSkills(
  adminToken: string,
  query?: PageQuery & { name?: string; status?: string }
): Promise<PageResult<SkillResponse>> {
  return requestJson<PageResult<SkillResponse>>(`/api/admin/skills${paramString(query)}`, {}, adminToken)
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
  return requestJson<void>(`/api/admin/skills/${id}/disable`, { method: 'PUT' }, adminToken)
}

export function enableSkill(adminToken: string, id: number): Promise<void> {
  return requestJson<void>(`/api/admin/skills/${id}/enable`, { method: 'PUT' }, adminToken)
}

export function listSkillVersions(adminToken: string, id: number): Promise<PageResult<SkillVersionResponse>> {
  return requestJson<PageResult<SkillVersionResponse>>(`/api/admin/skills/${id}/versions`, {}, adminToken)
}

export function publishSkillVersion(adminToken: string, id: number, versionId: number): Promise<SkillResponse> {
  return requestJson<SkillResponse>(`/api/admin/skills/${id}/versions/${versionId}/publish`, {
    method: 'POST'
  }, adminToken)
}

export function offlineSkillVersion(adminToken: string, id: number, versionId: number): Promise<SkillResponse> {
  return requestJson<SkillResponse>(`/api/admin/skills/${id}/versions/${versionId}/offline`, {
    method: 'POST'
  }, adminToken)
}

export interface SkillUsageResponse {
  count: number
}

export function getSkillUsage(adminToken: string, id: number): Promise<SkillUsageResponse> {
  return requestJson<SkillUsageResponse>(`/api/admin/skills/${id}/usage`, {}, adminToken)
}

export function rollbackSkill(adminToken: string, id: number, versionId: number): Promise<SkillResponse> {
  return requestJson<SkillResponse>(`/api/admin/skills/${id}/rollback`, {
    method: 'POST',
    body: JSON.stringify({ versionId })
  }, adminToken)
}

export interface SkillFileResponse {
  filePath: string
  content: string
}

export interface SkillEditorResponse {
  skill: SkillResponse
  versionId: number
  version: number
  files: SkillFileResponse[]
}

export function createSkillOnline(adminToken: string, name: string, description: string): Promise<SkillResponse> {
  return requestJson<SkillResponse>('/api/admin/skills/online', {
    method: 'POST',
    body: JSON.stringify({ name, description })
  }, adminToken)
}

export function getSkillEditor(adminToken: string, id: number): Promise<SkillEditorResponse> {
  return requestJson<SkillEditorResponse>(`/api/admin/skills/${id}/editor`, {}, adminToken)
}

export function createSkillFile(adminToken: string, id: number, filePath: string, content: string): Promise<void> {
  return requestJson<void>(`/api/admin/skills/${id}/files`, {
    method: 'POST',
    body: JSON.stringify({ filePath, content })
  }, adminToken)
}

export function updateSkillFile(adminToken: string, id: number, filePath: string, content: string): Promise<void> {
  return requestJson<void>(`/api/admin/skills/${id}/files`, {
    method: 'PUT',
    body: JSON.stringify({ filePath, content })
  }, adminToken)
}

export function deleteSkillFile(adminToken: string, id: number, filePath: string): Promise<void> {
  return requestJson<void>(`/api/admin/skills/${id}/files?path=${encodeURIComponent(filePath)}`, { method: 'DELETE' }, adminToken)
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

export function listModelOptions(adminToken: string, type?: 'CHAT' | 'EMBEDDING' | 'RERANK'): Promise<OptionResponse[]> {
  return requestJson<OptionResponse[]>(`/api/admin/models/options${type ? `?type=${type}` : ''}`, {}, adminToken)
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

export interface Role {
  id: number
  roleName: string
  roleKey: string
  roleSort: number
  dataScope?: string | null
  menuCheckStrictly?: boolean | null
  status: string
  remark?: string | null
  flag?: boolean
  createdAt?: string | null
  updatedAt?: string | null
}

export interface RoleForm {
  id?: number
  roleName: string
  roleKey: string
  roleSort: number
  dataScope?: string
  menuCheckStrictly?: boolean
  status?: string
  remark?: string
  menuIds?: number[]
}

export interface UserAccount {
  id: number
  userName: string
  nickName?: string | null
  userType?: string | null
  email?: string | null
  phoneNumber?: string | null
  gender?: string | null
  status?: string | null
  loginIp?: string | null
  loginDate?: string | null
  remark?: string | null
  createdAt?: string | null
  updatedAt?: string | null
  roles?: Role[] | null
  roleIds?: number[] | null
}

export interface UserForm {
  id?: number
  userName: string
  nickName: string
  password?: string
  phoneNumber?: string
  email?: string
  gender?: string
  status?: string
  remark?: string
  roleIds?: number[]
}

export interface UserDetail {
  user: UserAccount
  roleIds: number[]
  roles: Role[]
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

// ---- 用户管理（Plm 契约） ----

export function listUsers(adminToken: string, query?: PageQuery & { userName?: string; phoneNumber?: string; status?: string }): Promise<PageResult<UserAccount>> {
  return requestJson<PageResult<UserAccount>>(`/api/admin/users/list${paramString(query)}`, {}, adminToken)
}

export function getUserDetail(adminToken: string, userId: number): Promise<UserDetail> {
  return requestJson<UserDetail>(`/api/admin/users/${userId}`, {}, adminToken)
}

export function createUser(adminToken: string, form: UserForm): Promise<void> {
  return requestJson<void>('/api/admin/users', {
    method: 'POST',
    body: JSON.stringify(form)
  }, adminToken)
}

export function updateUser(adminToken: string, form: UserForm): Promise<void> {
  return requestJson<void>('/api/admin/users', {
    method: 'PUT',
    body: JSON.stringify(form)
  }, adminToken)
}

export function deleteUsers(adminToken: string, ids: number[]): Promise<void> {
  return requestJson<void>(`/api/admin/users/${ids.join(',')}`, { method: 'DELETE' }, adminToken)
}

/** 重置密码（接口带 @ApiEncrypt，请求体需加密）。 */
export function resetUserPwd(adminToken: string, id: number, password: string): Promise<void> {
  return requestJson<void>('/api/admin/users/resetPwd', {
    method: 'PUT',
    body: JSON.stringify({ id, password })
  }, adminToken, false, true)
}

export function changeUserStatus(adminToken: string, id: number, status: string): Promise<void> {
  return requestJson<void>('/api/admin/users/changeStatus', {
    method: 'PUT',
    body: JSON.stringify({ id, status })
  }, adminToken)
}

export function unlockUser(adminToken: string, id: number): Promise<void> {
  return requestJson<void>(`/api/admin/users/unlock/${id}`, {}, adminToken)
}

// ---- 角色管理（Plm 契约） ----

export function listRoles(adminToken: string, query?: PageQuery & { roleName?: string; roleKey?: string; status?: string }): Promise<PageResult<Role>> {
  return requestJson<PageResult<Role>>(`/api/admin/roles/list${paramString(query)}`, {}, adminToken)
}

export function getRole(adminToken: string, id: number): Promise<Role> {
  return requestJson<Role>(`/api/admin/roles/${id}`, {}, adminToken)
}

export function createRole(adminToken: string, form: RoleForm): Promise<void> {
  return requestJson<void>('/api/admin/roles', {
    method: 'POST',
    body: JSON.stringify(form)
  }, adminToken)
}

export function updateRole(adminToken: string, form: RoleForm): Promise<void> {
  return requestJson<void>('/api/admin/roles', {
    method: 'PUT',
    body: JSON.stringify(form)
  }, adminToken)
}

/** 角色权限分配入参（菜单 + 数据权限）。 */
export interface RolePermissionForm {
  id: number
  menuIds: number[]
  dataScope?: string
  menuCheckStrictly?: boolean
}

export function updateRolePermission(adminToken: string, form: RolePermissionForm): Promise<void> {
  return requestJson<void>('/api/admin/roles/permission', {
    method: 'PUT',
    body: JSON.stringify(form)
  }, adminToken)
}

export function changeRoleStatus(adminToken: string, id: number, status: string): Promise<void> {
  return requestJson<void>('/api/admin/roles/changeStatus', {
    method: 'PUT',
    body: JSON.stringify({ id, status })
  }, adminToken)
}

export function deleteRoles(adminToken: string, ids: number[]): Promise<void> {
  return requestJson<void>(`/api/admin/roles/${ids.join(',')}`, { method: 'DELETE' }, adminToken)
}

/** 角色下拉选项（系统全部正常角色）。 */
export function listRoleOptions(adminToken: string): Promise<Role[]> {
  return requestJson<Role[]>('/api/admin/roles/optionselect', {}, adminToken)
}

// ---- 菜单管理（Plm 契约） ----

export interface Menu {
  id: number
  menuName: string
  parentId?: number | null
  orderNum?: number | null
  path?: string | null
  component?: string | null
  queryParam?: string | null
  isFrame?: string | null // 'Y' 外链 | 'N' 非外链
  isCache?: string | null // 'Y' 缓存 | 'N' 不缓存
  menuType: string // 'M' 目录 | 'C' 菜单 | 'F' 按钮
  visible?: string | null // '0' 显示 | '1' 隐藏
  status?: string | null // '0' 正常 | '1' 停用
  perms?: string | null
  icon?: string | null
  remark?: string | null
}

export interface MenuForm {
  id?: number
  menuName: string
  parentId?: number
  orderNum?: number
  path?: string
  component?: string
  queryParam?: string
  isFrame?: string // 'Y' 外链 | 'N' 非外链
  isCache?: string // 'Y' 缓存 | 'N' 不缓存
  menuType: string
  visible?: string
  status?: string
  perms?: string
  icon?: string
  remark?: string
}

/** 菜单下拉树节点（后端 hutool Tree：name 序列化为 label）。 */
export interface MenuTreeNode {
  id?: number | null
  parentId?: number | null
  label?: string
  weight?: number | null
  children?: MenuTreeNode[]
}

export interface RoleMenuTreeSelect {
  checkedKeys: number[]
  menus: MenuTreeNode[]
}

export function listMenus(adminToken: string, query?: { menuName?: string; status?: string }): Promise<Menu[]> {
  return requestJson<Menu[]>(`/api/admin/menus/list${paramString(query)}`, {}, adminToken)
}

export function getMenu(adminToken: string, id: number): Promise<Menu> {
  return requestJson<Menu>(`/api/admin/menus/${id}`, {}, adminToken)
}

export function createMenu(adminToken: string, form: MenuForm): Promise<void> {
  return requestJson<void>('/api/admin/menus', {
    method: 'POST',
    body: JSON.stringify(form)
  }, adminToken)
}

export function updateMenu(adminToken: string, form: MenuForm): Promise<void> {
  return requestJson<void>('/api/admin/menus', {
    method: 'PUT',
    body: JSON.stringify(form)
  }, adminToken)
}

export function deleteMenu(adminToken: string, id: number): Promise<void> {
  return requestJson<void>(`/api/admin/menus/${id}`, { method: 'DELETE' }, adminToken)
}

export function deleteMenusCascade(adminToken: string, ids: number[]): Promise<void> {
  return requestJson<void>(`/api/admin/menus/cascade/${ids.join(',')}`, { method: 'DELETE' }, adminToken)
}

/** 角色菜单分配树（checkedKeys 为已选中菜单 ID）。 */
export function roleMenuTreeSelect(adminToken: string, roleId: number): Promise<RoleMenuTreeSelect> {
  return requestJson<RoleMenuTreeSelect>(`/api/admin/menus/roleMenuTreeselect/${roleId}`, {}, adminToken)
}

// ---- 客户端管理（Plm 契约） ----

export interface Client {
  id: number
  clientId: string
  clientKey: string
  clientSecret?: string | null
  grantTypeList?: string[] | null
  grantType?: string | null
  deviceType?: string | null
  accessPath?: string | null
  accessPathList?: string[] | null
  ipWhitelist?: string | null
  ipWhitelistList?: string[] | null
  activeTimeout?: number | null
  timeout?: number | null
  status?: string | null
}

export interface ClientForm {
  id?: number
  clientKey: string
  clientSecret: string
  grantTypeList: string[]
  deviceType?: string
  accessPath?: string
  ipWhitelist?: string
  activeTimeout?: number
  timeout?: number
  status?: string
}

export function listClients(adminToken: string, query?: PageQuery & { clientKey?: string; status?: string }): Promise<PageResult<Client>> {
  return requestJson<PageResult<Client>>(`/api/admin/client/list${paramString(query)}`, {}, adminToken)
}

export function getClient(adminToken: string, id: number): Promise<Client> {
  return requestJson<Client>(`/api/admin/client/${id}`, {}, adminToken)
}

export function createClient(adminToken: string, form: ClientForm): Promise<void> {
  return requestJson<void>('/api/admin/client', {
    method: 'POST',
    body: JSON.stringify(form)
  }, adminToken)
}

export function updateClient(adminToken: string, form: ClientForm): Promise<void> {
  return requestJson<void>('/api/admin/client', {
    method: 'PUT',
    body: JSON.stringify(form)
  }, adminToken)
}

export function changeClientStatus(adminToken: string, clientId: string, status: string): Promise<void> {
  return requestJson<void>('/api/admin/client/changeStatus', {
    method: 'PUT',
    body: JSON.stringify({ clientId, status })
  }, adminToken)
}

export function deleteClients(adminToken: string, ids: number[]): Promise<void> {
  return requestJson<void>(`/api/admin/client/${ids.join(',')}`, { method: 'DELETE' }, adminToken)
}

// ---- 日志（登录日志 / 操作日志，仅列表与清理） ----

export interface LoginLog {
  id: number
  userName?: string | null
  clientKey?: string | null
  deviceType?: string | null
  ipaddr?: string | null
  loginLocation?: string | null
  browser?: string | null
  os?: string | null
  status?: string | null // '0' 成功 | '1' 失败
  msg?: string | null
  loginTime?: string | null
}

export interface OperLog {
  id: number
  title?: string | null
  clientKey?: string | null
  businessType?: number | null
  method?: string | null
  requestMethod?: string | null
  operatorType?: number | null
  operName?: string | null
  userId?: number | null
  deviceType?: string | null
  browser?: string | null
  os?: string | null
  operUrl?: string | null
  operIp?: string | null
  operLocation?: string | null
  operParam?: string | null
  jsonResult?: string | null
  status?: number | null // 0 成功 | 1 失败
  errorMsg?: string | null
  operTime?: string | null
  costTime?: number | null
}

export function listLoginLogs(adminToken: string, query?: PageQuery & { userName?: string; ipaddr?: string; status?: string }): Promise<PageResult<LoginLog>> {
  return requestJson<PageResult<LoginLog>>(`/api/admin/login/log/list${paramString(query)}`, {}, adminToken)
}

export function deleteLoginLogs(adminToken: string, ids: number[]): Promise<void> {
  return requestJson<void>(`/api/admin/login/log/${ids.join(',')}`, { method: 'DELETE' }, adminToken)
}

export function cleanLoginLogs(adminToken: string): Promise<void> {
  return requestJson<void>('/api/admin/login/log/clean', { method: 'DELETE' }, adminToken)
}

export function unlockLoginUser(adminToken: string, userName: string): Promise<void> {
  return requestJson<void>(`/api/admin/login/log/unlock/${encodeURIComponent(userName)}`, {}, adminToken)
}

export function listOperLogs(adminToken: string, query?: PageQuery & { title?: string; operName?: string; businessType?: number; status?: number }): Promise<PageResult<OperLog>> {
  return requestJson<PageResult<OperLog>>(`/api/admin/oper/log/list${paramString(query)}`, {}, adminToken)
}

export function deleteOperLogs(adminToken: string, ids: number[]): Promise<void> {
  return requestJson<void>(`/api/admin/oper/log/${ids.join(',')}`, { method: 'DELETE' }, adminToken)
}

export function cleanOperLogs(adminToken: string): Promise<void> {
  return requestJson<void>('/api/admin/oper/log/clean', { method: 'DELETE' }, adminToken)
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

export function listUsage(adminToken: string, params: { agentKey?: string; from?: string; to?: string; pageNum?: number; pageSize?: number } = {}): Promise<PageResult<UsageRecord>> {
  const query = new URLSearchParams()
  if (params.agentKey) query.set('agentKey', params.agentKey)
  if (params.from) query.set('from', params.from)
  if (params.to) query.set('to', params.to)
  if (params.pageNum) query.set('pageNum', String(params.pageNum))
  if (params.pageSize) query.set('pageSize', String(params.pageSize))
  const qs = query.toString()
  return requestJson<PageResult<UsageRecord>>(`/api/admin/usage${qs ? `?${qs}` : ''}`, {}, adminToken)
}

export function getStatsOverview(adminToken: string): Promise<OverviewResponse> {
  return requestJson<OverviewResponse>('/api/admin/stats/overview', {}, adminToken)
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

export function listWorkflows(
  adminToken: string,
  query?: PageQuery & { name?: string; workflowKey?: string; status?: string }
): Promise<PageResult<WorkflowResponse>> {
  return requestJson<PageResult<WorkflowResponse>>(`/api/admin/workflows${paramString(query)}`, {}, adminToken)
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
