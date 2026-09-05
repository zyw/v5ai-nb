<script setup lang="ts">
/**
 * 知识库调试参数面板（参考稿左侧两段）：
 * - 检索参数：结果返回数量 / 问题改写 / 重排模型 / 阈值过滤 / 更多参数(融合策略 + RRF K 值)
 * - 模型回答参数（仅问答 tab）：选择模型 / 拼接邻近文本片数量 / 编写 Prompt（可插入 <Documents>）
 *
 * 参数为会话内调试状态（父级持有、tab 间共享、不回写），修改直接落到 props.params 对象。
 */
import { computed } from 'vue'
import {
  NButton,
  NCollapse,
  NCollapseItem,
  NIcon,
  NInput,
  NInputNumber,
  NSelect,
  NSlider,
  NSwitch,
  NTooltip
} from 'naive-ui'
import { CircleQuestionMark } from 'lucide-vue-next'
import type { OptionResponse } from '../../api/client'
import type { SelectOption } from 'naive-ui'

export interface KbSearchParamsState {
  resultCount: number
  questionRewrite: boolean
  /** 重排模型：未接入真实算法，仅保留控件位置（置灰） */
  rerankEnabled: boolean
  thresholdEnabled: boolean
  threshold: number
  fusionStrategy: 'RRF' | 'VECTOR' | 'KEYWORD'
  rrfK: number
}

export interface KbModelParamsState {
  modelId: number | null
  nearbySliceCount: number
  prompt: string
}

const props = defineProps<{
  params: KbSearchParamsState
  modelParams?: KbModelParamsState | null
  chatModels: OptionResponse[]
  /** retrieval=仅检索参数；qa=检索参数 + 模型回答参数 */
  mode: 'retrieval' | 'qa'
}>()

const fusionOptions: SelectOption[] = [
  { label: 'RRF (倒数排名融合)', value: 'RRF' },
  { label: '向量检索', value: 'VECTOR' },
  { label: '关键词检索', value: 'KEYWORD' }
]

const modelOptions = computed<SelectOption[]>(() =>
  props.chatModels.map((m) => ({ label: m.label, value: m.value }) as SelectOption)
)

const hasChatModel = computed(() => props.chatModels.length > 0)
const canRewrite = computed(() => hasChatModel.value && (props.modelParams?.modelId != null || props.chatModels.length > 0))

/** 无对话模型可用时：问题改写开关禁用 */
const rewriteDisabled = computed(() => !canRewrite.value)
</script>

<template>
  <div class="kb-params">
    <!-- 检索参数 -->
    <div class="params-section">
      <div class="params-title">检索参数</div>
      <div class="muted hint">调整检索参数，预览和调试 RAG 命中效果</div>
      <div class="param-row">
        <span class="param-label">
          结果返回数量
          <n-tooltip placement="top">
            <template #trigger><n-icon size="13" class="q-icon"><CircleQuestionMark /></n-icon></template>
            返回的命中数量上限（topK）
          </n-tooltip>
        </span>
        <div class="param-control">
          <n-button size="tiny" quaternary circle @click="params.resultCount = Math.max(1, params.resultCount - 1)">−</n-button>
          <n-input-number v-model:value="params.resultCount" :min="1" :max="100" size="small" style="width: 64px" />
          <n-button size="tiny" quaternary circle @click="params.resultCount = Math.min(100, params.resultCount + 1)">+</n-button>
        </div>
      </div>
      <div class="param-row">
        <span class="param-label">
          问题改写
          <n-tooltip placement="top">
            <template #trigger><n-icon size="13" class="q-icon"><CircleQuestionMark /></n-icon></template>
            用对话模型把问题改写为更适合检索的表达（需可用对话模型）
          </n-tooltip>
        </span>
        <n-switch v-model:value="params.questionRewrite" size="small" :disabled="rewriteDisabled" />
      </div>
      <div class="param-row">
        <span class="param-label">
          重排模型
          <n-tooltip placement="top">
            <template #trigger><n-icon size="13" class="q-icon"><CircleQuestionMark /></n-icon></template>
            重排序能力暂未接入，本开关为占位
          </n-tooltip>
        </span>
        <n-switch v-model:value="params.rerankEnabled" size="small" disabled />
      </div>
      <div class="param-row">
        <span class="param-label">
          阈值过滤
          <n-tooltip placement="top">
            <template #trigger><n-icon size="13" class="q-icon"><CircleQuestionMark /></n-icon></template>
            按向量相似度过滤低相关命中
          </n-tooltip>
        </span>
        <n-switch v-model:value="params.thresholdEnabled" size="small" />
      </div>
      <div v-if="params.thresholdEnabled" class="param-row sub">
        <span class="param-label">相似度阈值</span>
        <div class="slider-wrap">
          <n-slider v-model:value="params.threshold" :min="0" :max="1" :step="0.05" />
          <span class="slider-value">{{ params.threshold.toFixed(2) }}</span>
        </div>
      </div>
      <n-collapse :default-expanded-names="['more']" arrow-placement="right">
        <n-collapse-item name="more" title="更多参数" class="more-item">
          <div class="param-row">
            <span class="param-label">
              融合策略
              <n-tooltip placement="top">
                <template #trigger><n-icon size="13" class="q-icon"><CircleQuestionMark /></n-icon></template>
                向量 + 关键词两路召回结果的融合方式
              </n-tooltip>
            </span>
            <n-select v-model:value="params.fusionStrategy" :options="fusionOptions" size="small" class="fusion-select" />
          </div>
          <div class="param-row">
            <span class="param-label">
              RRF K 值
              <n-tooltip placement="top">
                <template #trigger><n-icon size="13" class="q-icon"><CircleQuestionMark /></n-icon></template>
                倒数排名融合中的平滑系数，越小越重视前列命中
              </n-tooltip>
            </span>
            <div class="param-control">
              <n-button size="tiny" quaternary circle @click="params.rrfK = Math.max(1, params.rrfK - 1)">−</n-button>
              <n-input-number v-model:value="params.rrfK" :min="1" :max="200" size="small" style="width: 64px" />
              <n-button size="tiny" quaternary circle @click="params.rrfK = Math.min(200, params.rrfK + 1)">+</n-button>
            </div>
          </div>
        </n-collapse-item>
      </n-collapse>
    </div>

    <!-- 模型回答参数（仅问答） -->
    <div v-if="mode === 'qa' && modelParams" class="params-section">
      <div class="params-title">模型回答参数</div>
      <div class="muted hint">调整大模型参数，预览和调试模型回答效果</div>
      <div class="param-row">
        <span class="param-label">选择模型</span>
        <n-select
          v-model:value="modelParams.modelId"
          :options="modelOptions"
          placeholder="请选择对话模型"
          filterable
          size="small"
          class="fusion-select"
        />
      </div>
      <div class="param-row">
        <span class="param-label">
          拼接邻近文本片数量
          <n-tooltip placement="top">
            <template #trigger><n-icon size="13" class="q-icon"><CircleQuestionMark /></n-icon></template>
            每个命中切片前后补足的邻近文本片数，让答案上下文更连贯
          </n-tooltip>
        </span>
        <div class="param-control">
          <n-button size="tiny" quaternary circle @click="modelParams.nearbySliceCount = Math.max(0, modelParams.nearbySliceCount - 1)">−</n-button>
          <n-input-number v-model:value="modelParams.nearbySliceCount" :min="0" :max="20" size="small" style="width: 64px" />
          <n-button size="tiny" quaternary circle @click="modelParams.nearbySliceCount = Math.min(20, modelParams.nearbySliceCount + 1)">+</n-button>
        </div>
      </div>
      <div class="param-row vertical">
        <div class="param-label-row">
          <span class="param-label">编写 Prompt</span>
          <n-button
            size="tiny"
            quaternary
            type="primary"
            @click="modelParams.prompt = modelParams.prompt ? modelParams.prompt + '\n\n<Documents>' : '<Documents>'"
          >
            插入 &lt;Documents&gt;
          </n-button>
        </div>
        <div class="muted hint">Prompt 用于定义大模型的人设和回答框架；&lt;Documents&gt; 代表引用 RAG 命中。</div>
        <n-input
          v-model:value="modelParams.prompt"
          type="textarea"
          :autosize="{ minRows: 8, maxRows: 16 }"
          placeholder="输入 Prompt 模板…"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.kb-params {
  display: flex;
  flex-direction: column;
  gap: 16px;
  font-size: 13px;
}

.params-section {
  padding: 10px 12px;
  border-radius: 8px;
}

.params-title {
  font-weight: 600;
  margin-bottom: 2px;
}

.hint {
  color: rgba(120, 120, 120, 0.9);
  margin-bottom: 10px;
}

.param-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 0;
}

.param-row.sub {
  padding-left: 4px;
}

.param-row.vertical {
  flex-direction: column;
  align-items: stretch;
  gap: 6px;
}

.param-label-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.param-label {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--kb-text, rgba(60, 60, 60, 1));
}

.q-icon {
  color: rgba(120, 120, 120, 0.8);
  cursor: help;
}

.param-control {
  display: inline-flex;
  align-items: center;
  gap: 2px;
}

.fusion-select {
  width: 170px;
}

.slider-wrap {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 10px;
  margin-left: 12px;
}

.slider-value {
  font-variant-numeric: tabular-nums;
  width: 34px;
}

.more-item :deep(.n-collapse-item__header) {
  font-size: 13px;
  padding: 4px 0;
}
</style>
