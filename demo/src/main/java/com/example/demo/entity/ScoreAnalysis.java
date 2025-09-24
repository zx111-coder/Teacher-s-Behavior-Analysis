package com.example.demo.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScoreAnalysis {
    private Long id;
    private Long videoId;
    private Float totalScore;
    private Float explanationScore;
    private Float writingScore;
    private Float interactionScore;
    private Float gesturesScore;
    private Float postureScore;
    private Float movementScore;
}
