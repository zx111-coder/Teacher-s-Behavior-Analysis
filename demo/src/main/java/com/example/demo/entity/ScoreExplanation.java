package com.example.demo.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScoreExplanation {
    private Long id;
    private Long scoreId;
    private String level;
    private String gradingStandard;
    private String comment;
    private String suggestion;
}
