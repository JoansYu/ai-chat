<script setup>
import {ref, onMounted} from 'vue'
import {getWorkspaceStatus, authorizeWorkspace, revokeWorkspace} from '../api/chat.js'

const showSettings = ref(false)
const workspacePath = ref('')
const authorized = ref(false)
const loading = ref(false)
const errorMsg = ref('')
const successMsg = ref('')

onMounted(async () => {
  await checkStatus()
})

async function checkStatus() {
  try {
    const res = await getWorkspaceStatus()
    const data = res.data || res
    if (data.authorized) {
      authorized.value = true
      workspacePath.value = data.workspacePath || ''
    } else {
      authorized.value = false
    }
  } catch (e) {
    console.error('获取工作区状态失败', e)
  }
}

async function handleAuthorize() {
  if (!workspacePath.value || !workspacePath.value.trim()) {
    errorMsg.value = '请输入工作区路径'
    return
  }
  errorMsg.value = ''
  successMsg.value = ''
  loading.value = true
  try {
    const res = await authorizeWorkspace(workspacePath.value.trim())
    const data = res.data || res
    if (data.authorized) {
      authorized.value = true
      workspacePath.value = data.workspacePath
      successMsg.value = '授权成功！Agent 现在可以读取该目录下的代码文件'
    } else {
      throw new Error(res.message || '授权失败')
    }
  } catch (e) {
    errorMsg.value = e.message || '授权失败，请检查路径是否正确'
  } finally {
    loading.value = false
  }
}

async function handleRevoke() {
  errorMsg.value = ''
  successMsg.value = ''
  loading.value = true
  try {
    await revokeWorkspace()
    authorized.value = false
    workspacePath.value = ''
    successMsg.value = '已撤销授权，Agent 无法再读取代码文件'
  } catch (e) {
    errorMsg.value = e.message || '撤销授权失败'
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
          <h3>🔧 工作区授权</h3>
          <button class="btn-close" @click="close">✕</button>
        </div>

        <div class="settings-body">
          <div class="info-banner">
            <p class="info-title">什么是工作区授权？</p>
            <p class="info-desc">授权后，AI Agent 可以读取你本地指定目录下的代码文件（仅读取，不可修改）。
              Agent 只能访问白名单中的文件类型（如 .java .py .js 等），且单文件不超过 1MB。
              这是实现"阅读代码架构、定位代码位置"能力的基础。</p>
          </div>

          <div v-if="authorized" class="status-box status-ok">
            <span class="status-dot ok"></span>
            <div class="status-text">
              <strong>已授权</strong>
              <code>{{ workspacePath }}</code>
            </div>
          </div>
          <div v-else class="status-box status-warn">
            <span class="status-dot warn"></span>
            <div class="status-text">
              <strong>未授权</strong>
              <span>Agent 暂无法读取代码文件，请输入路径并授权</span>
            </div>
          </div>

          <div class="input-section">
            <label>工作区路径（本地文件夹绝对路径）</label>
            <input
                v-model="workspacePath"
                type="text"
                placeholder="如：E:\agent\ai-chat-main\backend"
                :disabled="loading"
            />
          </div>

          <div v-if="errorMsg" class="msg-error">⚠️ {{ errorMsg }}</div>
          <div v-if="successMsg" class="msg-success">✅ {{ successMsg }}</div>

          <div class="action-row">
            <button v-if="!authorized" class="btn-authorize" @click="handleAuthorize" :disabled="loading">
              {{ loading ? '授权中...' : '授权访问' }}
            </button>
            <button v-if="authorized" class="btn-revoke" @click="handleRevoke" :disabled="loading">
              {{ loading ? '撤销中...' : '撤销授权' }}
            </button>
            <button class="btn-cancel" @click="close">关闭</button>
          </div>

          <div class="sandbox-info">
            <p class="sandbox-title">🔒 沙箱安全机制</p>
            <ul>
              <li>路径隔离：只能访问授权目录内的文件，防止 <code>../</code> 逃逸</li>
              <li>扩展名白名单：仅允许代码/配置文件（30+ 种）</li>
              <li>大小限制：单文件最大 1MB</li>
              <li>只读模式：阶段一仅支持读取，不支持修改/删除文件</li>
              <li>搜索限制：单次搜索最多返回 50 条结果</li>
            </ul>
          </div>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.settings-overlay {
  position: fixed;
  top: 0; left: 0; right: 0; bottom: 0;
  background: rgba(0,0,0,0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 9999;
}

.settings-modal {
  width: 560px;
  max-height: 85vh;
  overflow-y: auto;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 20px 60px rgba(0,0,0,0.15);
}

.settings-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px;
  border-bottom: 1px solid #f0f0f0;
}

.settings-header h3 {
  margin: 0;
  font-size: 18px;
  color: #1f2937;
}

.btn-close {
  border: none;
  background: none;
  font-size: 18px;
  color: #9ca3af;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 6px;
}

.btn-close:hover { background: #f3f4f6; }

.settings-body { padding: 20px 24px; }

.info-banner {
  background: #eff6ff;
  border: 1px solid #dbeafe;
  border-radius: 10px;
  padding: 14px 16px;
  margin-bottom: 20px;
}

.info-title { font-size: 13px; font-weight: 600; color: #1e40af; margin: 0 0 6px; }
.info-desc { font-size: 12px; color: #6b7280; line-height: 1.6; margin: 0; }

.status-box {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  border-radius: 10px;
  margin-bottom: 20px;
}

.status-ok { background: #f0fdf4; border: 1px solid #bbf7d0; }
.status-warn { background: #fffbeb; border: 1px solid #fde68a; }

.status-dot {
  width: 10px; height: 10px; border-radius: 50%; flex-shrink: 0;
}
.status-dot.ok { background: #22c55e; }
.status-dot.warn { background: #f59e0b; }

.status-text { display: flex; flex-direction: column; gap: 2px; }
.status-text strong { font-size: 14px; color: #1f2937; }
.status-text code { font-size: 12px; color: #6b7280; }
.status-text span { font-size: 12px; color: #92400e; }

.input-section { margin-bottom: 16px; }
.input-section label { display: block; font-size: 13px; color: #374151; margin-bottom: 6px; font-weight: 500; }
.input-section input {
  width: 100%;
  padding: 10px 14px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  font-size: 14px;
  outline: none;
  transition: all 0.2s;
  box-sizing: border-box;
  font-family: monospace;
}
.input-section input:focus { border-color: #6366f1; box-shadow: 0 0 0 3px rgba(99,102,241,0.12); }

.msg-error { color: #ef4444; font-size: 13px; margin-bottom: 12px; }
.msg-success { color: #22c55e; font-size: 13px; margin-bottom: 12px; }

.action-row { display: flex; gap: 10px; margin-bottom: 20px; }

.btn-authorize {
  padding: 10px 20px;
  border: none;
  border-radius: 8px;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
}
.btn-authorize:disabled { opacity: 0.6; cursor: not-allowed; }

.btn-revoke {
  padding: 10px 20px;
  border: 1px solid #fecaca;
  border-radius: 8px;
  background: #fff;
  color: #ef4444;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
}
.btn-revoke:disabled { opacity: 0.6; cursor: not-allowed; }

.btn-cancel {
  padding: 10px 20px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
  color: #6b7280;
  font-size: 14px;
  cursor: pointer;
  margin-left: auto;
}

.sandbox-info {
  background: #f9fafb;
  border: 1px solid #f3f4f6;
  border-radius: 10px;
  padding: 14px 16px;
}

.sandbox-title { font-size: 13px; font-weight: 600; color: #374151; margin: 0 0 8px; }
.sandbox-info ul { margin: 0; padding-left: 20px; }
.sandbox-info li { font-size: 12px; color: #6b7280; line-height: 1.8; }
.sandbox-info code { font-size: 11px; background: #f3f4f6; padding: 1px 4px; border-radius: 3px; }
</style>
