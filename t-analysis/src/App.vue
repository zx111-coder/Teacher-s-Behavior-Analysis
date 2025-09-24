<template>
    <el-container style="height: 98vh;display: flex;" v-if="role==='admin'">
    <!-- 左侧可收缩侧边栏 -->
    <el-aside :width="sidebarWidth" v-if="showSidebar" class="custom-aside">
      <!-- 系统Logo -->
      <div class="sidebar-header">
        <div class="logo">课堂视频行为<br/>分析系统</div>
        <el-tooltip 
          :content="isSidebarCollapsed ? '打开菜单' : '关闭菜单'" 
          placement="right"
          trigger="hover"
        >
         <!-- 收缩按钮 -->
        <el-button 
          circle 
          size="" 
          class="toggle-btn"
          @click="toggleSidebar"
        >
          <Menu /> <!-- 使用菜单图标 -->
        </el-button>
      </el-tooltip>
       
      </div>
      <!-- 菜单组件 文字激活状态是白色-->
      <el-menu
        router
        :default-active="$route.path"
        class="custom-menu"
        text-color="#b2aeb0"
        active-text-color="#fff"
        :collapse="isSidebarCollapsed"
      > 
        <!-- 用户信息区域 -->
        <!-- <div class="profile-section" v-if="!isSidebarCollapsed">
          <div class="profile-bg"></div>
          <div class="welcome-text">
            <span>Welcome,</span>
            <h3 class="username">{{ username }}</h3>
          </div> 
        </div> -->
        <!-- 菜单列表 -->

        <el-menu-item index="/analysis">
          <template >视频分析</template>
        </el-menu-item>
        <el-menu-item index="/dashboard">
          <template #title>数据看板</template>
        </el-menu-item>
        <el-menu-item index="/user" >
          <template #title>历史记录</template>
        </el-menu-item>
        <el-menu-item index="/admin" >
          <template #title>用户管理</template>
        </el-menu-item>
      </el-menu>
    </el-aside>
    
    <el-container class="main-container">
      <el-header v-if="showSidebar" class="custom-header">
        <div class="header-content">
          <div class="breadcrumb">
            <!-- 面包屑可扩展，这里仅示例 -->
            <el-breadcrumb separator="/">
              <el-breadcrumb-item :to="{ path: '/analysis' }">首页</el-breadcrumb-item>
              <el-breadcrumb-item>{{ $route.meta.title || '当前页面' }}</el-breadcrumb-item>
            </el-breadcrumb>
          </div>
          <div class="user-info">
            <el-dropdown @command="handleCommand">
              <span class="el-dropdown-link">
                {{ username }} 
              </span>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="logout">退出登录</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </div>
      </el-header>
      
      <el-main class="custom-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
  <el-container style="height: 98vh;display: flex;" v-else>
    <!-- 左侧可收缩侧边栏 -->
    <el-aside :width="sidebarWidth" v-if="showSidebar" class="custom-aside">
      <!-- 系统Logo -->
      <div class="sidebar-header">
        <div class="logo">课堂视频行为<br/>分析系统</div>
        <el-tooltip 
          :content="isSidebarCollapsed ? '打开菜单' : '关闭菜单'" 
          placement="right"
          trigger="hover"
        >
         <!-- 收缩按钮 -->
        <el-button 
          circle 
          size="" 
          class="toggle-btn"
          @click="toggleSidebar"
        >
          <Menu /> <!-- 使用菜单图标 -->
        </el-button>
      </el-tooltip>
       
      </div>
      <!-- 菜单组件 文字激活状态是白色-->
      <el-menu
        router
        :default-active="$route.path"
        class="custom-menu"
        text-color="#b2aeb0"
        active-text-color="#fff"
        :collapse="isSidebarCollapsed"
      > 
        <!-- 用户信息区域 -->
        <!-- <div class="profile-section" v-if="!isSidebarCollapsed">
          <div class="profile-bg"></div>
          <div class="welcome-text">
            <span>Welcome,</span>
            <h3 class="username">{{ username }}</h3>
          </div> 
        </div> -->
        <!-- 菜单列表 -->

        <el-menu-item index="/analysis">
          <template #title>视频分析</template>
        </el-menu-item>
        <el-menu-item index="/dashboard">
          <template #title>数据看板</template>
        </el-menu-item>
        <el-menu-item index="/user" >
          <template #title>历史记录</template>
        </el-menu-item>
      </el-menu>
    </el-aside>
    
    <el-container class="main-container">
      <el-header v-if="showSidebar" class="custom-header">
        <div class="header-content">
          <div class="breadcrumb">
            <!-- 面包屑可扩展，这里仅示例 -->
            <el-breadcrumb separator="/">
              <el-breadcrumb-item :to="{ path: '/analysis' }">首页</el-breadcrumb-item>
              <el-breadcrumb-item>{{ $route.meta.title || '当前页面' }}</el-breadcrumb-item>
            </el-breadcrumb>
          </div>
          <div class="user-info">
            <el-dropdown @command="handleCommand">
              <span class="el-dropdown-link">
                {{ username }} 
              </span>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="logout">退出登录</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </div>
      </el-header>
      
      <el-main class="custom-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script >
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { removeToken } from './utils/auth'
import { ElMessage, ElBreadcrumb, ElBreadcrumbItem } from 'element-plus'
import { useUserStore } from '@/store/user.js'
// 引入图标
import { Menu, VideoCamera, House } from '@element-plus/icons-vue'

export default {
  name: 'App',
  components: {
    ElBreadcrumb,
    ElBreadcrumbItem,
    Menu, // 注册菜单图标
    VideoCamera, // 注册视频图标
    House // 注册房子图标
  },
  setup() {
    const router = useRouter()
    const userStore = useUserStore()
    // if (storedUsername) {
    //   userStore.setUsername(storedUsername)
    // }

    // 侧边栏收缩状态
    const isSidebarCollapsed = ref(false)
    const sidebarWidth = computed(() => isSidebarCollapsed.value ? '64px' : '220px')
    const showSidebar = computed(() => !['/', '/register'].includes(router.currentRoute.value.path))
    const role = computed(() => userStore.role) // 补充管理员判断

    // 收缩侧边栏方法
    const toggleSidebar = () => {
      isSidebarCollapsed.value = !isSidebarCollapsed.value; // 切换菜单状态
    };

    // 统一处理下拉菜单命令
    const handleCommand = (command) => {
      if (command === 'logout') {
        userStore.clearUserInfo(); // 清空用户名
        router.push('/')
        ElMessage.success('已退出登录')
      }
    }
    
    return {
      username:computed(() => userStore.username),
      role,
      showSidebar,
      isSidebarCollapsed,
      sidebarWidth,
      toggleSidebar,
      handleCommand
    }
  }
}
</script>

<style scoped lang="scss">
// 全局样式重置
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

// 左侧侧边栏
.custom-aside {
  background-color: #472f3e;
  color: #fff;
  height:100%;// 让侧边栏充满整个容器高度
  flex-direction: column; // 确保内容垂直排列
  padding: 0; // 移除内边距
  margin: 0; // 移除外边距
  box-sizing: border-box; // 确保宽高计算包含边框和内边距
  //定义0.3s的动画的css属性
  transition: width 0.3s cubic-bezier(0.68, -0.55, 0.27, 1.55);
  //菜单的用户标题
  .sidebar-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 16px 20px;
    background-color: #463a40;
    //大标题的字体格式
    .logo {
      font-size: 18px;
      font-weight: 600;
      color: #e9e8e8;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
    //菜单图标格式
    .toggle-btn svg {
      color: #c1c1c1; /* 设置图标颜色为白色 */
      font-size: 16px; /* 设置图标大小 */
      width: 20px; /* 设置图标宽度 */
      height: 20px; /* 设置图标高度 */
    }
    //按钮的格式
    .toggle-btn {
      display: flex;
      align-items: center;
      justify-content: center;
      background-color: #463a40; /* 按钮背景色 */
      color: #fff; /* 按钮文字颜色 */
      border: none;
      padding: 8px 12px; /* 调整按钮内边距 */
      border-radius: 20px; /* 圆角效果 */
      font-size: 14px; /* 调整文字大小 */
      transition: all 0.3s ease; /* 添加过渡效果 */

      &:hover {
        background-color: #664858; /* 鼠标悬停时的背景色 */
      }

      .el-icon-menu {
        font-size: 16px; /* 图标大小 */
        color:#fff; /* 图标颜色 */
      }

      .toggle-text {
        white-space: nowrap; /* 防止文字换行 */
        font-size: 14px; /* 文字大小 */
      }
    }
   
  }

  // 用户信息区域
  // .profile-section {
  //   position: relative;
  //   padding: 15px 10px;
  //   text-align: center;
  //   background-color: #25171f;
  //   margin-bottom: 20px;
    // .profile-bg {
    //   position: absolute;
    //   top: 0;
    //   left: 0;
    //   width: 100%;
    //   height: 80px;
    //   background: url('@/assets/profile-bg.png') center/cover no-repeat;
    //   opacity: 0.2;
    // }
    // .profile-img {
    //   width: 64px;
    //   height: 64px;
    //   border-radius: 50%;
    //   border: 2px solid #fff;
    //   margin: 10px 0;
    //   position: relative;
    //   z-index: 1;
    // }
  //   .welcome-text {
  //     color: #fff;
  //     line-height: 1.4;
  //     span {
  //       font-size: 14px;
  //       opacity: 0.8;
  //     }
  //     .username {
  //       font-size: 16px;
  //       margin-top: 4px;
  //       font-weight: 500;
  //     }
  //   }
  // }

  // 菜单样式
  .custom-menu {
    border-right: none;
    background-color: #472f3e;
    .el-menu-item, .el-sub-menu__title {
      padding-left: 20px !important;
      transition: all 0.3s;
      
      &:hover {
        background-color: #25171f !important;
      }
    }

    // 收缩状态优化
    &.el-menu--collapse {
      .el-menu-item__icon {
        margin: 0 auto;
      }
    }
  }
}

// 主容器
.main-container {
  display: flex;
  flex-direction: column;
  flex: 1;
  background-color: #ffffff;
}

// 顶部导航栏
.custom-header {
  background-color: #fffeff;
  border-bottom: 1px solid #e6e8f0;
  color: #333;
  padding: 0 20px;
  
  .header-content {
    display: flex;
    align-items: center;
    justify-content: space-between;
    height: 60px;
    
    .breadcrumb {
      font-size: 14px;
      
      .el-breadcrumb {
        line-height: 60px;
        
        .el-breadcrumb__item {
          color: #666;
          
          &:last-child {
            color: #333;
            font-weight: 500;
          }
        }
      }
    }
    
    .user-info {
      .el-dropdown-link {
        color: #333;
        font-size: 14px;
        
        i {
          margin-left: 4px;
          font-size: 12px;
        }
      }
    }
  }
}

// 主要内容区
.custom-main {
  flex: 1;
  background-color: #f5f7fa;
  height: calc(100vh - 60px); // 减去顶部导航栏的高度
  overflow-y: auto; // 如果内容过多，允许垂直滚动
}

// 响应式优化
@media (max-width: 768px) {
  .custom-aside {
    width: 64px !important;
    .sidebar-header .logo,
    .profile-section .welcome-text {
      display: none;
    }
  }
}
</style>