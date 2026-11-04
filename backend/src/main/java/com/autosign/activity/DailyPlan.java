package com.autosign.activity;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class DailyPlan {
    private Long id;
    private Long userId;
    private Long accountId;
    private Long topicId;
    private LocalDate taskDate;
    private String accountName;
    private String topicName;
    private Integer signTarget;
    private Integer postTarget;
    private Integer replyTarget;
    private Integer forwardTarget;
    private Integer viewTarget;
    private LocalDateTime createdAt;

    public Map<String, Integer> targets() {
        Map<String, Integer> result = new LinkedHashMap<>();
        result.put(ActivityTask.ACTION_SIGN, signTarget);
        result.put(ActivityTask.ACTION_POST, postTarget);
        result.put(ActivityTask.ACTION_REPLY, replyTarget);
        result.put(ActivityTask.ACTION_FORWARD, forwardTarget);
        result.put(ActivityTask.ACTION_VIEW, viewTarget);
        return result;
    }
}
