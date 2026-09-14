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
        if (response.ok) {
          return;
        } else if (response.status === 401) {
          clearToken();
          alert('登录已过期，请重新登录');
          throw new Error('UNAUTHORIZED_EXPIRED');
        } else {
          throw new Error(`请求失败 (HTTP ${response.status})`)
        }
      },

      onmessage(msg) {
        if (!msg.data) return

        try {
          const evt = JSON.parse(msg.data)

          if (evt.type === 'token') {
            const content = evt.content || ''
            fullText += content
            onToken?.(content)
          } else if (evt.type === 'tool_call') {
            onToolCall?.(evt.name, evt.arguments)
          } else if (evt.type === 'tool_result') {
            onToolResult?.(evt.name, evt.content)
          } else if (evt.type === 'done') {
            onDone?.(evt.content ?? fullText, evt.sessionId)
          } else if (evt.type === 'error') {
            // 标记业务错误，使其穿透 catch 被上层捕获并显示
            const bizErr = new Error(evt.message || 'Agent 执行出错')
            bizErr.isBizError = true
            throw bizErr
          }
        } catch (err) {
          if (err.isBizError) {
            throw err
          }
          console.warn('SSE 事件解析异常', err, msg.data)
        }
      },

      onerror(err) {
        if (err.message === 'UNAUTHORIZED_EXPIRED') {
          throw err;
        }
        onError?.(err)
        throw err
      }
    })
    return fullText
  } catch (err) {
    if (err.name === 'AbortError') {
      console.log('用户主动停止了生成')
      onDone?.(fullText)
      return fullText
    }
    throw err
  }
}

export async function streamChat({sessionId, message, onToken, onDone, onError, signal}){
  const BASE_URL = import.meta.env?.VITE_API_BASE_URL || '/api'
  let fullText = ''

  try {
    await fetchEventSource(`${BASE_URL}/chat/stream`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'satoken': getToken() || ''
      },
      body: JSON.stringify({sessionId, message}),
      signal,

      async onopen(response) {
        if (response.ok) {
          return;
        } else if (response.status === 401) {
          console.warn("流式接口检测到登录过期，准备跳转...");
          clearToken();
          alert('您的登录状态已过期或失效，请重新登录！');
          throw new Error('UNAUTHORIZED_EXPIRED');
        } else {
          throw new Error(`请求失败 (HTTP ${response.status})`)
        }
      },

      onmessage(msg) {
        if (!msg.data) {
          return
        }

        try {
          const evt = JSON.parse(msg.data)

          if (evt.type === 'token') {
            const content = evt.content || ''
            fullText += content
            onToken?.(content)
          } else if (evt.type === 'done') {
            onDone?.(evt.content ?? fullText)
          } else if (evt.type === 'error') {
            throw new Error(evt.message || '对话出错')
          }
        } catch (err) {
          console.warn('JSON 解析异常或业务异常', err, msg.data)
        }
      },

      onerror(err) {
        if (err.message === 'UNAUTHORIZED_EXPIRED') {
          throw err;
        }
        onError?.(err)
        throw err
      }
    })
    return fullText
  } catch (err) {
    if (err.name === 'AbortError') {
      console.log('用户主动停止了生成')
      onDone?.(fullText)
      return fullText
    }
  }
}