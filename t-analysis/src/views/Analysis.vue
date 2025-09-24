<template>
  <div class="analysis-container">
    <el-card class="combined-card">  <!--卡片组件-->
      <el-row :gutter="24">
        <!-- 视频播放区域 -->
        <el-col :span="12">
          <div class="video-section">
            <div class="section-header">
              <span class="section-title">视频播放</span>
              <div class="section-actions">
                <el-tag type="success" size="small" v-if="videoUrl">已加载视频</el-tag>
                <el-tag type="danger" size="small" v-else>未加载视频</el-tag>
              </div>
            </div>
            <div class="video-wrapper" v-if="videoUrl">
              <!-- 注意：file:// 协议可能无法在浏览器直接播放，仅本地调试可用 -->
              <video controls width="100%" class="video-element"> <!-- controls属性显示播放控件 -->
                <source :src="videoUrl" type="video/mp4">  <!--:src是动态绑定语法-->
                您的浏览器不支持 HTML5 视频标签。
              </video>
              <div class="video-overlay">
                <el-button 
                  type="primary" 
                  size="small" 
                  circle
                  @click="toggleFullscreen"
                  class="fullscreen-btn"
                >
                  <el-icon><FullScreen /></el-icon>
                </el-button>
              </div>
            </div>
            <div class="video-placeholder" v-else>
              <!-- :show-file-list="false" :上传组件不会显示已上传文件的列表 -->
              <el-upload
                class="upload-container"
                :action="uploadUrl"
                accept="video/mp4"
                :data="{userId:userId}"
                :on-success="handleUploadSuccess"
                :on-error="handleUploadError"
                :show-file-list="false" 
                name="file"
                :before-upload="handleBeforeUpload"
               ><!--进度条：:on-progress="handleUploadProgress" -->
                <el-button type="primary" :loading="isUploading" class="upload-btn">
                  {{ isUploading ? '上传中...' : '点击上传视频' }}
                </el-button>
              </el-upload>
              <p class="placeholder-text">请上传视频进行分析</p>
            </div>
          </div>
        </el-col>

        <!-- 分析设置区域 -->
        <el-col :span="12">
          <div class="settings-section">
            <div class="section-header">
              <span class="section-title">分析设置</span>
              <el-tag type="warning" size="small">Beta</el-tag>
            </div>
            <el-form label-width="120px" class="settings-form">
              <el-form-item label="视频名称">
                <el-input 
                  v-model="videoName" 
                  placeholder="请输入视频名"
                  class="custom-input"
                >
                  <template #append>
                    <!-- 确保 Refresh 已导入，用作图标 -->
                    <el-button :icon="Refresh" @click="generateNewId " /> 
                  </template>
                </el-input>
              </el-form-item>
              
              <el-form-item label="视频信息">
                <el-input 
                  v-model="videoInfo" 
                  disabled
                  class="custom-input"
                >
                  <template #prepend>
                    <el-icon><VideoCamera /></el-icon>
                  </template>
                </el-input>
              </el-form-item>
              
              <el-form-item label="播放地址">
                <el-input 
                  v-model="videoUrl" 
                  disabled
                  class="custom-input"
                >
                  <template #prepend>
                    <el-icon><Link /></el-icon>
                  </template>
                </el-input>
              </el-form-item>


              <el-form-item><!--:loading:事件处理-->
                <el-button 
                  type="primary" 
                  class="submit-btn"
                  @click="startAnalysis"
                  :loading="isAnalyzing" 
                  :disabled="isAnalyzing"
                >
                  <template #icon>
                    <el-icon><MagicStick /></el-icon>
                  </template>
                  {{isAnalyzing ? '分析中...' : '开始分析'}}
                </el-button>
              </el-form-item>
            </el-form>
          </div>
        </el-col>
      </el-row>
    </el-card>
  </div>
</template>

<script setup>
import { ref } from 'vue';
import { ElMessage } from 'element-plus';
import axios from 'axios'; 
import { useRouter } from 'vue-router';
import { useUserStore } from '@/store/user';
import { useAnalysisStore } from '@/store/analysisStore';
// 提供用户ID给子组件
const userId=useUserStore().userid;
//存储分析结果
const router = useRouter()
const analysisStore = useAnalysisStore()
// 动态配置上传地址（通过环境变量或默认值）
const uploadUrl = import.meta.env.VITE_UPLOAD_API || '/api/video/upload';
const videoUrl = ref('');
const videoName = ref('');
const videoInfo = ref(''); // 视频信息显示
const isAnalyzing = ref(false); //分析状态
const isUploading = ref(false);//上传视频状态
const taskId = ref(''); // 存储任务ID用于状态查询
const progressInterval = ref(null); // 存储轮询的interval
// 分析进度相关变量
//const progressPercentage = ref(0);//分析进度条的值
//const progressStatus = ref('');//分析进度条的状态
// 生成新分析ID
const generateNewId = () => {
  videoName.value = 'ctrl_' + Math.random().toString(36).substring(2, 10);
  ElMessage.success('已生成新分析ID');
};

// 开始分析（需对接真实API）
const startAnalysis = async () => {
  console.log('用户信息',useUserStore().code);
  console.log(useUserStore().username)
  if (!videoUrl.value) {
    ElMessage.error('请先上传视频');
    return;
  }
  if (!videoName.value.trim()) {//.trim()去除前后空格
    ElMessage.error('请输入视频名');
    return;
  }
  isAnalyzing.value = true;
  try {
    // 实际调用API示例（需根据后端接口调整）
    console.log('开始提交分析任务');
    const res = await axios.post('/api/video/process', { videoUrl: videoUrl.value ,videoName:videoName.value});
    console.log('后端的分析任务响应:', res);
    if (!res.data || !res.data.taskId) {
      throw new Error('API返回数据格式不正确，缺少taskId');
    }
    taskId.value = res.data.taskId;

    ElMessage.success(`分析任务已启动，任务ID: ${taskId.value}`);
    // 开始轮询任务状态
    startStatusPolling(taskId.value);
  }catch (err) {
    console.error('分析任务提交失败:', err);
    ElMessage.error(`分析任务提交失败:${err.message}`);
    isAnalyzing.value = false; // 出错时重置状态
  }
};

// 新增：定义emits
const emit = defineEmits(['analysis-complete']);
// 全屏切换
const toggleFullscreen = () => {
  const videoElement = document.querySelector('.video-element');
  if (videoElement?.requestFullscreen) {
    videoElement.requestFullscreen();
  }
};

  
// 轮询任务状态,3s一次
const startStatusPolling = async (taskId, delay = 5000) => {
  // 先清除已有轮询
  if (progressInterval.value) {
    clearInterval(progressInterval.value);
  }
  try {
    const status = await axios.get(`/api/video/status/${taskId}`); ; // 发起轮询请求
    console.log('状态更新:', status);
    // 检查状态是否是预期值
    if (!status || !status.status) {
      throw new Error('无效的状态响应');
    }
    // // 更新UI
    // updateProgress(status);
    // 处理不同状态
    switch(status.data.status) {
      case 'uploaded':
      case 'queued':
        // 这些状态表示任务还在等待处理
        setTimeout(() => startStatusPolling(taskId, delay), delay);
        break;
      case 'processing':
        // 处理中状态
        setTimeout(() => startStatusPolling(taskId, delay), delay);
        break;
      case 'completed':
        // 完成状态
        isAnalyzing.value = false;
        ElMessage.success('分析完成!');
        // 存储分析结果到Pinia
        analysisStore.setAnalysisResult({
          resultVideoUrl: status.data.resultVideoUrl,
          taskId: status.data.taskId
        });
        console.log('分析结果已存储到Pinia:', analysisStore.value);
        // 跳转到Dashboard页面
        router.push('/dashboard');
        break;
      case 'failed':
        // 失败状态
        isAnalyzing.value = false;
        ElMessage.error(` ${'分析失败，请稍后重试'}`);
        break;
      default:
        // 未知状态
        setTimeout(() => startStatusPolling(taskId, delay), delay);
    }
  } catch (error) {
    console.error('轮询失败:', error);
    isAnalyzing.value = false; // 出错时重置状态
    // 失败后延迟重试（可加指数退避）若失败，则以1.5倍的延迟重试
    // setTimeout(() => startStatusPolling(taskId, delay * 1.5), delay);
  }
};




// 新增上传前处理函数
const handleBeforeUpload = () => {
  isUploading.value = true;
  return true; // 必须返回true才会继续上传
};
// 上传成功回调
const handleUploadSuccess = (response) => {
  isUploading.value = false; // 上传完成，重置状态
  if (!response || !response.videoUrl) {
    ElMessage.error('上传成功，但返回数据异常');
    return;
  }
  videoUrl.value = response.videoUrl;
  videoInfo.value = `文件名：${response.tempFileName}`;
  ElMessage.success(response.message || '视频上传成功');
  generateNewId();// 自动生成新的分析ID
};


// 上传失败回调
const handleUploadError = (err) => {
  isUploading.value = false; // 上传完成，重置状态
  console.error('上传失败详情:', err);
  if (err?.response?.status === 404) {
    ElMessage.error('上传接口不存在，请联系管理员');
  } else if (err.message.includes('Network Error')) {
    ElMessage.error('网络错误，请检查连接');
  } else {
    ElMessage.error(`上传失败: ${err.message}`);
  }
};
</script>

<style scoped lang="scss">
/* 原有样式保持不变 */
.combined-card {
  width: 100%;
  height:650px;
  border-radius: 8px; //圆角
  padding: 5px;
}

//小标题样式
.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 0;//上下内边距为 12 像素，左右内边距为 0
  margin-bottom: 16px;//容器的下外边距
  border-bottom: 1.5px solid #563148b9;
 }
.video-section {
  padding: 20px;
  height: 400px;
}
.video-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 450px;
  background-color: #f1f5f9;
  border-radius: 8px;
  color: #8b6472;
  .placeholder-text {
    font-size: 14px;
    margin-top: 16px;
  }
}
.upload-container {
  margin-top: 16px;
}
.upload-btn {
  color: #fff;
  background-color: #5e3c51;
  border:none;
  &:hover {
    background-color: #8b6278;
  }
}
//分析设置区域
.settings-section {
  padding: 20px;
  .settings-form {
    margin-top: 16px;
    /* 在全局样式文件*/
    .el-input{
      width: 500px;
      height: 40px;
    }
    .el-form-item{
      margin-bottom: 30px; // 设置表单项之间的间距
    }
    //表单标签样式
    .el-form-item__label {
      font-size: 100px;      /* 字体大小 */
    }
    // 使用深度选择器穿透Element Plus的样式
    :deep(.el-form-item:last-child) {
      display: flex;
      justify-content: center;
      
      .el-form-item__content {
        margin-left: 0 !important;
        justify-content: center;
      }
    }
  }
}

.custom-input {
  width: 100%;
}
.video-wrapper {
  position: relative;
  border-radius: 8px;
  overflow: hidden;
}
.video-overlay {
  position: absolute;
  top: 12px;
  right: 12px;
}
.fullscreen-btn {
  background-color: rgba(0,0,0,0.5);
  border: none;
  &:hover {
    background-color: rgba(0,0,0,0.7);
  }
}
.submit-btn {
  width: 180px; 
  height: 40px;
  padding: 12px 24px;
  background-color: #5e3c51;
  border: none;
  font-size: 16px;
  transition: all 0.3s ease; // 添加过渡效果
  &:hover {
    background-color: #8b6278;
  }
  &.is-disabled {
    background-color: #cccccc;
    cursor: not-allowed;
  }
  &.is-analyzing {
    background-color: #8b6278;
  }
}
// 添加进度条样式
.progress-container {
  margin-top: 20px;
  .progress-info {
    display: flex;
    justify-content: space-between;
    margin-bottom: 8px;
    font-size: 14px;
    color: #666;
  }
}

</style>