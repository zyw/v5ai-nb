<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch, type Component } from 'vue'
import { NIcon } from 'naive-ui'
import {
  ArrowRight,
  BookOpen,
  Compass,
  Globe,
  Layers,
  Lightbulb,
  Puzzle,
  Rocket,
  Search,
  Sparkles,
  Target,
  TrendingUp,
  Zap
} from 'lucide-vue-next'
import AgentAvatar from './AgentAvatar.vue'
import type { PortalAgent } from '../api/client'

/**
 * 空态引导：Agent 身份（头像 + 名称 + 描述）+ 欢迎语 + 预设问题。
 * 对话开始前（或新会话）显示，点预设问题即发送。
 *
 * 整块左对齐、靠同一条左边线立住。原先五样东西全部居中堆在中轴线上，没有一条边可循：
 * 多行段落居中后每一行的起点都不一样，读第二行要先找回行首；药丸宽度随文字长短变化，
 * 居中之后左右两边都是参差的，换行位置也随字数跳。列宽由 ChatView.vue 的 .intro-wrap
 * 给（与消息列、输入区同一列），这里只负责块内的排布。
 */
const props = defineProps<{ agent: PortalAgent }>()
const emit = defineEmits<{ (e: 'ask', question: string): void }>()

/**
 * 预设问题的前置记号，从池子里挑。
 *
 * 挑的都是「这是哪一类问题」这种程度的泛指：凭空断定某句话是在问排障 / 在问指标会误导，
 * 所以只当辨识用的记号，别当分类。形状彼此拉开（星 / 灯 / 罗盘 / 书 / 放大镜 / 闪电 /
 * 靶 / 层叠 / 火箭 / 拼图 / 地球 / 折线），五枚并排才不至于看成一串同款。
 */
const PRESET_ICONS: Component[] = [
  Sparkles,
  Lightbulb,
  Compass,
  BookOpen,
  Search,
  Zap,
  Target,
  Layers,
  Rocket,
  Puzzle,
  Globe,
  TrendingUp
]

/**
 * 「随机」得对文案稳定：直接用 Math.random() 的话，每次重渲染（流式输出时的每一帧、
 * 切一下联网搜索）都会换一枚图标，看着像坏了；而且同一个 Agent 每次进来还不一样。
 * 这里拿文案算个哈希定下标 —— 同一句话永远同一枚。
 * 种子里掺进下标，是为了文案恰好重复时并排两行不至于撞成同一枚。
 */
function presetIcon(question: string, index: number): Component {
  let hash = Math.imul(index + 1, 0x9e3779b1) >>> 0
  for (let i = 0; i < question.length; i += 1) {
    hash = (hash ^ question.charCodeAt(i)) >>> 0
    hash = Math.imul(hash, 0x01000193) >>> 0
  }
  return PRESET_ICONS[hash % PRESET_ICONS.length]
}

/** 图标只跟文案走，所以在这儿算一次就够，不必在模板里每次渲染重算 */
const presets = computed(() =>
  props.agent.presetQuestions.map((question, index) => ({
    question,
    icon: presetIcon(question, index)
  }))
)

/** 逐字跳出：整段走完的目标时长按字数算，但夹在这一对上下限里 ——
 *  短句不至于一闪而过，长段落也不至于让人干等十秒 */
const TYPING_MS_MIN = 600
const TYPING_MS_MAX = 2000
const TYPING_MS_PER_CHAR = 38

/** 已露出的字数；正常态等于全篇长度（没在打字时就是整段） */
const revealed = ref(0)
const typing = ref(false)
let frame = 0

function stopTyping() {
  if (frame) cancelAnimationFrame(frame)
  frame = 0
  typing.value = false
}

/**
 * 从头逐字放出欢迎语。
 *
 * 用 rAF 按已流逝时间算露出多少字，而不是「每 N 毫秒多一个字」：
 * 后者的总时长随字数线性增长，几十字的欢迎语要放十几秒；前者总时长恒定，
 * 长文案一帧多放几个字，读起来还是「一句句冒出来」。
 *
 * 关掉动效就整段直接给出 —— 逐字跳出纯属装饰，没有它信息一点不少，
 * 而这类「文字慢慢浮现」恰恰是最容易让人难受的一类动效。
 */
function startTyping() {
  stopTyping()
  const text = props.agent.greeting ?? ''
  revealed.value = 0
  if (!text) return
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
    revealed.value = text.length
    return
  }

  const duration = Math.min(TYPING_MS_MAX, Math.max(TYPING_MS_MIN, text.length * TYPING_MS_PER_CHAR))
  const startedAt = performance.now()
  typing.value = true
  const step = (now: number) => {
    const progress = Math.min(1, (now - startedAt) / duration)
    revealed.value = Math.floor(progress * text.length)
    if (progress < 1) {
      frame = requestAnimationFrame(step)
    } else {
      frame = 0
      typing.value = false
    }
  }
  frame = requestAnimationFrame(step)
}

/**
 * 换 Agent 或换欢迎语，就重放一遍。
 *
 * 比的是拼出来的字符串（原始值），不是 `[agentKey, greeting]` 数组 —— 数组每次都是新对象，
 * watch 的同一性比较永远判「变了」，于是 store 重新拉一次 bootstrap、agent 换个引用，
 * 明明内容一模一样也会平白重放一次（实测：改描述、换引用都会误触发）。
 * 拼成字符串后，值没变就是没变。
 */
const typingKey = computed(() => JSON.stringify([props.agent.agentKey, props.agent.greeting ?? '']))

onMounted(startTyping)
watch(typingKey, startTyping)
onBeforeUnmount(stopTyping)
</script>

<template>
  <div class="intro">
    <!-- 身份行：头像、名称、描述归成一块，而不是三样各占一行 -->
    <div class="intro-head">
      <AgentAvatar :agent-key="agent.agentKey" :name="agent.name" :size="56" />
      <div class="intro-head-text">
        <h2 class="intro-name">{{ agent.name }}</h2>
        <p class="intro-desc">{{ agent.description || '暂无描述' }}</p>
      </div>
    </div>

    <!-- 欢迎语做成「引述」而不是气泡：气泡（.bubble）自带底色和描边，
         这里没有署名，浮在那里会被读成一条已经发出来的消息 -->
    <p v-if="agent.greeting" class="intro-greeting">
      <span class="intro-greeting-body">
        <!-- 打底的一份：不可见但留在布局里，让这一段从第一帧起就是最终高度。
             少了它，字一个个冒出来会把下面的预设问题一路顶下去，整块还会因为垂直居中往上挪，
             也就是每帧都在跳 -->
        <span class="intro-greeting-ghost" aria-hidden="true">{{ agent.greeting }}</span>
        <span class="intro-greeting-typed">{{ agent.greeting.slice(0, revealed) }}<span
            v-if="typing"
            class="intro-greeting-caret"
            aria-hidden="true"
          /></span>
      </span>
    </p>

    <div v-if="agent.presetQuestions.length" class="intro-presets">
      <div class="intro-presets-label">试试这样问</div>
      <!-- 一块玻璃 + 行间发丝分隔：与登录页的能力清单（KeyView 的 .key-feats）同一套面板语言 -->
      <ul class="intro-presets-list portal-glass">
        <li v-for="(preset, index) in presets" :key="index">
          <button type="button" class="intro-preset" @click="emit('ask', preset.question)">
            <span class="intro-preset-mark" aria-hidden="true">
              <n-icon :component="preset.icon" :size="15" />
            </span>
            <span class="intro-preset-text">{{ preset.question }}</span>
            <!-- 扫到最右的那枚箭头才是「点了就发」的提示：前置记号只是记号，
                 少了它，这一行和登录页那份不可点的能力清单就长得一模一样了 -->
            <span class="intro-preset-go" aria-hidden="true">
              <n-icon :component="ArrowRight" :size="15" />
            </span>
          </button>
        </li>
      </ul>
    </div>
  </div>
</template>

<style scoped>
.intro {
  display: flex;
  flex-direction: column;
  gap: 20px;
  /* 不铺满整列：13px 的段落跑满 788px 会到一百来个字符一行，
     60–75 字符的可读区间守不住。左边的起点仍与消息列对齐。 */
  max-width: 600px;
}

.intro-head {
  display: flex;
  align-items: center;
  gap: 12px;
}

.intro-head-text {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.intro-name {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  line-height: 1.3;
  overflow-wrap: anywhere;
}

.intro-desc {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--portal-fg-muted);
  overflow-wrap: anywhere;
}

/* 左侧竖条代替气泡外框：竖条跨满整段，多行时才看得出是一段引述。
   竖条的高度由打底那份顶出来，所以从第一帧起就是最终长度，不会跟着字一起长。 */
.intro-greeting {
  margin: 0;
  padding-left: 14px;
  border-left: 2px solid var(--portal-accent);
  font-size: 14px;
  line-height: 1.7;
  overflow-wrap: anywhere;
}

/* 打底与逐字两份叠在同一格里。内边距在外层 <p> 上，这一层不带内边距，
   所以底下那句 inset: 0 对上去正好是文字区，不必再把 14px 抄一遍。 */
.intro-greeting-body {
  position: relative;
  display: block;
}

.intro-greeting-ghost {
  display: block;
  visibility: hidden;
}

/* 逐字那份压在打底上：宽高都与打底一致，折行位置才对得上，
   长到哪儿画到哪儿，露出的部分之外是空的（打底已经占住了位置） */
.intro-greeting-typed {
  position: absolute;
  inset: 0;
}

/* 光标只在逐字进行时挂着，打完就摘掉 —— 所以不必为「减少动效」另设一条停用：
   那条路径下 typing 始终是 false，这里根本不会渲染 */
.intro-greeting-caret {
  display: inline-block;
  width: 2px;
  height: 1.05em;
  margin-left: 1px;
  vertical-align: text-bottom;
  background: var(--portal-accent);
  animation: intro-caret-blink 1s step-end infinite;
}

@keyframes intro-caret-blink {
  50% {
    opacity: 0;
  }
}

.intro-presets-label {
  margin-bottom: 8px;
  font-size: 12.5px;
  font-weight: 500;
  color: var(--portal-fg-muted);
}

/* 一行一个：等宽的行有确定的左右边界，比居中换行的药丸整齐得多 */
.intro-presets-list {
  list-style: none;
  margin: 0;
  padding: 0;
  border-radius: var(--portal-radius);
  /* 首行/末行的悬停底色跟着圆角裁 */
  overflow: hidden;
}

.intro-presets-list > li + li {
  border-top: 1px solid var(--portal-border);
}

/* 行高由前置记号顶出来：10 + 28 + 10 = 48px，已经越过触屏 44pt 那条线，
   不必再像药丸那版另加一条 @media (hover: none) 去撑。 */
.intro-preset {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 10px 14px;
  border: 0;
  background: transparent;
  color: inherit;
  font: inherit;
  font-size: 13.5px;
  line-height: 1.5;
  text-align: left;
  cursor: pointer;
  transition: background 0.15s var(--portal-ease);
}

/* 悬停与按下同一个底色：触屏上 :hover 根本不触发，按下时的反馈只能由 :active 给 */
.intro-preset:hover,
.intro-preset:active {
  background: var(--portal-accent-soft);
}

/* 焦点圈画在内侧：列表裁了溢出（overflow: hidden），画在外侧会被切掉 */
.intro-preset:focus-visible {
  outline: 2px solid var(--portal-accent);
  outline-offset: -2px;
}

/* 前置记号：与登录页能力清单的 .key-feat-icon 同一套（圆角色块 + accent-soft 底），
   两块面板挨着看也像是同一套东西。色块统一一个色，不跟着图标变 —— 记号的区分靠形状。 */
.intro-preset-mark {
  display: inline-flex;
  flex: none;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 8px;
  background: var(--portal-accent-soft);
  color: var(--portal-accent);
}

.intro-preset-text {
  flex: 1;
  min-width: 0;
  overflow-wrap: anywhere;
}

.intro-preset-go {
  display: inline-flex;
  flex: none;
  color: var(--portal-fg-muted);
  transition: color 0.15s var(--portal-ease), transform 0.15s var(--portal-ease);
}

.intro-preset:hover .intro-preset-go,
.intro-preset:active .intro-preset-go {
  color: var(--portal-accent);
  transform: translateX(2px);
}
</style>
