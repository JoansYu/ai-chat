import request from "../utils/request.js";
import { getToken, clearToken } from "../utils/auth.js"; // 👈 1. 引入 clearToken
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

export async function streamChat({sessionId, message, onToken, onDone, onError, signal}){
    const BASE_URL = import.meta.env?.VITE_API_BASE_URL || '/api'
    let fullText = ''
    const seenEventIds = new Set()

    await fetchEventSource(`${BASE_URL}/chat/stream`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'satoken': getToken() || '' // 确保这里的名称与后端配置的 token-name 一致
        },
        body: JSON.stringify({sessionId, message}),
        signal,

        async onopen(response) {
            if (response.ok) {
                return;
            } else if (response.status === 401) {
                // 👇 2. 核心修改：遇到 401 时，直接清空 Token 触发前端视图跳转
                console.warn("流式接口检测到登录过期，准备跳转...");
                clearToken();
                alert('您的登录状态已过期或失效，请重新登录！');

                // 抛出特定的错误标识，阻止后续的重连和错误提示
                throw new Error('UNAUTHORIZED_EXPIRED');
            } else {
                throw new Error(`请求失败 (HTTP ${response.status})`)
            }
        },

        onmessage(msg) {
            if (!msg.data) {
                return
            }

            if (msg.id) {
                if (seenEventIds.has(msg.id)) {
                    return
                }
                seenEventIds.add(msg.id)
            }

            let evt

            try {
                evt = JSON.parse(msg.data)
            } catch (err) {
                console.warn('JSON 解析异常或业务异常', err, msg.data)
                return
            }

            if (evt.type === 'token') {
                const content = evt.content || ''
                fullText += content
                onToken?.(content)
            } else if (evt.type === 'done') {
                onDone?.(evt.content ?? fullText)
            } else if (evt.type === 'error') {
                throw new Error(evt.message || '对话出错')
            }
        },

        // 错误处理
        onerror(err) {
            // 👇 3. 新增逻辑：如果是 401 过期导致的断开，不要走普通的 onError，直接抛出中断重连即可
            if (err.message === 'UNAUTHORIZED_EXPIRED') {
                throw err; // 抛出错误以停止自动重连机制
            }

            // 处理其他普通的网络或业务错误
            onError?.(err)
            throw err
        }
    })

    return fullText
}
