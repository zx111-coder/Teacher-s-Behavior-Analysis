import {jwtDecode} from 'jwt-decode'

const TOKEN_KEY = 'teacher-video-token'

export function setToken(token) {//存储后端数据
  localStorage.setItem(TOKEN_KEY, token)
}

export function getToken() {
  return localStorage.getItem(TOKEN_KEY)
}

export function removeToken() {
  localStorage.removeItem(TOKEN_KEY)
}

export function checkAuth() {
  const token = getToken()
  if (!token) return false
  
  try {
    const decoded = jwtDecode(token)
    return decoded.exp > Date.now() / 1000
  } catch (e) {
    return false
  }
}
export function getUserRole() {
  const token = getToken()
  if (!token) return null
  
  try {
    const decoded = jwtDecode(token)
    return decoded.role
  } catch (e) {
    return null
  }
}