package com.example.demo.service;

import com.example.demo.Exception.FileNotFoundException;
import com.example.demo.entity.*;
import com.example.demo.mapper.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class JsonService {
    @Autowired
    private ActionAnalysisMapper actionAnalysisMapper;
    @Autowired
    private ScoreExplanationMapper scoreExplanationMapper;
    @Autowired
    private ScoreAnalysisMapper scoreAnalysisMapper;
    @Autowired
    private EmotionAnalysisMapper emotionAnalysisMapper;

    private final Map<String, Object> resultMap = new HashMap<>();
    // 保存动作分析结果到数据库
    private void saveActionAnalysisResults(Long videoId, Map<String, Object> actionResult) {
        ActionAnalysis actionAnalysis = new ActionAnalysis();
        actionAnalysis.setVideoId(videoId);
        actionAnalysis.setHandPointDown((Integer) actionResult.get("hand_point_down"));
        actionAnalysis.setHeadPointFront((Integer) actionResult.get("head_point_front"));
        actionAnalysis.setHeadPointSide((Integer) actionResult.get("head_point_side"));
        actionAnalysis.setHeadPointBack((Integer) actionResult.get("head_point_back"));
        actionAnalysis.setHandPointUp((Integer) actionResult.get("hand_point_up"));
        actionAnalysis.setHandPointHorizon((Integer) actionResult.get("hand_point_horizon"));
        actionAnalysis.setHandPointFront((Integer) actionResult.get("hand_point_front"));
        actionAnalysis.setLookAtStudent((Integer) actionResult.get("look_at_student"));
        actionAnalysis.setLookAtComputer((Integer) actionResult.get("look_at_computer"));
        actionAnalysis.setWriteBlackboard((Integer) actionResult.get("write_blackboard"));
        actionAnalysis.setExplanation((Integer) actionResult.get("explanation"));
        actionAnalysis.setLookAtBlackboard((Integer) actionResult.get("look_at_blackboard"));
        actionAnalysis.setLookAtProjector((Integer) actionResult.get("look_at_projector"));
        actionAnalysis.setHandWave((Integer) actionResult.get("hand_wave"));
        actionAnalysis.setSitTimes((Integer) actionResult.get("sit_times"));
        actionAnalysis.setStandTimes((Integer) actionResult.get("stand_times"));
        actionAnalysis.setWalkTimes((Integer) actionResult.get("walk_times"));
        actionAnalysis.setTakeTimes((Integer) actionResult.get("take_times"));
        actionAnalysis.setSitDown((Integer) actionResult.get("sit_down"));
        actionAnalysisMapper.insert(actionAnalysis);
    }

    // 保存表情分析结果到数据库
    private void saveEmotionAnalysisResults(Long videoId, Map<String, Object> emotionResult) {
        EmotionAnalysis emotionAnalysis = new EmotionAnalysis();
        emotionAnalysis.setVideoId(videoId);
        emotionAnalysis.setHappy((Integer) emotionResult.get("happy"));
        emotionAnalysis.setSad((Integer) emotionResult.get("sad"));
        emotionAnalysis.setAngry((Integer) emotionResult.get("angry"));
        emotionAnalysis.setSurprise((Integer) emotionResult.get("surprise"));
        emotionAnalysis.setNeutral((Integer) emotionResult.get("neutral"));
        emotionAnalysis.setFear((Integer) emotionResult.get("fear"));
        emotionAnalysis.setDisgust((Integer) emotionResult.get("disgust"));
        emotionAnalysis.setTotal((Integer) emotionResult.get("sum"));
        emotionAnalysisMapper.insert(emotionAnalysis);
    }

    // 保存分数分析结果到数据库
    private void saveScoreAnalysisResults(Long videoId, Map<String, Object> scoreResult){
        ScoreAnalysis scoreAnalysis = new ScoreAnalysis();
        scoreAnalysis.setVideoId(videoId);
        scoreAnalysis.setTotalScore((Float) scoreResult.get("totalScore"));
        scoreAnalysis.setExplanationScore((Float) scoreResult.get("explanation"));
        scoreAnalysis.setWritingScore((Float) scoreResult.get("writing"));
        scoreAnalysis.setInteractionScore((Float) scoreResult.get("interaction"));
        scoreAnalysis.setGesturesScore((Float) scoreResult.get("gestures"));
        scoreAnalysis.setPostureScore((Float) scoreResult.get("posture"));
        scoreAnalysis.setMovementScore((Float) scoreResult.get("movement"));
        scoreAnalysisMapper.insert(scoreAnalysis);

        scoreAnalysis = scoreAnalysisMapper.findByVideoId(videoId);
        Map<String, Object> scoreExplanationResult = (Map<String, Object>) scoreResult.get("score_explanation");
        ScoreExplanation scoreExplanation = new ScoreExplanation();
        scoreExplanation.setScoreId(scoreAnalysis.getId());
        if (scoreExplanationResult.containsKey("level")) {
            scoreExplanation.setLevel((String) scoreExplanationResult.get("level"));
        }
        if (scoreExplanationResult.containsKey("comments")) {
            // 安全处理类型转换
            Object commentsObj = scoreExplanationResult.get("comments");
            if (commentsObj instanceof List) {
                List<String> comments = (List<String>) commentsObj;
                scoreExplanation.setComment(String.join(";", comments));
            }
        }
        if (scoreExplanationResult.containsKey("suggestions")) {
            // 安全处理类型转换
            Object suggestionsObj = scoreExplanationResult.get("suggestions");
            if (suggestionsObj instanceof List) {
                List<String> suggestions = (List<String>) suggestionsObj;
                scoreExplanation.setSuggestion(String.join(";", suggestions));
            }
        }
        if (scoreExplanationResult.containsKey("grading_standard")) {
            Object gradingStandard = scoreExplanationResult.get("grading_standard");
            scoreExplanation.setGradingStandard((String) gradingStandard);
        }
        System.out.println(scoreExplanation);
        scoreExplanationMapper.insert(scoreExplanation);
    }

    //分析JSON文件
    public void parseResultJson(Long videoId,String localJsonPath){
        // 校验文件路径
        String filePath = localJsonPath;
        if (filePath == null || filePath.trim().isEmpty()) {
            throw new IllegalArgumentException("分析结果文件路径不能为空");
        }
        File jsonFile = new File(filePath);
        if (!jsonFile.exists()) {
            throw new FileNotFoundException("分析结果文件不存在: " + filePath);
        }
        ObjectMapper objectMapper = new ObjectMapper();
        try (FileInputStream fis = new FileInputStream(jsonFile);
             InputStreamReader isr = new InputStreamReader(fis, StandardCharsets.UTF_8)) {
            // 读取整个JSON文件
            JsonNode rootNode = objectMapper.readTree(isr);
            if (rootNode.has("video_id")) {
                resultMap.put("video_id", rootNode.get("video_id").asText());
            }
            Map<String,Object> action = parseActionResult(rootNode);
            Map<String,Object> emotion = parseEmotionResult(rootNode);
            Map<String,Object> score = parseScoreResult(rootNode);

            saveActionAnalysisResults(videoId, action);
            saveEmotionAnalysisResults(videoId, emotion);
            saveScoreAnalysisResults(videoId,score);

            System.out.println("\n"+"resultMap---"+resultMap);//////////////////////////////////////////////
        } catch (IOException e) {
            throw new RuntimeException("解析分析结果JSON文件失败", e);
        }
    }

    //辅助方法
    public Map<String, Object> convertJsonValue(Iterator<Map.Entry<String, JsonNode>> fields,Map<String, Object> result){
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String key = field.getKey();
            JsonNode valueNode = field.getValue();
            // 根据值类型进行转换
            if (valueNode.isNumber()) {
                result.put(key, valueNode.numberValue());
            } else if (valueNode.isTextual()) {
                result.put(key, valueNode.textValue());
            } else if (valueNode.isBoolean()) {
                result.put(key, valueNode.booleanValue());
            } else if (valueNode.isArray()) {
                List<Object> arrayList = new ArrayList<>();
                for (JsonNode element : valueNode) {
                    if (element.isTextual()) {
                        arrayList.add(element.textValue());
                    } else if (element.isNumber()) {
                        arrayList.add(element.numberValue());
                    }
                }
                result.put(key, arrayList);
            }
        }
        return result;
    }

    public Map<String, Object> parseActionResult(JsonNode rootNode){
        Map<String, Object> actionResult = new HashMap<>();
        if (rootNode.has("analyze_result")) {
            JsonNode analyzeResultNode = rootNode.get("analyze_result");
            // 遍历所有字段并添加到结果Map
            Iterator<Map.Entry<String, JsonNode>> fields = analyzeResultNode.fields();
            actionResult = convertJsonValue(fields, actionResult);
        }
        resultMap.put("actionResult", actionResult);
        return actionResult;
    }

    public Map<String,Object> parseEmotionResult(JsonNode rootNode){
        Map<String, Object> emotionResult = new HashMap<>();
        if (rootNode.has("emotion_result")) {
            JsonNode analyzeResultNode = rootNode.get("emotion_result");
            // 遍历所有字段并添加到结果Map
            Iterator<Map.Entry<String, JsonNode>> fields = analyzeResultNode.fields();
            emotionResult = convertJsonValue(fields, emotionResult);
        }
        resultMap.put("emotionResult", emotionResult);
        return emotionResult;
    }

    public Map<String, Object> parseScoreResult(JsonNode rootNode) {
        Map<String, Object> scoreResult = new HashMap<>();
        Float score = 0F;
        if (rootNode.has("teaching_score")) {
            score = rootNode.get("teaching_score").floatValue();
            scoreResult.put("totalScore", score);
        }
        if (rootNode.has("score_explanation")) {
            JsonNode explainNode = rootNode.get("score_explanation");
            // 处理嵌套对象（如score_breakdown）
            if (explainNode.has("score_breakdown")) {
                JsonNode breakdownNode = explainNode.get("score_breakdown");
                breakdownNode.fields().forEachRemaining(entry -> {
                    String key = entry.getKey();
                    JsonNode value = entry.getValue();
                    if (value.isNumber()) {
                        scoreResult.put(key, value.floatValue());
                    }
                });
            }
            //////////////////////////////
            Map<String, Object> scoreExplanationResult = new HashMap<>();
            if (explainNode.has("comments")) {
                List<String> comments = new ArrayList<>();
                for (JsonNode item : explainNode.get("comments")) {
                    if (item.isTextual()) {
                        comments.add(item.asText());
                    }
                }
                scoreExplanationResult.put("comments", comments);
            }
            if (explainNode.has("suggestions")) {
                List<String> suggestions = new ArrayList<>();
                for (JsonNode item : explainNode.get("suggestions")) {
                    if (item.isTextual()) {
                        suggestions.add(item.asText());
                    }
                }
                scoreExplanationResult.put("suggestions",suggestions);
            }
            if(score>=90){
                scoreExplanationResult.put("level", "excellent");
                scoreExplanationResult.put("grading_standard", "90+分：各项指标均超过阈值且分布均衡");
            } else if (score>=75) {
                scoreExplanationResult.put("level", "good");
                scoreExplanationResult.put("grading_standard", "75-89分：主要指标达标但有改进空间");
            } else if (score>=60) {
                scoreExplanationResult.put("level", "average");
                scoreExplanationResult.put("grading_standard", "60-74分：部分关键指标不足");
            }else{
                scoreExplanationResult.put("level", "poor");
                scoreExplanationResult.put("grading_standard", "<60分：需要全面提升教学表现");
            }
            scoreResult.put("score_explanation", scoreExplanationResult);
        }
        resultMap.put("scoreResult", scoreResult);
        return scoreResult;
    }
}



