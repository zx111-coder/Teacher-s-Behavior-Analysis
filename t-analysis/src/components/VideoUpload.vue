<template>
    <el-upload
      class="video-upload"
      drag
      action="#"
      :auto-upload="false"
      :on-change="handleChange"
      :show-file-list="false"
      accept="video/*"
    >
      <i class="el-icon-upload"></i>
      <div class="el-upload__text">将视频文件拖到此处，或<em>点击上传</em></div>
      <div class="el-upload__tip">支持MP4、AVI等常见视频格式</div>
    </el-upload>
  
    <el-form :model="config" label-width="120px" v-if="file">
      <el-form-item label="分析类型">
        <el-checkbox-group v-model="config.analysisTypes">
          <el-checkbox label="behavior">行为分析</el-checkbox>
          <el-checkbox label="emotion">情绪分析</el-checkbox>
          <el-checkbox label="speech">语音分析</el-checkbox>
        </el-checkbox-group>
      </el-form-item>
      <el-form-item label="分析精度">
        <el-select v-model="config.precision" placeholder="请选择分析精度">
          <el-option label="低" value="low"></el-option>
          <el-option label="中" value="medium"></el-option>
          <el-option label="高" value="high"></el-option>
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="submitUpload">开始分析</el-button>
      </el-form-item>
    </el-form>
  
    <el-progress 
      v-if="uploadProgress > 0"
      :percentage="uploadProgress"
      :status="uploadStatus"
    ></el-progress>
  </template>
  
  <script>
  import { ref } from 'vue'
  import { uploadVideo } from '../api/analysis'
  import { ElMessage } from 'element-plus'
  
  export default {
    name: 'VideoUpload',
    emits: ['upload-success'],
    setup(props, { emit }) {
      const file = ref(null)
      const uploadProgress = ref(0)
      const uploadStatus = ref('')
      const config = ref({
        analysisTypes: ['behavior', 'emotion'],
        precision: 'medium'
      })
  
      const handleChange = (uploadFile) => {
        file.value = uploadFile.raw
      }
  
      const submitUpload = async () => {
        if (!file.value) {
          ElMessage.warning('请先选择视频文件')
          return
        }
  
        try {
          uploadStatus.value = ''
          uploadProgress.value = 0
          
          const res = await uploadVideo(file.value, config.value, {
            onUploadProgress: progressEvent => {
              uploadProgress.value = Math.round(
                (progressEvent.loaded * 100) / progressEvent.total
              )
            }
          })
          
          uploadStatus.value = 'success'
          ElMessage.success('视频上传并分析成功')
          emit('upload-success', res.data)
        } catch (error) {
          uploadStatus.value = 'exception'
          ElMessage.error('上传失败: ' + error.message)
        }
      }
  
      return {
        file,
        config,
        uploadProgress,
        uploadStatus,
        handleChange,
        submitUpload
      }
    }
  }
  </script>
  
  <style scoped>
  .video-upload {
    margin-bottom: 20px;
  }
  </style>