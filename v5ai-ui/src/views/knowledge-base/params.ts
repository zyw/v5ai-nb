/**
 * 知识库检索/模型回答参数的共享默认值与工厂（新建页与详情页共用，避免默认值漂移）。
 */
import type { KbModelParamsState, KbSearchParamsState } from './KnowledgeDebugParams.vue'
import type { RagModelParams, RagSearchParams } from '../../api/client'

export const DEFAULT_PROMPT = `你是一位在线知识库问答助手。你的首要任务是根据『参考资料』回答用户问题，这些信息可以帮助你生成更准确的回复；如果参考资料没有覆盖用户问题，请明确说明不知道，不要编造。请使用与用户问题一致的语言作答。

<Documents>`

export function defaultSearchParams(): KbSearchParamsState {
  return {
    resultCount: 20,
    questionRewrite: false,
    rerankEnabled: false,
    rerankModelId: null,
    enterRerankCount: 30,
    thresholdEnabled: false,
    threshold: 0.5,
    fusionStrategy: 'RRF',
    rrfK: 60,
    denseWeight: 0.5
  }
}

export function defaultModelParams(): KbModelParamsState {
  return {
    modelId: null,
    nearbySliceCount: 5,
    prompt: DEFAULT_PROMPT
  }
}

/** 将调试态（可空 modelId / 联合 fusionStrategy）序列化为后端配置契约。 */
export function toRagSearchParams(s: KbSearchParamsState): RagSearchParams {
  return {
    resultCount: s.resultCount,
    rerankEnabled: s.rerankEnabled,
    rerankModelId: s.rerankModelId ?? undefined,
    enterRerankCount: s.enterRerankCount,
    thresholdEnabled: s.thresholdEnabled,
    threshold: s.threshold,
    denseWeight: s.denseWeight,
    fusionStrategy: s.fusionStrategy,
    rrfK: s.rrfK,
    questionRewrite: s.questionRewrite
  }
}

export function toRagModelParams(m: KbModelParamsState): RagModelParams {
  return {
    modelId: m.modelId ?? undefined,
    nearbySliceCount: m.nearbySliceCount,
    prompt: m.prompt
  }
}