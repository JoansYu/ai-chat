<script setup>
import { ref } from 'vue'

defineProps({
  sessions: { type: Array, default: () => [] },
  activeId: { type: String, default: null }
})

const emit = defineEmits(['select', 'create', 'remove'])

const hoverId = ref(null)

function formatTime(ts) {
  const d = new Date(ts)
  const now = new Date()
  const sameDay = d.toDateString() === now.toDateString()
  const pad = (n) => String(n).padStart(2, '0')
  const hm = `${pad(d.getHours())}:${pad(d.getMinutes())}`
  if (sameDay) return hm
  return `${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}
</script>

<template>
  <aside class="session-list">
    <div class="list-header">
      <div class="logo">
        <span class="logo-icon">AI</span>
        <span class="logo-text">智能对话</span>
      </div>
      <button class="btn-new" @click="emit('create')">
        <span class="plus">+</span> 新对话
      </button>
    </div>

    <div class="list-body">
      <div
        v-for="s in sessions"
        :key="s.id"
        class="session-item"
        :class="{ active: s.id === activeId }"
        @click="emit('select', s.id)"
        @mouseenter="hoverId = s.id"
        @mouseleave="hoverId = null"
      >
        <span class="session-title">{{ s.title || '新会话' }}</span>
        <button
          v-if="hoverId === s.id || s.id === activeId"
          class="btn-del"
          title="删除会话"
          @click.stop="emit('remove', s.id)"
        >
          ✕
        </button>
        <span class="session-time">{{ formatTime(s.createdAt) }}</span>
      </div>

      <div v-if="sessions.length === 0" class="empty-tip">暂无会话，点击「新对话」开始吧</div>
    </div>

    <div class="list-footer">Vue 3 + Spring Boot 3</div>
  </aside>
</template>

<style scoped>
.session-list {
  width: 260px;
  min-width: 260px;
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #ffffff;
  border-right: 1px solid #e8eaef;
}

.list-header {
  padding: 16px 14px 12px;
}

.logo {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}

.logo-icon {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: #fff;
  font-size: 14px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}

.logo-text {
  font-size: 16px;
  font-weight: 600;
  color: #1f2937;
}

.btn-new {
  width: 100%;
  padding: 10px;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  background: #f9fafb;
  color: #374151;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
}

.btn-new:hover {
  background: #eef2ff;
  border-color: #c7d2fe;
  color: #4f46e5;
}

.plus {
  font-size: 16px;
  font-weight: 700;
}

.list-body {
  flex: 1;
  overflow-y: auto;
  padding: 4px 10px;
}

.session-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 12px;
  margin-bottom: 4px;
  border-radius: 10px;
  cursor: pointer;
  position: relative;
  transition: background 0.15s;
}

.session-item:hover {
  background: #f3f4f6;
}

.session-item.active {
  background: #eef2ff;
}

.session-title {
  flex: 1;
  font-size: 14px;
  color: #374151;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.session-item.active .session-title {
  color: #4f46e5;
  font-weight: 500;
}

.btn-del {
  border: none;
  background: transparent;
  color: #9ca3af;
  font-size: 12px;
  cursor: pointer;
  padding: 2px 4px;
  border-radius: 4px;
}

.btn-del:hover {
  color: #ef4444;
  background: #fee2e2;
}

.session-time {
  font-size: 11px;
  color: #9ca3af;
  flex-shrink: 0;
}

.empty-tip {
  padding: 24px 12px;
  text-align: center;
  color: #9ca3af;
  font-size: 13px;
}

.list-footer {
  padding: 12px;
  text-align: center;
  font-size: 12px;
  color: #c0c4cc;
  border-top: 1px solid #f1f2f4;
}
</style>
