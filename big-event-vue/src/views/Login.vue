<script setup>
import { User, Lock, OfficeBuilding } from '@element-plus/icons-vue'
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter, useRoute } from 'vue-router'
import { setToken } from '@/utils/auth.js'
import { useUserStore } from '@/stores/user'
// 统一到门面 api
import api from '@/api'

// 定义事件
const emit = defineEmits(['login-success'])
//控制注册与登录表单的显示， 默认显示注册
const isRegister = ref(false)
//定义数据模型
const registerData = ref({
    username:'',
    password:'',
    rePassword:'',
    departmentId: '' // 新增部门ID字段
})

// 路由实例
const router = useRouter()
const route = useRoute()

// 部门列表
const departmentOptions = ref([])

// 是否正在加载部门数据
const loadingDepartments = ref(false)

//校验密码的函数
const checkRePassword=(rule,value,callback)=>{
    if(value===''){
        callback(new Error('请再次确认密码'))
    }else if(value !== registerData.value.password){
        callback(new Error('请确保两次输入的密码一样'))
    }else{
        callback()
    }
}

//定义表单校验规则
const rules={
    username:[
        {required:true,message:'请输入用户名',trigger:'blur'},
        {min:5,max:10,message:'长度为5~16位非空字符',trigger:'blur'}
    ],
    password:[
        {required:true,message:'请输入密码',trigger:'blur'},
        {min:5,max:10,message:'长度为5~16位非空字符',trigger:'blur'}
    ],
    rePassword:[
        {validator:checkRePassword,trigger:'blur'}
    ],
    departmentId: [
        {required:true,message:'请选择所属部门',trigger:'change'}
    ]
}

//调用后台接口，完成注册
// 使用统一 API 门面进行登录/注册
// import {userRegisterService,userLoginService} from'@/api/user.js'
import {getAllDepartmentsService, getDepartmentTreeService} from'@/api/department.js'

// 获取部门列表
const fetchDepartments = async () => {
    try {
        loadingDepartments.value = true
        console.log('开始获取部门列表...')
        
        // 使用getDepartmentTreeService代替getAllDepartmentsService
        // 因为/department/tree接口不需要token验证，而/department接口需要token验证
        const res = await getDepartmentTreeService()
        console.log('部门接口响应:', res)
        
        if (res && res.code === 0 && res.data) {
            // 将树形结构扁平化处理
            const flattenDepartments = (departments, result = []) => {
                departments.forEach(dept => {
                    if (dept && dept.id && dept.departmentName) {
                        result.push({
                            id: dept.id,
                            departmentName: dept.departmentName
                        })
                        if (dept.children && dept.children.length > 0) {
                            flattenDepartments(dept.children, result)
                        }
                    }
                })
                return result
            }
            
            const flatDepartments = flattenDepartments(res.data)
            departmentOptions.value = flatDepartments.map(dept => ({
                value: dept.id,
                label: dept.departmentName
            }))
            
            console.log('成功获取部门列表，数量:', departmentOptions.value.length)
        } else {
            console.warn('部门数据格式异常:', res)
            // 设置默认选项，确保用户可以继续注册
            departmentOptions.value = [
                { value: 1, label: '默认部门' }
            ]
            ElMessage.warning('获取部门列表失败，已设置默认部门选项')
        }
    } catch (error) {
        console.error('获取部门列表错误:', error)
        
        // 设置默认部门选项，确保注册功能可用
        departmentOptions.value = [
            { value: 1, label: '默认部门' }
        ]
        
        // 根据错误类型显示不同消息
        if (error.response && error.response.status === 500) {
            ElMessage.warning('服务器正在处理中，已为您设置默认部门选项')
        } else if (error.code === 'NETWORK_ERROR') {
            ElMessage.warning('网络连接异常，已为您设置默认部门选项')
        } else {
            ElMessage.warning('获取部门列表失败，已为您设置默认部门选项')
        }
        
        console.log('已设置默认部门选项，注册功能仍可正常使用')
    } finally {
        loadingDepartments.value = false
    }
}

// 组件挂载时获取部门列表
onMounted(() => {
    fetchDepartments()
    // 若因异地登录被跳转至此，显示明确提示
    try {
        if (route.query && route.query.reason === 'force_logout') {
            ElMessage.error('该账号已在另一台设备登录')
        }
    } catch (_) {}
})

const register = async()=>{
    const payload = {
        username: registerData.value.username,
        password: registerData.value.password,
        rePassword: registerData.value.rePassword,
        departmentId: registerData.value.departmentId
    }
    const result = await api.account.register(payload)
    ElMessage.success(result.msg ? result.msg : '注册成功')
}

//绑定数据，复用注册表单的数据模型
//表单数据校验
/**
 * 登录函数：完成认证、缓存信息并跳转
 * 功能说明：
 * 1) 调用后端登录接口获取token
 * 2) 将token持久化到localStorage，便于请求拦截器携带
 * 3) 调用“获取当前用户信息”接口，并把结果写入localStorage的 userInfo
 * 4) 触发 login-success 事件（兼容既有逻辑）
 * 5) 跳转到欢迎页 /welcome（根路径已重定向）
 */
const login = async()=>{
    try {
        const userStore = useUserStore()
        // 1. 后端认证，获取token
        const result = await api.account.login({
            username: registerData.value.username,
            password: registerData.value.password
        })
        // 显示登录成功消息
        ElMessage.success(result.msg ? result.msg : '登录成功')
        
        // 2. 保存token
        if (result.data) {
            setToken(result.data)
        }

        // 3. 记录登录活动（可选，不阻断流程）
        try { await api.account.loginActivity() } catch (_) {}

        // 4. 拉取并持久化用户信息（路由守卫依赖 localStorage.userInfo）
        try {
            const infoRes = await api.account.me()
            if (infoRes && infoRes.code === 0 && infoRes.data) {
                localStorage.setItem('userInfo', JSON.stringify(infoRes.data))
                try { userStore.clear(); await userStore.loadUserInfo() } catch (_) {}
            }
        } catch (e) {
            console.error('获取用户信息失败:', e)
        }
        
        /**
         * 新增：建立“新的登录会话”
         * 函数级注释：
         * - 清空 Pinia 中与上次登录关联的聊天数据（保留系统公告会话）
         * - 设置 chatStore.loginAt = 当前时间，用于“未解锁历史时仅展示离线/新消息”的过滤条件
         * - 注意：绑定 ownerUserId 的逻辑仍在 Home.vue 的 loadUserInfo 中执行
         */
        // 已移除：聊天模块初始化逻辑（chatStore.beginNewLoginSession）
        
        // 5. 触发事件（向后兼容）
        if (result.data) {
            emit('login-success', result.data)
        }

        // 6. 导航到首页/欢迎页
        await router.replace('/welcome')
    } catch (error) {
        // 登录失败处理
        console.error('登录失败:', error)
    }
}

//定义函数，清空数据模型的数据
const clearRegisterData = ()=>{
    registerData.value={
        username:'',
        password:'',
        rePassword:'',
        departmentId: ''
    }
}
</script>

<template>
    <el-row class="login-page">
        <el-col :span="12" class="bg"></el-col>
        <el-col :span="6" :offset="3" class="form">
            <!-- 注册表单 -->
            <el-form ref="form" size="large" autocomplete="off" v-if="isRegister" :model="registerData" :rules="rules">
                <el-form-item>
                    <h1>注册</h1>
                </el-form-item>
                <el-form-item prop="username">
                    <el-input :prefix-icon="User" placeholder="请输入用户名" v-model="registerData.username"></el-input>
                </el-form-item>
                <el-form-item prop="password">
                    <el-input :prefix-icon="Lock" type="password" placeholder="请输入密码" v-model="registerData.password"></el-input>
                </el-form-item>
                <el-form-item prop="rePassword">
                    <el-input :prefix-icon="Lock" type="password" placeholder="请输入再次密码" v-model="registerData.rePassword"></el-input>
                </el-form-item>
                <el-form-item prop="departmentId">
                    <el-select 
                        v-model="registerData.departmentId" 
                        placeholder="请选择所属部门" 
                        style="width: 100%" 
                        :loading="loadingDepartments">
                        <el-option 
                            v-for="item in departmentOptions" 
                            :key="item.value" 
                            :label="item.label" 
                            :value="item.value">
                        </el-option>
                    </el-select>
                </el-form-item>
                <!-- 注册按钮 -->
                <el-form-item>
                    <el-button class="button" type="primary" auto-insert-space @click="register">
                        注册
                    </el-button>
                </el-form-item>
                <el-form-item class="flex">
                    <el-link type="info" :underline="false" @click="isRegister = false;clearRegisterData()">
                        ← 返回
                    </el-link>
                </el-form-item>
            </el-form>
            <!-- 登录表单 -->
            <el-form ref="form" size="large" autocomplete="off" v-else :model="registerData" :rules="rules">
                <el-form-item>
                    <h1>登录</h1>
                </el-form-item>
                <el-form-item prop="username">
                    <el-input :prefix-icon="User" placeholder="请输入用户名" v-model="registerData.username"></el-input>
                </el-form-item>
                <el-form-item prop="password">
                    <el-input name="password" :prefix-icon="Lock" type="password" placeholder="请输入密码" v-model="registerData.password"></el-input>
                </el-form-item>
                <!-- 已移除：记住我 / 忘记密码 -->
                <!-- 登录按钮 -->
                <el-form-item>
                    <el-button class="button" type="primary" auto-insert-space @click="login">登录</el-button>
                </el-form-item>
                <el-form-item class="flex">
                    <el-link type="info" :underline="false" @click="isRegister = true;clearRegisterData()">
                        注册 →
                    </el-link>
                </el-form-item>
            </el-form>
        </el-col>
    </el-row>
</template>

<style lang="scss" scoped>
/* 样式 */
.login-page {
    height: 100vh;
    background-color: #fff;

    .bg {
        background: url('') no-repeat 60% center / 240px auto,
            url('@/assets/login_bg.jpg') no-repeat center / cover;
        border-radius: 0 20px 20px 0;
    }

    .form {
        display: flex;
        flex-direction: column;
        justify-content: center;
        user-select: none;

        .title {
            margin: 0 auto;
        }

        .button {
            width: 100%;
        }

        .flex {
            width: 100%;
            display: flex;
            justify-content: space-between;
        }
    }
}
</style>