package com.example.demo.mapper;

import com.example.demo.entity.EmotionAnalysis;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface EmotionAnalysisMapper {
    int insert(EmotionAnalysis result);
    EmotionAnalysis findByVideoId(Long videoId);
}
