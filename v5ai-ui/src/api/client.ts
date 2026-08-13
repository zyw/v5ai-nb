export interface LoginResponse {
  tokenType: string
  token: string
}

export interface ProviderResponse {
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

export interface ModelResponse {
  providerKey: string
  modelKey: string
  type: string
  credentialsCiphertext: string
}

export interface CreateModelRequest {
  providerKey: string
  modelKey: string
  type: string
  credentials: string
}

export interface ApplicationResponse {
  appKey: string
  name: string
  status: string
  modelId: number
  publishedVersion?: number | null
}

export interface CreateApplicationRequest {
  appKey: string
  name: string
  modelId: number
}

export interface ChatRequest {
  conversationId?: string
  query: string
}

export interface AgentRunResult {
  runId: string
  answer: string
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
    ...(init.body ? { 'Content-Type': 'application/json' } : {}),
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

export function listProviders(adminToken: string): Promise<ProviderResponse[]> {
  return requestJson<ProviderResponse[]>('/api/admin/providers', {}, adminToken)
}

export function createProvider(adminToken: string, request: CreateProviderRequest): Promise<ProviderResponse> {
  return requestJson<ProviderResponse>('/api/admin/providers', {
    method: 'POST',
    body: JSON.stringify(request)
  }, adminToken)
}

export function listModels(adminToken: string): Promise<ModelResponse[]> {
  return requestJson<ModelResponse[]>('/api/admin/models', {}, adminToken)
}

export function createModel(adminToken: string, request: CreateModelRequest): Promise<ModelResponse> {
  return requestJson<ModelResponse>('/api/admin/models', {
    method: 'POST',
    body: JSON.stringify(request)
  }, adminToken)
}

export function listApplications(adminToken: string): Promise<ApplicationResponse[]> {
  return requestJson<ApplicationResponse[]>('/api/admin/applications', {}, adminToken)
}

export function createApplication(adminToken: string, request: CreateApplicationRequest): Promise<ApplicationResponse> {
  return requestJson<ApplicationResponse>('/api/admin/applications', {
    method: 'POST',
    body: JSON.stringify(request)
  }, adminToken)
}

export function publishApplication(adminToken: string, appKey: string, description: string): Promise<ApplicationResponse> {
  return requestJson<ApplicationResponse>(`/api/admin/applications/${encodeURIComponent(appKey)}/publish`, {
    method: 'POST',
    body: JSON.stringify({ description })
  }, adminToken)
}

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
