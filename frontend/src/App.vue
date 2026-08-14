<script setup>
import { ref, computed, onMounted } from 'vue'
import SessionList from './components/SessionList.vue'
import ChatWindow from './components/ChatWindow.vue'
import { createSession, listSessions } from './api'

const sessions = ref([])
const currentId = ref(null)

const currentSession = computed(() => sessions.value.find((s) => s.id === currentId.value) || null)

async function loadSessions() {
  sessions.value = await listSessions()
}

async function createNewSession() {
  const session = await createSession()
  sessions.value.unshift(session)
  currentId.value = session.id
  return session
}

function onCreated(session) {
  const exists = sessions.value.find((s) => s.id === session.id)
  if (!exists) {
    sessions.value.unshift(session)
  }
  currentId.value = session.id
}

function selectSession(id) {
  currentId.value = id
}

function removeSession(id) {
  sessions.value = sessions.value.filter((s) => s.id !== id)
  if (currentId.value === id) {
    currentId.value = sessions.value[0]?.id || null
  }
}

async function refresh() {
  await loadSessions()
  if (!currentId.value && sessions.value.length > 0) {
    currentId.value = sessions.value[0].id
  }
}

onMounted(async () => {
  try {
    await refresh()
  } catch (e) {
    console.error('加载会话失败', e)
  }
})
</script>

<template>
  <div class="app">
    <SessionList
      :sessions="sessions"
      :active-id="currentId"
      @select="selectSession"
      @create="createNewSession"
      @remove="removeSession"
    />
    <ChatWindow
      :session="currentSession"
      @new="createNewSession"
      @created="onCreated"
      @refreshed="refresh"
    />
  </div>
</template>

<style scoped>
.app {
  display: flex;
  height: 100vh;
  width: 100vw;
  overflow: hidden;
}
</style>
