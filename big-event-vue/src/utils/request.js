//定制请求的实例

//导入axios  npm install axios
import axios from 'axios';
import { ElMessage } from 'element-plus'
import { getToken, removeToken } from '@/utils/auth.js'
import { useUserStore } from '@/stores/user'
import router from '@/router'
//定义一个变量,记录公共的前缀  ,  baseURL
//const baseURL = 'http://localhost:8080';
const baseURL = '/api';
const instance = axios.create({baseURL})

// 新增：辅助函数 - 判断是否为“已取消/中止”的请求错误
// 函数级注释：
// - axios 在请求被主动取消（如组件卸载、路由跳转）或浏览器中止（net::ERR_ABORTED）时，会抛出 CanceledError/ERR_CANCELED
// - 这类错误属于预期行为，不应弹出“服务异常”提示，也无需打印冗长错误日志
// - 统一在响应拦截器中进行静默处理，返回拒绝的 Promise 以维持调用方逻辑一致性
function isCanceledRequest(err) {
  return axios.isCancel?.(err) ||
         err?.code === 'ERR_CANCELED' ||
         err?.name === 'CanceledError' ||
         (typeof err?.message === 'string' && err.message.toLowerCase().includes('canceled'))
}

// 添加请求拦截器
instance.interceptors.request.use(
    config => {
        // 从auth工具类获取token
        const token = getToken()
        // 如果token存在，则添加到请求头中（使用原始token，不添加Bearer前缀）
        if (token) {
            config.headers.Authorization = token
        }
        return config
    },
    error => {
        // 请求错误处理
        return Promise.reject(error)
    }
)

//添加响应拦截器
instance.interceptors.response.use(
    result=>{
        console.log('响应拦截器收到响应:', result)
        //判断业务状态码
        if(result.data.code===0){
            //操作成功
            return result.data;
        }
        
        //操作失败
        console.error('业务错误:', result.data)
        // 提取错误信息 - 修复字段名称从msg改为message
        let errorMsg = result.data.message || "服务异常"
        
        // 记录最终处理后的错误信息
        console.log('处理后的错误信息:', errorMsg)
        
        ElMessage.error(errorMsg)
        //把异步操作的状态转换为失败
        return Promise.reject(result.data)
    },
    err=>{
        // 新增：首先识别并静默处理“请求被取消/中止”的情况（如路由跳转、组件卸载）
        // 函数级注释：
        // - 常见于浏览器报错 net::ERR_ABORTED 或 axios 报错 ERR_CANCELED
        // - 这不是后端错误，直接忽略即可，避免误导性的错误提示
        if (isCanceledRequest(err)) {
            console.warn('请求已取消：', err?.config?.url || '')
            return Promise.reject(err)
        }

        console.error('请求错误:', err)
        // 输出详细错误信息
        if (err.response) {
            console.error('错误状态码:', err.response.status)
            console.error('错误数据:', err.response.data)
            console.error('错误头信息:', err.response.headers)
            
            // 记录请求URL和方法
            if (err.config) {
                console.error('请求URL:', err.config.url)
                console.error('请求方法:', err.config.method)
                console.error('请求头:', err.config.headers)
            }
        } else if (err.request) {
            console.error('未收到响应:', err.request)
        } else {
            console.error('错误信息:', err.message)
        }
        console.error('错误配置:', err.config)
        
        // 处理401未授权错误
        if (err.response && err.response.status === 401) {
            ElMessage.error('该账号已在另一台设备登录，你已被登出')
            // 清除token
            removeToken()
            try { useUserStore().clear() } catch (_) {}
            // 使用路由导航到登录页，而不是刷新页面
            router.push('/login')
        } else {
            ElMessage.error('服务异常: ' + (err.message || '未知错误'))
        }
        return Promise.reject(err);//异步的状态转化成失败的状态
    }
)

export default instance;