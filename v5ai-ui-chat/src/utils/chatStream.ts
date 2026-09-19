import type { AttachmentRef, KbHitResponse, RunUsage } from '../api/client'

/**
 * 对话消息（流式）。知识库问答 tab 与智能体「预览与调试」共用同一套渲染状态：
 * 思考与回答分流、引用列表、错误、进行中标记。
 */
export interface ChatMsg {
  role: 'user' | 'assistant'
  content: string
  /** 模型思考内容（REASONING_DELTA 累积），与 content 分开展示 */
  reasoning?: string
  /** 思考折叠是否展开：初始展开，回答开始后自动收起一次；之后用户手动开合不再干预 */
  reasoningOpen?: boolean
  /** 本次回答引用的切片（RETRIEVAL 载荷解析而来） */
  citations?: KbHitResponse[]
  error?: string
  loading?: boolean
  /** 用户消息携带的图片：已解析为可直接用于 <img> 的地址（本地 blob: 或后端资源 blob:） */
  attachments?: string[]
  /** 随消息提交的附件资源引用：重新生成时原样重发，不必重新上传 */
  attachmentRefs?: AttachmentRef[]
  /** 本次运行的用量与用时（RUN_COMPLETED 载荷；被停止或失败的回答没有） */
  usage?: RunUsage
  /** 这条回答是被调用方主动停止的（区别于运行失败） */
  stopped?: boolean
  /**
   * 持久化主键：用户消息来自历史接口的 messageId 或本轮 RUN_STARTED 载荷的 userMessageId。
   * 「重新生成」以**该轮提问**的 id 为锚点，取不到时按钮不可用。
   *
   * 一律按**字符串**存：主键是 19 位雪花号，超出 JS 安全整数，落进 number 就会被四舍五入
   * （…417794 → …417800），再回传时服务端查无此消息（404「提问不存在」）。
   */
  messageId?: string
}

/**
 * 取出 RuntimeEvent 包体里的 payload。
 *
 * SSE 的 `data:` 是整个 RuntimeEvent（`{runId,type,payload,createdAt}`），payload 恒为字符串：
 * 文本增量就是原文，而 RETRIEVAL / RUN_STARTED / RUN_COMPLETED 的 payload 本身又是一段 JSON 串，
 * 所以这些载荷必须**先解包、再各自 JSON.parse 一次**。少了这一步就会解成一个只有
 * runId/type/createdAt 的信封对象，于是静默解析失败：用量、引用、可重新生成的锚点当场都不出来，
 * 只有重新拉历史（历史接口给的是已经解好的对象）才正常。非包体（异常路径）时原样返回。
 */
function unwrapPayload(data: string): string {
  try {
    const payload = (JSON.parse(data) as { payload?: unknown }).payload
    return typeof payload === 'string' ? payload : data
  } catch {
    return data
  }
}

/** 解析 RETRIEVAL 载荷：命中数组 JSON 串（两条链路同构，故解析器共用）。 */
export function parseHits(data: string): KbHitResponse[] | undefined {
  if (!data) return undefined
  try {
    const parsed = JSON.parse(unwrapPayload(data))
    return Array.isArray(parsed) ? (parsed as KbHitResponse[]) : undefined
  } catch {
    return undefined
  }
}

/**
 * 合并两批引用：一次运行里智能调用可能检索多次（每次 rag_search 之后追加一条 RETRIEVAL），
 * 服务端按 (knowledgeBaseId, documentId, chunkIndex) **保序去重**后落库（见 docs/adr/0009）。
 * 实时侧必须用同一口径累积，否则「实时看到的」与「刷新后回看的」对不上。
 *
 * <p>保留**首次命中**：顺序即模型实际看到的顺序，相似度也取首次那一次。</p>
 */
export function mergeHits(
  prev: KbHitResponse[] | undefined,
  next: KbHitResponse[] | undefined
): KbHitResponse[] | undefined {
  if (!next || next.length === 0) return prev
  if (!prev || prev.length === 0) return next
  const seen = new Set(prev.map(hitKey))
  const merged = [...prev]
  for (const hit of next) {
    const key = hitKey(hit)
    if (!seen.has(key)) {
      seen.add(key)
      merged.push(hit)
    }
  }
  return merged
}

/** 同一切片的判定键：与后端 accumulateCitations 一致（载荷里没有切片的向量标识）。 */
function hitKey(hit: KbHitResponse): string {
  return `${hit.knowledgeBaseId}:${hit.documentId}:${hit.chunkIndex}`
}

/**
 * 解析 RUN_COMPLETED 载荷里的用量；载荷为空或不是用量对象时返回 undefined
 * （知识库问答链路等不携带用量，此时界面上就不显示这一行）。
 */
export function parseUsage(data: string): RunUsage | undefined {
  if (!data) return undefined
  try {
    return normalizeUsage(JSON.parse(unwrapPayload(data)))
  } catch {
    return undefined
  }
}

/**
 * 归一化用量对象：SSE 载荷与历史消息的 usage 都走这里，避免两处各写一套判空。
 * 缺 totalTokens 时按两项之和补齐（旧数据/局部载荷都能显示）。
 */
export function normalizeUsage(value: unknown): RunUsage | undefined {
  if (!value || typeof value !== 'object') return undefined
  const raw = value as Partial<RunUsage>
  const promptTokens = typeof raw.promptTokens === 'number' ? raw.promptTokens : 0
  const completionTokens = typeof raw.completionTokens === 'number' ? raw.completionTokens : 0
  if (typeof raw.promptTokens !== 'number' && typeof raw.completionTokens !== 'number') return undefined
  return {
    promptTokens,
    completionTokens,
    totalTokens: typeof raw.totalTokens === 'number' ? raw.totalTokens : promptTokens + completionTokens,
    durationMs: typeof raw.durationMs === 'number' ? raw.durationMs : 0
  }
}

/**
 * 解析 RUN_STARTED 载荷里的本轮提问消息 id（{@code {"userMessageId":n}}）。
 * 服务端在推送 RUN_STARTED 之前就已落库，因此刚生成的那一轮不用刷新历史也能重新生成。
 */
export function parseUserMessageId(data: string): string | undefined {
  if (!data) return undefined
  try {
    const parsed = JSON.parse(unwrapPayload(data)) as { userMessageId?: unknown } | null
    return toMessageId(parsed?.userMessageId)
  } catch {
    return undefined
  }
}

/**
 * 把服务端给的消息 id 归一成**字符串**，任何地方都不要把它转成 number。
 *
 * 消息主键是 19 位雪花号，超出 JS 的 Number.MAX_SAFE_INTEGER：一旦落进 number 就会被
 * 四舍五入（…417794 → …417800），「重新生成」把它回传时服务端按这个 id 查不到那条提问，
 * 直接 404「提问不存在」。服务端两条路给的都是字符串（历史接口的 messageId 走 Jackson 的
 * BigNumberSerializer，RUN_STARTED 载荷显式转字符串）；这里再兜一层 number，
 * 兼容小 id 或旧版本服务端。
 */
export function toMessageId(raw: unknown): string | undefined {
  if (typeof raw === 'string') return raw.trim() || undefined
  if (typeof raw === 'number' && Number.isFinite(raw)) return String(raw)
  return undefined
}

/** 首个 token 之前：还没有任何思考内容可展示。 */
export function awaitingFirstToken(m: ChatMsg): boolean {
  return !!m.loading && !m.reasoning && !m.content && !m.error
}

/** 思考仍在进行：回答（或错误）尚未开始。 */
export function isThinking(m: ChatMsg): boolean {
  return !!m.loading && !m.content && !m.error
}

/** 思考折叠开合（受控）：用户点击时同步回消息状态。 */
export function onReasoningToggle(m: ChatMsg, names: string | number | Array<string | number> | null): void {
  m.reasoningOpen = Array.isArray(names) ? names.includes('thinking') : names === 'thinking'
}
