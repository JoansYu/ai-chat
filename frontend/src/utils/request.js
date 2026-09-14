import axios from 'axios'
import { clearToken } from './auth.js'

/**
 * 统一 HTTP 请求封装（基于 axios）。
 * 约定：响应拦截器直接返回后端返回体（Result 统一结构），
 * 业务层自行根据 res.code / res.data 处理。
 */
const http = axios.create({
  baseURL: '/api',
  timeout: 60000,
  headers: { 'Content-Type': 'application/json' }
})

// 请求拦截器：自动携带 satoken
http.interceptors.request.use((config) => {
  const token = localStorage.getItem('satoken')
  if (token) {
    config.headers['satoken'] = token
  }
  return config
})

// 响应拦截器：直接返回响应体；统一错误提示与登录失效清理
http.interceptors.response.use(
  (response) => response.data,
  (error) => {
    const status = error.response?.status
    const data = error.response?.data
    const message = data?.message || `请求失败（HTTP ${status || '未知'}）`
    if (status === 401) {
      clearToken()
    }
    return Promise.reject(new Error(message))
  }
)

const request = {
  get: (url, config) => http.get(url, config),
  post: (url, data, config) => http.post(url, data, config),
  put: (url, data, config) => http.put(url, data, config),
  delete: (url, config) => http.delete(url, config)
}

export default request
