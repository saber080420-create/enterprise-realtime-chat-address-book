import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
// import vueDevTools from 'vite-plugin-vue-devtools'

// https://vite.dev/config/
export default defineConfig({
  // 显式指定为单页应用模式，确保返回 index.html
  appType: 'spa',
  plugins: [
    vue(),
    // vueDevTools(),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    },
  },
  server:{
    host: 'localhost',
    port: 5173,
    strictPort: true,
    hmr: {
      overlay: false // 关闭错误覆盖层，防止开发时弹窗遮挡
    },
    proxy:{
      '/api':{//获取路径中包含了/api的请求
          target:'http://localhost:8081',//后台服务所在的源
          changeOrigin:true,//修改源
          // 不再重写WebSocket请求路径
          rewrite:(path)=>{
            // 如果是WebSocket请求，保留/api前缀
            if(path.startsWith('/api/ws')) {
              return path;
            }
            // 其他API请求移除/api前缀
            return path.replace(/^\/api/,'');
          },
          ws: true // 启用WebSocket代理
      },
      // 新增：静态资源代理，将 /uploads 下的头像等静态文件代理到后端
      '/uploads': {
        target: 'http://localhost:8081',
        changeOrigin: true
        // 不做 rewrite，保持路径不变，形如 /uploads/avatars/xxx.jpg
      }
    }
  }
})
