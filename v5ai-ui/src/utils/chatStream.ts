import type { AttachmentRequest, KbHitResponse } from '../api/client'

/**
 * 对话消息（流式）。知识库问答 tab 与智能体「预览与调试」共用同一套渲染状态：
 * 思考与回答分流、引用列表、错误、进行中标记。
 */
export interface ChatMsg {
  role: 'user' | 'assistant'
  content: string
  /** 用户消息携带的图片（本地 objectURL，仅用于展示） */
  images?: string[]
  /** 用户消息的附件引用：重新生成时原样重发，避免「重跑一次图就没了」 */
  attachmentRefs?: AttachmentRequest[]
  /** 模型思考内容（REASONING_DELTA 累积），与 content 分开展示 */
  reasoning?: string
  /** 思考折叠是否展开：初始展开，回答开始后自动收起一次；之后用户手动开合不再干预 */
  reasoningOpen?: boolean
  /** 本次回答引用的切片（RETRIEVAL 载荷解析而来） */
  citations?: KbHitResponse[]
  error?: string
  loading?: boolean
}

/** 解析 RETRIEVAL 载荷：命中数组 JSON 串（两条链路同构，故解析器共用）。 */
export function parseHits(data: string): KbHitResponse[] | undefined {
  try {
    const parsed = JSON.parse(data)
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
