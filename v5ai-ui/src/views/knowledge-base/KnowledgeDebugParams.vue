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
import { CircleQuestionMark,FileSearch2Icon,MessagesSquare } from 'lucide-vue-next'
import type { OptionResponse } from '../../api/client'
import type { SelectOption } from 'naive-ui'

export interface KbSearchParamsState {
  resultCount: number
  questionRewrite: boolean
  /** 重排序能力暂未接入，配置保存备用 */
  rerankEnabled: boolean
  rerankModelId: number | null
  enterRerankCount: number
  thresholdEnabled: boolean
  threshold: number
  fusionStrategy: 'RRF' | 'WEIGHTED_SUM' | 'VECTOR' | 'KEYWORD'
  rrfK: number
  denseWeight: number
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
  rerankModels?: OptionResponse[]
  /** retrieval=仅检索参数；qa=检索参数 + 模型回答参数 */
  mode: 'retrieval' | 'qa'
}>()

const fusionOptions: SelectOption[] = [
  { label: 'RRF (倒数排名融合)', value: 'RRF' },
  { label: '加权求和 (WEIGHTED_SUM)', value: 'WEIGHTED_SUM' },
  // { label: '向量检索 (VECTOR)', value: 'VECTOR' },
  // { label: '关键词检索 (KEYWORD)', value: 'KEYWORD' }
]

const modelOptions = computed<SelectOption[]>(() =>
  props.chatModels.map((m) => ({ label: m.label, value: m.value }) as SelectOption)
)

const rerankOptions = computed<SelectOption[]>(() =>
  (props.rerankModels ?? []).map((m) => ({ label: m.label, value: m.value }) as SelectOption)
)

const hasChatModel = computed(() => props.chatModels.length > 0)
const canRewrite = computed(() => hasChatModel.value && (props.modelParams?.modelId != null || props.chatModels.length > 0))

/** 无对话模型可用时：问题改写开关禁用 */
const rewriteDisabled = computed(() => !canRewrite.value)

/**
 * 拼接邻近文本片数量写入口：清空数字框时 naive-ui 会 emit `null`，直接写回模型会让滑块把
 * `null` 夹成 min（`Math.max(min, null)` ⇒ 0）而与输入框脱钩，故归一化到区间下限。
 */
function writeNearbySliceCount(value: number | null) {
  if (props.modelParams) {
    props.modelParams.nearbySliceCount = value ?? 0
  }
}
</script>

<template>
  <div class="kb-params">
    <div v-if="mode === 'qa' && modelParams" class="params-title">
      <n-icon color="#2f6bff" style="margin-right: 5px"><MessagesSquare/></n-icon>
      知识问答
      <n-tooltip placement="top">
        <template #trigger><n-icon size="13" class="q-icon"><CircleQuestionMark /></n-icon></template>
        适合用RAG进行大模型多轮问答的场景，基于历史对话信息和参考知识，进行专业回答
      </n-tooltip>
    </div>
    <div v-else class="params-title">
      <n-icon color="#2f6bff" style="margin-right: 5px"><FileSearch2Icon/></n-icon>
      知识检索
    </div>
    <!-- 检索参数 -->
    <n-card class="params-section" content-style="padding: 5px;">
      <div class="params-title search-title">检索参数</div>
      <div class="muted hint">调整检索参数，预览和调试RAG命中效果</div>
      <div class="param-row">
        <span class="param-label">
          结果返回数量
          <n-tooltip placement="top">
            <template #trigger><n-icon size="13" class="q-icon"><CircleQuestionMark /></n-icon></template>
            设置检索返回的结果数量
          </n-tooltip>
        </span>
        <div class="param-control">
          <n-input-number v-model:value="params.resultCount" :min="1" :max="100" size="small" style="width: 100px" />
        </div>
      </div>
      <div class="param-row">
        <n-slider v-model:value="params.resultCount" :min="1" :max="100" />
      </div>
      <n-divider class="param-divider" />
      <div class="param-row">
        <span class="param-label">
          问题改写
          <n-tooltip placement="top">
            <template #trigger><n-icon size="13" class="q-icon"><CircleQuestionMark /></n-icon></template>
            对用户问题进行改写优化，提升检索效果
          </n-tooltip>
        </span>
        <n-switch v-model:value="params.questionRewrite" size="small" :disabled="rewriteDisabled" />
      </div>
      <n-divider class="param-divider" />
      <div class="param-row">
        <span class="param-label">
          重排模型
          <n-tooltip placement="top">
            <template #trigger><n-icon size="13" class="q-icon"><CircleQuestionMark /></n-icon></template>
            启用重排模型对检索结果二次排序
          </n-tooltip>
        </span>
        <n-switch v-model:value="params.rerankEnabled" size="small" />
      </div>
      <div v-if="params.rerankEnabled" class="param-row sub">
        <n-select
          v-model:value="params.rerankModelId"
          :options="rerankOptions"
          placeholder="请选择重排模型"
          filterable
          clearable
          size="small"
          class="fusion-select"
        />
      </div>
      <n-divider class="param-divider" />
      <div class="param-row">
        <span class="param-label">
          进入重排数量
          <n-tooltip placement="top">
            <template #trigger><n-icon size="13" class="q-icon"><CircleQuestionMark /></n-icon></template>
            参与重排的结果数量
          </n-tooltip>
        </span>
        <div class="param-control">
          <n-input-number v-model:value="params.enterRerankCount" :min="0" :max="500" size="small" style="width: 100px" />
        </div>
      </div>
      <div class="param-row">
        <n-slider v-model:value="params.enterRerankCount" :min="0" :max="500" />
      </div>
      <n-divider class="param-divider" />
      <div class="param-row">
        <span class="param-label">
          阈值过滤
          <n-tooltip placement="top">
            <template #trigger><n-icon size="13" class="q-icon"><CircleQuestionMark /></n-icon></template>
            只返回相似度高于阈值的结果
          </n-tooltip>
        </span>
        <n-switch v-model:value="params.thresholdEnabled" size="small" />
      </div>
      <div v-if="params.thresholdEnabled" class="param-row sub">
        <span class="param-label">
          相似度阈值
        </span>
        <div class="param-control">
          <n-input-number v-model:value="params.threshold" :min="0" :max="1" :step="0.01" size="small" style="width: 100px" />
        </div>
      </div>
      <div v-if="params.thresholdEnabled" class="param-row">
        <n-slider v-model:value="params.threshold" :min="0" :max="1" :step="0.01" />
      </div>
<!--      <div v-if="params.thresholdEnabled" class="param-row sub">-->
<!--        <span class="param-label">相似度阈值</span>-->
<!--        <div class="slider-wrap">-->
<!--          <n-slider v-model:value="params.threshold" :min="0" :max="1" :step="0.05" />-->
<!--          <span class="slider-value">{{ params.threshold.toFixed(2) }}</span>-->
<!--        </div>-->
<!--      </div>-->
      <n-divider class="param-divider" />
      <n-collapse :default-expanded-names="['more']" arrow-placement="right">
        <n-collapse-item name="more" title="更多参数" class="more-item">
          <div class="param-row">
            <span class="param-label">
              融合策略
              <n-tooltip placement="top">
                <template #trigger><n-icon size="13" class="q-icon"><CircleQuestionMark /></n-icon></template>
                选择向量检索与BM25检索结果的融合方式
              </n-tooltip>
            </span>
          </div>
          <div class="param-row">
            <n-select v-model:value="params.fusionStrategy" :options="fusionOptions" size="small" class="fusion-select" />
          </div>
          <n-divider class="param-divider" />
          <div v-if="params.fusionStrategy === 'WEIGHTED_SUM'" class="param-row sub">
            <span class="param-label">
              向量权重
              <n-tooltip placement="top">
                <template #trigger><n-icon size="13" class="q-icon"><CircleQuestionMark /></n-icon></template>
                稠密向量检索的权重（1 - denseWeight 为BM25权重）
              </n-tooltip>
            </span>
            <div class="param-control">
              <n-input-number v-model:value="params.denseWeight" :min="0" :max="1" :step="0.01" size="small" style="width: 100px" />
            </div>
          </div>
          <div v-if="params.fusionStrategy === 'WEIGHTED_SUM'" class="param-row">
            <n-slider v-model:value="params.denseWeight" :min="0" :max="1" :step="0.01" />
          </div>
          <div v-if="params.fusionStrategy === 'RRF'" class="param-row">
            <span class="param-label">
              RRF K 值
              <n-tooltip placement="top">
                <template #trigger><n-icon size="13" class="q-icon"><CircleQuestionMark /></n-icon></template>
                RRF 公式中的常数 k，值越大排名差距越平滑，默认 60
              </n-tooltip>
            </span>
            <div class="param-control">
              <n-input-number v-model:value="params.rrfK" :min="1" :max="200" size="small" style="width: 100px" />
            </div>
          </div>
          <div v-if="params.fusionStrategy === 'RRF'" class="param-row">
            <n-slider v-model:value="params.rrfK" :min="1" :max="200" />
          </div>
        </n-collapse-item>
      </n-collapse>
    </n-card>

    <!-- 模型回答参数（仅问答） -->
    <n-card v-if="mode === 'qa' && modelParams" class="params-section" content-style="padding: 5px;">
      <div class="params-title search-title">模型回答参数</div>
      <div class="muted hint">调整大模型参数，预览和调试模型回答效果</div>
      <div class="param-row">
        <span class="param-label">选择模型</span>
      </div>
      <div class="param-row">
        <n-select
            v-model:value="modelParams.modelId"
            :options="modelOptions"
            placeholder="请选择对话模型"
            filterable
            size="small"
            class="fusion-select"
        />
      </div>
      <n-divider class="param-divider" />
      <div class="param-row">
        <span class="param-label">
          拼接邻近文本片数量
          <n-tooltip placement="top">
            <template #trigger><n-icon size="13" class="q-icon"><CircleQuestionMark /></n-icon></template>
            将检索到的文本片与其邻近文本片拼接，扩展上下文
          </n-tooltip>
        </span>
        <div class="param-control">
          <!-- 清空数字框 emit null 的归一化见 writeNearbySliceCount -->
          <n-input-number
            :value="modelParams.nearbySliceCount"
            :min="0"
            :max="20"
            :step="1"
            :precision="0"
            size="small"
            style="width: 84px"
            @update:value="writeNearbySliceCount"
          />
        </div>
      </div>
      <div class="param-row">
        <n-slider v-model:value="modelParams.nearbySliceCount" :min="0" :max="20" :step="1" />
      </div>
      <n-divider class="param-divider" />
      <div class="param-row vertical">
        <div class="param-label-row">
          <span class="param-label">编写 Prompt</span>
        </div>
        <div class="muted hint">
          Prompt 用于定义大模型的人设和回答框架。可引入 1 项变量：&lt;Documents> 代表引用RAG。点击即可插入变量：
          <n-button
              size="tiny"
              quaternary
              type="primary"
              @click="modelParams.prompt = modelParams.prompt ? modelParams.prompt + '\n\n<Documents>' : '<Documents>'"
          >
            &lt;Documents&gt;
          </n-button>
        </div>
        <n-input
          v-model:value="modelParams.prompt"
          type="textarea"
          :autosize="{ minRows: 8, maxRows: 16 }"
          placeholder="输入 Prompt 模板…"
        />
      </div>
    </n-card>
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

.search-title {
  font-size: 15px;
  font-weight: 700;
  padding-left: 10px;
  border-left: 3px solid #1890ff;
  line-height: 1;
}

.params-title {
  font-weight: 600;
  margin-bottom: 2px;
  font-size: 16px;
}

.param-divider {
  margin: 12px 0;
}


.hint {
  color: rgba(120, 120, 120, 0.9);
  margin-bottom: 10px;
  font-size: 13px;
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

/* 只放按钮/定宽输入框：inline-flex 是 shrink-to-fit 容器，100% 宽的子元素（slider/select/textarea）会塌成 0 宽 */
.param-control {
  display: inline-flex;
  align-items: center;
  gap: 2px;
}

.fusion-select {
  width: 100%;
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
  font-size: 14px;
  padding: 4px 0;
}
</style>
