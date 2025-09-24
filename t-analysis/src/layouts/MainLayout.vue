<template>
    <div>
      <Sidebar v-if="!isAuthPage" /> <!-- 侧边栏 -->
      <Header v-if="!isAuthPage" /> <!-- 顶部导航 -->
      <div class="content">
        <router-view />
      </div>
    </div>
  </template>
  
  <script>
  import { useRoute } from 'vue-router';
  
  export default {
    setup() {
      const route = useRoute();
      const isAuthPage = ['Register', 'Login'].includes(route.name); // 判断是否为注册或登录页面
      return { isAuthPage };
    }
  };
  </script>