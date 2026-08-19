<script setup>

import {computed, ref} from "vue";
import {login, register} from "../api/chat.js";
import {setToken} from "../utils/auth.js";

const isLoginMode = ref(true)
const username = ref('')
const password = ref('')
const loading= ref(false)
const errorMsg = ref('')
const titleText = computed(() => isLoginMode.value? '欢迎使用智能对话助手':'创建新账号')
const submitText = computed(() => isLoginMode.value?'登 录':'注 册')
const toggleText = computed(() => isLoginMode.value?'没有账号？点击注册':'已有账号，返回登录')

// 切换登录/注册模式

function toggleMode(){
  isLoginMode.value = !isLoginMode.value
  errorMsg.value = ''
}

async function handleSubmit(){
  if (!username.value || !password.value) {
    errorMsg.value = '请输入用户名和密码'
    return

  }

  errorMsg.value = ''
  loading.value = true

  try {
    if (isLoginMode.value) {
      const res = await login(username.value, password.value)
      if (res.code === 200 && res.data && res.data.tokenValue) {
        setToken(res.data.tokenValue)
      }else {
        throw new Error(res.message || '登录失败')
      }
    }else {
      await register(username.value, password.value)
      alert('注册成功，请登录！')
      isLoginMode.value = true
      password.value = ''
    }

  }catch (err) {
    errorMsg.value = err.message || (isLoginMode.value ? '登录失败，请检查账号密码' : '注册失败，用户名可能已存在')
  }finally {
    loading.value = false
  }
}
</script>

<template>

  <div class="login-wrapper">
    <div class="login-box">
      <div class="logo">
        <span class="logo-icon">
          AI
        </span>
      </div>
      <h2>{{ titleText }}</h2>
      <p class="subtitle">{{isLoginMode ? '请登录后继续访问':'注册账号以保存你的对话历史'}}</p>
      <form @submit.prevent="handleSubmit" class="login-form">
        <div class="input-group">
          <input
              v-model="username"
              type="text"
              placeholder="请输入用户名"
              :disabled="loading"
          />
        </div>
        <div class="input-group">
          <input
              v-model="password"
              type="password"
              placeholder="请输入密码"
              :disabled="loading"
          />
        </div>
        <div v-if="errorMsg" class="error-msg">{{errorMsg}}</div>
        <button type="submit" class="btn-submit" :disabled="loading">{{loading ? '处理中...' : submitText}}</button>

        <div class="toggle-mode">
          <a href="#" @click.prevent="toggleMode">{{toggleText}}</a>
        </div>
      </form>
    </div>
  </div>
</template>

<style scoped>
.login-wrapper{
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100vh;
  width: 100vw;
  background: #f7f8fa;
}

.login-box {
  text-align: center;
  width: 100%;
  max-width: 400px;
  background: #ffffff;
  padding: 40px;
  border-radius: 20px;
  box-shadow: 0 10px 40px rgba(0,0,0,0.05);
}

.logo-icon {
  width: 56px;
  height: 56px;
  margin: 0 auto 16px;
  border-radius: 16px;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: #ffffff;
  font-size: 20px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 8px 24px rgba(99, 102, 241, 0.3);
}

h2 {
  font-size: 22px;
  color: #1f2037;
  margin: 0 0 8px;
}

.subtitle{
  font-size: 14px;
  color: #6b7280;
  margin-bottom: 30px;
}

.login-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.input-group input{
  width: 100%;
  padding: 12px 16px;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  font-size: 14px;
  outline: none;
  transition: all 0.2s;
  box-sizing: border-box;
}

.input-group input:focus {
  border-color: #6366f1;
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.12);
}

.error-msg{
  color: #ef4444;
  font-size: 13px;
  text-align: left;
}

.btn-submit{
  margin-top: 10px;
  width: 100%;
  padding: 12px;
  border: none;
  border-radius: 10px;
  background: linear-gradient(135deg, #6366f1, #865cf6);
  color: #ffffff;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-submit:disabled{
  opacity: 0.6;
  cursor: not-allowed;
}

.toggle-mode{
  margin-top: 16px;
  font-size: 13px;
}

.toggle-mode a {
  color: #6366f1;
  text-decoration: none;
  transition: color 0.2s;
}

.toggle-mode a:hover {
  color: #4f46e5;
  text-decoration: underline;
}
</style>