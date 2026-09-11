/**
 * 权限控制工具类
 * 用于根据用户角色控制页面访问权限和功能权限
 */

import router from '@/router'
import { getToken } from '@/utils/auth'
import { ElMessage } from 'element-plus'

// 角色权限映射表
const rolePermissions = {
  // 系统管理员权限
  system_admin: {
    pages: ['Welcome', 'Contacts', 'Department', 'Announcement', 'SystemNotice', 'Statistics', 'SystemManagement', 'UserProfile', 'AccountSettings', 'Chat'],
    features: ['manage_users', 'manage_departments', 'publish_company_announcement', 'view_all_statistics', 'manage_announcements']
  },
  // 兼容旧版角色名称
  admin: {
    pages: ['Welcome', 'Contacts', 'Department', 'Announcement', 'SystemNotice', 'Statistics', 'SystemManagement', 'UserProfile', 'AccountSettings', 'Chat'],
    features: ['manage_users', 'manage_departments', 'publish_company_announcement', 'view_all_statistics', 'manage_announcements']
  },
  // 部门管理员权限
  department_admin: {
    pages: ['Welcome', 'Contacts', 'Department', 'Announcement', 'SystemNotice', 'UserProfile', 'AccountSettings', 'Chat'],
    features: ['manage_department_members', 'publish_department_announcement', 'view_department_announcement_status']
  },
  // 普通员工权限
  employee: {
    pages: ['Welcome', 'Contacts', 'Department', 'Announcement', 'SystemNotice', 'UserProfile', 'AccountSettings', 'Chat'],
    features: ['view_contacts', 'view_announcements']
  }
}

/**
 * 检查用户是否有访问指定页面的权限
 * @param {string} pageName - 页面名称
 * @param {string} role - 用户角色
 * @returns {boolean} - 是否有权限
 */
export const hasPagePermission = (pageName, role) => {
  if (!role || !rolePermissions[role]) {
    return false
  }
  
  return pageName === 'AiAssistant' || rolePermissions[role].pages.includes(pageName)
}

/**
 * 检查用户是否有使用指定功能的权限
 * @param {string} feature - 功能名称
 * @param {string} role - 用户角色
 * @returns {boolean} - 是否有权限
 */
export const hasFeaturePermission = (feature, role) => {
  if (!role || !rolePermissions[role]) {
    return false
  }
  
  return rolePermissions[role].features.includes(feature)
}

/**
 * 获取用户角色对应的功能权限列表
 * @param {string} role - 用户角色
 * @returns {Array} - 功能权限列表
 */
export const getFeaturePermissions = (role) => {
  if (!role || !rolePermissions[role]) {
    return []
  }
  
  return rolePermissions[role].features
}

/**
 * 初始化路由权限控制
 */
export const setupPermissionGuard = () => {
  router.beforeEach((to, from, next) => {
    // 获取token
    const token = getToken()
    
    // 如果访问的是登录页，直接放行
    if (to.path === '/login') {
      next()
      return
    }
    
    // 如果没有token，重定向到登录页
    if (!token) {
      next('/login')
      return
    }
    
    // 获取用户角色
    const userInfo = JSON.parse(localStorage.getItem('userInfo') || '{}')
    const role = userInfo.role
    
    // 检查页面权限
    if (to.name && !hasPagePermission(to.name, role)) {
      ElMessage.error('您没有权限访问该页面')
      next(from.path) // 返回上一页
      return
    }
    
    // 其他情况放行
    next()
  })
}
