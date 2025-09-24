import request from '../utils/request'

export function login(username, password) {
  return request({
    url: '/api/login',
    method: 'post',
    data: { username, password }
  })
}

// 统一使用 request 工具
export const register = (username, password) => {
  return request({
    url: '/register',  // 保持路径风格一致
    method: 'post',
    data: userData
  })
}
// // 检查用户是否已登录
// export const checkAuth = () => {
//   return !!localStorage.getItem('token'); // 检查是否存在有效的 Token
// };

// // 存储 Token
// export const setToken = (token) => {
//   localStorage.setItem('token', token);
// };

// // 移除 Token
// export const removeToken = () => {
//   localStorage.removeItem('token');
// };

// export function logout() {
//   return request({
//     url: '/auth/logout',
//     method: 'post'
//   })
// }

// export function getUserInfo() {
//   return request({
//     url: '/auth/info',
//     method: 'get'
//   })
// }