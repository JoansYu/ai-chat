<script setup>
import {ref} from 'vue'
import {getWorkspaceStatus} from '../api/chat.js'

const showSettings = ref(false)
const projects = ref([])
const loading = ref(false)
const errorMsg = ref('')

async function checkStatus() {
  loading.value = true
  errorMsg.value = ''
  projects.value = []
  try {
    const res = await getWorkspaceStatus()
    if (res.code !== undefined && res.code !== 200) throw new Error(res.message || '获取项目连接失败')
    const data = res.data || res
    projects.value = data.projects || []
  } catch (e) {
    errorMsg.value = e.message || '获取项目连接失败'
  } finally {
    loading.value = false
  }
}

function open() {
  showSettings.value = true
  checkStatus()
}

function close() {
  showSettings.value = false
}

defineExpose({open})
</script>

<template>
  <Teleport to="body">
    <div v-if="showSettings" class="settings-overlay" @click.self="close">
      <div class="settings-modal">
        <div class="settings-header">
          <h3>IDEA 项目工作区</h3>
          <button class="btn-close" @click="close" aria-label="关闭">×</button>
        </div>
        <div class="settings-body">
          <div class="info-banner">
            <p class="info-title">自动使用插件所在的项目目录</p>
            <p class="info-desc">在目标 IDEA 项目中连接 AI Chat Agent 插件，并使用与网页相同的账号登录。
              工作区由插件自动识别，无需输入或授权目录路径。文件操作在该 IDEA 项目中执行。</p>
          </div>
          <p v-if="loading" class="info-desc">正在获取项目连接…</p>
          <div v-else-if="errorMsg" class="msg-error" role="alert">{{ errorMsg }}</div>
          <template v-else>
            <div v-for="project in projects" :key="project.clientId" class="status-box status-ok">
              <span class="status-dot ok"></span>
              <div class="status-text">
                <strong>已连接</strong>
                <code>{{ project.workspacePath }}</code>
              </div>
            </div>
            <div v-if="!projects.length" class="status-box status-warn">
              <span class="status-dot warn"></span>
              <div class="status-text">
                <strong>尚未连接 IDEA 项目</strong>
                <span>请先在目标项目中连接 AI Chat Agent 插件。</span>
              </div>
            </div>
            <p v-else-if="projects.length > 1" class="info-desc">当前连接了多个项目，请在目标项目的插件窗口中发起对话。</p>
            <p v-else class="info-desc">网页 Agent 对话将自动使用此项目作为工作区。</p>
          </template>
          <div class="action-row">
            <button class="btn-refresh" @click="checkStatus" :disabled="loading">刷新连接</button>
            <button class="btn-cancel" @click="close">关闭</button>
          </div>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.settings-overlay { position: fixed; inset: 0; background: rgba(0,0,0,0.4); display: flex; align-items: center; justify-content: center; z-index: 9999; }
.settings-modal { width: min(560px, 90vw); max-height: 85vh; overflow-y: auto; background: #fff; border-radius: 16px; box-shadow: 0 20px 60px rgba(0,0,0,0.15); }
.settings-header { display: flex; align-items: center; justify-content: space-between; padding: 20px 24px; border-bottom: 1px solid #f0f0f0; }
.settings-header h3 { margin: 0; font-size: 18px; color: #1f2937; }
.btn-close { border: none; background: none; font-size: 24px; color: #9ca3af; cursor: pointer; padding: 4px 8px; border-radius: 6px; }
.btn-close:hover { background: #f3f4f6; }
.settings-body { padding: 20px 24px; }
.info-banner { background: #eff6ff; border: 1px solid #dbeafe; border-radius: 10px; padding: 14px 16px; margin-bottom: 20px; }
.info-title { font-size: 13px; font-weight: 600; color: #1e40af; margin: 0 0 6px; }
.info-desc { font-size: 12px; color: #6b7280; line-height: 1.6; }
.status-box { display: flex; align-items: center; gap: 12px; padding: 14px 16px; border-radius: 10px; margin-bottom: 20px; }
.status-ok { background: #f0fdf4; border: 1px solid #bbf7d0; }
.status-warn { background: #fffbeb; border: 1px solid #fde68a; }
.status-dot { width: 10px; height: 10px; border-radius: 50%; flex-shrink: 0; }
.status-dot.ok { background: #22c55e; }
.status-dot.warn { background: #f59e0b; }
.status-text { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
.status-text strong { font-size: 14px; color: #1f2937; }
.status-text code { font-size: 12px; color: #6b7280; overflow-wrap: anywhere; }
.status-text span { font-size: 12px; color: #92400e; }
.msg-error { color: #ef4444; font-size: 13px; margin-bottom: 12px; }
.action-row { display: flex; gap: 10px; margin-top: 20px; }
.btn-refresh, .btn-cancel { padding: 10px 20px; border: 1px solid #e5e7eb; border-radius: 8px; background: #fff; color: #6b7280; font-size: 14px; cursor: pointer; }
.btn-refresh:disabled { opacity: 0.6; cursor: not-allowed; }
.btn-cancel { margin-left: auto; }
</style>
