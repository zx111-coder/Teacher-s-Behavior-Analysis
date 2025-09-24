package com.example.demo.service;

import com.example.demo.entity.Video;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.Map;

//视频处理
@Service
public interface VideoProcessingService {
    Video uploadVideo(String taskId,String originalFilename, String originalVideoPath,
                      String originalVideoUrl,long fileSize,long userId
                      );

    Map<String, Object> processVideo(String videoUrl, String taskId, Path tempDirPath, Path processingDirPath,String ip);

    Map<String, Object> getTaskStatus(String taskId);

}