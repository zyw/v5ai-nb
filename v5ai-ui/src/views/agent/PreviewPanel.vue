<script setup lang="ts">
/**
 * 预览与调试：智能体编辑页右栏。面板高度固定为右栏高度，只有会话区内部滚动。
 *
 * 两种状态：
 * - 未开始：展示智能体图标/名称/描述 + 推荐问题（用户视角的首屏）。
 * - 已开始（点击推荐问题或发送过消息）：首屏信息与推荐问题整体隐藏，面板变成纯会话窗口。
 */
import { computed, nextTick, onUnmounted, reactive, ref, watch } from 'vue'
import { NButton, NIcon, NInput, NTooltip, useMessage } from 'naive-ui'
import { Image, Link2, Send, X } from 'lucide-vue-next'
import ChatMessageBody from '../../components/ChatMessageBody.vue'
import MessageActions from '../../components/MessageActions.vue'
import { mergeHits, parseHits, type ChatMsg } from '../../utils/chatStream'
import {
  adminDebugStream,
  loadImagePreview,
  uploadResource,
  type AttachmentRequest
} from '../../api/client'
import { adminToken } from '../../stores/session'

const props = defineProps<{
  agentKey: string
  name: string
  description: string
  avatar: string
  greeting: string
  presetQuestions: string[]
  /** 当前所选模型是否支持图片输入（取决于「模型管理」里的模型配置）。 */
  imageSupported?: boolean
  /**
   * 是否展示 RAG 引用折叠块（Agent 的「引用展示」开关）。
   *
   * <p>调试面板与门户走同一个组件、同一份开关值，所以在这里改开关能**当场看到**引用的显隐；
   * 默认 true 是「未传即展示」——与改动前的行为一致。</p>
   */
  showCitations?: boolean
}>()

const message = useMessage()

const query = ref('')
const loading = ref(false)
const runId = ref('')
const messages = ref<ChatMsg[]>([])
/** 会话区滚动：贴底判断阈值与知识库问答一致。 */
const listRef = ref<HTMLElement | null>(null)
const BOTTOM_THRESHOLD = 40

/** 会话是否已开始：一开始就把首屏信息与推荐问题收起来，避免会话中被占位。 */
const started = computed(() => messages.value.length > 0)
const avatarChar = computed(() => (props.name || '智').trim().slice(0, 1))
/** 头像预览地址：原始 avatar 是带鉴权的 /api/ 路径，直接当 <img> src 会 401，需带鉴权拉成 blob。 */
const avatarSrc = ref('')
let avatarSeq = 0
watch(
  () => props.avatar,
  async (val) => {
    const seq = ++avatarSeq
    if (avatarSrc.value.startsWith('blob:')) URL.revokeObjectURL(avatarSrc.value)
    avatarSrc.value = ''
    if (!val) return
    try {
      const url = await loadImagePreview(adminToken.value, val)
      if (seq === avatarSeq) avatarSrc.value = url
      else if (url.startsWith('blob:')) URL.revokeObjectURL(url)
    } catch {
      /* 拉取失败保持空 → 回退首字符占位 */
    }
  },
  { immediate: true }
)
onUnmounted(() => {
  if (avatarSrc.value.startsWith('blob:')) URL.revokeObjectURL(avatarSrc.value)
  clearPendingImages()
})
/** 未填写欢迎语时回退到描述，避免首屏空面板。 */
const greetText = computed(() => props.greeting.trim() || props.description.trim())
const imageTip = computed(() =>
  props.imageSupported
    ? '添加图片'
    : '当前模型不支持图片输入，可在「模型管理」中为模型开启「支持图片输入」'
)

// ---- 图片附件：与门户（v5ai-ui-chat）同一套约束与流程 ----
// PNG/JPEG/WebP、单张 5MB、每条最多 3 张；选中即本地预览，**上传发生在发送时**（先传完再发，
// 避免「消息发出去了图没带上」）。资源存进通用资源库（bizType=ATTACHMENT），消息只带引用。
const ACCEPTED_IMAGE_TYPES = ['image/png', 'image/jpeg', 'image/webp']
const MAX_IMAGE_BYTES = 5 * 1024 * 1024
const MAX_IMAGES_PER_MESSAGE = 3

/** 待发送的图片：本地预览 URL + 原始文件。 */
const pendingImages = ref<{ file: File; url: string }[]>([])
const imageInput = ref<HTMLInputElement | null>(null)
const uploading = ref(false)

const canSend = computed(
  () => !loading.value && !uploading.value && (query.value.trim().length > 0 || pendingImages.value.length > 0)
)

function pickImages() {
  if (!props.imageSupported || loading.value || uploading.value) return
  imageInput.value?.click()
}

function onImagesChosen(event: Event) {
  const input = event.target as HTMLInputElement
  const chosen = Array.from(input.files ?? [])
  // 清空 input：否则连选同一张图不会再触发 change
  input.value = ''
  for (const file of chosen) {
    if (pendingImages.value.length >= MAX_IMAGES_PER_MESSAGE) {
      message.warning(`每条消息最多 ${MAX_IMAGES_PER_MESSAGE} 张图片`)
      break
    }
    if (!ACCEPTED_IMAGE_TYPES.includes(file.type)) {
      message.warning('只支持 PNG / JPEG / WebP 图片')
      continue
    }
    if (file.size > MAX_IMAGE_BYTES) {
      message.warning(`单张图片不能超过 ${Math.round(MAX_IMAGE_BYTES / 1024 / 1024)}MB`)
      continue
    }
    pendingImages.value.push({ file, url: URL.createObjectURL(file) })
  }
}

function removePendingImage(index: number) {
  const [removed] = pendingImages.value.splice(index, 1)
  if (removed) URL.revokeObjectURL(removed.url)
}

/**
 * 清空待发送列表。
 *
 * @param revoke 是否回收 objectURL：图片交给消息气泡继续展示时传 false（所有权转移）
 */
function clearPendingImages(revoke = true) {
  if (revoke) {
    for (const item of pendingImages.value) URL.revokeObjectURL(item.url)
  }
  pendingImages.value = []
}

/**
 * 取出增量文本。SSE 的 data 是整个 RuntimeEvent 的 JSON（{runId,type,payload,createdAt}），
 * 直接展示会把 JSON 原样打出来，所以必须解出 payload。
 * 注意用 !== undefined 而不是 ??：payload 为空串时 ?? 会落到 return data，
 * 把整段 JSON 当成文本拼进去（表现为思考里混进 {"runId":...}）。
 */
function parseDelta(data: string): string {
  if (!data) return ''
  try {
    const body = JSON.parse(data) as { payload?: unknown; answer?: unknown; text?: unknown }
    if (typeof body.payload === 'string') return body.payload
    if (typeof body.answer === 'string') return body.answer
    if (typeof body.text === 'string') return body.text
    return ''
  } catch {
    return data
  }
}

function parseRunId(data: string): string {
  if (!data) return ''
  try {
    return (JSON.parse(data) as { runId?: string }).runId ?? ''
  } catch {
    return ''
  }
}

function parseRunFailed(data: string): string {
  if (!data) return '未知错误'
  try {
    return (JSON.parse(data) as { payload?: string }).payload || '未知错误'
  } catch {
    return data
  }
}

function reset() {
  messages.value = []
  runId.value = ''
  query.value = ''
  clearPendingImages()
}

/** 点击推荐问题：填入输入框并直接发送（发送后首屏信息即隐藏）。 */
function ask(question: string) {
  query.value = question
  void send()
}

/** 流「静默」多久算卡死（期间一条事件都没来）。协议正常结束时流会自行关闭，不会走到这里。 */
const STREAM_SILENCE_TIMEOUT_MS = 60_000

async function send() {
  const question = query.value.trim()
  if (!canSend.value) return
  if (!props.agentKey) {
    message.warning('智能体尚未创建，无法调试')
    return
  }
  query.value = ''

  // 附件上传：先全部传完再发消息。上传失败就整条不发——否则用户以为图发出去了
  const files = pendingImages.value.map((item) => item.file)
  const urls = pendingImages.value.map((item) => item.url)
  // 所有权转移给消息气泡：这里不 revoke
  clearPendingImages(false)

  let attachmentRefs: AttachmentRequest[] = []
  if (files.length) {
    uploading.value = true
    try {
      const resources = await Promise.all(
        files.map((file) => uploadResource(adminToken.value, file, { bizType: 'ATTACHMENT' }))
      )
      attachmentRefs = resources.map((resource) => ({ type: 'IMAGE' as const, resourceId: resource.id }))
    } catch (e) {
      for (const url of urls) URL.revokeObjectURL(url)
      message.error(e instanceof Error ? e.message : '图片上传失败')
      return
    } finally {
      uploading.value = false
    }
  }
  await runTurn(question, { images: urls, attachmentRefs })
}

/**
 * 重新生成第 index 条（助手）回答：丢弃该回答及其之后的全部消息，用上一条提问重跑。
 * 本页走的是普通流式链路（服务端不读历史、每次新建会话），所以它等价于重发那一条提问。
 */
async function regenerate(index: number) {
  if (loading.value) return
  if (!props.agentKey) {
    message.warning('智能体尚未创建，无法调试')
    return
  }
  const question = messages.value[index - 1]
  if (!question || question.role !== 'user' || (!question.content && !question.images?.length)) return
  messages.value.splice(index)
  await nextTick()
  scrollToBottom()
  // 图片资源还在资源库里，引用原样复用即可（重跑一次不该把图丢掉）
  await runTurn(question.content, { images: question.images, attachmentRefs: question.attachmentRefs })
}

/**
 * 跑一轮对话：追加用户提问 + 助手占位并就地流式填充（发送与重新生成同一条路径）。
 *
 * @param extras 该轮提问的图片：{@code images} 仅用于展示（本地 objectURL），
 *               {@code attachmentRefs} 随请求提交给服务端
 */
async function runTurn(
  question: string,
  extras: { images?: string[]; attachmentRefs?: AttachmentRequest[] } = {}
) {
  messages.value.push({
    role: 'user',
    content: question,
    images: extras.images,
    attachmentRefs: extras.attachmentRefs
  })
  // 必须是 reactive 代理：SSE 回调里直接改内容，靠代理触发增量渲染；
  // 裸对象写属性不走 setter，会等 finally 里 loading 变更才整段刷出（表现为"不流式"）。
  const assistant = reactive<ChatMsg>({ role: 'assistant', content: '', loading: true, reasoningOpen: true })
  const assistantIndex = messages.value.push(assistant) - 1
  loading.value = true
  runId.value = ''
  // 主动发问时无视当前滚动位置，直接到底（用户刚发完就想看回答）
  await nextTick()
  scrollToBottom()

  // 看门狗：每条事件都重置计时；静默超时说明连接卡死（服务端不再发也不关），
  // 主动中断，否则发送按钮会永久停在 loading、且 send() 被 loading 守卫挡住无法再发。
  const controller = new AbortController()
  let watchdog: ReturnType<typeof setTimeout> | undefined
  const resetWatchdog = () => {
    if (watchdog) clearTimeout(watchdog)
    watchdog = setTimeout(() => controller.abort(), STREAM_SILENCE_TIMEOUT_MS)
  }
  resetWatchdog()

  try {
    await adminDebugStream(
      props.agentKey,
      adminToken.value,
      { query: question, attachments: extras.attachmentRefs },
      (event, data) => {
        resetWatchdog()
        const target = messages.value[assistantIndex]
        if (event === 'RETRIEVAL') {
          target.citations = mergeHits(target.citations, parseHits(data))
        } else if (event === 'REASONING_DELTA' && data) {
          // 必须与回答同样解析：SSE 的 data 是整段 RuntimeEvent JSON，直接拼接会把 JSON 显示出来
          target.reasoning = (target.reasoning ?? '') + parseDelta(data)
        } else if (event === 'TEXT_DELTA') {
          // 回答开始的第一个片段：思考自动收起（只此一次，之后用户手动开合不再干预）
          if (!target.content) target.reasoningOpen = false
          target.content += parseDelta(data)
        } else if (event === 'RUN_FAILED') {
          target.error = parseRunFailed(data)
        } else if (event === 'MESSAGE_COMPLETED' && !target.content) {
          target.content = parseDelta(data)
        } else if (event === 'RUN_STARTED') {
          runId.value = parseRunId(data)
        }
      },
      controller.signal
    )
  } catch (e) {
    const target = messages.value[assistantIndex]
    // 看门狗主动中断：给出可读原因，避免表现为「莫名其妙失败」
    target.error =
      e instanceof DOMException && e.name === 'AbortError'
        ? `流式响应超时（${STREAM_SILENCE_TIMEOUT_MS / 1000} 秒无数据），已中断`
        : e instanceof Error
          ? e.message
          : '调用失败'
  } finally {
    if (watchdog) clearTimeout(watchdog)
    // 无论流如何结束（正常收尾 / 报错 / 超时中断），都必须复位，否则按钮永久 loading
    loading.value = false
    const target = messages.value[assistantIndex]
    target.loading = false
    // 失败且无内容时不留空壳
    if (!target.content && target.error) target.content = ''
  }
}

/** 流式内容（回答 + 思考）总长度：任一片段到达即变化，作为「该滚到底了」的触发源。 */
const streamedLength = computed(() =>
  messages.value.reduce((n, m) => n + m.content.length + (m.reasoning?.length ?? 0), 0)
)

function scrollToBottom() {
  const el = listRef.value
  if (el) el.scrollTop = el.scrollHeight
}

/**
 * 自动贴底。默认 flush（pre）时刻 DOM 还是「增长前」的旧高度，正好用来判断用户当前是否停在底部：
 * 停在底部就顺势跟到底，往上翻看历史则不动滚动条，等他滚回底部后下次判定自动恢复。
 * 不用 scroll 事件判断——同帧内多次滚动会被浏览器合并成一次且只报最终位置，
 * 用户上翻后紧跟一个增量片的强制贴底会把它覆盖掉，表现为「上翻被拽回底部」。
 */
watch(streamedLength, async () => {
  const el = listRef.value
  if (!el) return
  if (el.scrollHeight - el.scrollTop - el.clientHeight > BOTTOM_THRESHOLD) return
  await nextTick()
  scrollToBottom()
})

defineExpose({ reset })
</script>

<template>
  <div class="preview-panel">
    <div class="preview-head">
      <span class="preview-title">预览与调试</span>
      <n-button v-if="started" size="tiny" quaternary @click="reset">清空对话</n-button>
    </div>

    <!-- 首屏信息：仅在会话开始前展示（会话区此时不渲染，这块居中占位） -->
    <div v-if="!started" class="preview-intro">
      <div class="preview-profile">
        <div class="profile-avatar">
          <img v-if="avatarSrc" :src="avatarSrc" alt="" />
          <span v-else>{{ avatarChar }}</span>
        </div>
        <div class="profile-body">
          <div class="profile-name">{{ name || '未命名智能体' }}</div>
          <div v-if="greetText" class="profile-desc">{{ greetText }}</div>
          <div v-else class="profile-desc muted">暂无描述</div>
        </div>
      </div>

      <div v-if="presetQuestions.length" class="preset-block">
        <div class="preset-label">推荐问题</div>
<!--        <n-grid :x-gap="12" :y-gap="8" :cols="2">-->
<!--          <n-grid-item v-for="(question, index) in presetQuestions" :key="index">-->
<!--            <button class="preset-item" @click="ask(question)">-->
<!--              {{ question }}-->
<!--            </button>-->
<!--          </n-grid-item>-->
<!--        </n-grid>-->
        <div class="preset-list">
          <button
            v-for="(question, index) in presetQuestions"
            :key="index"
            type="button"
            class="preset-item"
            @click="ask(question)"
          >
            {{ question }}
          </button>
        </div>
      </div>
    </div>

    <!-- 会话区：仅开始对话后存在（未开始时整块不渲染），也是唯一的滚动区域 -->
    <div v-if="started" ref="listRef" class="chat-window">
      <div v-for="(msg, index) in messages" :key="index" :class="['chat-row', msg.role]">
        <div class="chat-bubble">
          <!-- 助手消息：思考折叠 + 回答 + 引用，与知识库问答 tab 共用同一组件 -->
          <ChatMessageBody
            v-if="msg.role === 'assistant'"
            :msg="msg"
            :show-citations="showCitations ?? true"
          />
          <template v-else>
            <div v-if="msg.images?.length" class="bubble-images">
              <img
                v-for="(url, imageIndex) in msg.images"
                :key="imageIndex"
                :src="url"
                alt="提问携带的图片"
              />
            </div>
            <span v-if="msg.content">{{ msg.content }}</span>
          </template>
        </div>
        <!-- 操作栏在气泡外、气泡下方（DeepSeek 的摆法）；生成中复制禁用、「重新生成」隐藏 -->
        <MessageActions
          :text="msg.content"
          :can-regenerate="msg.role === 'assistant'"
          :busy="loading"
          :align="msg.role === 'user' ? 'end' : 'start'"
          @regenerate="regenerate(index)"
        />
      </div>
    </div>

    <div class="chat-input">
      <div v-if="pendingImages.length" class="pending-images">
        <div v-for="(item, index) in pendingImages" :key="item.url" class="pending-image">
          <img :src="item.url" :alt="item.file.name" />
          <n-button
            text
            size="tiny"
            class="pending-image-remove"
            :aria-label="`移除图片 ${item.file.name}`"
            @click="removePendingImage(index)"
          >
            <template #icon><n-icon :component="X" :size="12" /></template>
          </n-button>
        </div>
      </div>
      <n-input
        v-model:value="query"
        type="textarea"
        :autosize="{ minRows: 3, maxRows: 6 }"
        placeholder="问我任何问题（可附图片）"
        @keydown.enter.exact.prevent="send"
      />
      <input
        ref="imageInput"
        class="chat-image-input"
        type="file"
        accept="image/png,image/jpeg,image/webp"
        multiple
        tabindex="-1"
        aria-hidden="true"
        @change="onImagesChosen"
      />
      <div class="chat-actions">
        <n-tooltip v-if="!imageSupported" trigger="hover">
          <template #trigger>
            <n-button quaternary circle disabled :aria-label="imageTip">
              <template #icon><n-icon :component="Image" /></template>
            </n-button>
          </template>
          {{ imageTip }}
        </n-tooltip>
        <n-button
          v-else
          quaternary
          circle
          :title="imageTip"
          aria-label="添加图片"
          :disabled="loading || uploading"
          @click="pickImages"
        >
          <template #icon><n-icon :component="Image" /></template>
        </n-button>
        <n-button
          circle
          type="primary"
          :loading="loading || uploading"
          :disabled="!canSend"
          aria-label="发送"
          @click="send"
        >
          <template #icon><n-icon :component="Send" /></template>
        </n-button>
      </div>
    </div>
    <div class="chat-foot">
      <div class="chat-runid" v-if="runId">runId: {{ runId }}</div>
      <!-- 图片按钮被禁用时把原因直接写出来，避免用户以为「点不动是坏了」 -->
      <div v-if="!imageSupported" class="chat-image-hint">
        <n-icon :component="Image" size="13" />
        <span>当前模型未开启「支持图片输入」，暂不能发送图片</span>
      </div>
      <!--
        关掉引用展示后，调试时看到的「没有引用」与「RAG 没命中」长得一样，
        所以把原因写出来——这一区分只有调试面板做得了（门户不该提示这件事）。
      -->
      <div v-if="showCitations === false" class="chat-citations-hint">
        <n-icon :component="Link2" size="13" />
        <span>该智能体已关闭「引用展示」：回答下方不显示引用（检索与引用记录不受影响）</span>
      </div>
      <div class="chat-disclaimer">AI 生成内容可能有误，请核实重要信息</div>
    </div>
  </div>
</template>

<style scoped>
.preview-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  /* 面板本身不滚动：只有 .chat-window 内部滚动。
     固定区（首屏/推荐问题/输入区）总高小于面板高度，因此不写 overflow 也不会溢出。 */
  overflow: hidden;
  padding: 12px 14px 10px;
  background: var(--n-color-2, #f7f8fa);
  border: 1px solid rgba(128, 128, 128, 0.16);
  border-radius: 8px;
}

.preview-head {
  flex: none;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 10px;
  border-bottom: 1px solid rgba(128, 128, 128, 0.16);
}

.preview-title {
  font-size: 14px;
  font-weight: 600;
}

/* 未开始时占满会话区空出来的高度：内容水平 + 垂直都居中。
   justify-content 居中不依赖「推荐问题是否存在」——之前用 margin-top/bottom:auto 的写法，
   在没有预设问题（.preset-block 因 v-if 不渲染）时会把头像名称整块推到面板底部。
   居中不会裁切：内容超高时 flex 子项自身收缩（min-height:0），父级 overflow:hidden 只是兜底。 */
.preview-intro {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

/* max-width + width:100%：未超出时横向撑满（内容只占一行时会收缩），超出时限制行长。
   没有它，宽面板上的描述会拉成很长一行。 */
.preview-profile {
  flex: none;
  display: flex;
  gap: 14px;
  width: 100%;
  max-width: 700px;
  padding: 12px 2px 2px;
}

.profile-avatar {
  flex: none;
  width: 64px;
  height: 64px;
  border-radius: 50%;
  background: #e9eaee;
  color: #4b5563;
  font-size: 26px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.profile-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.profile-body {
  min-width: 0;
}

.profile-name {
  font-size: 17px;
  font-weight: 600;
  line-height: 1.4;
}

.profile-desc {
  margin-top: 6px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--n-text-color-2, #667085);
  white-space: pre-wrap;
}

.profile-desc.muted {
  color: var(--n-text-color-3, #98a2b3);
}

.preset-block {
  flex: none;
  width: 100%;
  max-width: 700px;
  padding: 14px 0 0;
  min-height: 0;
}

.preset-label {
  font-size: 13px;
  margin-bottom: 8px;
}

.preset-list {
  display: flow;
  flex-direction: column;
  gap: 6px;
}

.preset-item {

  text-align: left;
  padding: 8px 12px;
  font-size: 13px;
  line-height: 1.5;
  color: var(--n-text-color-1, #344054);
  background: var(--n-color-1, #fff);
  border: 1px solid rgba(128, 128, 128, 0.22);
  border-radius: 8px;
  cursor: pointer;
  transition: border-color 0.2s, color 0.2s;
  margin: 5px;
}

.preset-item:hover {
  border-color: var(--n-primary-color, #7c3aed);
  color: var(--n-primary-color, #7c3aed);
}

/* flex:1 + min-height:0：唯一会滚动的区域，始终占满剩余高度。 */
.chat-window {
  flex: 1;
  /* 唯一会滚动的区域；下限要小于「面板高 - 固定区高」，否则会把面板撑破。 */
  min-height: 96px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 10px;
  margin-top: 10px;
  background: var(--n-color-1, #fff);
  border-radius: 8px;
}

/* 纵向排布：气泡在上、操作栏在下（DeepSeek 的摆法）。
   原本是横向 flex + justify-content 对齐，直接加第二个子节点会让操作栏挤到气泡旁边，
   故改为 column + align-items 表达左右对齐。 */
.chat-row {
  display: flex;
  flex-direction: column;
}

.chat-row.user {
  align-items: flex-end;
}

.chat-row.assistant {
  align-items: stretch;
}

/* 操作栏默认透明（见 MessageActions.vue），悬停或键盘聚焦该行时显形。
   注意 .msg-actions 是子组件的根元素，父级 scoped 样式能命中它——改名会静默失效。 */
.chat-row:hover .msg-actions,
.chat-row:focus-within .msg-actions {
  opacity: 1;
}

.chat-bubble {
  max-width: 88%;
  padding: 8px 11px;
  border-radius: 10px;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

/* 助手气泡要放折叠面板与引用列表，窄气泡会把它们挤变形：直接撑满会话区宽度。 */
.chat-row.assistant .chat-bubble {
  max-width: 100%;
  width: 100%;
}

.chat-row.user .chat-bubble {
  background: var(--n-primary-color, #7c3aed);
  color: #fff;
  border-bottom-right-radius: 2px;
}

.chat-row.assistant .chat-bubble {
  background: var(--n-color-3, #f2f4f7);
  border-bottom-left-radius: 2px;
}

/* 用户提问携带的图片：竖排在文字上方，宽度受气泡限制 */
.bubble-images {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 6px;
}

.bubble-images img {
  max-width: 160px;
  max-height: 160px;
  border-radius: 8px;
  display: block;
}

.chat-input {
  flex: none;
  display: flex;
  align-items: flex-end;
  /* 待发送图片自成一行（flex-basis: 100%），输入框与操作区仍在下一行 */
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.chat-input .n-input {
  flex: 1;
}

/* 隐藏的原生文件选择器：由「添加图片」按钮代为触发 */
.chat-image-input {
  display: none;
}

/* 待发送图片缩略图：发送前的本地预览，可逐张移除 */
.pending-images {
  flex-basis: 100%;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.pending-image {
  position: relative;
  width: 52px;
  height: 52px;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid rgba(128, 128, 128, 0.28);
}

.pending-image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.pending-image-remove {
  position: absolute;
  top: 0;
  right: 0;
  padding: 2px;
  background: rgba(0, 0, 0, 0.45);
  color: #fff;
  border-bottom-left-radius: 6px;
}

/* 输入框右侧的多选操作区：图片 + 发送 */
.chat-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  flex: none;
  padding-bottom: 2px;
}

.chat-foot {
  flex: none;
  margin-top: 6px;
}

.chat-runid {
  font-size: 12px;
  color: var(--n-text-color-3, #98a2b3);
  word-break: break-all;
}

.chat-image-hint {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  margin-top: 4px;
  font-size: 12px;
  color: var(--n-text-color-3, #98a2b3);
}

/* 与 .chat-image-hint 同一视觉权重：都是「写出来免得你以为是坏了」的说明行 */
.chat-citations-hint {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  margin-top: 4px;
  font-size: 12px;
  color: var(--n-text-color-3, #98a2b3);
}

.chat-disclaimer {
  margin-top: 2px;
  text-align: center;
  font-size: 12px;
  color: var(--n-text-color-3, #98a2b3);
}
</style>
