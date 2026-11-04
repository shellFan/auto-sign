package com.autosign.activity;

import lombok.Data;

@Data
public class ActivityTemplates {
    private Long topicId;
    private Long userId;
    private String postTemplates;
    private String replyTemplates;
}
