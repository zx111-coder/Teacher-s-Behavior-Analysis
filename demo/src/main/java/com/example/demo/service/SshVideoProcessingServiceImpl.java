package com.example.demo.service;
import com.example.demo.Exception.TaskNotFoundException;
import com.example.demo.utils.SshUtil;
import org.apache.commons.io.FilenameUtils;
import com.example.demo.Exception.ServiceException;
import com.example.demo.entity.*;
import com.example.demo.mapper.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

// 视频上传与数据库记录
@Service
public class SshVideoProcessingServiceImpl implements VideoProcessingService {
    @Autowired
    private VideoMapper videoMapper;
    @Autowired
    private JsonService jsonService;
    // 存储任务状态
    private final Map<String, Map<String, Object>> taskStatusMap = new ConcurrentHashMap<>();

    @Override
    @Transactional
    public Video uploadVideo(String taskId,String originalFilename,
                             String originalVideoPath, String originalVideoUrl,
                             long fileSize,long userId) {
        Video video = new Video();
        video.setUserId(userId);
        video.setOriginalFilename(originalFilename);
        video.setOriginalVideoPath(originalVideoPath);
        video.setOriginalVideoUrl(originalVideoUrl);
        video.setFileSize(fileSize);
        video.setStatus("uploaded");
        video.setVideoName("未命名视频");
        video.setTaskId(taskId);
        videoMapper.insert(video);
        return video;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW) // 开启新事务
    public Map<String, Object> processVideo(String videoUrl, String taskId, Path tempDirPath, Path processingDirPath,String ip) {
        // 查找视频记录
        Video video = videoMapper.findByPath(videoUrl);
        if (video == null) {
            throw new RuntimeException("找不到对应的视频记录");
        }
        // 验证视频状态
        if (!"processing".equals(video.getStatus())) {
            if(!"failed".equals(video.getStatus())){
                throw new ServiceException("视频状态无效，当前状态: " + video.getStatus());
            }
        }
        // 更新视频状态为处理中
        video.setStatus("processing");
        video.setProgress(0);
        videoMapper.update(video);
        // 初始化任务状态
        Map<String, Object> taskStatus = new HashMap<>();
        taskStatus.put("taskId", taskId);
        taskStatus.put("status", "processing");
        taskStatus.put("progress", 0);
        taskStatus.put("success", false);
        taskStatusMap.put(taskId, taskStatus);
        try {
            Long videoId = video.getId();
            // 服务器配置
            String serverHost = "10.11.5.167";  // 服务器IP
            int serverPort = 22;  // SSH端口
            String serverUser = "user5";  // 服务器用户名
            String serverPassword = "123123";  // 服务器密码
            String condaEnv = "lzy";
            String mmactionPath = "/home/user5/mmaction2_YF_2"; // MMAction2项目根目录
//            String serverScriptPath = mmactionPath+"/demo/cutted_demo_spatiotemporal_det_slowfast.py";
            String serverScriptPath = mmactionPath+"/demo/A.py";
            String serverTempDir = mmactionPath+"/demo/tmp/"; //临时路径（原始的
            String serverOutputDir = mmactionPath+"/demo/processed_result/";   //输出路径（处理的
            // 构建服务器上的文件路径
            String serverVideoPath = serverTempDir + taskId + ".mp4"; //原始的
            String serverOutputPath = serverOutputDir + "processed_" + taskId + ".mp4";
            String serverJsonPath = serverOutputDir + "results_" + taskId + ".json";
            // 本地视频路径
            String localTempVideoPath = tempDirPath.resolve(taskId+".mp4").toString();
            //处理后的
            String localOutputPath = processingDirPath.resolve("processed_"+taskId + ".mp4").toString();
            String localJsonPath = processingDirPath.resolve("results_" + taskId + ".json").toString();
            // 将视频上传到服务器临时目录     从本地传到服务器
            SshUtil.uploadFile(localTempVideoPath, serverHost, serverPort, serverUser, serverPassword, serverTempDir, taskId + ".mp4");
            video.setProgress(10);
            videoMapper.update(video);  ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
            // 构建命令 - 使用完整路径，确保目录存在
            String command =
                    "source /home/user5/miniconda3/etc/profile.d/conda.sh && " +
                    "conda activate " + condaEnv + " && " +
                    "python " + serverScriptPath +
                    " --video " + serverVideoPath +
                    " --out-filename " + serverOutputPath +
                    " --video_id " + videoId +
                    " --result-json " + serverJsonPath;
            System.out.println("执行命令: " + command);
            String executionResult = SshUtil.executeCommand(serverHost, serverPort, serverUser, serverPassword, command);
//            System.out.println("服务器执行结果: " + executionResult);
            // 检查执行结果
            if (executionResult.contains("ERROR") || executionResult.contains("failed")) {
                throw new RuntimeException("服务器上执行Python脚本失败");
            }
            /// /////////////////////////////////////////////////////
            // 将视频从服务器下载到本地
            SshUtil.downloadFile(serverOutputPath, serverHost, serverPort, serverUser, serverPassword, localOutputPath);
            SshUtil.downloadFile(serverJsonPath, serverHost, serverPort, serverUser, serverPassword, localJsonPath);
            String resultVideoUrl = "http://" + ip + ":8080/video/download/processed_" + taskId + ".mp4";
            // 更新视频状态
            video.setProcessedVideoUrl(resultVideoUrl);
            video.setProcessedVideoPath(localOutputPath);
            video.setProcessedResultJsonPath(localJsonPath);
            video.setStatus("completed");
            video.setProgress(100);
            videoMapper.update(video);
            // 更新任务状态
            taskStatus = taskStatusMap.get(taskId);
            taskStatus.put("status", "completed");
            taskStatus.put("progress", 100);
            taskStatus.put("success", true);
            taskStatus.put("resultVideoUrl", resultVideoUrl);
            taskStatus.put("resultJsonPath", localJsonPath);
            // 保存分析结果到数据库
            jsonService.parseResultJson(video.getId(), localJsonPath);///////////////////////////////////
            System.out.println(taskStatus);
            return taskStatus;
        } catch (Exception e) {
            // 更新视频状态为失败
            video.setStatus("failed");
            video.setProgress(0);
            video.setProcessedVideoPath(null);
            videoMapper.update(video);
            taskStatus = taskStatusMap.get(taskId);
            taskStatus.put("status", "failed");
            taskStatus.put("message", e.getMessage());
            System.out.println(taskStatus);
            return taskStatus;
        }
    }

    //获取状态
    @Override
    public Map<String, Object> getTaskStatus(String taskId) {
        // 参数验证
        if (taskId == null || taskId.trim().isEmpty()) {
            throw new IllegalArgumentException("任务ID不能为空");
        }
        // 从内存映射中获取任务状态
        Map<String, Object> taskStatus = taskStatusMap.get(taskId);
        // 任务不存在于内存映射中，尝试从数据库查询
        if (taskStatus == null) {
            Video video = videoMapper.findByTaskId(taskId);
            if (video == null) {
                throw new TaskNotFoundException("任务ID: " + taskId + " 不存在");
            }
            // 从数据库状态构建任务状态
            taskStatus = buildTaskStatusFromDatabase(video);
        } else {
            // 内存状态与数据库状态同步
            Video video = videoMapper.findByTaskId(taskId);
            if (video != null) {
                // 如果数据库状态更更新，更新内存状态
                if (!taskStatus.get("status").equals(video.getStatus())) {
                    taskStatus.put("status", video.getStatus());
                    taskStatus.put("progress", video.getProgress());
                    taskStatus.put("success", "completed".equals(video.getStatus()));
                    // 如果任务已完成，补充结果信息
                    if ("completed".equals(video.getStatus())) {
                        taskStatus.put("resultVideoUrl", video.getProcessedVideoUrl());
                        taskStatus.put("resultJsonPath", video.getProcessedResultJsonPath());
                    }
                }
            }
        }
        return taskStatus;
    }

     //从数据库视频记录构建任务状态
    private Map<String, Object> buildTaskStatusFromDatabase(Video video) {
        Map<String, Object> taskStatus = new HashMap<>();
        taskStatus.put("taskId", video.getTaskId());
        taskStatus.put("status", video.getStatus());
        taskStatus.put("progress", video.getProgress());
        taskStatus.put("success", "completed".equals(video.getStatus()));
        // 补充结果信息
        if ("completed".equals(video.getStatus())) {
            taskStatus.put("resultVideoUrl", video.getProcessedVideoUrl());
            taskStatus.put("resultJsonPath", video.getProcessedResultJsonPath());
        }
        return taskStatus;
    }

    // 辅助方法：确定内容类型
    public String determineContentType(String filename) {
        String extension = FilenameUtils.getExtension(filename).toLowerCase();
        return switch (extension) {
            case "mp4" -> "video/mp4";
            case "avi" -> "video/x-msvideo";
            case "mov" -> "video/quicktime";
            case "webm" -> "video/webm";
            default -> MediaType.APPLICATION_OCTET_STREAM_VALUE;
        };
    }
}