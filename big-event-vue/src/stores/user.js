import { defineStore } from 'pinia'
import { ref } from 'vue'
import api from '@/api'

/**
 * 用户状态管理
 * 管理用户信息、权限等状态
 */
export const useUserStore = defineStore('user', () => {
  // 用户信息
  const userInfo = ref(null)
  // 用户权限
  const isAdmin = ref(false)

  /**
   * 加载用户信息
   * @returns {Promise<void>}
   */
  const loadUserInfo = async () => {
    try {
      const res = await api.account.me()
      if (res.code === 0) {
        userInfo.value = res.data
        // 判断用户是否为管理员（兼容两种角色名称：admin和system_admin）
        isAdmin.value = res.data.role === 'system_admin' || res.data.role === 'admin'
        console.log('用户角色:', res.data.role, '是否管理员:', isAdmin.value)
      }
    } catch (error) {
      console.error('加载用户信息失败:', error)
    }
  }

  /**
   * 清空当前登录态（用于退出/被踢/401等场景）
   */
  const clear = () => {
    userInfo.value = null
    isAdmin.value = false
  }

  /**
   * 更新用户昵称
   * @param {string} nickname - 用户昵称
   * @returns {Promise<boolean>} - 是否更新成功
   */
  const updateNickname = async (nickname) => {
    try {
      const res = await api.account.updateNickname(nickname)
      if (res.code === 0) {
        await loadUserInfo()
        return true
      }
      return false
    } catch (error) {
      console.error('更新用户昵称失败:', error)
      return false
    }
  }

  /**
   * 更新用户真实姓名
   * @param {string} realname - 用户真实姓名
   * @returns {Promise<boolean>} - 是否更新成功
   */
  const updateRealname = async (realname) => {
    try {
      const res = await api.account.updateRealname(realname)
      if (res.code === 0) {
        await loadUserInfo()
        return true
      }
      return false
    } catch (error) {
      console.error('更新用户真实姓名失败:', error)
      return false
    }
  }

  /**
   * 更新用户邮箱
   * @param {string} email - 用户邮箱
   * @returns {Promise<boolean>} - 是否更新成功
   */
  const updateEmail = async (email) => {
    try {
      const res = await api.account.updateEmail(email)
      if (res.code === 0) {
        await loadUserInfo()
        return true
      }
      return false
    } catch (error) {
      console.error('更新用户邮箱失败:', error)
      return false
    }
  }

  /**
   * 更新用户电话
   * @param {string} phone - 用户电话
   * @returns {Promise<boolean>} - 是否更新成功
   */
  const updatePhone = async (phone) => {
    try {
      const res = await api.account.updatePhone(phone)
      if (res.code === 0) {
        await loadUserInfo()
        return true
      }
      return false
    } catch (error) {
      console.error('更新用户电话失败:', error)
      return false
    }
  }

  /**
   * 更新用户头像
   * @param {string} avatarUrl - 头像URL
   * @returns {Promise<boolean>} - 是否更新成功
   */
  const updateAvatar = async (avatarUrl) => {
    try {
      const res = await api.account.updateAvatar(avatarUrl)
      if (res.code === 0) {
        await loadUserInfo()
        return true
      }
      return false
    } catch (error) {
      console.error('更新用户头像失败:', error)
      return false
    }
  }

  /**
   * 更新用户部门
   * @param {number} userId - 用户ID
   * @param {number} departmentId - 部门ID
   * @returns {Promise<boolean>} - 是否更新成功
   */
  const updateDepartment = async (userId, departmentId) => {
    try {
      const res = await api.system.updateDepartment(userId, departmentId)
      if (res.code === 0) {
        await loadUserInfo()
        return true
      }
      return false
    } catch (error) {
      console.error('更新用户部门失败:', error)
      return false
    }
  }

  return {
    userInfo,
    isAdmin,
    loadUserInfo,
    updateNickname,
    updateRealname,
    updateEmail,
    updatePhone,
    updateAvatar,
    updateDepartment,
    clear
  }
})