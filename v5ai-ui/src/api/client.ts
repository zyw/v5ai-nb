export interface LoginResponse {
  tokenType: string
  token: string
}

export interface ChatRequest {
  conversationId?: string
  query: string
}

const apiBase = import.meta.env.VITE_API_BASE ?? ''

export async function login(username: string, password: string): Promise<LoginResponse> {
  const response = await fetch(`${apiBase}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password })
  })
  if (!response.ok) {
    throw new Error(`login failed: ${response.status}`)
  }
  return response.json()
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
      Authorization: `Bearer ${apiKey}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(request)
  })

  if (!response.ok || !response.body) {
    throw new Error(`chat stream failed: ${response.status}`)
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
