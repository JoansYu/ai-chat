import request from '../utils/request.js'
import { getToken, clearToken } from '../utils/auth.js'
import { fetchEventSource } from '@microsoft/fetch-event-source'

export async function login(username, password) {
  return request.post('/login', { username, password })
}

export async function register(username, password) {
  return request.post('/register', { username, password })
}

export async function createSession() {
  return request.post('/sessions')
}

export async function listSessions() {
  return request.get('/sessions')
}

export async function deleteSession(id) {
  return request.delete(`/sessions/${id}`)
}

export async function getMessages(sessionId) {
  return request.get(`/sessions/${sessionId}/messages`)
}

/**
 * Streams a chat response and exposes observable lifecycle events.
 * Hidden model reasoning is never returned; only safe progress metadata is exposed.
 */
export async function streamChat({
  sessionId,
  message,
  taskMode = false,
  onToken,
  onDone,
  onPlan,
  onStep,
  onStatus,
  onHeartbeat,
  onError,
  signal
}) {
  const baseUrl = import.meta.env?.VITE_API_BASE_URL || '/api'
  const internal = new AbortController()
  const seenEventIds = new Set()
  let fullText = ''
  let completed = false
  let timeoutError = null
  let firstTimer = null
  let idleTimer = null
  let totalTimer = null

  const FIRST_ACTIVITY_TIMEOUT = 45_000
  const IDLE_TIMEOUT = 180_000
  const TOTAL_TIMEOUT = 10 * 60_000

  const abortFromCaller = () => internal.abort()
  signal?.addEventListener('abort', abortFromCaller, { once: true })

  const clearTimers = () => {
    clearTimeout(firstTimer)
    clearTimeout(idleTimer)
    clearTimeout(totalTimer)
  }

  const timeout = (code, message) => {
    timeoutError = new Error(message)
    timeoutError.code = code
    onStatus?.({ status: 'timeout', message, code })
    internal.abort()
  }

  const armFirstActivityTimer = () => {
    clearTimeout(firstTimer)
    firstTimer = setTimeout(() => timeout('FIRST_ACTIVITY_TIMEOUT', 'Connected to the server, but no SSE activity was received for 45 seconds'), FIRST_ACTIVITY_TIMEOUT)
  }

  const armIdleTimer = () => {
    clearTimeout(idleTimer)
    idleTimer = setTimeout(() => timeout('IDLE_TIMEOUT', '已超过 180 秒没有收到模型事件，连接可能已中断'), IDLE_TIMEOUT)
  }

  totalTimer = setTimeout(() => timeout('TOTAL_TIMEOUT', '本次任务超过 10 分钟，已自动停止'), TOTAL_TIMEOUT)

  try {
    await fetchEventSource(`${baseUrl}/chat/stream`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        satoken: getToken() || ''
      },
      body: JSON.stringify({ sessionId, message, taskMode }),
      signal: internal.signal,

      async onopen(response) {
        if (response.ok) {
          onStatus?.({ status: 'connected', message: '已连接模型，正在等待首个结果' })
          armFirstActivityTimer()
          armIdleTimer()
          return
        }
        if (response.status === 401) {
          clearToken()
          throw new Error('登录状态已失效，请重新登录')
        }
        throw new Error(`请求失败 (HTTP ${response.status})`)
      },

      onmessage(msg) {
        if (!msg.data) return
        if (msg.id) {
          if (seenEventIds.has(msg.id)) return
          seenEventIds.add(msg.id)
        }

        let event
        try {
          event = JSON.parse(msg.data)
        } catch (error) {
          const parseError = new Error(`SSE 数据解析失败: ${error.message}`)
          parseError.code = 'SSE_PARSE_ERROR'
          onError?.(parseError)
          throw parseError
        }

        clearTimeout(firstTimer)
        armIdleTimer()
        switch (event.type) {
          case 'plan':
            onPlan?.(event)
            break
          case 'step_started':
          case 'step_done':
            onStep?.(event)
            break
          case 'status':
            onStatus?.(event)
            break
          case 'heartbeat':
            onHeartbeat?.(event)
            break
          case 'token': {
            const content = event.content || ''
            fullText += content
            onStatus?.({ status: 'generating', message: '正在生成内容' })
            onToken?.(content)
            break
          }
          case 'done':
            completed = true
            clearTimers()
            onDone?.(event.content ?? fullText, event)
            break
          case 'error': {
            const error = new Error(event.message || '服务端生成失败')
            error.code = event.code || 'SERVER_ERROR'
            throw error
          }
          default:
            onStatus?.({ status: 'unknown', message: `收到未知事件: ${event.type || 'empty'}` })
        }
      },

      onclose() {
        if (!completed) {
          const error = timeoutError || new Error('SSE 连接在收到 done 之前关闭')
          error.code ||= 'STREAM_CLOSED'
          throw error
        }
      },

      onerror(error) {
        const finalError = timeoutError || error
        onError?.(finalError)
        throw finalError
      }
    })
  } finally {
    clearTimers()
    signal?.removeEventListener('abort', abortFromCaller)
  }

  return fullText
}
