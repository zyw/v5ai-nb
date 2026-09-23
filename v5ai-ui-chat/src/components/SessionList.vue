<script setup lang="ts">
import { h, nextTick, ref } from 'vue'
import type { Component, ComponentPublicInstance, VNode } from 'vue'
import { NButton, NDropdown, NIcon, NInput, NRadioButton, NRadioGroup } from 'naive-ui'
import type { DropdownOption } from 'naive-ui'
import { Archive, ArchiveRestore, Check, MessageSquare, MoreHorizontal, Pencil, X } from 'lucide-vue-next'
import type { ConversationSummary } from '../api/client'

/**
 * 左栏会话列表：服务端返回的会话（限本 Key 本 Agent，按最近活跃倒序），
 * 支持切换、行内改名、归档 / 取消归档，以及「未归档 / 已归档」的范围切换。
 *
 * 每行的操作收在「…」菜单里（重命名 / 归档），鼠标悬停或该行处于当前会话时显示，
 * 避免两个图标常驻挤压标题空间。未命名会话用首条提问预览做标题
 * （名称与预览的取值由 store 的 sessionTitle 统一）。
 */
const props = defineProps<{
  items: ConversationSummary[]
  currentId: string
  showArchived: boolean
  /** 列表正在整表重拉：这段内容让位给骨架屏（见模板里的说明） */
  loading: boolean
  titleOf: (session: ConversationSummary) => string
}>()

/**
 * 骨架行的标题条宽度：长短交错。
 * 几行等宽会看着像排出来的表格，而不是「这里将出现一段文字」。
 */
const SKELETON_TITLE_WIDTHS = ['86%', '62%', '92%', '70%', '80%']

const emit = defineEmits<{
  (e: 'open', session: ConversationSummary): void
  (e: 'rename', session: ConversationSummary, name: string): void
  (e: 'archive', session: ConversationSummary, archived: boolean): void
  (e: 'update:showArchived', value: boolean): void
}>()

const editingId = ref('')
const editingName = ref('')
/** 当前展开「…」菜单的会话：菜单开着的时候触发器要保持可见（鼠标移开也不再 hover） */
const openMenuId = ref('')
const nameInput = ref<InstanceType<typeof NInput> | null>(null)

function formatTime(value?: string | null): string {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  const today = new Date()
  const time = `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
  return date.toDateString() === today.toDateString() ? time : `${date.getMonth() + 1}/${date.getDate()}`
}

/**
 * 改名输入框的模板 ref：列表是 v-for 渲染的，字符串 ref 会被 Vue 收集成数组
 * （vnode 上带 `ref_for`），拿不到组件实例，因此用函数 ref 只记录正在改名的那一个。
 */
function setNameInput(el: Element | ComponentPublicInstance | null) {
  // 卸载时 Vue 会回调 null；忽略它，免得「新行先挂载、旧行后卸载」把新实例冲掉
  if (el) nameInput.value = el as InstanceType<typeof NInput>
}

function startRename(session: ConversationSummary) {
  editingId.value = session.conversationId
  editingName.value = session.name ?? props.titleOf(session)
  void nextTick(() => nameInput.value?.focus())
}

/**
 * 改名输入框的按键：Enter 保存、Esc 取消。
 *
 * 和输入区一样先挡输入法合成态：合成中按 Enter 是在选词、按 Esc 是在取消选词，
 * 都不该被当成提交/放弃（否则中文改名会把半截名字存下去）。详见 Composer.vue 的说明。
 *
 * 不写成 `@keydown.enter` + `@keydown.esc`：同一元素的多个 keydown 监听会被编译成数组，
 * 而 n-input 的 `onKeydown` 只接受单个函数（Vue 会报 Invalid prop 警告）。
 */
function onRenameKeydown(event: KeyboardEvent, session: ConversationSummary) {
  if (event.isComposing || event.keyCode === 229) return
  if (event.key !== 'Enter' && event.key !== 'Escape') return
  event.preventDefault()
  if (event.key === 'Enter') confirmRename(session)
  else cancelRename()
}

function cancelRename() {
  editingId.value = ''
  editingName.value = ''
}

function confirmRename(session: ConversationSummary) {
  const name = editingName.value.trim()
  if (!name) return
  emit('rename', session, name)
  cancelRename()
}

/** 下拉项图标：naive-ui 的 `icon` 要求一个返回 VNode 的函数，不能直接给组件。 */
function renderIcon(icon: Component): () => VNode {
  return () => h(NIcon, { size: 14 }, { default: () => h(icon) })
}

/** 每行的「…」菜单：重命名 / 归档（已归档的会话则是取消归档）。 */
function menuOptions(session: ConversationSummary): DropdownOption[] {
  return [
    { label: '重命名', key: 'rename', icon: renderIcon(Pencil) },
    {
      label: session.archived ? '取消归档' : '归档',
      key: 'archive',
      icon: renderIcon(session.archived ? ArchiveRestore : Archive)
    }
  ]
}

function onMenuSelect(key: string | number, session: ConversationSummary) {
  if (key === 'rename') {
    startRename(session)
    return
  }
  if (key === 'archive') {
    emit('archive', session, !session.archived)
  }
}
</script>

<template>
  <div class="session-list portal-scroll">
    <!--
      归档筛选：两个互斥的视图，二选一。

      原来是一枚「显示已归档」开关。问题不在样式而在语义：服务端那个参数拼的是
      ?archived=true（client.ts 的 fetchConversations），意思是「只给我已归档的」，
      不是「把已归档的也带上」—— 开关的文案只命名了其中一个视图，ON 时说不清是
      「多显示一批」还是「只看这批」。换成两个选项都写在面上的分段控件：当前在看哪个
      一眼可见，点哪边就去哪边。

      用单选而不是标签页：这里换的是同一份列表的取值（切换会向服务端重拉一次），
      不涉及面板的显隐与惰性渲染，radio 的语义正好卡住「二选一」。
    -->
    <n-radio-group
      class="session-filter"
      size="small"
      :value="showArchived ? 'archived' : 'active'"
      aria-label="会话范围"
      @update:value="(value: string) => emit('update:showArchived', value === 'archived')"
    >
      <n-radio-button value="active">
        <n-icon :component="MessageSquare" :size="14" />
        <span>未归档</span>
      </n-radio-button>
      <n-radio-button value="archived">
        <n-icon :component="Archive" :size="14" />
        <span>已归档</span>
      </n-radio-button>
    </n-radio-group>

    <!--
      整表重拉期间（切未归档 / 已归档、换 Agent、首次进入）：列表位置换成骨架屏。

      为什么是骨架而不是转圈：这段要换的是「列表本身」，占位行和真行一样高、一样排，
      数据到了就地换字，位置不动；转圈得先清空再填，看着是两跳。
      分段控件不在这一段里 —— 它正是用户刚点的那个东西，得一直看得见。

      items 这时还留着上一个视图的内容（loadSessions 只在拿到新数据时才赋值），
      所以下面几条分支必须让开，否则会先露出旧视图那批会话。

      骨架条 aria-hidden：它没有语义，说了也是噪声；「正在加载」由同一段里那句
      .portal-sr-only 播报（与 ChatMessageBody 播报生成进度是同一套做法）。
    -->
    <template v-if="loading">
      <span class="portal-sr-only" role="status">加载中…</span>
      <div class="session-skeleton" aria-hidden="true">
        <div v-for="(width, i) in SKELETON_TITLE_WIDTHS" :key="i" class="session-skeleton-row">
          <span class="session-skeleton-bar" :style="{ width }"></span>
          <span class="session-skeleton-bar is-time"></span>
        </div>
      </div>
    </template>

    <!--
      空列表：一个记号 + 主句 + 副句，居中。
      原先是一枚 18px 裸图标加一行 12px 说明 —— 竖向上两样都小、中间隔着 8px，块被撑得又高又空；
      横向上那句话几乎占满整栏、贴到两边，看着像一段没排完的正文，而不是一个「空态」。
      改成现在这样：记号给块一个重心，主句短、不贴边，副句再补一句该做什么。
      记号随状态换（归档列表用 Archive）：两种空态的长度差不多，只靠读文字才知道自己在看哪个列表。
    -->
    <div v-else-if="items.length === 0" class="session-empty">
      <span class="session-empty-mark" aria-hidden="true">
        <n-icon :component="showArchived ? Archive : MessageSquare" :size="17" />
      </span>
      <p class="session-empty-title">{{ showArchived ? '没有已归档的会话' : '还没有会话' }}</p>
      <p class="session-empty-hint">
        {{ showArchived ? '归档后的会话会出现在这里' : '发送第一条消息即可创建' }}
      </p>
    </div>

    <template v-else>
      <div
        v-for="session in items"
        :key="session.conversationId"
        class="session-item"
        :class="{
          'is-active': session.conversationId === currentId,
          'is-archived': session.archived,
          'is-menu-open': openMenuId === session.conversationId
        }"
      >
        <template v-if="editingId === session.conversationId">
          <n-input
            :ref="setNameInput"
            v-model:value="editingName"
            size="tiny"
            maxlength="100"
            :aria-label="'会话名称'"
            @keydown="onRenameKeydown($event, session)"
          />
          <n-button text size="tiny" aria-label="保存名称" @click="confirmRename(session)">
            <template #icon><n-icon :component="Check" :size="14" /></template>
          </n-button>
          <n-button text size="tiny" aria-label="取消改名" @click="cancelRename">
            <template #icon><n-icon :component="X" :size="14" /></template>
          </n-button>
        </template>

        <template v-else>
          <!--
            整行的命中区是这个真按钮，外层不再挂 role="button"：行内还有「…」菜单
            （改名时还有输入框和两个按钮），交互控件嵌在 role="button" 里会被无障碍树
            扁平化，读屏读不到内层控件。
          -->
          <button
            type="button"
            class="session-open"
            :aria-current="session.conversationId === currentId ? 'true' : undefined"
            @click="emit('open', session)"
          >
            <span class="session-title">{{ titleOf(session) }}</span>
            <span class="session-time">
              <span v-if="session.archived" class="session-flag">已归档</span>
              {{ formatTime(session.updatedAt) }}
            </span>
          </button>
          <n-dropdown
            trigger="click"
            placement="bottom-end"
            :options="menuOptions(session)"
            @select="(key: string | number) => onMenuSelect(key, session)"
            @update:show="(show: boolean) => (openMenuId = show ? session.conversationId : '')"
          >
            <n-button text size="tiny" class="session-more" aria-label="更多操作">
              <template #icon><n-icon :component="MoreHorizontal" :size="16" /></template>
            </n-button>
          </n-dropdown>
        </template>
      </div>
    </template>
  </div>
</template>

<style scoped>
.session-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 4px;
  overflow-y: auto;
  flex: 1;
  min-height: 0;
}

/* 分段控件：两段等宽铺满。
   n-radio-group 默认是 display: inline-block（radio-group.cssr），不掰成 flex 的话
   :deep(.n-radio-button){flex:1} 不起作用，两段只会各按文字宽度缩着，右边空一截。

   左右各 6px 的内边距与上面「新建会话」按钮对齐（列表自身 padding: 4px），
   整块的左右边缘因此和那枚按钮、和下面的会话行在同一条竖线上。

   底边留 14px（加上列表自身 2px 的 gap，与会话行实隔 16px）：这一行是下面那串会话的
   「表头」，原来只隔 10px —— 控件那道描边和选中行的强调色底几乎连成一片，加上两边都是
   紫色调，看着就是没有间隔。

   flex: none：这一行是个固定控件，会话多了只该让下面的列表滚，不该把它压扁。 */
.session-filter {
  display: flex;
  flex: none;
  padding: 4px 6px 14px;
  margin-bottom: 8px;
}

.session-filter :deep(.n-radio-button) {
  flex: 1;
  font-size: 13px;
}

/* 图标与文字的这一行在这里排。
   n-radio-button 没有 #icon 插槽（RadioButton.mjs 只渲染 default slot），图标与文字都落进
   .n-radio__label，而它默认 display: inline-block —— 两样各贴各的基线，14px 的 svg 会往下沉。
   改成铺满按钮内容区的 flex 行，两样各自居中；居中因此不再依赖基线（换个字体度量也不会偏），
   原来落在 .n-radio-button 上的 text-align: center 也就一并撤了 —— 那时它管的是这个 label，
   现在 label 是容器，居中由 justify-content 接管。

   height: 100% 撑满按钮的内容区：n-radio-button 是 border-box、高度 28px，去掉上下各 1px
   描边，内容区 26px 是个确定值，百分比高度有得可解析。不写的话 label 会按内容撑到 28px，
   又多出 2px 的偏移。
   padding: 0 是挡着 .n-radio__label 自己那条 padding: var(--n-label-padding)：
   按钮组没定义这个变量，眼下算出来是 0，但真填上值时会连 height: 100% 一起顶出去。 */
.session-filter :deep(.n-radio__label) {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  height: 100%;
  padding: 0;
}

/* 空态：记号 / 主句 / 副句自上而下居中，贴着上面那行分段控件往下排。
   不做垂直居中撑满整栏 —— 那样它会飘在列表中间，离上面那行控件很远，反而更空。 */
.session-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 28px 16px;
  text-align: center;
}

/* 与欢迎页预设问题的记号、登录页能力清单的 .key-feat-icon 同一套：
   圆角色块 + accent-soft 底，三处并排看像一套东西 */
.session-empty-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  margin-bottom: 4px;
  border-radius: 9px;
  background: var(--portal-accent-soft);
  color: var(--portal-accent);
}

/* p 自带上下外边距，这里靠 gap 排，一律清掉 */
.session-empty-title {
  margin: 0;
  font-size: 13px;
  font-weight: 500;
}

.session-empty-hint {
  margin: 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--portal-fg-muted);
}

/* 骨架屏：整表重拉期间占住列表的位置。
   行的 min-height / 内边距 / 块间距都对着 .session-item 与列表自身的 gap 抄，
   数据到了换成真行时位置不跳。 */
.session-skeleton {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.session-skeleton-row {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 6px;
  min-height: 44px;
  padding: 0 8px;
}

/* 条子本身：portal-border 打底，再让一道亮带从上面扫过去。
   亮带用 --portal-shine（浅色是白、深色是半透明白），两个主题下都是「提亮」这一侧，
   不必各写一套。 */
.session-skeleton-bar {
  height: 10px;
  border-radius: 4px;
  background-color: var(--portal-border);
  background-image: linear-gradient(
    90deg,
    transparent 0%,
    var(--portal-shine) 50%,
    transparent 100%
  );
  background-size: 250% 100%;
  background-repeat: no-repeat;
  animation: session-skeleton-sweep 1.2s linear infinite;
}

/* 时间那一条：对着真行的时间戳，短而矮。宽度写在类上而不是行内 ——
   行内样式只给标题条（宽度逐行不同），写这里会被行内样式压掉也没必要 */
.session-skeleton-bar.is-time {
  width: 34px;
  height: 8px;
}

/* 与 ChatMessageBody 的 thinking-shimmer 同一套走向：从右往左扫。
   两端各自落在可视区外，所以一整轮里「有亮带」和「没有亮带」是连着的，看不出接缝 */
@keyframes session-skeleton-sweep {
  from {
    background-position-x: 150%;
  }
  to {
    background-position-x: -150%;
  }
}

/* 系统开了「减少动态效果」就停扫光：静态的灰条照样说明「这儿在加载」 */
@media (prefers-reduced-motion: reduce) {
  .session-skeleton-bar {
    animation: none;
  }
}

.session-item {
  display: flex;
  align-items: center;
  gap: 4px;
  min-height: 44px;
  /* 纵向内边距交给 .session-open，好让整行高度都是它的命中区 */
  padding: 0 8px;
  border-radius: 8px;
  transition: background 0.15s var(--portal-ease);
}

.session-item:hover {
  background: var(--portal-surface-2);
}

/* 当前会话：淡强调色底 + 左侧竖条，不整块刷成高饱和。
   底色之外还有语义——按钮上带 aria-current，读屏才知道「当前在这条」。 */
.session-item.is-active {
  background: var(--portal-accent-soft);
  box-shadow: inset 2px 0 0 var(--portal-accent);
}

/* 整行的命中区：align-self 撑满 44px 行高，键盘 Enter/Space 由原生按钮行为负责 */
.session-open {
  flex: 1;
  min-width: 0;
  align-self: stretch;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 2px;
  padding: 6px 0;
  border: 0;
  border-radius: 6px;
  background: transparent;
  text-align: left;
  color: inherit;
  font: inherit;
  cursor: pointer;
}

.session-open:focus-visible {
  outline: 2px solid var(--portal-accent);
  outline-offset: -2px;
}

.session-item.is-archived .session-title {
  color: var(--portal-fg-muted);
}

.session-title {
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 时间戳是次要文字：给足对比度（11px + opacity 曾是 2.3:1） */
.session-time {
  display: flex;
  gap: 6px;
  font-size: 11px;
  color: var(--portal-fg-muted);
}

.session-flag {
  color: var(--portal-fg-muted);
}

.session-more {
  flex: none;
  /* 撑到 WCAG 2.2 的 24×24 命中区：图标仍是 16px，多出来的是透明内边距 */
  min-width: 24px;
  min-height: 24px;
  opacity: 0;
}

.session-item:hover .session-more,
.session-item:focus-within .session-more,
.session-item.is-active .session-more,
.session-item.is-menu-open .session-more {
  opacity: 1;
}

/*
 * 触屏没有 hover：操作按钮常驻。否则 opacity: 0 的元素仍然接收点击 ——
 * 用户会点到一个看不见的「…」，直接触发归档。
 */
@media (hover: none) {
  .session-more {
    opacity: 1;
  }
}
</style>
