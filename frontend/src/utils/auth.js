import {ref} from 'vue'

const TOKEN_KEY = 'satoken'

export const isLogin = ref(!!localStorage.getItem(TOKEN_KEY))

export function getToken(){
    return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token){
    localStorage.setItem(TOKEN_KEY, token)
    isLogin.value = true
}

export function clearToken(){
    localStorage.removeItem(TOKEN_KEY)
    isLogin.value = false
}