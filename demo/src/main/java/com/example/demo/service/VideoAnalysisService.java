package com.example.demo.service;

import com.example.demo.entity.*;
import com.example.demo.mapper.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class VideoAnalysisService {
    @Autowired
    private ActionAnalysisMapper actionAnalysisMapper;
    @Autowired
    private EmotionAnalysisMapper emotionAnalysisMapper;
    @Autowired
    private ScoreExplanationMapper scoreExplanationMapper;
    @Autowired
    ScoreAnalysisMapper scoreAnalysisMapper;
    @Autowired
    private VideoMapper videoMapper;
    public Map<String, Object> getAnalysisResultByVideoId(Long videoId) {
        Map<String, Object> analysisResult = new HashMap<>();
        Video video = videoMapper.findById(videoId);
        analysisResult.put("video", video);
        ActionAnalysis actionAnalysis = actionAnalysisMapper.findByVideoId(videoId);
        analysisResult.put("actionAnalysis", actionAnalysis);
        EmotionAnalysis emotionAnalysis = emotionAnalysisMapper.findByVideoId(videoId);
        analysisResult.put("emotionAnalysis", emotionAnalysis);
        ScoreAnalysis scoreAnalysis = scoreAnalysisMapper.findByVideoId(videoId);
        analysisResult.put("scoreAnalysis", scoreAnalysis);
        ScoreExplanation scoreExplanation = scoreExplanationMapper.findByScoreId(scoreAnalysis.getId());
        analysisResult.put("scoreExplanation", scoreExplanation);
        System.out.println(analysisResult);
        return analysisResult;
    }
}