<template>
  <div class="history-container">
    <el-row :gutter="20">
      <el-col :span="24">
        <el-card class="history-card">
          <template #header>
            <div class="card-header" >
              <span class="header-title">我的历史记录</span>
            </div>
          </template>
          <div class="table-wrapper">
            <!-- tasks:绑定数据源 -->
            <el-table 
              :data="tasks" 
              border 
              style="width: 100%" 
              v-loading="loading"
              :header-cell-style="{
                background: '#f5f7fa',
                color: '#606266',
                fontWeight: '600'
              }"
              :cell-style="{padding: '12px 0'}"
              stripe
            > <!-- scope.row：当前行的数据对象   prop：绑定数据源的属性 -->
              <el-table-column prop="videoName" label="视频名称">
                <template #default="scope">
                  <div class="video-name-cell">
                    <el-icon><VideoPlay /></el-icon><!--标签-->
                    <span>{{ scope.row.videoName || '未命名视频' }}</span>
                  </div>
                </template>
              </el-table-column>
             
              <el-table-column prop="createdAt" label="创建时间" width="200">
                <template #default="scope">
                  {{ formatDate(scope.row.createdAt) }}
                </template>
              </el-table-column>
                <!-- upload--上传成功，没有分析 -->
              <el-table-column prop="status" label="状态" width="200">
                <template #default="scope">
                  <el-tag :type="getStatusType(scope.row.status)">
                    {{ scope.row.status }}
                  </el-tag>
                </template>
              </el-table-column>

              <el-table-column label="操作" width="120">
                <template #default="scope">
                  <el-button 
                    type="primary" 
                    size="small" 
                    class="check-btn"
                    @click="viewAnalysisResult(scope.row.taskId,scope.row.processedVideoUrl)"
                    plain
                    :disabled="scope.row.status !== 'completed'"
                  >
                    查看结果
                  </el-button>
                </template>
              </el-table-column>
              
              <template #empty>
                <div class="empty-table">
                  <el-empty description="暂无历史记录" />
                </div>
              </template>
            </el-table>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { VideoPlay } from '@element-plus/icons-vue';
import axios from 'axios';
import { useAnalysisStore } from '@/store/analysisStore';
import { useUserStore } from '@/store/user';

const router = useRouter();
const analysisStore = useAnalysisStore();
const tasks = ref([]);
const loading = ref(false);

// 格式化日期
const formatDate = (dateString) => {
  if (!dateString) return "无日期";
    try {
      const date = new Date(dateString);
      if (isNaN(date.getTime())) return dateString; // 如果解析失败，返回原始字符串
      const year = date.getFullYear();
      const month = String(date.getMonth() + 1).padStart(2, "0");
      const day = String(date.getDate()).padStart(2, "0");
      const hour = String(date.getHours()).padStart(2, "0");
      const minute = String(date.getMinutes()).padStart(2, "0");
      
      return `${year}-${month}-${day} ${hour}:${minute}`;
    } catch (e) {
      console.error("日期格式化失败:", e);
      return dateString; // 异常时返回原始字符串
    }
};

// 状态标签类型
const getStatusType = (status) => {
  const typeMap = {
    'completed': 'success',
    'uploaded': 'warning',
    'failed': 'danger',
    'analyzing': 'info',
    'pending': 'primary'
  };
  return typeMap[status] || 'info';
};
// 获取用户历史记录
const fetchUserHistory = async () => {
  loading.value = true;
  try {
    const userId = useUserStore().userid; // 获取当前用户ID
    console.log('当前用户ID:', userId);
    const response = await axios.get(`/api/user/${userId}`);
    console.log('后端的用户历史记录:', response);
    if (response.data) {
      tasks.value = response.data || [];
    } else {
      throw new Error('获取历史记录失败');
    }
  } catch (err) {
    ElMessage.error(`加载失败: ${err.message}`);
  } finally {
    loading.value = false;
  }
};

// 查看分析结果
const viewAnalysisResult = async (taskId,resultVideoUrl) => {
  try {
    analysisStore.setAnalysisResult({
      taskId: taskId,
      resultVideoUrl:resultVideoUrl,
    });
    router.push('/dashboard');
  } catch (err) {
    ElMessage.error(`加载分析结果失败: ${err.message}`);
  }
};

onMounted(() => {
  fetchUserHistory();
});
</script>

<style scoped>

/* 大框架 */
.history-container {
  padding: 3px;
}

.history-card {
  border-radius: 8px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
}
/* 我的历史记录大标题的框 */
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 5px 5px;
}
/* 我的历史记录大标题 */
.header-title {
  font-size: 20px;
  font-weight: 800;
  color: #472f3e;
}
/* 移除历史标题卡片标题底部边框 */
:deep(.el-card__header) {
  border-bottom: none !important;
  padding-bottom: 0 !important;
}

/* 视频名字的格式 */
.video-name-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}
/* 查看结果的按钮 */
.check-btn {
  background-color: #55304730;  /* 按钮背景色 */
   color: #5a575a;
   border-color: #55304730;
}
/* 按钮悬停背景色为紫色 */
.check-btn:hover {
  background-color: #764f67 !important; /* 紫色 */
  border-color: #764f67 !important;
  color: #fff !important;
}
/* 禁用状态的按钮的样式 */
.check-btn.is-disabled {
  opacity: 0.6;
  color: #5530475a
}
/* 没有历史记录的格式 */
.empty-table {
  padding: 10px 0;
  height: 500px;
}

/* 表格容器样式，实现滚动 */
.table-wrapper {
  max-height: 600px; /* 可根据实际需求调整高度 */
  overflow-y: auto;
}

/* 美化滚动条 */
.table-wrapper::-webkit-scrollbar {
  width: 6px;
}

.table-wrapper::-webkit-scrollbar-thumb {
  background-color: #c1c1c1;
  border-radius: 3px;
}

.table-wrapper::-webkit-scrollbar-track {
  background-color: #f1f1f1;
}

:deep(.el-table__header) th {
  font-weight: 600;
  position: sticky;
  top: 0;
  z-index: 1;
}

:deep(.el-table__body) tr:hover {
  background-color: #faf5f7 !important;
}
</style>