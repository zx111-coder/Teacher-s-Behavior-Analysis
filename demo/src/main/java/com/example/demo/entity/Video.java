package com.example.demo.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Video {
    private Long id;
    private Long userId;
    private String taskId;
    private String videoName;
    private String originalFilename;
    private String processedVideoPath;
    private String originalVideoUrl;
    private String originalVideoPath;
    private String processedVideoUrl;
    private String processedResultJsonPath;
    private String status;
    private Integer progress;
    private Long fileSize;
    private Date createdAt;
    private Date updatedAt;
}
