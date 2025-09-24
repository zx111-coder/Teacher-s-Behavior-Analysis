// src/api/video.js
import request from '../utils/request';
import axios from 'axios'; 
export const uploadVideo = (file) => {
  const formData = new FormData();
  formData.append('file', file);
  return request.post('/api/video/upload', formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  });
};

export const processVideo = (videoUrl) => {
try {
    const response =request.post('/api/video/process', { videoUrl:videoUrl.value });
    
    // 确保响应包含taskId
    if (!response.data || !response.data.taskId) {
        throw new Error('Invalid response: missing taskId');
    }
    
    return {
        success: true,
        taskId: response.data.taskId,
        status: response.data.status || 'processing',
        message: response.data.message || '视频处理已开始'
    };
    } catch (error) {
    console.error('视频处理请求失败:', error);
    throw error;
    }
};

//轮询的端口
export const getTaskStatus = (taskId) => {
  return axios.get(`/api/video/status/${taskId}`);
};