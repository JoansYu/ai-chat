import axios from 'axios'
import {getToken, clearToken} from "./auth.js";

const BASE_URL = import.meta.env?.VITE_API_BASE_URL || 'api'

const request = axios.create({
    baseURL: BASE_URL,
    timeout: 15000
})

// 请求拦截器：自动注入 Token
request.interceptors.request.use(
    (config) => {
        const token = getToken()
        if (token) {
            config.headers['sa_token'] = token
        }
        return config
    },
    (error) => Promise.reject(error)
)

request.interceptors.response.use(
    (response) => {
        return response.data;
    },
    (error) => {
        if (error.response) {
            if (error.response.status === 401) {
                clearToken()
                alert('登录已过期，请重新登陆')
            } else {
                const msg = error.response.data?.message || '服务器内部错误'
                console.error(msg)
            }
        } else {
            console.error('网络连接异常')
        }

        return Promise.reject(error)
    }
)

export default request


