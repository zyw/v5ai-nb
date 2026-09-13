import type { KbHitResponse } from '../api/client'

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
