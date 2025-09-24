import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
import path from 'path'; // 引入 path 模块

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src'), //  添加 '@' 指向 src 目录
    }
  },
  define: {
    'process.env': {}
  },
  server: {//校园网：10.130.205.77 宿舍网：172.22.189.77  sin网：172.20.10.2
    proxy: {
      '/api': { // 自定义代理前缀
        target: 'http://172.22.189.77:8080', // 目标URL
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, '') // 路径重写
      }
    }
  }
});