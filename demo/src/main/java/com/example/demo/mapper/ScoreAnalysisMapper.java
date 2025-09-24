package com.example.demo.mapper;

import com.example.demo.entity.ScoreAnalysis;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ScoreAnalysisMapper {
    int insert(ScoreAnalysis result);
    ScoreAnalysis findByVideoId(Long videoId);
}
