/**
 * 后端 API 封装
 */

const BASE = '/api'

async function handle(res) {
  if (!res.ok) {
    let message = `请求失败（HTTP ${res.status}）`
    try {
      const data = await res.json()
      if (data?.message) message = data.message
    } catch {
      // ignore
    }
    throw new Error(message)
  }
  return res.json()
}

/** 创建新会话 */
export async function createSession() {
  const res = await fetch(`${BASE}/sessions`, { method: 'POST' })
  return handle(res)
}

/** 会话列表 */
export async function listSessions() {
  const res = await fetch(`${BASE}/sessions`)
  return handle(res)
}

/** 删除会话 */
export async function deleteSession(id) {
  const res = await fetch(`${BASE}/sessions/${id}`, { method: 'DELETE' })
  if (!res.ok) {
    throw new Error(`删除失败（HTTP ${res.status}）`)
  }
}

/** 获取会话历史消息 */
export async function getMessages(sessionId) {
  const res = await fetch(`${BASE}/sessions/${sessionId}/messages`)
  return handle(res)
}

/**
 * 流式对话
 * @param {Object} opts
 * @param {string} opts.sessionId 会话 ID（可为空，由后端自动创建）
 * @param {string} opts.message 用户消息
 * @param {Function} opts.onToken 每个增量 token 回调 (text) => void
 * @param {Function} opts.onDone 完整回复回调 (text) => void
 * @param {Function} opts.onError 错误回调 (err) => void
 * @param {AbortSignal} [opts.signal] 取消信号
 * @returns {Promise<string>} 完整回复
 */
export async function streamChat({ sessionId, message, onToken, onDone, onError, signal }) {
  const res = await fetch(`${BASE}/chat/stream`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ sessionId, message }),
    signal
  })

  if (!res.ok) {
    const err = new Error(`对话请求失败（HTTP ${res.status}）`)
    onError?.(err)
    throw err
  }

  const reader = res.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  let full = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })

    // 按空行切分 SSE 事件
    let idx
    while ((idx = buffer.indexOf('\n\n')) >= 0) {
      const raw = buffer.slice(0, idx)
      buffer = buffer.slice(idx + 2)
      const lines = raw.split('\n').filter((l) => l.startsWith('data:'))
      for (const line of lines) {
        const data = line.slice(5).trim()
        if (!data) continue
        let evt
        try {
          evt = JSON.parse(data)
        } catch {
          continue
        }
        if (evt.type === 'token') {
          full += evt.content ?? ''
          onToken?.(evt.content ?? '')
        } else if (evt.type === 'done') {
          onDone?.(evt.content ?? full)
        } else if (evt.type === 'error') {
          const err = new Error(evt.message || '对话出错')
          onError?.(err)
          throw err
        }
      }
    }
  }

  return full
}
