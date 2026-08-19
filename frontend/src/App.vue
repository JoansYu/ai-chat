<script setup>
import { ref, computed, watch } from 'vue'
import Login from './components/Login.vue'
import SessionList from './components/SessionList.vue'
import ChatWindow from './components/ChatWindow.vue'
import { createSession, listSessions } from './api/chat.js' // 👈 确保路径是 ./api
import { isLogin } from "./utils/auth.js" // 👈 确保路径是 ./utils

// 状态定义
const sessions = ref([])
const currentId = ref(null)
const chatWindowRef = ref(null) // 👈 用来获取 ChatWindow 子组件实例

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
  sessions.value = Array.isArray(list) ? list : []
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
</style>