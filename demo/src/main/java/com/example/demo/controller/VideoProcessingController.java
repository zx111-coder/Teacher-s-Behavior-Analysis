package com.example.demo.controller;

import com.example.demo.Exception.TaskNotFoundException;
import com.example.demo.entity.Video;
import com.example.demo.mapper.VideoMapper;
import com.example.demo.service.SshVideoProcessingServiceImpl;
import com.example.demo.service.VideoAnalysisService;

import jakarta.annotation.PostConstruct;
import org.springframework.core.io.Resource;
import org.springframework.core.io.PathResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/video")

public class VideoProcessingController {
    @Autowired
    private SshVideoProcessingServiceImpl videoProcessingService;
    @Autowired
    private VideoAnalysisService videoAnalysisService;
    @Autowired
    private VideoMapper videoMapper;

    private String ip = "localhost";

    private Path tempDirPath;  // 定义为类成员变量
    private Path processingDirPath;

    // 配置目录路径
    @Value("${temp.dir:temp_videos}")
    private String TEMP_DIR;//temp.dir=C:/Users/lzy/Desktop/1/result/origin
    @Value("${processing.dir:processed_videos}")
    private String PROCESSING_DIR;//processing.dir=C:/Users/lzy/Desktop/1/result/processed
    @Value("${max.upload.size.mb:1000}")
    private int maxUploadSizeMB;

    @PostConstruct
    public void init() {
        // 初始化临时目录路径
        tempDirPath = Paths.get(TEMP_DIR);
        try {
            // 确保目录存在
            if (!Files.exists(tempDirPath)) {
                Files.createDirectories(tempDirPath);
//                log.info("创建临时目录: {}", tempDirPath.toAbsolutePath());
            }
        } catch (IOException e) {
//            log.error("无法创建临时目录: {}", TEMP_DIR, e);
            throw new RuntimeException("无法创建临时目录", e);
        }
        // 初始化临时目录路径
        processingDirPath = Paths.get(PROCESSING_DIR);
        try {
            // 确保目录存在
            if (!Files.exists(processingDirPath)) {
                Files.createDirectories(processingDirPath);
            }
        } catch (IOException e) {
//            log.error("无法创建临时目录: {}", TEMP_DIR, e);
            throw new RuntimeException("无法创建临时目录", e);
        }
    }

    @PostMapping("/upload")     //上传
    public ResponseEntity<Map<String, Object>> uploadVideo(@RequestParam("file") MultipartFile file, @RequestParam("userId") Long userId) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(
                        createErrorResponse("400", "上传的文件不能为空"));
            }
            // 验证文件类型
            if (!isValidVideoType(file.getContentType())) {
                return ResponseEntity.badRequest().body(
                        createErrorResponse("400", "不支持的文件类型"));
            }
            // 验证文件大小
            long maxBytes = maxUploadSizeMB * 1024L * 1024L;
            if (file.getSize() > maxBytes) {
                return ResponseEntity.badRequest().body(
                        createErrorResponse("400", "文件大小超过" + maxUploadSizeMB + "MB限制"));
            }
            // 确保临时目录存在
            init();
            // 保存上传的文件
            String originalFilename = file.getOriginalFilename();
            String fileExtension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                int dotIndex = originalFilename.lastIndexOf('.');
                if (dotIndex > 0) {
                    fileExtension = originalFilename.substring(dotIndex);
                }
            }
            String taskId = UUID.randomUUID().toString();
            String tempFileName = taskId + fileExtension;
            Path tempPath = tempDirPath.resolve(tempFileName);
            file.transferTo(tempPath.toFile());     //保存原始文件到指定位置   C:\Users\lzy\Desktop\1\result\origin
            // 构建视频URL
            String videoUrl ="http://" + ip + ":8080/video/download/" + tempFileName;
//            String videoUrl ="http://localhost:8080/video/download/" + tempFileName;
            // 创建视频记录
            videoProcessingService.uploadVideo(
                    taskId,
                    file.getOriginalFilename(),
                    String.valueOf(tempPath),
                    videoUrl,
                    file.getSize(),
                    userId
            );
            Map<String, Object> response = new HashMap<>();
            response.put("videoUrl", videoUrl);
            response.put("tempFileName", tempFileName);
            response.put("status", "uploaded");
            response.put("message", "视频上传成功");
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("500", "文件上传失败: " + e.getMessage()));
        }
    }

    @GetMapping("/download/{filename:.+}")   //视频播放 or 下载
    public ResponseEntity<Resource> downloadFile(@PathVariable String filename) {
        try {
            // 1. 安全解析文件名
            String safeFilename = Paths.get(filename).getFileName().toString();
            // 2. 构建文件路径
            Path filePath = tempDirPath.resolve(safeFilename);
            // 3. 验证文件存在
            if (!Files.exists(filePath)) {
                filePath = processingDirPath.resolve(safeFilename);
                if(!Files.exists(filePath)){
                    return ResponseEntity.notFound().build();
                }
            }
            // 4. 创建Resource对象
            Resource resource = new PathResource(filePath);
            // 5. 确定内容类型
            String contentType = videoProcessingService.determineContentType(safeFilename);
            // 6. 构建响应
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=\"" + safeFilename + "\"") // 使用inline在浏览器中预览
                    .body(resource);
        } catch (Exception e) {
//            log.error("文件下载失败: {}", filename, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/process")    //分析视频
    public ResponseEntity<Map<String, Object>> processVideo(@RequestBody Map<String, String> request) {
        String videoUrl = request.get("videoUrl");
        String videoName = request.get("videoName");
        // 验证URL格式
        if (!videoUrl.matches("^(file|http|https)://.*")) {
            return ResponseEntity.badRequest().body(
                    createErrorResponse("400", "无效的视频URL格式"));
        }
        if (videoUrl == null || videoUrl.isEmpty()) {
            return ResponseEntity.badRequest().body(
                    createErrorResponse("400", "视频URL不能为空"));
        }
        try {
            Video video = videoMapper.findTaskIdbyUrl(videoUrl);
            video.setVideoName(videoName);
            video.setStatus("processing");
            video.setProgress(0);
            videoMapper.update(video);
            String taskId = video.getTaskId();
            //异步处理视频
            CompletableFuture.supplyAsync(() -> {
                try {
                    //logger.info("开始处理视频，taskId: {}", taskId);
                    // 确保临时目录存在!!!!!!!!!!!!!!!!!!!!!!!
                    init();
                    System.out.println("开始处理视频！！！！！！！");
                    return videoProcessingService.processVideo(videoUrl, taskId,tempDirPath,processingDirPath,ip);
                } catch (Exception e) {
                    //logger.error("视频处理失败，taskId: {}", taskId, e);
                    Map<String, Object> error = new HashMap<>();
                    error.put("taskId", taskId);
                    error.put("success", false);
                    error.put("message", "视频处理失败: " + e.getMessage());
                    // 记录错误日志
                    return error;
                }
            })
            .exceptionally(ex -> {
//                logger.error("异步处理异常，taskId: {}", taskId, ex);
                Map<String, Object> error = new HashMap<>();
                error.put("taskId", taskId);
                error.put("success", false);
                error.put("message", "异步处理异常: " + ex.getMessage());
                return error;
            });
            //返回任务ID给前端
            Map<String, Object> response = new HashMap<>();
            response.put("taskId", taskId);
            response.put("Videoname", videoName);
            response.put("VideoUrl", videoUrl);
            response.put("videoId",video.getId());
            response.put("message", "视频处理已开始");
            response.put("status", "processing");
            response.put("timestamp", System.currentTimeMillis());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
//            logger.error("处理请求时发生异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("500", "处理请求时发生错误: " + e.getMessage()));
        }
    }

    @GetMapping("/status/{taskId}")    //状态检查API
    public ResponseEntity<?> getTaskStatus(@PathVariable String taskId) {
        try {
//            logger.info("收到任务状态查询请求: taskId={}", taskId);
            Map<String, Object> status = videoProcessingService.getTaskStatus(taskId);
            status.put("taskId", taskId);
            return ResponseEntity.ok(status);
        } catch (TaskNotFoundException e) {
//            logger.warn("任务状态不存在: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    Map.of("error", "404", "message", e.getMessage())
            );
        } catch (IllegalArgumentException e) {
//            logger.warn("无效的任务ID: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                    Map.of("error", "400", "message", e.getMessage())
            );
        } catch (Exception e) {
//            logger.error("获取任务状态失败: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    Map.of("error", "500", "message", "获取任务状态失败")
            );
        }
    }

    @GetMapping("/result/{taskId}")    //查看视频分析数据
    public ResponseEntity<Map<String, Object>> getAnalysisResult(@PathVariable String taskId) {
        try {
            Video video = videoMapper.findByTaskId(taskId);
            Long videoId = video.getId();
            Map<String, Object> result = videoAnalysisService.getAnalysisResultByVideoId(videoId);
            if (result != null) {
                return ResponseEntity.ok(result);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("error", "获取分析结果失败: " + e.getMessage()));
        }
    }

    // 统一错误响应格式
    private Map<String, Object> createErrorResponse(String code, String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("code", code);
        response.put("message", message);
        response.put("timestamp", System.currentTimeMillis());
        return response;
    }

    private boolean isValidVideoType(String contentType) {
        if (contentType == null) return false;
        return contentType.startsWith("video/") ||
                contentType.equals("application/octet-stream"); // 允许未知类型
    }
}