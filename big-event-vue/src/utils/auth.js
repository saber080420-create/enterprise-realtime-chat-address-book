/**
 * 认证相关的工具函数
 */

// Token 在 localStorage 中的键名
const TOKEN_KEY = 'token'

/**
 * 获取存储的token
 * @returns {string|null} 存储的token或null
 */
export const getToken = () => {
  return localStorage.getItem(TOKEN_KEY)
}

/**
 * 设置token到localStorage
 * @param {string} token - 要存储的token
 */
export const setToken = (token) => {
  localStorage.setItem(TOKEN_KEY, token)
}

/**
 * 移除存储的token
 */
export const removeToken = () => {
  localStorage.removeItem(TOKEN_KEY)
}

/**
 * 判断用户是否已登录
 * @returns {boolean} 是否已登录
 */
export const isLoggedIn = () => {
  return !!getToken()
}