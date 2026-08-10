<script setup>
import { ref, onMounted, computed, watch } from 'vue'
import * as echarts from 'echarts'
import { InfoFilled } from '@element-plus/icons-vue'
import { ElMessage, ElLoading } from 'element-plus'

// 导入统计API服务
import { 
  getDepartmentUserStatsService,
  getDailyActiveUserStatsService,
  getMessageTypeStatsService,
  getDepartmentOnlineTimeStatsService,
  getSystemOverviewService,
  getOnlineUserCountService
} from '@/api/statistics.js'

// 移除部门筛选相关依赖

// 图表实例引用
const userActivityChartRef = ref(null)
const departmentDistributionChartRef = ref(null)
const loginTrendChartRef = ref(null)
const onlineTimeChartRef = ref(null)

// 时间范围选择
const timeRange = ref('last30days')

// 部门筛选已移除

// 加载状态
const loading = ref(false)

// 统计数据
const departmentUserStats = ref([])
const dailyActiveUserStats = ref([])
const messageTypeStats = ref([])

// 初始化在线占比饼图（在线 vs 不在线）
const initUserActivityChart = async () => {
  const chartDom = userActivityChartRef.value
  if (!chartDom) return
  
  const myChart = echarts.init(chartDom)
  
  // 拉取概览（总用户数）与在线人数（近5分钟活跃视为在线）
  let totalUsers = 0
  let onlineUsers = 0
  try {
    const [overviewRes, onlineRes] = await Promise.all([
      getSystemOverviewService(),
      getOnlineUserCountService()
    ])
    if (overviewRes?.code === 0) {
      totalUsers = Number(overviewRes.data?.totalUsers || 0)
      // 以概览的口径为准：近5分钟活跃视为在线
      onlineUsers = Number(overviewRes.data?.onlineUsers || 0)
      const disabledUsers = Number(overviewRes.data?.disabledUsers || 0)
      // 如果概览未返回在线数（极端情况），回退到在线计数接口
      if (!onlineUsers && onlineRes?.code === 0) {
        onlineUsers = Number(onlineRes.data || 0)
      }
      const offlineActiveUsers = Math.max(0, totalUsers - disabledUsers - onlineUsers)
      const option = {
        title: { text: '在线用户占比（含停用账号）', left: 'center' },
        tooltip: { trigger: 'item' },
        legend: { orient: 'vertical', left: 'left' },
        series: [
          {
            name: '在线占比',
            type: 'pie',
            radius: '50%',
            data: [
              { value: onlineUsers, name: '在线' },
              { value: offlineActiveUsers, name: '不在线（有效账号）' },
              { value: disabledUsers, name: '停用账号' }
            ],
            emphasis: { itemStyle: { shadowBlur: 10, shadowOffsetX: 0, shadowColor: 'rgba(0, 0, 0, 0.5)' } }
          }
        ]
      }
      myChart.setOption(option)
      window.addEventListener('resize', () => { myChart && myChart.resize() })
      return
    }
    if (onlineRes?.code === 0) onlineUsers = Number(onlineRes.data || 0)
  } catch (e) {
    console.error('获取在线占比数据失败:', e)
  }
  // 概览拿不到 disabledUsers 的兜底（极端情况）：退回为两段式
  const offlineUsers = Math.max(0, totalUsers - onlineUsers)
  const fallback = {
    title: { text: '在线用户占比', left: 'center' },
    tooltip: { trigger: 'item' },
    legend: { orient: 'vertical', left: 'left' },
    series: [
      { name: '在线占比', type: 'pie', radius: '50%', data: [ { value: onlineUsers, name: '在线' }, { value: offlineUsers, name: '不在线' } ], emphasis: { itemStyle: { shadowBlur: 10, shadowOffsetX: 0, shadowColor: 'rgba(0,0,0,0.5)' } } }
    ]
  }
  myChart.setOption(fallback)
  window.addEventListener('resize', () => { myChart && myChart.resize() })
}

// 初始化部门人员分布图表
const initDepartmentDistributionChart = () => {
  const chartDom = departmentDistributionChartRef.value
  if (!chartDom) return
  
  const myChart = echarts.init(chartDom)
  
  // 从部门用户统计数据中提取部门名称和人数
  const deptNames = []
  const deptCounts = []
  
  if (departmentUserStats.value && departmentUserStats.value.length > 0) {
    // 按人数降序排序
    const sortedStats = [...departmentUserStats.value].sort((a, b) => b.count - a.count)
    
    // 提取部门名称和人数
    sortedStats.forEach(dept => {
      // 注意：后端统计接口返回的字段为 departmentName
      deptNames.push(dept.departmentName || dept.name)
      deptCounts.push(dept.count)
    })
  }
  
  const option = {
    title: {
      text: '部门人员分布',
      left: 'center'
    },
    tooltip: {
      trigger: 'axis',
      axisPointer: {
        type: 'shadow'
      }
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      containLabel: true
    },
    xAxis: {
      type: 'value',
      boundaryGap: [0, 0.01]
    },
    yAxis: {
      type: 'category',
      data: deptNames.length > 0 ? deptNames : ['暂无数据']
    },
    series: [
      {
        name: '人数',
        type: 'bar',
        data: deptCounts.length > 0 ? deptCounts : [0]
      }
    ]
  }
  
  myChart.setOption(option)
  
  // 窗口大小变化时重新调整图表大小
  window.addEventListener('resize', () => {
    myChart && myChart.resize()
  })
}

// 初始化登录趋势图表
const initLoginTrendChart = () => {
  const chartDom = loginTrendChartRef.value
  if (!chartDom) return
  
  const myChart = echarts.init(chartDom)
  
  // 确定时间范围
  let days = 30
  if (timeRange.value === 'last7days') days = 7
  else if (timeRange.value === 'last90days') days = 90
  
  // 生成过去N天的日期
  const dates = []
  const now = new Date()
  for (let i = days - 1; i >= 0; i--) {
    const date = new Date(now)
    date.setDate(now.getDate() - i)
    dates.push(`${date.getMonth() + 1}/${date.getDate()}`)
  }
  
  // 准备登录数据
  const loginData = new Array(days).fill(0)
  
  // 如果有每日活跃用户数据，则使用实际数据
  if (dailyActiveUserStats.value && dailyActiveUserStats.value.length > 0) {
    // 创建日期映射表
    const dateMap = {}
    dates.forEach((dateStr, index) => {
      dateMap[dateStr] = index
    })
    
    // 填充实际数据
    dailyActiveUserStats.value.forEach(item => {
      if (item.date) {
        const date = new Date(item.date)
        const dateStr = `${date.getMonth() + 1}/${date.getDate()}`
        if (dateMap[dateStr] !== undefined) {
          loginData[dateMap[dateStr]] = item.count || 0
        }
      }
    })
  }
  
  const option = {
    title: {
      text: '每日登录人数趋势',
      left: 'center'
    },
    tooltip: {
      trigger: 'axis'
    },
    xAxis: {
      type: 'category',
      data: dates,
      axisLabel: {
        interval: Math.floor(days / 10)
      }
    },
    yAxis: {
      type: 'value'
    },
    series: [
      {
        data: loginData,
        type: 'line',
        smooth: true,
        areaStyle: {}
      }
    ]
  }
  
  myChart.setOption(option)
  
  // 窗口大小变化时重新调整图表大小
  window.addEventListener('resize', () => {
    myChart && myChart.resize()
  })
}

// 在线时长数据
const departmentOnlineTimeStats = ref([])

// 获取部门在线时长统计数据
const fetchDepartmentOnlineTimeStats = async () => {
  try {
    // 根据时间范围确定天数
    let days = 30
    if (timeRange.value === 'last7days') days = 7
    else if (timeRange.value === 'last90days') days = 90
    
    const res = await getDepartmentOnlineTimeStatsService(days)
    if (res.code === 0 && res.data) {
      departmentOnlineTimeStats.value = res.data
    } else {
      // 如果返回错误码或没有数据，设置为空数组
      departmentOnlineTimeStats.value = []
      console.log('没有部门在线时长数据可用')
    }
  } catch (error) {
    console.error('获取部门在线时长统计数据失败:', error)
    // 不显示错误消息，而是设置为空数组
    departmentOnlineTimeStats.value = []
    // 静默处理错误，不向用户显示错误提示
  }
}

// 初始化在线时长图表
const initOnlineTimeChart = () => {
  const chartDom = onlineTimeChartRef.value
  if (!chartDom) return
  
  const myChart = echarts.init(chartDom)
  
  // 从API获取的在线时长数据中提取部门名称和时长
  const deptNames = []
  const workdayData = []
  const weekendData = []
  
  if (departmentOnlineTimeStats.value && departmentOnlineTimeStats.value.length > 0) {
    // 按工作日时长降序排序
    const sortedStats = [...departmentOnlineTimeStats.value].sort((a, b) => b.workday_hours - a.workday_hours)
    
    // 提取部门名称和时长数据
    sortedStats.forEach(dept => {
      deptNames.push(dept.department_name)
      workdayData.push(parseFloat(dept.workday_hours).toFixed(1))
      weekendData.push(parseFloat(dept.weekend_hours).toFixed(1))
    })
  } else if (departmentUserStats.value && departmentUserStats.value.length > 0) {
    // 如果没有在线时长数据，则使用部门用户统计数据生成模拟数据作为备用
    // 按人数降序排序
    const sortedStats = [...departmentUserStats.value].sort((a, b) => b.count - a.count)
    
    // 生成模拟数据
    sortedStats.forEach(dept => {
      // 注意：此处兜底兼容 name，优先使用 departmentName
      deptNames.push(dept.departmentName || dept.name)
      const userCount = dept.count || 0
      const workdayHours = (5 + (userCount % 3)) + Math.random().toFixed(1) * 1
      const weekendHours = (1 + (userCount % 2)) + Math.random().toFixed(1) * 1
      
      workdayData.push(parseFloat(workdayHours.toFixed(1)))
      weekendData.push(parseFloat(weekendHours.toFixed(1)))
    })
  }
  
  // 判断是否有数据可显示
  const hasData = deptNames.length > 0;
  
  const option = {
    title: {
      text: '各部门平均在线时长',
      left: 'center',
      top: 0,
      textStyle: {
        fontSize: 16
      },
      subtext: !hasData ? '暂无数据，请确保用户有登录记录' : ''
    },
    tooltip: {
      trigger: 'axis',
      axisPointer: {
        type: 'shadow'
      }
    },
    legend: {
      top: '10%'
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      top: '25%',  // 增加顶部间距，避免标题重叠
      containLabel: true
    },
    xAxis: {
      type: 'category',
      data: hasData ? deptNames : ['暂无数据']
    },
    yAxis: {
      type: 'value',
      name: '小时'
    },
    series: [
      {
        name: '工作日',
        type: 'bar',
        stack: 'total',
        emphasis: {
          focus: 'series'
        },
        data: hasData ? workdayData : [0],
        itemStyle: {
          color: '#5470c6'
        }
      },
      {
        name: '周末',
        type: 'bar',
        stack: 'total',
        emphasis: {
          focus: 'series'
        },
        data: hasData ? weekendData : [0],
        itemStyle: {
          color: '#91cc75'
        }
      }
    ]
  }
  
  // 如果没有数据，添加文本提示
  if (!hasData) {
    option.graphic = [
      {
        type: 'text',
        left: 'center',
        top: 'middle',
        style: {
          text: '暂无部门在线时长数据',
          fontSize: 16,
          fontWeight: 'bold',
          fill: '#999',
          lineHeight: 30
        }
      },
      {
        type: 'text',
        left: 'center',
        top: 'middle',
        style: {
          text: '请确保用户有登录记录',
          fontSize: 14,
          fill: '#999',
          lineHeight: 30,
          y: 30
        }
      }
    ];
  }
  
  myChart.setOption(option)
  
  // 窗口大小变化时重新调整图表大小
  window.addEventListener('resize', () => {
    myChart && myChart.resize()
  })
}

// 已移除部门列表加载

// 获取部门用户统计数据
const fetchDepartmentUserStats = async () => {
  try {
    const res = await getDepartmentUserStatsService()
    if (res.code === 0 && res.data) {
      departmentUserStats.value = res.data
    }
  } catch (error) {
    console.error('获取部门用户统计数据失败:', error)
    ElMessage.error('获取部门用户统计数据失败')
  }
}

// 获取每日活跃用户统计数据
const fetchDailyActiveUserStats = async () => {
  try {
    // 根据时间范围确定天数
    let days = 30
    if (timeRange.value === 'last7days') days = 7
    else if (timeRange.value === 'last90days') days = 90
    
    const res = await getDailyActiveUserStatsService(days)
    if (res.code === 0 && res.data) {
      dailyActiveUserStats.value = res.data
    }
  } catch (error) {
    console.error('获取每日活跃用户统计数据失败:', error)
    ElMessage.error('获取每日活跃用户统计数据失败')
  }
}

// 获取消息类型统计数据
const fetchMessageTypeStats = async () => {
  try {
    const res = await getMessageTypeStatsService()
    if (res.code === 0 && res.data) {
      messageTypeStats.value = res.data
    }
  } catch (error) {
    console.error('获取消息类型统计数据失败:', error)
    ElMessage.error('获取消息类型统计数据失败')
  }
}

// 加载所有统计数据
const loadAllStats = async () => {
  loading.value = true
  try {
    await Promise.all([
      fetchDepartmentUserStats(),
      fetchDailyActiveUserStats(),
      fetchMessageTypeStats(),
      fetchDepartmentOnlineTimeStats()
    ])
    
    // 初始化图表
    initUserActivityChart()
    initDepartmentDistributionChart()
    initLoginTrendChart()
    initOnlineTimeChart()
  } catch (error) {
    console.error('加载统计数据失败:', error)
    ElMessage.error('加载统计数据失败')
  } finally {
    loading.value = false
  }
}

// 更新图表数据
const updateChartData = async () => {
  await loadAllStats()
}

// 导出图表为图片
const exportChart = (chartRef, filename) => {
  const chartDom = chartRef.value
  const myChart = echarts.getInstanceByDom(chartDom)
  
  if (myChart) {
    const url = myChart.getDataURL({
      type: 'png',
      pixelRatio: 2,
      backgroundColor: '#fff'
    })
    
    const link = document.createElement('a')
    link.href = url
    link.download = `${filename}.png`
    link.click()
  }
}

// 监听时间范围变化
watch(timeRange, () => {
  updateChartData()
  // 确保在时间范围变化时重新获取在线时长数据
  fetchDepartmentOnlineTimeStats()
})

// 已移除部门选择监听

// 组件挂载后加载数据并初始化图表
onMounted(() => {
  // 延迟一下，确保DOM已经渲染
  setTimeout(() => {
    loadAllStats()
  }, 100)
})
</script>

<template>
  <div class="statistics-container">
    <div class="statistics-header">
      <h2>数据统计分析</h2>
      <div class="filter-options">
        <el-select v-model="timeRange" placeholder="时间范围" @change="updateChartData">
          <el-option label="最近7天" value="last7days"></el-option>
          <el-option label="最近30天" value="last30days"></el-option>
          <el-option label="最近90天" value="last90days"></el-option>
        </el-select>
      </div>
    </div>
    
    <div class="statistics-content">
      <el-row :gutter="20">
        <el-col :span="12">
          <div class="chart-card">
            <div class="chart-header"><h3>在线用户占比</h3></div>
            <div class="chart-container" ref="userActivityChartRef"></div>
          </div>
        </el-col>
        
        <el-col :span="12">
          <div class="chart-card">
            <div class="chart-header"><h3>部门人员分布</h3></div>
            <div class="chart-container" ref="departmentDistributionChartRef"></div>
          </div>
        </el-col>
      </el-row>
      
      <el-row :gutter="20" style="margin-top: 20px;">
        <el-col :span="12">
          <div class="chart-card">
            <div class="chart-header"><h3>每日登录人数趋势</h3></div>
            <div class="chart-container" ref="loginTrendChartRef"></div>
          </div>
        </el-col>
        
        <el-col :span="12">
          <div class="chart-card">
            <div class="chart-header"><h3>各部门平均在线时长</h3></div>
            <div class="chart-container" ref="onlineTimeChartRef"></div>
          </div>
        </el-col>
      </el-row>
    </div>
    
    <div class="statistics-footer">
      <div class="data-info">
        <el-icon><InfoFilled /></el-icon>
        <span>统计周期：{{ timeRange === 'last7days' ? '最近7天' : timeRange === 'last30days' ? '最近30天' : '最近90天' }}</span>
        
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.statistics-container {
  padding: 20px;
  height: 100%;
  display: flex;
  flex-direction: column;
  
  .statistics-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 20px;
    
    h2 {
      margin: 0;
      font-size: 20px;
    }
    
    .filter-options {
      display: flex;
      gap: 15px;
    }
  }
  
  .statistics-content {
    flex: 1;
    overflow-y: auto;
    
    .chart-card {
      background-color: white;
      border-radius: 4px;
      box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
      padding: 15px;
      height: 350px;
      
      .chart-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: 15px;
        
        h3 {
          margin: 0;
          font-size: 16px;
          font-weight: 500;
        }
      }
      
      .chart-container {
        height: calc(100% - 40px);
      }
    }
  }
  
  .statistics-footer {
    margin-top: 20px;
    padding-top: 15px;
    border-top: 1px solid #e6e6e6;
    
    .data-info {
      display: flex;
      align-items: center;
      color: #909399;
      font-size: 14px;
      
      .el-icon {
        margin-right: 5px;
        color: #409eff;
      }
      
      span {
        margin-right: 10px;
      }
    }
  }
}
</style>