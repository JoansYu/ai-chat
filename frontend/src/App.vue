<script setup>
import { ref, computed, watch } from 'vue'
import Login from './components/Login.vue'
import SessionList from './components/SessionList.vue'
import ChatWindow from './components/ChatWindow.vue'
import Settings from './components/Settings.vue'
import { createSession, listSessions } from './api/chat.js'
import { isLogin } from "./utils/auth.js"

// 状态定义
const sessions = ref([])
const currentId = ref(null)
const chatWindowRef = ref(null)
const settingsRef = ref(null)

// 监听登录状态，自动拉取或清空数据
watch(isLogin, async (newVal) => {
  if (newVal) {
    try {
      sessions.value = await listSessions()
      // 适配后端的 Result 统一返回体（如果有 data 就取 data）
      const list = sessions.value.data || sessions.value
      sessions.value = Array.isArray(list) ? list : []

      if (sessions.value.length > 0) {
        currentId.value = sessions.value[0].id
      }
    } catch (e) {
      console.error('获取列表失败', e)
    }
  } else {
    // 退出登录时，清空数据，防止窜号
    sessions.value = []
    currentId.value = null // 👈 必须是 null，不能是 []
  }
}, { immediate: true })

// 计算当前选中的会话信息
const currentSession = computed(() => {
  return sessions.value.find((s) => s.id === currentId.value) || null
})

// 重新加载会话列表
async function loadSessions() {
  const res = await listSessions()
  const list = res.data || res
  const backendSessions = Array.isArray(list) ? list : []

  // 保留本地已创建但后端尚未同步的会话（防止 agent 创建的会话被后端数据覆盖后丢失）
  const backendIds = new Set(backendSessions.map(s => s.id))
  const localOnly = sessions.value.filter(s => !backendIds.has(s.id))

  sessions.value = [...localOnly, ...backendSessions]
}

// 主动创建新会话
async function createNewSession() {
  const res = await createSession()
  const session = res.data || res
  sessions.value.unshift(session)
  currentId.value = session.id
  return session
}

// ChatWindow 内部首次发送消息时自动创建了会话，通知 App 更新列表
function onCreated(session) {
  const exists = sessions.value.find((s) => s.id === session.id)
  if (!exists) {
    sessions.value.unshift(session)
  }
  currentId.value = session.id
}

// 切换会话
function selectSession(id) {
  currentId.value = id
}

// 删除会话
function removeSession(id) {
  // 👈 核心拦截：在删除之前，通知 ChatWindow 掐断这个会话还在进行的网络请求！
  chatWindowRef.value?.abortSession(id)

  sessions.value = sessions.value.filter((s) => s.id !== id)
  if (currentId.value === id) {
    currentId.value = sessions.value[0]?.id || null
  }
}

// 刷新会话状态（比如大模型回复完后，更新最新时间）
async function refresh() {
  await loadSessions()
  if (!currentId.value && sessions.value.length > 0) {
    currentId.value = sessions.value[0].id
  }
}
function openSettings() {
  settingsRef.value?.open()
}
</script>

<template>
  <!-- 如果未登录，展示登录页面 -->
  <Login v-if="!isLogin" />

  <!-- 如果已登录，展示主体架构（包含左侧边栏和右侧聊天窗） -->
  <div v-else class="app-container">
    <!-- 左侧列表 -->
    <SessionList
        :sessions="sessions"
        :active-id="currentId"
        @select="selectSession"
        @create="createNewSession"
        @remove="removeSession"
    />
    <!-- 右侧聊天窗 -->
    <ChatWindow
        ref="chatWindowRef"
        :session="currentSession"
        @new="createNewSession"
        @created="onCreated"
        @refreshed="refresh"
    />
    <!-- 设置弹窗 -->
    <Settings ref="settingsRef" />
    <!-- 设置入口按钮 -->
    <button class="settings-fab" @click="openSettings" title="工作区授权设置">🔧</button>
  </div>
</template>

<style>
/* 这里不加 scoped，作为全局重置 */
body {
  margin: 0;
  padding: 0;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
  box-sizing: border-box;
}

.app-container {
  display: flex;
  height: 100vh;
  width: 100vw;
  overflow: hidden;
  background-color: #f7f8fa;
}

.settings-fab {
  position: fixed;
  bottom: 24px;
  right: 24px;
  width: 48px;
  height: 48px;
  border: none;
  border-radius: 50%;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: #fff;
  font-size: 20px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 16px rgba(99, 102, 241, 0.4);
  z-index: 1000;
  transition: all 0.2s;
}

.settings-fab:hover {
  transform: scale(1.1);
  box-shadow: 0 6px 20px rgba(99, 102, 241, 0.5);
}
</style>