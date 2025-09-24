package com.example.demo.mapper;

import com.example.demo.entity.ActionAnalysis;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ActionAnalysisMapper {
    int insert(ActionAnalysis result);
    ActionAnalysis findByVideoId(Long videoId);
}
