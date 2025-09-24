import request from '../utils/request'

export function uploadVideo(file, config) {// 上传视频文件和配置参数
  const formData = new FormData() 
  formData.append('file', file)
  formData.append('config', JSON.stringify(config))
  return request({
    url: '/upload',
    method: 'post',
    data: formData,
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}

export function getAnalysisResults(id) {// 获取分析结果
  return request({
    url: `/analysis/results/${id}`,
    method: 'get'
  })
}

export function getRealtimeData() {
  return request({
    url: '/analysis/realtime',
    method: 'get'
  })
}