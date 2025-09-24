import { createRouter, createWebHistory } from 'vue-router'
import { computed } from 'vue';
import { useUserStore } from '../store/user' //检查用户是否登录
const routes = [
  {
    path: '/',
    name: 'Login',
    component: () => import('../views/Login.vue') // 默认路径指向登录页面
  },
  {
    path: '/dashboard',
    name: 'Dashboard',
    component: () => import('../views/Dashboard.vue'),
    meta: {
      title: '数据看板'  // 定义路由元信息（动态展示界面）
    }
  },
  {
    path: '/analysis',
    name: 'Analysis',
    component: () => import('@/views/Analysis.vue'),
    meta: { 
      title:'视频分析', // 定义路由元信息（动态展示界面）
      requiresAuth: true } // 添加认证要求
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('../views/Register.vue')
  },
  {
    path: '/user',
    name: 'User',
    component: () => import('../views/User.vue'),
    meta: {
      title: '历史记录'  // 定义路由元信息
    }
  },
  {
    path:'/admin',
    name: 'Admin',
    component: () => import('@/views/Admin.vue'),
    meta: {
      title: '用户管理'  // 定义路由元信息
    }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})
// // 添加全局路由守卫
// router.beforeEach((to,form,next) => {
//   const token = computed(() => useUserStore().token).value; // 检查用户是否已登录
//   // 如果用户未登录，且目标路由不是注册页面或登录页面，则跳转到登录页面
//   if (token!== 1 && to.path !== '/register' && to.path !== '/') {
//     next('/'); // 重定向到登录页面
//   } else {
//     next(); // 放行
//   }
// });

export default router