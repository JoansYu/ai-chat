<script setup>
import { ref, watch, nextTick, onBeforeUnmount } from 'vue'
import { streamChat, getMessages, createSession } from '../api'
import { renderMarkdown } from '../utils/markdown'

const props = defineProps({
  session: { type: Object, default: null }
})

const emit = defineEmits(['new', 'created', 'refreshed'])

const messages = ref([])
const input = ref('')
const loading = ref(false)
const inputRef = ref(null)
const listRef = ref(null)
let abortController = null

watch(
  () => props.session?.id,
  async (id) => {
    messages.value = []
    if (id) {
      try {
        messages.value = await getMessages(id)
      } catch (e) {
        console.error('加载历史消息失败', e)
      }
    }
    scrollToBottom()
  },
  { immediate: true }
)

function scrollToBottom() {
  nextTick(() => {
    if (listRef.value) {
      listRef.value.scrollTop = listRef.value.scrollHeight
    }
  })
}

async function send() {
  const text = input.value.trim()
  if (!text || loading.value) return

  // 确保有会话 ID（没有则先创建）
  let sessionId = props.session?.id
  if (!sessionId) {
    try {
      const created = await createSession()
      sessionId = created.id
      emit('created', created)
    } catch (e) {
      alert('创建会话失败：' + e.message)
      return
    }
  }

  // 追加用户消息
  messages.value.push({ role: 'user', content: text })
  input.value = ''
  scrollToBottom()

  // 追加空的助手消息，用于流式填充
  messages.value.push({ role: 'assistant', content: '' })
  const aiIndex = messages.value.length - 1
  loading.value = true
  abortController = new AbortController()

  try {
    await streamChat({
      sessionId,
      message: text,
      signal: abortController.signal,
      onToken: (token) => {
        messages.value[aiIndex].content += token
        scrollToBottom()
      },
      onDone: () => {
        if (messages.value[aiIndex].content === '') {
          messages.value[aiIndex].content = '（无回复）'
        }
        emit('refreshed')
      }
    })
  } catch (e) {
    if (e.name !== 'AbortError') {
      messages.value[aiIndex].content = '⚠️ ' + (e.message || '对话出错，请稍后重试')
    }
  } finally {
    loading.value = false
    abortController = null
    scrollToBottom()
    inputRef.value?.focus()
  }
}

function handleKeydown(e) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    send()
  }
}

function stop() {
  abortController?.abort()
}

onBeforeUnmount(() => {
  abortController?.abort()
})
</script>

<template>
  <main class="chat-window">
    <header class="chat-header">
      <div class="header-left">
        <span class="header-dot"></span>
        <span class="header-title">{{ session?.title || '新对话' }}</span>
      </div>
      <button v-if="loading" class="btn-stop" @click="stop">■ 停止生成</button>
    </header>

    <div class="message-list" ref="listRef">
      <!-- 空状态 -->
      <div v-if="messages.length === 0" class="empty-state">
        <div class="empty-logo">AI</div>
        <h2>你好，我是 AI 助手</h2>
        <p>支持多轮对话，我会记住上下文，围绕你的话题持续深入回答。</p>
        <div class="suggest-cards">
          <button class="suggest-card" @click="input = '帮我写一个 Java 的冒泡排序示例'">
            <span class="suggest-icon">💻</span> 写一段代码
          </button>
          <button class="suggest-card" @click="input = '解释一下什么是 RESTful API，并举例子'">
            <span class="suggest-icon">📚</span> 解释概念
          </button>
          <button class="suggest-card" @click="input = '帮我制定一个学习 Spring Boot 的计划'">
            <span class="suggest-icon">📝</span> 制定学习计划
          </button>
        </div>
      </div>

      <!-- 消息列表 -->
      <div
        v-for="(m, i) in messages"
        :key="i"
        class="message-row"
        :class="m.role"
      >
        <div class="avatar">{{ m.role === 'user' ? '我' : 'AI' }}</div>
        <div class="bubble">
          <div v-if="m.role === 'assistant' && m.content" class="markdown-body" v-html="renderMarkdown(m.content)"></div>
          <div v-else-if="m.role === 'assistant' && loading && i === messages.length - 1" class="typing">
            <span></span><span></span><span></span>
          </div>
          <div v-else-if="m.role === 'assistant' && !m.content" class="typing">
            <span></span><span></span><span></span>
          </div>
          <div v-else class="plain-text">{{ m.content }}</div>
        </div>
      </div>
    </div>

    <footer class="input-area">
      <div class="input-box">
        <textarea
          ref="inputRef"
          v-model="input"
          :disabled="loading"
          rows="1"
          placeholder="输入消息，Enter 发送，Shift+Enter 换行"
          @keydown="handleKeydown"
        ></textarea>
        <button
          class="send-btn"
          :disabled="loading || !input.trim()"
          @click="send"
        >
          <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor">
            <path d="M2.01 21L23 12 2.01 3 2 10l15 2-15 2z" />
          </svg>
        </button>
      </div>
      <div class="input-hint">内容由 AI 生成，请注意甄别 · 当前引擎可在后端 application.yml 中配置</div>
    </footer>
  </main>
</template>

<style scoped>
.chat-window {
  flex: 1;
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #f7f8fa;
}

.chat-header {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  background: #ffffff;
  border-bottom: 1px solid #e8eaef;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.header-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #22c55e;
}

.header-title {
  font-size: 15px;
  font-weight: 600;
  color: #1f2937;
}

.btn-stop {
  border: 1px solid #e5e7eb;
  background: #fff;
  color: #ef4444;
  font-size: 13px;
  padding: 6px 12px;
  border-radius: 8px;
  cursor: pointer;
}

.btn-stop:hover {
  background: #fef2f2;
}

.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 24px 20px;
}

.empty-state {
  max-width: 520px;
  margin: 60px auto;
  text-align: center;
  color: #6b7280;
}

.empty-logo {
  width: 64px;
  height: 64px;
  margin: 0 auto 16px;
  border-radius: 18px;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: #fff;
  font-size: 24px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 8px 24px rgba(99, 102, 241, 0.3);
}

.empty-state h2 {
  margin: 0 0 8px;
  color: #1f2937;
  font-size: 22px;
}

.empty-state p {
  font-size: 14px;
  line-height: 1.7;
}

.suggest-cards {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 24px;
}

.suggest-card {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  background: #fff;
  color: #374151;
  font-size: 14px;
  text-align: left;
  cursor: pointer;
  transition: all 0.2s;
}

.suggest-card:hover {
  border-color: #c7d2fe;
  background: #eef2ff;
  transform: translateY(-1px);
}

.suggest-icon {
  font-size: 18px;
}

.message-row {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
  max-width: 820px;
}

.message-row.user {
  flex-direction: row-reverse;
  margin-left: auto;
}

.avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 600;
  color: #fff;
}

.message-row.assistant .avatar {
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
}

.message-row.user .avatar {
  background: #10b981;
}

.bubble {
  padding: 12px 16px;
  border-radius: 14px;
  font-size: 14px;
  line-height: 1.7;
  max-width: 100%;
  word-break: break-word;
}

.message-row.assistant .bubble {
  background: #ffffff;
  border: 1px solid #e8eaef;
  border-top-left-radius: 4px;
  color: #1f2937;
}

.message-row.user .bubble {
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: #fff;
  border-top-right-radius: 4px;
}

.plain-text {
  white-space: pre-wrap;
}

.typing {
  display: inline-flex;
  gap: 4px;
  padding: 4px 0;
}

.typing span {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #a5b4fc;
  animation: blink 1.2s infinite ease-in-out;
}

.typing span:nth-child(2) {
  animation-delay: 0.2s;
}

.typing span:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes blink {
  0%, 80%, 100% { opacity: 0.3; transform: translateY(0); }
  40% { opacity: 1; transform: translateY(-3px); }
}

.input-area {
  padding: 12px 20px 16px;
  background: #f7f8fa;
}

.input-box {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  max-width: 820px;
  margin: 0 auto;
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 14px;
  padding: 10px 12px;
  transition: border-color 0.2s, box-shadow 0.2s;
}

.input-box:focus-within {
  border-color: #6366f1;
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.12);
}

textarea {
  flex: 1;
  border: none;
  outline: none;
  resize: none;
  font-size: 14px;
  line-height: 1.6;
  max-height: 120px;
  font-family: inherit;
  color: #1f2937;
  background: transparent;
}

.send-btn {
  width: 38px;
  height: 38px;
  border: none;
  border-radius: 10px;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: #fff;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
  flex-shrink: 0;
}

.send-btn:hover:not(:disabled) {
  transform: scale(1.05);
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.35);
}

.send-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.input-hint {
  text-align: center;
  margin-top: 8px;
  font-size: 12px;
  color: #b0b4bf;
}
</style>
