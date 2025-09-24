<template>
  <div class="login-container">
    <div class="login-box">
      <div class="title">
        <h1>教师视频行为分析系统</h1>
      </div>
      <h2 class="login-title">登录</h2>
      <form @submit.prevent="handleLogin">
        <input
          type="text"
          v-model="loginForm.username"
          placeholder="用户名"
          required
        />
        <input
          type="password"
          v-model="loginForm.password"
          placeholder="密码"
          required
        />
        <button type="submit" :disabled="loading.value"><!--disabled属性会根据loading的值动态设置-->
          {{ loading ? '登录中...' : '登录' }}
        </button>
      </form>
      <div class="register-link">
        <p>没有账号？<router-link to="/register">注册</router-link></p >
      </div>
    </div>
  </div>
</template>

<script>
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import axios from 'axios'; // 直接使用axios
import { ElMessage } from 'element-plus';
import { useUserStore } from '../store/user';

export default {
  name: 'Login',
  setup() {
    const router = useRouter();
    const userStore = useUserStore();
    const loginForm = ref({  //可以使用reactive
      username: '',
      password: '',
      role:'user' // 默认角色为普通用户
    });
    const loading = ref(false); //创建响应式数据，当值发生变化，vue会自动更新

    const handleLogin = async () => { //异步箭头函数
      if (!loginForm.value.username || !loginForm.value.password) {
        ElMessage.error('用户名和密码不能为空');
        return;
      }
      console.log("登录表单数据", loginForm);
      loading.value = true;
      
      try {
        // 使用axios直接发送请求，确保URL正确
        const response = await axios.post('/api/login', {
          username: loginForm.value.username,
          password: loginForm.value.password
        }, {
          headers: {
            'Content-Type': 'application/json'
          }
        });
        console.log("登录成功返回信息",response);
        // 处理后端响应
        if (response.data && response.data.code === 1) { // 后端返回数据是否存在
          // 更新用户状态
          userStore.setUserInfo(response.data.data);
          userStore.token= 1; //记载登录成功
          ElMessage.success("登录成功");
          router.push('/analysis');
        } else {
          ElMessage.error(response.data.msg);
        }
      } catch (error) {
        // 更详细的错误处理
        let errorMessage = '登录失败';
        if (error.response) {
          // 处理HTTP错误状态
          switch (error.response.status) {
            case 400:
              errorMessage = '请求参数错误';
              break;
            case 401:
              errorMessage = '用户名或密码错误';
              break;
            case 500:
              errorMessage = '服务器内部错误';
              break;
            default:
              errorMessage = `请求错误: ${error.response.status}`;
          }
          
          // 如果有后端返回的错误信息
          if (error.response.data && error.response.data.msg) {
            errorMessage = error.response.data.msg;
          }
        } else if (error.request) {
          errorMessage = '网络请求失败，请检查网络连接';
        } else {
          errorMessage = error.message;
        }
        ElMessage.error(errorMessage);// 显示错误信息给用户
      } finally {
        loading.value = false;
      }
    };

    return {
      loginForm,
      loading,
      handleLogin
    };
  }
};
</script>

<style scoped>

/* 登录总页面 */
.login-container {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  /* 渐变背景提升质感 */
  background: linear-gradient(120deg, #fdfbfb 0%, #ebedee 100%); 
  display: flex;
  justify-content: center;
  align-items: center;
  font-family: 'Microsoft YaHei', sans-serif; /* 替换为更适配中文的字体 */
}

/* 登录卡片优化 */
.login-box {
  background-color: #fff;
  padding: 40px 30px; /* 增大内边距，避免内容拥挤 */
  border-radius: 12px; 
  box-shadow: 0 8px 25px rgba(19, 19, 19, 0.12); /* 更柔和的阴影 */
  width: 450px; /* 适度加宽 */
  height: 450px;
  position: relative;
  overflow: hidden;
}

/* 大标题栏*/
.title {
  background-color: #77455f;
  padding: 12px 20px; /* 增加内边距 */
  border-radius: 8px 8px 0 0; /* 与卡片圆角呼应 */
  margin: -40px -30px 20px; /* 负边距拉伸满容器 */
}
.title h1 {
  color: #fff;
  font-size: 18px;
  font-weight: 600;
  margin: 10px 0px;
}

/* “登录”标题 */
.login-title {
  text-align: center;
  font-size: 28px;
  font-weight: 700;
  color: #333;
  margin-bottom: 30px;
  position: relative;
}
/* 标题下方装饰线 */
.login-title::after {
  content: '';
  position: absolute;
  bottom: -10px;
  left: 50%;
  transform: translateX(-50%);
  width: 60px;
  height: 3px;
  background-color: #472f3e;
  border-radius: 3px;
}

/* 输入框 */
.login-container input[type="text"],
.login-container input[type="password"] {
  width: 100%;
  padding: 10px 12px; /* 增大点击区域 */
  margin: 12px 0px; 
  border: 1px solid #ddd;
  border-radius: 6px; 
  font-size: 18px;
  box-sizing: border-box;
  transition: all 0.3s ease; /* 增加过渡动画 */
}

/* 输入框聚焦时样式变化 */
.login-container input[type="text"]:focus,
.login-container input[type="password"]:focus {
  border-color: #472f3e;
  box-shadow: 0 0 8px rgba(76, 175, 80, 0.15);
  outline: none;
}

/* 按钮优化 */
.login-container button {
  width: 100%;
  padding: 18px;
  margin: 20px 0px; /* 自动水平居中 */
  background-color: #81536f;  /*linear-gradient(90deg, #623852 0%, #452438 100%);  按钮渐变 */
  color: white;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  font-size: 18px;
  font-weight: 600;
  transition: transform 0.3s ease, box-shadow 0.3s ease;
  position: relative; /* 使按钮可以有更好的定位 */
  overflow: hidden;
}
/* 按钮 hover 反馈 */
.login-container button:hover {
  transform: translateY(-1px);
  box-shadow: 0 6px 18px rgba(76, 175, 80, 0.2);
}
/* 加载状态时的禁用样式 */
.login-container button:disabled {
  background: #ccc;
  cursor: not-allowed;
  transform: none;
  box-shadow: none;
}

/* 注册链接优化 - 更柔和的交互 */
.register-link {
  margin-top: 20px;
  text-align: center;
  font-size: 14px;
  color: #666;
}
.register-link a {
  color: #472f3e;
  text-decoration: none;
  font-weight: 500;
  transition: color 0.3s ease;
}
.register-link a:hover {
  color: #472f3e;
  text-decoration: underline;
}
</style>