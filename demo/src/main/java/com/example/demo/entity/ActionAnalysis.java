package com.example.demo.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActionAnalysis {
    private Long id;
    private Long videoId;
    private Integer handPointDown;
    private Integer headPointFront;
    private Integer headPointSide;
    private Integer headPointBack;
    private Integer handPointUp;
    private Integer handPointHorizon;
    private Integer handPointFront;
    private Integer lookAtStudent;
    private Integer lookAtComputer;
    private Integer writeBlackboard;
    private Integer explanation;
    private Integer lookAtBlackboard;
    private Integer lookAtProjector;
    private Integer handWave;
    private Integer sitTimes;
    private Integer standTimes;
    private Integer walkTimes;
    private Integer takeTimes;
    private Integer sitDown;
}
