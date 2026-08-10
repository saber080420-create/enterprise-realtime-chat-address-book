<template>
  <div id="app">
    <!-- Vue Router 视图出口 -->
    <RouterView />
  </div>
</template>

<script setup>
/**
 * App.vue - Vue 应用根组件
 * 
 * 功能：
 * 1. 提供 Vue 应用的根容器
 * 2. 包含 RouterView 组件用于路由视图显示
 * 3. 全局样式和布局设置
 */
import { RouterView } from 'vue-router'
</script>

<style scoped>
/* 应用根容器样式 */
#app {
  width: 100%;
  height: 100vh;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, sans-serif;
}
</style>