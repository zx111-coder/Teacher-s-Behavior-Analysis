<template>
  <div class="dashboard-container">
    <el-card class="result-card">
      <template #header>
        <div class="card-header">
          <h3>课堂行为分析结果</h3>
          <el-button 
            type="primary"  
            size="small"
            class="new-analysis-btn"
            @click="handleNewAnalysis"
          >
            新建分析
          </el-button>
        </div>
      </template>
      <div class="analysis-layout" v-if="analysisResult">
        <!-- 左侧视频区域 -->
        <div class="video-section">
          <h4>分析结果视频</h4>
          <video 
            controls 
            class="result-video"
            :key="analysisResult.resultVideoUrl"
            v-if="analysisResult.resultVideoUrl"
          >
            <source :src="analysisResult.resultVideoUrl" type="video/mp4">
            您的浏览器不支持 HTML5 视频标签。
          </video>
          <div v-else class="no-video">
            <el-empty description="无结果视频" />
          </div> 
        </div>
        <!-- 右侧图表区域 -->
        <div class="chart-section">
          <el-tabs type="border-card">
            <el-tab-pane label="行为分析条形图">
              <div ref="behaviorChartRef" style="width: 100%; height: 400px;"></div>
            </el-tab-pane>
            <el-tab-pane label="行为分析饼状图">
              <div ref="timelineChartRef" style="width: 100%; height: 400px;"></div>
            </el-tab-pane>
            <el-tab-pane label="表情分析雷达图">
              <div ref="emotionChartRef" style="width: 100%; height: 400px;"></div>
            </el-tab-pane>
          </el-tabs>
        </div>
      </div>
      <div v-else class="no-result">
        <el-empty description="暂无分析结果" />
        <el-button 
          type="primary" 
          @click="router.push('/analysis')"
          class="go-analysis-btn"
        >
          前往分析页面
        </el-button>
      </div>
     <!-- 教师评分和反馈区域 -->
     <div class="feedback-section" v-if="analysisResult && teachingScore">
        <h3 class="feedback-title">教学行为评估</h3>
        <!-- 综合评分卡片 -->
        <el-card class="overall-score-card" shadow="hover">
          <div class="overall-score-content">
            <!-- 评分圆环 + 等级 -->
            <div class="score-circle" :style="{'--score-color': getScoreColor(teachingScore.teaching_score)}">
              <div class="score-value">{{ teachingScore.teaching_score }}</div> <!-- 显示综合评分数值 -->
              <div class="score-label">综合评分</div> <!-- 显示评分标签的文案 -->
              <div class="score-level">{{ getScoreLevel(teachingScore.teaching_score) }}</div>
            </div>
            <div class="score-details"> <!--具体指标的得分-->
              <div class="detail-item" v-for="(value, key) in teachingScore.score_explanation.score_breakdown" :key="key">
                <span class="detail-label">{{ translateScoreKey(key) }}:</span>
                <span class="detail-value">{{ value }}</span>
                <!-- 根据得分渲染进度条：percentage-得分 show-text-控制是否在进度条内显示百分比文字-->
                <el-progress 
                  :percentage="getPercentage(value, key)" 
                  :color="getScoreColor(getPercentage(value,key))"
                  :show-text="true"
                  class="detail-progress"
                />
              </div>
            </div>
          </div>
        </el-card>

        <!-- 评分详情 -->
        <el-card class="comments-card" shadow="hover">
          <el-collapse v-model="activeCollapse">
            <el-collapse-item title="评分详情" name="scoreDetails">
              <ul class="comments-list">
                <li v-for="(comment, index) in teachingScore.score_explanation.comments" :key="index">
                  <i class="el-icon-check" style="color: #67C23A; margin-right: 8px;"></i>
                  {{ comment }}
                </li>
              </ul>
            </el-collapse-item>
            <el-collapse-item title="教学建议" name="suggestions" >
              <ul class="suggestions-list">
                <li v-for="(suggestion, index) in teachingScore.score_explanation.suggestions" :key="index">
                  <i class="el-icon-warning-outline" style="color: #E6A23C; margin-right: 8px;"></i>
                  {{ suggestion }}
                </li>
              </ul>
            </el-collapse-item>
            <el-collapse-item title="评分标准" name="gradingStandard">
              <div class="grading-standard">
                {{ teachingScore.score_explanation.grading_standard }}
              </div>
            </el-collapse-item>
          </el-collapse>
        </el-card>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { debounce } from 'lodash'; // 或自行实现防抖
import { useRouter } from 'vue-router'
import { useAnalysisStore } from '@/store/analysisStore'
import axios from 'axios'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus';  
const router = useRouter()
const analysisStore = useAnalysisStore()
const emotionChartRef = ref(null) // 新增情感图表引用
const behaviorChartRef = ref(null) //行为频率图表容器
const timelineChartRef = ref(null) //行为时间线图表容器
const isLoading = ref(false)

const analysisResult = computed(() => analysisStore.result)
const action=ref({})//json文件中的行为个数数据
const emotion=ref({})//json文件中的表情个数数据
//忽略警告
const originalWarn = console.warn;
console.warn = function (message, ...optionalParams) {
  // 过滤 ECharts 刻度警告，根据实际警告文本调整判断条件
  if (typeof message === 'string' && message.includes('[ECharts] The ticks may be not readable when set min: 0, max: 31.2 and alignTicks: true')) {
    return; // 忽略该警告
  }
  originalWarn.call(console, message, ...optionalParams); // 正常打印其他警告
};

// 计算百分比的函数
const getPercentage = (score, dimensionKey) => {
  // 定义每个维度的满分值
  const maxScores = {
    'explanation': 20,
    'writing': 20,
    'interaction': 25,
    'gestures': 27,    // 基础25分 + 最多2分额外加分
    'posture': 5,
    'movement': 5
  };
  // 根据维度获取满分值，计算百分比（保留整数）
  const maxScore = maxScores[dimensionKey] || 100; // 默认100分
  return parseFloat(((score / maxScore) * 100).toFixed(2));
};

// 教学评分数据
const teachingScore = ref({
  teaching_score: 0,
  score_explanation: {
    score_breakdown: {},//各项具体评分指标
    comments: [], //对教学行为的评价
    suggestions: [],//针对教学行为的改进建议列表 
    grading_standard: {} //评分标准
  }
})
const behaviorChart = ref(null)
const timelineChart = ref(null)
const emotionChart = ref(null)
// 全局防抖函数（resize）
const debouncedResize = debounce(() => {
  behaviorChart.value?.resize();
  timelineChart.value?.resize();
  emotionChart.value?.resize();
}, 200);
// 防抖处理图表初始化
const debouncedInitCharts = debounce(() => {
  initBehaviorChart();
  initTimelineChart();
  initEmotionChart(emotion.value);
}, 300);


// 处理分析数据
const processAnalysisData = (data) => {
  if (!data) return
  teachingScore.value = {
    teaching_score: data.scoreAnalysis.totalScore || 0,
    score_explanation: {
      score_breakdown: {//各项具体评分指标
        explanation: data.scoreAnalysis.explanationScore || 0,
        writing: data.scoreAnalysis.writingScore || 0,
        interaction: data.scoreAnalysis.interactionScore || 0,
        gestures: data.scoreAnalysis.gesturesScore || 0,
        posture: data.scoreAnalysis.postureScore || 0,
        movement: parseFloat((data.scoreAnalysis.movementScore || 0).toFixed(2)) // 确保移动频率是小数点后两位的字符串
      },
      comments: data.scoreExplanation.comment.split(';') || [], // 教学评价
      suggestions: data.scoreExplanation.suggestion.split(';') || [], // 教学建议
      grading_standard: data.scoreExplanation.gradingStandard || {} // 评分标准
    }
  }
  action.value = data.actionAnalysis || {} // 行为分析结果
  emotion.value = data.emotionAnalysis  || {} // 表情分析结果

  // 初始化图表
  nextTick(() => { //nextTick--确保DOM已经更新
    debouncedInitCharts(); // 防抖后初始化图表
  })
}

// 教学评分数据
// 评分项翻译
const translateScoreKey = (key) => {
  const translations = {
    'explanation': '讲解能力',
    'writing': '板书能力',
    'interaction': '师生互动',
    'gestures': '肢体语言',
    'posture': '教姿教态',
    'movement': '移动频率'
  }
  return translations[key] || key
}


// 根据分数获取颜色
const getScoreColor = (score) => { // 获取百分比
  if (score >= 90) return '#67C23A' // 优秀 - 绿色
  if (score >= 75) return '#409EFF' // 良好 - 蓝色
  if (score >= 60) return '#E6A23C' // 及格 - 黄色
  return '#F56C6C' // 不及格 - 红色
}
// 获取评分等级
const getScoreLevel = (score) => {
  if (score >= 90) return '优秀'
  if (score >= 75) return '良好'
  if (score >= 60) return '及格'
  return '待提升'
}

// 行为频率图表
const initBehaviorChart = () => {
  if (!behaviorChartRef.value|| !Object.keys(action.value).length) return
  // 销毁之前的图表实例
  if (behaviorChart.value) {
    behaviorChart.value.dispose()
    behaviorChart.value = null; // 重置引用111
  }
  nextTick(() => {
    behaviorChart.value = echarts.init(behaviorChartRef.value) //初始化一个 ECharts 实例
    // 过滤掉不需要展示的字段
    const filteredResults = Object.entries(action.value)
      .filter(([key]) => !['hand_images', 'pose_images','id','videoId'].includes(key))//排除不需要展示的字段
      .filter(([, value]) => value > 0) // 只显示有数据的项目
      .sort((a, b) => b[1] - a[1]) // 按值降序排序
    const xDataValues = filteredResults.map(([, value]) => value);
    const xMin = xDataValues.length > 0 ? Math.min(...xDataValues) : 0;
    const xMax = xDataValues.length > 0 ? Math.max(...xDataValues) : 31.2;
    // 行为频率图表
    const options = {
      tooltip: {
        trigger: 'axis', // 鼠标悬停时触发
        axisPointer: { //阴影指示器
          type: 'shadow'
        },
        formatter: '{b}: {c}次' // 提示的格式
      },
      // 图表布局
      grid: {
        left: '3%', //左边距
        right: '12%',
        top: '10%',
        bottom: '5%',
        containLabel: true // 强制将坐标轴的标签纳入绘图区域的计算范围内
      },
      xAxis: {
        type: 'value', //数值轴
        name: '出现次数', // x轴名称
        alignTicks: false, // 关闭对齐
        min: xMin,         // 动态设置最小值
        max: xMax,         // 动态设置最大值
      },
      yAxis: {
        type: 'category',// 类目轴
        data: filteredResults.map(([key]) => translateBehavior(key)),// 得出行为名称
        axisLabel: {
          interval: 0,// 强制显示所有标签
          rotate: 30,// 标签旋转 30 度（避免重叠）
        },
        name: '行为类型', // y轴名称
        alignTicks: false
      },
      series: [{  //柱状图的设置
        name: '行为频率',
        type: 'bar',//柱状图
        barMinWidth: 10, // 设置最小宽度
        barMaxWidth: 30, // 设置最大宽度
        data: filteredResults.map(([, value]) => value),//得到次数
        itemStyle: {
          color: function(params) {
            // 动态分配渐变色
            const colorList = [
              '#5e3c51', '#8b6278', '#c995a6', '#e6c7d5',
              '#a78ba8', '#7d5e7f', '#4e3c50', '#2e1f2f'
            ]
            return colorList[params.dataIndex % colorList.length]
          }
        },
        label: {
          show: true,// 显示数值标签
          position: 'right'// 标签位置（柱条右侧）
        }
      }]
    }
    behaviorChart.value.setOption(options)// 将配置应用到图表
    // 在setOption之后添加：
    behaviorChart.value.on('finished', () => {
      behaviorChart.value.resize()
    })
  })
}

// 行为时间线图表
const initTimelineChart = () => {
  if (!timelineChartRef.value|| !Object.keys(action.value).length) return
  // 销毁之前的图表实例
  if (timelineChart.value) {
    timelineChart.value.dispose()
    timelineChart.value = null; // 重置引用
  }
      if (timelineChartRef.value.clientWidth === 0) {
      setTimeout(initTimelineChart, 100)
      return
    }
  // 确保容器有有效尺寸
  nextTick(() => {

    timelineChart.value = echarts.init(timelineChartRef.value)//初始化一个 ECharts 实例
    // 转换为图表数据格式
    const chartData = Object.entries(action.value)//将对象转换为键值对数组
      //过滤无效字段和零值
      .filter(([key]) => !['hand_images', 'pose_images','id','videoId'].includes(key))
      .filter(([, value]) => value > 0)
      .map(([name, value]) => ({ 
        name: translateBehavior(name), 
        value 
      }))
      .sort((a, b) => b.value - a.value) // 按持续时间降序排序

    // 行为时间线图表配置
    const options = {
      tooltip: {
        trigger: 'item',// 悬停在饼图扇区时触发
        formatter: '{a} <br/>{b}: {c} ({d}%)'// 显示格式：系列名 + 行为名 + 次数 + 百分比
      },
      legend: {
        orient: 'vertical',// 垂直排列
        right: '5%',  // 距离右侧 10px
        top: 'middle',  // 垂直居中
        itemWidth: 12,
        itemHeight: 12,
        textStyle: {
          fontSize: 10
        },
        data: chartData.map(item => item.name)// 图例数据（行为名称列表）
      },
      series: [{
        name: '饼状图',
        type: 'pie', // 饼图类型
        radius: ['40%', '70%'],// 内半径 40%，外半径 70%（环形效果）
        center: ['40%', '50%'],// 圆心位置（水平 40%，垂直 50%）
        data: chartData,// 行为名称
        itemStyle: {
          borderRadius: 5,// 扇区圆角
          borderColor: '#fff',// 边框颜色
          borderWidth: 2,// 边框宽度
          color: function(params) {
            // 动态分配颜色
            const colorList = [
              '#5e3c51', '#8b6278', '#c995a6', '#e6c7d5',
              '#a78ba8', '#7d5e7f', '#4e3c50', '#2e1f2f'
            ]
            return colorList[params.dataIndex % colorList.length]
          }
        },
        label: {
          show: true, // 显示标签
          formatter: '{b}: {c}' // 标签格式（行为名: 次数）
        },
        emphasis: {//高亮样式（悬停时）
          focus: 'none',
          scale: false, // 禁用鼠标悬停时放大
        }
      }]
    }
    
    timelineChart.value.setOption(options)// 渲染图表

    // 响应式调整：图表渲染完成后自动调整大小
    timelineChart.value.on('finished', () => {
      timelineChart.value.resize()
    })
  
  })
}

// 表情雷达图
const initEmotionChart = (emotionData) => {
  if (!emotionChartRef.value || !Object.keys(action.value).length) return
  // 销毁之前的图表实例
  if (emotionChart.value) {
    emotionChart.value.dispose();
    emotionChart.value = null;
  }
  // 过滤掉不需要的字段
  const filteredEmotions = Object.entries(emotionData)
    .filter(([key]) => !['id', 'total', 'videoId'].includes(key))
    .filter(([, value]) => value > 0)
  
  if (filteredEmotions.length === 0) return // 如果没有有效的情感数据，则不绘制图表
  // 确保容器有尺寸
  nextTick(() => {
    emotionChart.value = echarts.init(emotionChartRef.value)
    
    // 准备雷达图数据
    const indicator = filteredEmotions.map(([key]) => ({
      name: translateEmotion(key), //转换为中文名称
      max: Math.max(...filteredEmotions.map(([, value]) => value)) * 1.2 // 设置每个坐标轴的最大值
    }))
    
    const seriesData = filteredEmotions.map(([, value]) => value)//提取情感值作为数组传给雷达图
    
    const options = {
      tooltip: { //鼠标悬停时的提示框配置
        trigger: 'item'//悬停在具体的数据点
      },
      grid: {//确保图表区域充分利用空间
          top: 20,
          right: 20,
          bottom: 20,
          left: 20,
          containLabel: true
        },
      radar: {
        indicator: indicator, //使用准备好的雷达数据
        center: ['50%', '50%'], // 确保居中
        startAngle: 90, // 起始角度调整
        shape: 'polygon', // 明确指定多边形
        radius: '75%', //雷达图的半径大小
        splitNumber: 5,//雷达图的分割层数
        axisName: { //坐标轴名称的样式
          color: '#333',
          fontSize: 12,
          padding: [3, 5]
        },
        splitArea: { //雷达图分割区域的样式
          areaStyle: {
            color: ['rgba(132, 89, 115, 0.1)', 
                    'rgba(132, 89, 115, 0.2)', 
                    'rgba(132, 89, 115, 0.4)', 
                    'rgba(132, 89, 115, 0.6)'],
            shadowColor: 'rgba(0, 0, 0, 0.2)', //阴影颜色
            shadowBlur: 10 //阴影模糊半径
          }
        },
        axisLine: { 
          lineStyle: {//坐标轴线的颜色和宽度
            color: 'rgba(132, 89, 115, 0.5)',
            width: 1
          }
        },
        splitLine: { //分割线的样式
          lineStyle: {
            color: 'rgba(132, 89, 115, 0.5)',
            width: 1
          }
        }
        
      },
      series: [{
        name: '情感分布图',
        type: 'radar',// 指定图表类型为雷达图
        data: [{
          value: seriesData, //情感值数组
          name: '表情分布',
          areaStyle: { //区域填充样式，使用半透明紫色
            color: 'rgba(132, 89, 115, 0.4)'
          },
          lineStyle: { //线条样式，使用更深一点的紫色
            color: 'rgba(132, 89, 115, 0.8)'
          },
          itemStyle: { //数据点样式，使用纯色紫色
            color: '#845973'
          },
          label: {
            show: true,//显示标签
            formatter: function(params) { //自定义标签显示内容
              return params.value
            }
          }
        }],
        alignTicks: false
      }]
    }
    emotionChart.value.setOption(options)
    // 响应式调整：图表渲染完成后自动调整大小
    emotionChart.value.on('finished', () => {
      emotionChart.value.resize()
    })
  
  })
}

// 表情翻译
const translateEmotion = (emotion) => {
  const translations = {
    'happy': '开心',
    'sad': '悲伤',
    'angry': '生气',
    'surprise': '惊讶',
    'neutral': '中性',
    'fear': '害怕',
    'disgust': '厌恶'
  }
  return translations[emotion] || emotion
}

// 行为名称翻译
const translateBehavior = (key) => {
  const translations = {
    'handPointDown': '手指向下',
    'headPointFront': '头部向前',
    'headPointSide': '头部侧向',
    'headPointBack': '头部向后',
    'handPointUp': '手指向上',
    'handPointHorizon': '手指水平',
    'handPointFront': '手指向前',
    'lookAtStudent': '看学生',
    'lookAtComputer': '看电脑',
    'writeBlackboard': '写黑板',
    'explanation': '讲解',
    'lookAtBlackboard': '看黑板',
    'lookAtProjector': '看投影',
    'handWave': '挥手',
    'sitTimes': '坐下',
    'standTimes': '站立',
    'walkTimes': '走动',
    'takeTimes': '拿取物品',
    'sitDown': '坐下动作',
    '站': '站立',
    '看学生': '看学生',
    '走动': '走动'
  }
  return translations[key] || key
}

// 加载分析数据
const loadAnalysisData = async () => {
  //判断是否存在有效的taskid
  console.log('当前分析结果:', analysisResult.value);
  if (!analysisResult.value?.taskId) {
    ElMessage.warning('暂无分析任务，请先进行分析！');
    return;
  }
  isLoading.value = true
  try {
    // 调用后端API，传入 taskId 获取分析结果
    const taskId = analysisResult.value.taskId;
    const response = await axios.get(`/api/video/result/${taskId}`); 
    console.log('后端对taskId的分析结果:', response);
    if (response.data) {
      // 处理后端返回的数据
      analysisStore.setAnalysisResult({
        resultVideoUrl: response.data.video.processedVideoUrl,        
        taskId: taskId
      });
      processAnalysisData(response.data);
    } else {
      throw new Error(response.data?.msg || '获取分析结果失败');
    }
  } catch (err) {
    console.error('获取分析结果失败:', err);
    ElMessage.error(`获取分析结果失败: ${err.message}`);
  } finally {
    isLoading.value = false
  }

  // //TEST
  // const response=testData;
  // console.log("json文件：",testData)
  // processAnalysisData(response.data);
}

const handleNewAnalysis = () => {
  analysisStore.clearAnalysisResult()
  router.push('/analysis')
}

// 组件挂载时加载数据
onMounted(() => {
  loadAnalysisData();
  window.addEventListener('resize', debouncedResize); // 全局绑定 resize
})
onUnmounted(() => {
  window.removeEventListener('resize', debouncedResize); // 销毁 resize 监听
  // 逐个销毁实例（关键：避免内存泄漏和重复警告）
  if (behaviorChart.value) {
    behaviorChart.value.dispose();
    behaviorChart.value = null;
  }
  if (timelineChart.value) {
    timelineChart.value.dispose();
    timelineChart.value = null;
  }
  if (emotionChart.value) {
    emotionChart.value.dispose();
    emotionChart.value = null;
  }
});
// 监听analysisResult变化
// watch(analysisResult, (newVal) => {
//   if (newVal) {
//     loadAnalysisData()
//   }
// }, { immediate: true })
</script>

<style scoped lang="scss">
// 总体容器
.dashboard-container {
  padding: 0px;
  //卡片的样式
  .result-card {
    border-radius: 8px;
    box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
    //标题的样式
    .card-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 0 10px;
      // border-bottom: 1px solid #eee;
      //标题的大小
      h3 {
        margin: 0;
        color: #63415a;
        padding: 0px;
      }
      //新建分析按钮的样式
      .new-analysis-btn {
        background-color: #845973;
        border-color: #845973;
      }
    }
    //主要展示内容框的样式
    .analysis-layout {
      display: flex;
      gap: 20px;
      margin-top: 0px;
      height: 450px; /* 或固定高度，如 600px */ 
      @media (max-width: 1200px) {
        flex-direction: column;
      }
      //左侧视频区域
      .video-section {
        flex: 1;
        display: flex;
        flex-direction: column;
        gap: 10px;//让子区域保持的间距

        //“分析结果视频”的标题样式
        h4 {
          margin: 0;
          color: #333;
          font-size: 16px;
          font-weight: 500;
          padding-bottom: 10px;
        }
        // 视频播放器的样式
        .result-video {
          width: 100%;
          border-radius: 4px;
          background: #000;
          max-height: 400px;
        }
        //没视频的样式
        .no-video {
          padding: 20px;
          background: #f5f5f5;
          border-radius: 4px;
          text-align: center;
          max-height: 400px;
        }
        //基本统计的样式
        .basic-stats {
          margin-top: 10px;
          padding: 15px;
          background: #f9f9f9;
          border-radius: 4px;
        }
      }
      //右侧图表区域
      .chart-section {
        flex: 1; //依赖父容器的高度传递
        height: 100%; 
        min-height: 400px; // 确保有最小高度
         overflow: hidden; // 防止内容溢出

        //图名的样式
        ::v-deep .el-tabs__item {
          color: #666; 
          //width: 350px;
          &.is-active { //选中的样式
            color: purple !important; 
          }
        }
        .el-tabs {
          height: 100%;
          box-shadow: none;//移除标签页的默认阴影效果
          .el-tabs__content {
            height: 100% !important; // 减去标签页高度
          }
          ::v-deep(.el-tabs__content) {
          > div {
            height: 100% !important;
            position: relative;  // 添加定位
            }
          }
        }
      }
    }
    //无分析结果的样式
    .no-result {
      display: flex;
      flex-direction: column;
      align-items: center;
      padding: 60px 0;
      height: 435px;
      .go-analysis-btn {
        margin-top: 20px;
        width: 200px;
        background-color: #6e4960;
        border-color: #6e4960;
      }
    }
    //教师评分和反馈区域
    .feedback-section {
      margin-top: 30px;
      
      .feedback-title {
        margin-bottom: 20px;
        color: #333;
        font-size: 18px;
        font-weight: 600;
        padding-left: 10px;
        border-left: 4px solid #845973;
      }
      
      .overall-score-card {
        margin-bottom: 20px;
        border-radius: 8px;
        
        .overall-score-content {
          display: flex;
          align-items: center;
          padding: 20px;
          
          @media (max-width: 768px) {
            flex-direction: column;
          }
          
          .score-circle {
            width: 120px;
            height: 120px;
            border-radius: 50%;
            border: 8px solid var(--score-color);
            display: flex;
            flex-direction: column;
            justify-content: center;
            align-items: center;
            margin-right: 30px;
            
            @media (max-width: 768px) {
              margin-right: 0;
              margin-bottom: 20px;
            }
            
            .score-value {
              font-size: 36px;
              font-weight: bold;
              color: var(--score-color);
            }
            
            .score-label {
              font-size: 14px;
              color: #666;
            }
            .score-level{
              color:#f70a0a
            }
          }
          
          .score-details {
            flex: 1;
            padding: 10px;
            .detail-item {
              margin-bottom: 10px;
              display: flex; /* 使用 flex 布局 */
              align-items: center; /* 垂直居中 */
              &:last-child {
                margin-bottom: 0;
              }
              
              .detail-label {
                flex-shrink: 0; /* 防止标签缩小 */
                width: 80px; /* 固定宽度 */
                color: #666;
                white-space: nowrap; /* 防止换行 */
              }
              
              .detail-value {
                flex-shrink: 0; /* 防止数值缩小 */
                width: 40px; /* 固定宽度 */
                text-align: right;
                margin-right: 10px;
                font-weight: bold;
                white-space: nowrap; /* 防止换行 */
              }
              
              .detail-progress {
                flex-grow: 1; /* 进度条占据剩余空间 */
                width: calc(100% - 140px); /* 确保宽度足够 */
                vertical-align: middle;
              }
              .detail-max {
                flex-shrink: 0; /* 防止最大值缩小 */
                margin-left: 10px;
                white-space: nowrap; /* 防止换行 */
              }
            }
          }
        }
      }
      .comments-card {
        border-radius: 8px;
        
        .comments-list, .suggestions-list {
          padding-left: 10px;
        
          li {
            margin-bottom: 10px;
            line-height: 1.2;
          }
        }
      }
    }
  }
}
.grading-standard{
  padding-left: 15px;
}
</style>