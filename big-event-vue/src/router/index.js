import { createRouter, createWebHistory } from 'vue-router'
import Home from '@/views/Home.vue'
import Login from '@/views/Login.vue'
import Contacts from '@/views/Contacts.vue'
import Chat from '@/views/chat.vue'
import Department from '@/views/Department.vue'
import Announcement from '@/views/Announcement.vue'
import Statistics from '@/views/Statistics.vue'
import SystemManagement from '@/views/SystemManagement.vue'
import SystemNotice from '@/views/SystemNotice.vue'
import UserProfile from '@/views/UserProfile.vue'
import AccountSettings from '@/views/AccountSettings.vue'
import { hasPagePermission } from '@/utils/permission'
import { useUserStore } from '@/stores/user'

// 定义路由配置
const routes = [
  {
    path: '/',
    redirect: '/welcome'
  },
  {
    path: '/login',
    name: 'Login',
    component: Login
  },
  {
    path: '/',
    component: Home,
    children: [
      {
        path: '/welcome',
        name: 'Welcome',
        component: () => import('@/views/Welcome.vue')
      },
      {
        path: '/contacts',
        name: 'Contacts',
        component: Contacts
      },
      {
        path: '/chat',
        name: 'Chat',
        component: Chat
      },
      {
        path: '/department',
        name: 'Department',
        component: Department
      },
      {
        path: '/announcement',
        name: 'Announcement',
        component: Announcement
      },
      {
        path: '/system-notice',
        name: 'SystemNotice',
        component: SystemNotice
      },
      {
        path: '/statistics',
        name: 'Statistics',
        component: Statistics
      },
      {
        path: '/system',
        name: 'SystemManagement',
        component: SystemManagement
      },
      {
        path: '/profile',
        name: 'UserProfile',
        component: UserProfile
      },
      {
        path: '/settings',
        name: 'AccountSettings',
        component: AccountSettings
      }
    ]
  }
]

// 创建路由实例
const router = createRouter({
  history: createWebHistory(),
  routes
})

// 全局前置守卫
router.beforeEach((to, from, next) => {
  // 获取token
  const token = localStorage.getItem('token')
  
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
  
  // 获取用户角色进行权限控制
  const userInfoStr = localStorage.getItem('userInfo')
  if (!userInfoStr) {
    // 如果没有用户信息但有token，清除token并重定向到登录页
    localStorage.removeItem('token')
    next('/login')
    return
  }
  
  const userInfo = JSON.parse(userInfoStr)
  const role = userInfo.role

  // 仅系统管理员可访问统计页面
  if (to.name === 'Statistics' && role !== 'system_admin') {
    next('/welcome')
    return
  }
  
  // 检查页面权限
  if (to.name && !hasPagePermission(to.name, role)) {
    // 如果没有权限访问该页面，重定向到首页
    next('/welcome')
    return
  }
  
  // 其他情况放行
  next()
})

export default router