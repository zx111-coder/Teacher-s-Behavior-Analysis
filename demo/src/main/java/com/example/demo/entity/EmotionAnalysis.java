package com.example.demo.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmotionAnalysis {
    private Long id;
    private Long videoId;
    private Integer happy;
    private Integer sad;
    private Integer angry;
    private Integer surprise;
    private Integer neutral;
    private Integer fear;
    private Integer disgust;
    private Integer total;
}
