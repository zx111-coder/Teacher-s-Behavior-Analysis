import { createApp } from 'vue' //创建Vue应用
import App from './App.vue'
import router from './router' //引入路由，用于管理页面导航和路由
import ElementPlus from 'element-plus' //引入 Element Plus 组件库及其样式文件，
import 'element-plus/dist/index.css' //提供丰富的 UI 组件（如按钮、表单等）
import * as echarts from 'echarts' //引入 ECharts 图表库，用于数据可视化
import * as ElIcons from '@element-plus/icons-vue';  //引入 Element Plus 图标库
import { createPinia } from 'pinia';

const app = createApp(App)
// 注册所有图标组件
for (const [key, component] of Object.entries(ElIcons)) {
    app.component(key, component);
}  
app.use(router)
app.use(ElementPlus)
app.config.globalProperties.$echarts = echarts //将 ECharts 实例挂载到全局属性上
// 以便在组件中访问（画图）
app.use(createPinia()); // 注册 Pinia
app.mount('#app') // 挂载 Vue 应用到 HTML 中的 #app 元素上