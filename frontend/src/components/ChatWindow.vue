<script setup>
import { computed, nextTick, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { createSession, getMessages, streamChat } from '../api/chat.js'
import { renderMarkdown } from '../utils/markdown'

const props = defineProps({ session: { type: Object, default: null } })
const emit = defineEmits(['created', 'refreshed'])

const sessionCache = ref({})
const abortControllers = reactive(new Map())
const metaBySession = reactive(new Map())
const lastRequestBySession = reactive(new Map())
const taskMode = ref(false)
const input = ref('')
const inputRef = ref(null)
const listRef = ref(null)
const now = ref(Date.now())
const clock = setInterval(() => { now.value = Date.now() }, 1000)

const messages = computed(() => {
  const id = props.session?.id
  return id ? (sessionCache.value[id] || []) : []
})
const loading = computed(() => {
  const id = props.session?.id
  return id ? abortControllers.has(id) : false
})
const currentMeta = computed(() => metaBySession.get(props.session?.id) || null)
const elapsedSeconds = computed(() => {
  const meta = currentMeta.value
  if (!meta?.startedAt) return 0
  return Math.max(0, Math.floor((now.value - meta.startedAt) / 1000))
})

function ensureMeta(id) {
  if (!metaBySession.has(id)) {
    metaBySession.set(id, {
      status: 'idle', statusMessage: 'Ready', requestId: '', startedAt: 0,
      lastEventAt: 0, lastEvent: '', plan: [], step: 0, total: 0, error: ''
    })
  }
  return metaBySession.get(id)
}

function patchMeta(id, patch) {
  Object.assign(ensureMeta(id), patch, { lastEventAt: Date.now() })
}

function scrollToBottom() {
  nextTick(() => {
    if (listRef.value) listRef.value.scrollTop = listRef.value.scrollHeight
  })
}

watch(() => props.session?.id, async (id) => {
  if (!id) return
  ensureMeta(id)
  if (!sessionCache.value[id]) {
    sessionCache.value[id] = []
    try {
      const res = await getMessages(id)
      const history = res.data || res || []
      if (history.length) sessionCache.value[id] = history
    } catch (error) {
      patchMeta(id, { status: 'error', statusMessage: 'Unable to load history', error: error.message })
    }
  }
  scrollToBottom()
}, { immediate: true })

defineExpose({
  abortSession(id) {
    abortControllers.get(id)?.abort()
    abortControllers.delete(id)
    delete sessionCache.value[id]
    patchMeta(id, { status: 'cancelled', statusMessage: 'Cancelled by user' })
  }
})

async function send(textOverride = null) {
  const rawText = typeof textOverride === 'string' ? textOverride : input.value
  const text = typeof rawText === 'string' ? rawText.trim() : ''
  if (!text || loading.value) return
  const useTaskMode = taskMode.value || text.length > 300

  let sessionId = props.session?.id
  if (!sessionId) {
    try {
      const created = await createSession()
      const session = created.data || created
      sessionId = session.id
      emit('created', session)
    } catch (error) {
      alert(`Unable to create session: ${error.message || 'unknown error'}`)
      return
    }
  }

  sessionCache.value[sessionId] ||= []
  sessionCache.value[sessionId].push({ role: 'user', content: text })
  input.value = ''
  const marker = `msg_${Date.now()}`
  sessionCache.value[sessionId].push({ role: 'assistant', content: '', _flag: marker })
  lastRequestBySession.set(sessionId, text)

  const controller = new AbortController()
  abortControllers.set(sessionId, controller)
  patchMeta(sessionId, {
    status: 'submitting', statusMessage: 'Submitting request', startedAt: Date.now(),
    requestId: '', lastEvent: 'request created', error: '', plan: [], step: 0, total: 0
  })
  scrollToBottom()

  const target = () => sessionCache.value[sessionId]?.find(item => item._flag === marker)
  try {
    await streamChat({
      sessionId,
      message: text,
      taskMode: useTaskMode,
      signal: controller.signal,
      onPlan: event => patchMeta(sessionId, {
        requestId: event.requestId || '', plan: event.steps || [], total: event.total || 0,
        status: 'planned', statusMessage: 'Plan received'
      }),
      onStep: event => patchMeta(sessionId, {
        step: event.step || 0, total: event.total || 0,
        status: event.type === 'step_done' ? 'step_done' : 'step_started',
        statusMessage: event.type === 'step_done' ? `Finished step ${event.step}` : `Starting step ${event.step}: ${event.title}`,
        lastEvent: event.type
      }),
      onStatus: event => patchMeta(sessionId, {
        status: event.status || 'working', statusMessage: event.message || event.status || 'Working',
        lastEvent: event.code || event.status || 'status'
      }),
      onHeartbeat: () => patchMeta(sessionId, { lastEvent: 'heartbeat', statusMessage: 'Connection is healthy; model is still working' }),
      onToken: token => {
        const message = target()
        if (message) message.content += token
        patchMeta(sessionId, { status: 'generating', statusMessage: 'Generating content', lastEvent: 'token' })
        if (props.session?.id === sessionId) scrollToBottom()
      },
      onDone: (finalText) => {
        const message = target()
        if (message && typeof finalText === 'string' && finalText) {
          message.content = finalText
        } else if (message && !message.content) {
          message.content = '(No visible answer returned)'
        }
        patchMeta(sessionId, { status: 'done', statusMessage: 'Completed', lastEvent: 'done' })
        emit('refreshed')
      },
      onError: error => patchMeta(sessionId, {
        status: 'error', statusMessage: error.message || 'Generation failed',
        error: `${error.code || 'UNKNOWN'}: ${error.message || 'unknown error'}`, lastEvent: 'error'
      })
    })
  } catch (error) {
    const message = target()
    if (message && error.name !== 'AbortError' && !message.content) {
      message.content = `Error: ${error.message || 'generation failed'}`
    }
    if (error.name === 'AbortError') patchMeta(sessionId, { status: 'cancelled', statusMessage: 'Cancelled by user' })
  } finally {
    abortControllers.delete(sessionId)
    if (props.session?.id === sessionId) {
      scrollToBottom()
      nextTick(() => inputRef.value?.focus())
    }
  }
}

function retry() {
  const id = props.session?.id
  const text = id ? lastRequestBySession.get(id) : ''
  if (text && !loading.value) send(text)
}

function stop() {
  const id = props.session?.id
  abortControllers.get(id)?.abort()
  abortControllers.delete(id)
  if (id) patchMeta(id, { status: 'cancelled', statusMessage: 'Stopping generation...' })
}

function handleKeydown(event) {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    send()
  }
}

onBeforeUnmount(() => {
  clearInterval(clock)
  abortControllers.forEach(controller => controller.abort())
})
</script>

<template>
  <main class="chat-window">
    <header class="chat-header">
      <div class="header-left">
        <span class="header-dot" :class="{ busy: loading, error: currentMeta?.status === 'error' }"></span>
        <div>
          <div class="header-title">{{ session?.title || 'New chat' }}</div>
          <div class="status-line">{{ currentMeta?.statusMessage || 'Ready' }}<span v-if="loading"> · {{ elapsedSeconds }}s</span></div>
        </div>
      </div>
      <div class="header-actions">
        <label class="mode-toggle"><input v-model="taskMode" type="checkbox" /> phased task mode (auto for long requests)</label>
        <button v-if="loading" class="btn-stop" @click="stop">Stop</button>
        <button v-else-if="currentMeta?.status === 'error' || currentMeta?.status === 'cancelled'" class="btn-retry" @click="retry">Retry</button>
      </div>
    </header>

    <section v-if="currentMeta?.plan?.length" class="task-panel">
      <div class="task-panel-title">Task plan <span>{{ currentMeta.step || 0 }}/{{ currentMeta.total }}</span></div>
      <ol><li v-for="(step, index) in currentMeta.plan" :key="step" :class="{ active: index + 1 === currentMeta.step, done: index + 1 < currentMeta.step }">{{ step }}</li></ol>
    </section>

    <div class="message-list" ref="listRef">
      <div v-if="messages.length === 0" class="empty-state"><div class="empty-logo">AI</div><h2>Ready when you are</h2><p>Ask a question or enable phased task mode for larger implementation requests.</p></div>
      <div v-for="(message, index) in messages" :key="index" class="message-row" :class="message.role">
        <div class="avatar">{{ message.role === 'user' ? 'You' : 'AI' }}</div>
        <div class="bubble">
          <div v-if="message.role === 'assistant' && message.content" class="markdown-body" v-html="renderMarkdown(message.content)"></div>
          <div v-else-if="message.role === 'assistant' && loading && index === messages.length - 1" class="typing"><span></span><span></span><span></span></div>
          <div v-else class="plain-text">{{ message.content }}</div>
        </div>
      </div>
    </div>

    <details v-if="currentMeta && (loading || currentMeta.requestId || currentMeta.error)" class="diagnostics">
      <summary>Diagnostics</summary>
      <div>requestId: <code>{{ currentMeta.requestId || 'waiting for server' }}</code></div>
      <div>status: {{ currentMeta.status }}</div>
      <div>last event: {{ currentMeta.lastEvent || 'none' }}</div>
      <div v-if="currentMeta.lastEventAt">last event at: {{ new Date(currentMeta.lastEventAt).toLocaleTimeString() }}</div>
      <div v-if="currentMeta.error" class="diagnostic-error">{{ currentMeta.error }}</div>
    </details>

    <footer class="input-area">
      <div class="input-box"><textarea ref="inputRef" v-model="input" :disabled="loading" rows="1" placeholder="Type a message. Enter sends, Shift+Enter adds a line." @keydown="handleKeydown"></textarea><button class="send-btn" :disabled="loading || !input.trim()" @click="send()">➤</button></div>
      <div class="input-hint">The app reports connection, model, timeout, and task-step status in real time.</div>
    </footer>
  </main>
</template>

<style scoped>
.chat-window{flex:1;height:100%;display:flex;flex-direction:column;background:#f7f8fa}.chat-header{min-height:60px;display:flex;align-items:center;justify-content:space-between;padding:0 20px;background:#fff;border-bottom:1px solid #e8eaef}.header-left{display:flex;align-items:center;gap:10px}.header-dot{width:8px;height:8px;border-radius:50%;background:#22c55e}.header-dot.busy{background:#f59e0b}.header-dot.error{background:#ef4444}.header-title{font-size:15px;font-weight:600;color:#1f2937}.status-line{font-size:12px;color:#6b7280;margin-top:3px}.header-actions{display:flex;align-items:center;gap:10px}.mode-toggle{font-size:12px;color:#4b5563}.btn-stop,.btn-retry{border:1px solid #e5e7eb;background:#fff;color:#b42318;font-size:13px;padding:6px 12px;border-radius:6px;cursor:pointer}.btn-retry{color:#2563eb}.task-panel{padding:10px 20px;background:#fff;border-bottom:1px solid #e8eaef}.task-panel-title{font-size:12px;color:#4b5563;display:flex;justify-content:space-between}.task-panel ol{display:flex;gap:8px;list-style:none;padding:8px 0 0;margin:0;overflow:auto}.task-panel li{white-space:nowrap;font-size:12px;color:#9ca3af;padding:5px 8px;border:1px solid #e5e7eb;border-radius:5px}.task-panel li.active{color:#1d4ed8;border-color:#93c5fd;background:#eff6ff}.task-panel li.done{color:#15803d;border-color:#86efac;background:#f0fdf4}.message-list{flex:1;overflow-y:auto;padding:24px 20px}.empty-state{max-width:520px;margin:60px auto;text-align:center;color:#6b7280}.empty-logo{width:64px;height:64px;margin:0 auto 16px;border-radius:18px;background:#4f46e5;color:#fff;font-size:24px;font-weight:700;display:flex;align-items:center;justify-content:center}.empty-state h2{margin:0 0 8px;color:#1f2937;font-size:22px}.empty-state p{font-size:14px;line-height:1.7}.message-row{display:flex;gap:10px;max-width:900px;margin:0 auto 18px}.message-row.user{flex-direction:row-reverse}.avatar{width:30px;height:30px;border-radius:50%;background:#e5e7eb;color:#374151;font-size:10px;display:flex;align-items:center;justify-content:center;flex:none}.message-row.assistant .avatar{background:#4f46e5;color:#fff}.bubble{max-width:78%;padding:11px 14px;border-radius:8px;background:#fff;color:#1f2937;line-height:1.6;box-shadow:0 1px 2px rgba(0,0,0,.04)}.message-row.user .bubble{background:#eef2ff}.plain-text{white-space:pre-wrap}.typing{display:flex;gap:4px;padding:4px}.typing span{width:6px;height:6px;border-radius:50%;background:#9ca3af;animation:blink 1.2s infinite}.typing span:nth-child(2){animation-delay:.15s}.typing span:nth-child(3){animation-delay:.3s}@keyframes blink{0%,80%,100%{opacity:.25}40%{opacity:1}}.diagnostics{margin:0 20px 8px;padding:8px 10px;background:#fff;border:1px solid #e5e7eb;border-radius:6px;color:#6b7280;font-size:11px;line-height:1.8}.diagnostics summary{cursor:pointer;color:#374151}.diagnostic-error{color:#b42318}.input-area{padding:12px 20px;background:#fff;border-top:1px solid #e8eaef}.input-box{display:flex;gap:8px;max-width:900px;margin:auto;border:1px solid #d1d5db;border-radius:8px;padding:8px;background:#fff}.input-box textarea{flex:1;border:0;resize:none;outline:0;font:inherit;line-height:1.5}.send-btn{width:36px;border:0;border-radius:6px;background:#4f46e5;color:#fff;cursor:pointer}.send-btn:disabled{opacity:.4;cursor:not-allowed}.input-hint{max-width:900px;margin:6px auto 0;color:#9ca3af;font-size:11px}
@media (max-width:700px){.header-actions{gap:5px}.mode-toggle{font-size:11px}.task-panel ol{display:block}.task-panel li{display:inline-block;margin:2px}.bubble{max-width:85%}}
</style>
