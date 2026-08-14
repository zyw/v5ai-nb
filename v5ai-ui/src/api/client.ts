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

export interface ApplicationResponse {
  appKey: string
  name: string
  description?: string | null
  status: string
  modelId: number
  publishedVersion?: number | null
}

export interface CreateApplicationRequest {
  appKey: string
  name: string
  description?: string
  modelId: number
}

export interface UpdateApplicationRequest {
  name?: string
  description?: string
  modelId?: number
}

export interface ApplicationVersionResponse {
  appKey: string
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
  appKey: string
  apiKey: string
}

export interface ApiKeyRecord {
  appKey: string
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
  return response.json()
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

// ---- Application ----

export function listApplications(adminToken: string): Promise<ApplicationResponse[]> {
  return requestJson<ApplicationResponse[]>('/api/admin/applications', {}, adminToken)
}

export function createApplication(adminToken: string, request: CreateApplicationRequest): Promise<ApplicationResponse> {
  return requestJson<ApplicationResponse>('/api/admin/applications', {
    method: 'POST',
    body: JSON.stringify(request)
  }, adminToken)
}

export function updateApplication(adminToken: string, appKey: string, request: UpdateApplicationRequest): Promise<ApplicationResponse> {
  return requestJson<ApplicationResponse>(`/api/admin/applications/${encodeURIComponent(appKey)}`, {
    method: 'PUT',
    body: JSON.stringify(request)
  }, adminToken)
}

export function disableApplication(adminToken: string, appKey: string): Promise<void> {
  return requestJson<void>(`/api/admin/applications/${encodeURIComponent(appKey)}`, {
    method: 'DELETE'
  }, adminToken)
}

export function listApplicationVersions(adminToken: string, appKey: string): Promise<ApplicationVersionResponse[]> {
  return requestJson<ApplicationVersionResponse[]>(`/api/admin/applications/${encodeURIComponent(appKey)}/versions`, {}, adminToken)
}

export function publishApplication(adminToken: string, appKey: string, description: string): Promise<ApplicationResponse> {
  return requestJson<ApplicationResponse>(`/api/admin/applications/${encodeURIComponent(appKey)}/publish`, {
    method: 'POST',
    body: JSON.stringify({ description })
  }, adminToken)
}

// ---- Runtime chat ----

export function chat(appKey: string, apiKey: string, request: ChatRequest): Promise<AgentRunResult> {
  return requestJson<AgentRunResult>(`/api/v1/apps/${encodeURIComponent(appKey)}/chat`, {
    method: 'POST',
    headers: {
      Authorization: normalizeBearer(apiKey)
    },
    body: JSON.stringify(request)
  })
}

export async function streamChat(
  appKey: string,
  apiKey: string,
  request: ChatRequest,
  onEvent: (event: string, data: string) => void
): Promise<void> {
  const response = await fetch(`${apiBase}/api/v1/apps/${appKey}/chat/stream`, {
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

export function getConversationMessages(appKey: string, apiKey: string, conversationId: string): Promise<ConversationMessageResponse[]> {
  return requestJson<ConversationMessageResponse[]>(
    `/api/v1/apps/${encodeURIComponent(appKey)}/chat/conversations/${encodeURIComponent(conversationId)}`,
    { headers: { Authorization: normalizeBearer(apiKey) } }
  )
}

export async function resumeConversation(
  appKey: string,
  apiKey: string,
  conversationId: string,
  query: string,
  onEvent: (event: string, data: string) => void
): Promise<void> {
  const response = await fetch(`${apiBase}/api/v1/apps/${appKey}/chat/conversations/${conversationId}/resume`, {
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

export function bindKnowledgeBases(adminToken: string, appKey: string, knowledgeBaseIds: number[]): Promise<void> {
  return requestJson<void>(`/api/admin/applications/${encodeURIComponent(appKey)}/knowledge-bindings`, {
    method: 'POST',
    body: JSON.stringify({ knowledgeBaseIds })
  }, adminToken)
}

export function getKnowledgeBindings(adminToken: string, appKey: string): Promise<number[]> {
  return requestJson<number[]>(`/api/admin/applications/${encodeURIComponent(appKey)}/knowledge-bindings`, {}, adminToken)
}

// ---- API keys ----

export function createApiKey(adminToken: string, appKey: string): Promise<CreateApiKeyResult> {
  return requestJson<CreateApiKeyResult>(`/api/admin/applications/${encodeURIComponent(appKey)}/api-keys`, {
    method: 'POST'
  }, adminToken)
}

export function listApiKeys(adminToken: string): Promise<ApiKeyRecord[]> {
  return requestJson<ApiKeyRecord[]>('/api/admin/api-keys', {}, adminToken)
}

export function deleteApiKey(adminToken: string, appKey: string): Promise<void> {
  return requestJson<void>(`/api/admin/applications/${encodeURIComponent(appKey)}/api-keys`, {
    method: 'DELETE'
  }, adminToken)
}
