<template>
  <div class="register-container">
    <div class="register-box">
      <div class="title">
        <h1>教师视频行为分析系统</h1>
      </div>
      <h2 class="register-title">注册</h2>
      <form @submit.prevent="handleRegister">
        <input
          type="text"
          v-model="registerForm.username"
          placeholder="用户名"
          required
        />
        <input
          type="password"
          v-model="registerForm.password"
          placeholder="密码"
          required
        />
        <button type="submit" :disabled="loading">
          {{ loading ? '注册中...' : '注册' }}
        </button>
      </form>
      <div class="login-link">
        <p>已有账号？<router-link to="/">登录</router-link></p>
      </div>
    </div>
  </div>
</template>

<script>
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import axios from 'axios';
import { ElMessage } from 'element-plus';
import { useUserStore } from '../store/user';

export default {
  name: 'Register',
  setup() {
    const router = useRouter();
    const userStore = useUserStore();
    const registerForm = ref({
      username: '',
      password: '',
      role:'user' // 默认角色为普通用户
    });
    const loading = ref(false);

    const handleRegister = async () => {
      // 仅保留最基本的非空验证
      if (!registerForm.value.username.trim() || !registerForm.value.password.trim()) {
        ElMessage.error('用户名和密码不能为空');
        return;
      }

      loading.value = true;
      
      try {
        // 使用axios直接发送请求 http://localhost:8080/register
        const response = await axios.post('api/register', {
          username: registerForm.value.username,
          password: registerForm.value.password,
          role: registerForm.value.role // 传递角色信息
        }, {
          headers: {
            'Content-Type': 'application/json'
          }
        });
        console.log("注册返回信息", response);
        // 处理后端响应
        if (response.data && response.data.code === 1) { // 假设成功code为1

          //更新用户状态
          userStore.setUserInfo(response.data.data);
          userStore.token= 1; // 记载注册成功
          ElMessage.success('注册成功');//response.data.msg是后端返回的信息
          router.push('/analysis'); // 注册成功后跳转
        } else {
          ElMessage.error('注册失败');
        }
      } catch (error) {
        // 详细的错误处理
        let errorMessage = '注册失败';
        
        if (error.response) {
          // 如果有后端返回的错误信息
          errorMessage = error.response.data.message;
         
        } else if (error.request) {
          errorMessage = '网络请求失败，请检查网络连接';
        } else {
          errorMessage = error.message;
        }
        
        ElMessage.error(errorMessage);
      } finally {
        loading.value = false;
      }
    };

    return {
      registerForm,
      loading,
      handleRegister
    };
  }
};
</script>

<style scoped>
/* 注册的总界面 */
.register-container {
  position: absolute; /*基于父元素定位*/
  top: 0;
  left: 0; /* 拉伸到全屏 */
  width: 100%;
  height: 100%;
  background: linear-gradient(120deg, #fdfbfb 0%, #ebedee 100%); 
  display: flex;
  justify-content: center;
  align-items: center;
  font-family: 'Microsoft YaHei', sans-serif; 
}
/* 注册的卡片界面 */
.register-box {
  background-color: #fff;
  padding: 40px 30px; /* 增大内边距，避免内容拥挤 */
  border-radius: 12px; 
  box-shadow: 0 8px 25px rgba(0, 0, 0, 0.12); /* 更柔和的阴影 */
  width: 450px; /* 适度加宽 */
  height: 450px;
  position: relative;
  overflow: hidden;
}
/* 系统标题栏  */
.title {
  background-color: #77455f;
  padding: 12px 20px; /* 增加内边距 */
  border-radius: 8px 8px 0 0; /* 与卡片圆角呼应 */
  margin: -40px -30px 20px; /* 负边距拉伸满容器 */
}
/* 大标题样式 */
.title h1 {
  color: #fff;
  font-size: 18px;
  font-weight: 600;
  margin: 10px 0px;
}

/* “注册”标题样式 */
.register-title {
  text-align: center;
  font-size: 28px;
  font-weight: 700;
  color: #333;
  margin-bottom: 30px;
  position: relative;
}

/* “注册”标题下方增加装饰线 */
.register-title::after {
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
/* 输入框样式优化 */
.register-container input[type="text"],
.register-container input[type="password"] {
  width: 100%;/*输入框的长度*/
  padding: 10px 12px; /* 内边距*/
  margin: 12px 0; /* 外边距*/
  border: 1px solid #ddd;
  border-radius: 6px; /* 圆角半径 */
  font-size: 18px;
  box-sizing: border-box; /*使用 border-box 模型，确保输入框的宽度包含内边距和边框*/
  transition: all 0.3s ease; /* 增加过渡动画 */
}
/* 输入框的聚焦效果 */
.register-container input[type="text"]:focus,
.register-container input[type="password"]:focus {
  border-color: #472f3ea9; /* 聚焦时边框颜色 */
  box-shadow: 0 0 8px rgba(76, 175, 80, 0.15);/*外阴影效果*/
  outline: none;/*移除了默认的浏览器聚焦样式 */
}
/* 按钮优化 */
.register-container button {
  width: 100%;
  padding: 18px;
  margin: 20px 0px; /* 自动水平居中 */
  background: #81536f;
  color: white;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  font-size: 18px;
  font-weight: 600;
  transition: transform 0.3s ease, box-shadow 0.3s ease;
  position: relative;
  overflow: hidden;
}
/* 按钮聚焦时的样式变化 */
.register-container button:hover {
  transform: translateY(-1px);
  box-shadow: 0 6px 18px rgba(76, 175, 80, 0.2);
}
/* 按钮点击时的样式变化 */
.register-container button:disabled {
  background: #ccc;
  cursor: not-allowed;
  transform: none;
  box-shadow: none;
}

.login-link{
  margin-top: 20px;
  text-align: center;
  font-size: 14px;
  color: #666;
}

.login-link a {
  color: #472f3e;
  text-decoration: none;
  font-weight: 500;
  transition: color 0.3s ease;
}

.login-link a:hover {
  color: #472f3e;
  text-decoration: underline;
}
</style>