package com.example.demo.mapper;

import com.example.demo.entity.ScoreExplanation;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ScoreExplanationMapper {
    int insert(ScoreExplanation result);
    ScoreExplanation findByScoreId(Long scoreId);
}
