<script setup>
// 欢迎页面组件
import { ref, computed } from 'vue'
import { User, ChatDotRound, OfficeBuilding, Bell } from '@element-plus/icons-vue'
</script>

<template>
  <div class="welcome-container">
    <div class="welcome-content">
      <img src="@/assets/logo.svg" alt="企业通讯录系统" class="logo" />
      <h1>欢迎使用企业通讯录系统</h1>
      <p>一站式企业通讯、沟通与组织管理平台</p>
      
      <div class="feature-cards">
        <el-card class="feature-card">
          <template #header>
            <div class="card-header">
              <el-icon><User /></el-icon>
              <span>通讯录管理</span>
            </div>
          </template>
          <div class="card-content">
            高效管理企业员工联系信息，快速查找和分组联系人
          </div>
        </el-card>
        
        <el-card class="feature-card">
          <template #header>
            <div class="card-header">
              <el-icon><ChatDotRound /></el-icon>
              <span>即时通讯</span>
            </div>
          </template>
          <div class="card-content">
            支持一对一和群组聊天，提高团队沟通效率
          </div>
        </el-card>
        
        <el-card class="feature-card">
          <template #header>
            <div class="card-header">
              <el-icon><OfficeBuilding /></el-icon>
              <span>组织架构</span>
            </div>
          </template>
          <div class="card-content">
            直观展示企业部门结构，便于了解组织关系
          </div>
        </el-card>
        
        <el-card class="feature-card">
          <template #header>
            <div class="card-header">
              <el-icon><Bell /></el-icon>
              <span>公告发布</span>
            </div>
          </template>
          <div class="card-content">
            及时发布和接收企业重要通知和公告
          </div>
        </el-card>
      </div>
      
      <div class="quick-start">
        <h3>快速开始</h3>
        <el-button type="primary" @click="$router.push('/contacts')">
          <el-icon><User /></el-icon>进入通讯录
        </el-button>
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.welcome-container {
  padding: 20px;
  height: 100%;
  overflow-y: auto;
  
  .welcome-content {
    max-width: 1000px;
    margin: 0 auto;
    padding: 40px 20px;
    text-align: center;
    
    .logo {
      width: 120px;
      height: 120px;
      margin-bottom: 20px;
    }
    
    h1 {
      font-size: 28px;
      color: #303133;
      margin-bottom: 10px;
    }
    
    p {
      font-size: 16px;
      color: #606266;
      margin-bottom: 40px;
    }
    
    .feature-cards {
      display: flex;
      flex-wrap: wrap;
      justify-content: center;
      gap: 20px;
      margin-bottom: 40px;
      
      .feature-card {
        width: 220px;
        height: 180px;
        transition: transform 0.3s;
        
        &:hover {
          transform: translateY(-5px);
        }
        
        .card-header {
          display: flex;
          align-items: center;
          font-size: 16px;
          font-weight: bold;
          
          .el-icon {
            margin-right: 8px;
            font-size: 18px;
            color: #409EFF;
          }
        }
        
        .card-content {
          font-size: 14px;
          color: #606266;
          line-height: 1.6;
          text-align: left;
        }
      }
    }
    
    .quick-start {
      margin-top: 30px;
      
      h3 {
        margin-bottom: 20px;
        font-size: 18px;
        color: #303133;
      }
      
      .el-button {
        margin: 0 10px;
        
        .el-icon {
          margin-right: 5px;
        }
      }
    }
  }
}
</style>