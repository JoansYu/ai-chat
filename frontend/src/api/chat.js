import request from "../utils/request.js";
import { getToken, clearToken } from "../utils/auth.js";
import { fetchEventSource } from '@microsoft/fetch-event-source'

/**
 * 登录
 * @param username
 * @param password
 * @returns
 */
export async function login(username, password){
  return request.post('/login', {username, password})
}

export async function register(username, password) {
  return request.post('/register', {username, password})
}

// ===================== 工作区授权 =====================

export async function getWorkspaceStatus() {
  return request.get('/workspace')
}

export async function authorizeWorkspace(workspacePath) {
  return request.post('/workspace/authorize', {workspacePath})
}

export async function revokeWorkspace() {
  return request.post('/workspace/revoke')
}

export async function createSession(){
  return request.post('/sessions')
}

export async function listSessions() {
  return request.get('/sessions')
}

export async function deleteSession(id){
  return request.delete(`/sessions/${id}`)
}

export async function getMessages(sessionId){
  return request.get(`/sessions/${sessionId}/messages`)
}

export async function agentChat(data, config) {
  return request.post('/agent/chat', data, config)
}

/**
 * 流式 Agent 对话（SSE）
 * @param {Object} param0
 * @param {string} param0.sessionId - 会话ID
 * @param {string} param0.message - 用户消息
 * @param {Function} param0.onToken - 逐 token 回调
 * @param {Function} param0.onToolCall - 工具调用回调
 * @param {Function} param0.onToolResult - 工具结果回调
 * @param {Function} param0.onDone - 完成回调
 * @param {Function} param0.onError - 错误回调
 * @param {AbortSignal} param0.signal - 中断信号
 */
export async function streamAgentChat({sessionId, message, onToken, onToolCall, onToolResult, onDone, onError, signal}) {
  const BASE_URL = import.meta.env?.VITE_API_BASE_URL || '/api'
  let fullText = ''

  // ---- 前端侧日志：与服务端日志用同一个 traceId 对齐，便于端到端排查 ----
  const startedAt = Date.now()
  const tag = `[SSE][${sessionId || 'new'}]`
  const elapsed = () => `${Date.now() - startedAt}ms`
  let traceId = '-'
  let tokenCount = 0
  let firstTokenAt = null

  try {
    await fetchEventSource(`${BASE_URL}/agent/chat`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'satoken': getToken() || ''
      },
      body: JSON.stringify({sessionId, message}),
      signal,

      async onopen(response) {
        // 服务端在响应头回写 traceId，把它打出来就能直接去翻服务端日志
        traceId = response.headers.get('X-Trace-Id') || '-'
        if (response.ok) {
          console.debug(`${tag} 连接已建立 +${elapsed()} traceId=${traceId} contentType=${response.headers.get('Content-Type')}`)
          return;
        } else if (response.status === 401) {
          console.warn(`${tag} 登录已过期 +${elapsed()} traceId=${traceId}`)
          clearToken();
          alert('登录已过期，请重新登录');
          throw new Error('UNAUTHORIZED_EXPIRED');
        } else {
          console.error(`${tag} 请求失败 +${elapsed()} traceId=${traceId} HTTP ${response.status}`)
          throw new Error(`请求失败 (HTTP ${response.status})`)
        }
      },

      onmessage(msg) {
        if (!msg.data) return

        try {
          const evt = JSON.parse(msg.data)

          if (evt.type === 'token') {
            const content = evt.content || ''
            tokenCount += 1
            if (firstTokenAt === null) {
              firstTokenAt = Date.now()
              console.debug(`${tag} 首字到达 +${firstTokenAt - startedAt}ms traceId=${traceId}`)
            }
            fullText += content
            onToken?.(content)
          } else if (evt.type === 'tool_call') {
            console.debug(`${tag} 工具调用 +${elapsed()} name=${evt.name} args=${evt.arguments}`)
            onToolCall?.(evt.name, evt.arguments)
          } else if (evt.type === 'tool_result') {
            console.debug(`${tag} 工具结果 +${elapsed()} name=${evt.name}`)
            onToolResult?.(evt.name, evt.content)
          } else if (evt.type === 'done') {
            console.debug(`${tag} 生成完成 +${elapsed()} token事件=${tokenCount} 回复长度=${(evt.content ?? fullText).length} traceId=${traceId}`)
            onDone?.(evt.content ?? fullText, evt.sessionId)
          } else if (evt.type === 'error') {
            console.error(`${tag} 服务端错误事件 +${elapsed()} traceId=${traceId} message=${evt.message}`)
            // 标记业务错误，使其穿透 catch 被上层捕获并显示
            const bizErr = new Error(evt.message || 'Agent 执行出错')
            bizErr.isBizError = true
            throw bizErr
          }
        } catch (err) {
          if (err.isBizError) {
            throw err
          }
          console.warn(`${tag} SSE 事件解析异常 traceId=${traceId}`, err, msg.data)
        }
      },

      onerror(err) {
        if (err.message === 'UNAUTHORIZED_EXPIRED') {
          throw err;
        }
        console.error(`${tag} 流式连接异常 +${elapsed()} traceId=${traceId} 已接收token=${tokenCount}`, err)
        onError?.(err)
        throw err
      }
    })
    return fullText
  } catch (err) {
    if (err.name === 'AbortError') {
      console.debug(`${tag} 用户主动停止生成 +${elapsed()} 已接收token=${tokenCount} traceId=${traceId}`)
      onDone?.(fullText)
      return fullText
    }
    throw err
  }
}

// 说明：原 streamChat()（直连 Java 侧 /chat/stream）已随 Java 端大模型直连代码一并移除。
// 现在唯一的大模型通路是 streamAgentChat() → /api/agent/chat → Java 编排 → Python 服务。