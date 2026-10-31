<template>
  <div class="ai-pg" :style="cssVars">
    <n-card :bordered="false" class="pg-card" content-style="padding:0;display:flex;flex-direction:column;flex:1;min-height:0;">
      <!-- 顶部栏 -->
      <header class="pg-head">
        <div class="pg-id">
          <div class="pg-logo">
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor"
                 stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <rect width="18" height="10" x="3" y="11" rx="2" /><circle cx="12" cy="5" r="2" />
              <path d="M12 7v4" /><line x1="8" x2="8" y1="16" y2="16" /><line x1="16" x2="16" y1="16" y2="16" />
            </svg>
          </div>
          <div>
            <div class="pg-name">对话试验台</div>
            <div class="pg-desc">验证当前 AI 配置是否可正常对话</div>
          </div>
        </div>
        <div class="pg-status">
          <n-tag :type="available ? 'success' : 'warning'" round size="small" :bordered="false">
            <template #icon><span class="pg-dot" :class="{ on: available }" /></template>
            {{ available ? (model || '已就绪') : '未就绪' }}
          </n-tag>
          <n-tooltip>
            <template #trigger>
              <n-button quaternary circle size="small" :loading="checking" @click="loadStatus">
                <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor"
                     stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8" /><path d="M21 3v5h-5" />
                  <path d="M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16" /><path d="M3 21v-5h5" />
                </svg>
              </n-button>
            </template>
            重新检测
          </n-tooltip>
        </div>
      </header>

      <n-alert v-if="!available && !checking" type="warning" :bordered="false" class="pg-alert">
        AI 未启用或配置不完整，请先到
        <n-button text type="primary" @click="goConfig">「AI 配置」</n-button>
        填写 Provider / API Key 并测试通过。
      </n-alert>

      <!-- 消息区 -->
      <div ref="boxRef" class="pg-body">
        <div v-if="!messages.length" class="pg-empty">
          <div class="pg-empty-icon">
            <svg viewBox="0 0 24 24" width="30" height="30" fill="none" stroke="currentColor"
                 stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
              <path d="m12 3-1.9 5.8a2 2 0 0 1-1.3 1.3L3 12l5.8 1.9a2 2 0 0 1 1.3 1.3L12 21l1.9-5.8a2 2 0 0 1 1.3-1.3L21 12l-5.8-1.9a2 2 0 0 1-1.3-1.3Z" />
            </svg>
          </div>
          <div class="pg-empty-title">开始一段对话</div>
          <div class="pg-empty-hint">输入问题，按 Ctrl / ⌘ + Enter 发送</div>
          <div class="pg-suggest">
            <n-button v-for="s in suggests" :key="s" size="small" round secondary :disabled="!available"
                      @click="pickSuggest(s)">
              {{ s }}
            </n-button>
          </div>
        </div>

        <div v-for="(m, i) in messages" :key="i" class="pg-msg" :class="m.role">
          <div class="pg-avatar" :class="m.role">
            <svg v-if="m.role === 'assistant'" viewBox="0 0 24 24" width="18" height="18" fill="none"
                 stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <rect width="18" height="10" x="3" y="11" rx="2" /><circle cx="12" cy="5" r="2" />
              <path d="M12 7v4" /><line x1="8" x2="8" y1="16" y2="16" /><line x1="16" x2="16" y1="16" y2="16" />
            </svg>
            <svg v-else viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor"
                 stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2" /><circle cx="12" cy="7" r="4" />
            </svg>
          </div>
          <div class="pg-bubble" :class="m.role">
            <span v-if="!m.content && loading && i === messages.length - 1" class="pg-typing"><span /><span /><span /></span>
            <template v-else>{{ m.content }}</template>
          </div>
        </div>
      </div>

      <!-- 输入区 -->
      <footer class="pg-foot">
        <div class="pg-sys">
          <n-button text size="tiny" :focusable="false" @click="showSystem = !showSystem">
            {{ showSystem ? '▾ 收起系统提示' : '▸ 系统提示（可选）' }}
          </n-button>
          <n-input v-if="showSystem" v-model:value="system" size="small" clearable class="pg-sys-input"
                   placeholder="设定助手角色 / 风格，例：你是简洁严谨的中文技术助手" />
        </div>
        <div class="pg-compose">
          <n-input v-model:value="input" type="textarea" :autosize="{ minRows: 1, maxRows: 5 }"
                   :disabled="!available" placeholder="输入问题…" @keydown="onKey" />
          <div class="pg-ops">
            <n-button v-if="messages.length" quaternary size="small" :focusable="false" @click="clear">
              清空
            </n-button>
            <n-button type="primary" :loading="loading" :disabled="!available || !input.trim()" @click="send">
              <template #icon>
                <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor"
                     stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M14.536 21.686a.5.5 0 0 0 .937-.024l6.5-19a.496.496 0 0 0-.635-.635l-19 6.5a.5.5 0 0 0-.024.937l7.93 3.18a2 2 0 0 1 1.112 1.11z" />
                  <path d="m21.854 2.147-10.94 10.939" />
                </svg>
              </template>
              发送
            </n-button>
          </div>
        </div>
      </footer>
    </n-card>
  </div>
</template>

<script lang="ts" setup>
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useThemeVars } from 'naive-ui'
import { status, chat, streamChat } from '../api/ai'

interface Msg { role: 'user' | 'assistant'; content: string }

const router = useRouter()
const t = useThemeVars()

const available = ref(false)
const checking = ref(false)
const model = ref('')
const messages = ref<Msg[]>([])
const input = ref('')
const system = ref('')
const showSystem = ref(false)
const loading = ref(false)
const boxRef = ref<HTMLElement>()
let abort: AbortController | null = null

const suggests = ['用一句话介绍你自己', '把下面这段话润色得更书面：……', '帮我写一个 Kotlin 数据类示例']

const cssVars = computed(() => ({
  '--pg-card': t.value.cardColor,
  '--pg-chat': t.value.actionColor,
  '--pg-border': t.value.dividerColor,
  '--pg-text': t.value.textColor2,
  '--pg-text3': t.value.textColor3,
  '--pg-primary': t.value.primaryColor,
  '--pg-primary-hover': t.value.primaryColorHover,
}))

async function loadStatus() {
  checking.value = true
  try {
    const res: any = await status()
    available.value = !!res.data?.chatAvailable
    model.value = res.data?.model ?? ''
  } catch {
    available.value = false
  } finally {
    checking.value = false
  }
}

function goConfig() {
  router.push('/ai/config')
}

function pickSuggest(s: string) {
  input.value = s
}

function onKey(e: KeyboardEvent) {
  if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') {
    e.preventDefault()
    send()
  }
}

function clear() {
  messages.value = []
}

async function scrollBottom() {
  await nextTick()
  boxRef.value?.scrollTo({ top: boxRef.value.scrollHeight, behavior: 'smooth' })
}

async function send() {
  const text = input.value.trim()
  if (!text || loading.value || !available.value) return
  messages.value.push({ role: 'user', content: text })
  input.value = ''
  loading.value = true
  const sys = system.value?.trim() || undefined
  const idx = messages.value.push({ role: 'assistant', content: '' }) - 1
  await scrollBottom()
  abort?.abort()
  abort = new AbortController()
  try {
    // 优先流式；服务端 / 网关不支持时回退普通对话
    const { streamed } = await streamChat(
      { user: text, system: sys },
      (delta) => {
        messages.value[idx].content += delta
        scrollBottom()
      },
      abort.signal,
    )
    if (!streamed) {
      const res: any = await chat({ user: text, system: sys })
      messages.value[idx].content = res.data?.content ?? '（空响应）'
    } else if (!messages.value[idx].content) {
      messages.value[idx].content = '（空响应）'
    }
  } catch {
    // 流式失败：未产出任何内容则回退普通对话，否则提示已中断
    if (!messages.value[idx].content) {
      try {
        const res: any = await chat({ user: text, system: sys })
        messages.value[idx].content = res.data?.content ?? '（空响应）'
      } catch {
        messages.value[idx].content = '⚠️ 调用失败，请到「AI 配置」检查 Provider / API Key。'
      }
    } else {
      messages.value[idx].content += '\n\n⚠️ 输出已中断'
    }
  } finally {
    loading.value = false
    await scrollBottom()
  }
}

onMounted(loadStatus)
onUnmounted(() => abort?.abort())
</script>

<style scoped>
.ai-pg {
  height: calc(100vh - 185px);
  min-height: 460px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.pg-card {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  border-radius: 12px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
}

/* 顶部栏 */
.pg-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 18px;
  border-bottom: 1px solid var(--pg-border);
}
.pg-id {
  display: flex;
  align-items: center;
  gap: 12px;
}
.pg-logo {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  border-radius: 10px;
  color: #fff;
  background: linear-gradient(135deg, var(--pg-primary), var(--pg-primary-hover));
  box-shadow: 0 4px 10px -3px var(--pg-primary);
}
.pg-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--pg-text);
}
.pg-desc {
  font-size: 12px;
  color: var(--pg-text3);
  margin-top: 1px;
}
.pg-status {
  display: flex;
  align-items: center;
  gap: 8px;
}
.pg-dot {
  display: inline-block;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #d03050;
}
.pg-dot.on {
  background: #18a058;
  box-shadow: 0 0 0 3px rgba(24, 160, 88, 0.18);
}

.pg-alert {
  margin: 12px 18px 0;
  border-radius: 8px;
}

/* 消息区 */
.pg-body {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
  padding: 18px;
  background: var(--pg-chat);
}
.pg-empty {
  flex:1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  color: var(--pg-text3);
}
.pg-empty-icon {
  display: grid;
  place-items: center;
  width: 64px;
  height: 64px;
  border-radius: 50%;
  margin-bottom: 8px;
  color: var(--pg-primary);
  background: var(--pg-card);
  box-shadow: 0 6px 18px -10px var(--pg-primary);
}
.pg-empty-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--pg-text);
}
.pg-empty-hint {
  font-size: 12.5px;
}
.pg-suggest {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: center;
  margin-top: 14px;
  max-width: 520px;
}

.pg-msg {
  display: flex;
  flex-shrink: 0;
  gap: 10px;
  margin-bottom: 16px;
  align-items: flex-start;
}
.pg-msg.user {
  flex-direction: row-reverse;
}
.pg-avatar {
  flex: none;
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border-radius: 10px;
}
.pg-avatar.assistant {
  color: #fff;
  background: linear-gradient(135deg, var(--pg-primary), var(--pg-primary-hover));
}
.pg-avatar.user {
  color: var(--pg-text);
  background: var(--pg-card);
  border: 1px solid var(--pg-border);
}
.pg-bubble {
  max-width: min(76%, 720px);
  padding: 10px 14px;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.65;
  white-space: pre-wrap;
  word-break: break-word;
}
.pg-bubble.assistant {
  color: var(--pg-text);
  background: var(--pg-card);
  border: 1px solid var(--pg-border);
  border-top-left-radius: 3px;
}
.pg-bubble.user {
  color: #fff;
  background: var(--pg-primary);
  border-top-right-radius: 3px;
}

/* 输入气泡的「思考中」动画 */
.pg-typing {
  display: inline-flex;
  gap: 5px;
  align-items: center;
}
.pg-typing span {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--pg-text3);
  animation: pg-bounce 1.2s infinite ease-in-out;
}
.pg-typing span:nth-child(2) {
  animation-delay: 0.18s;
}
.pg-typing span:nth-child(3) {
  animation-delay: 0.36s;
}
@keyframes pg-bounce {
  0%, 60%, 100% {
    transform: translateY(0);
    opacity: 0.4;
  }
  30% {
    transform: translateY(-5px);
    opacity: 1;
  }
}

/* 输入区 */
.pg-foot {
  padding: 12px 18px 16px;
  border-top: 1px solid var(--pg-border);
  background: var(--pg-card);
}
.pg-sys {
  margin-bottom: 8px;
}
.pg-sys-input {
  margin-top: 6px;
}
.pg-compose {
  display: flex;
  gap: 10px;
  align-items: flex-end;
}
.pg-compose :deep(.n-input) {
  flex: 1;
}
.pg-ops {
  display: flex;
  gap: 8px;
  align-items: center;
}
</style>
