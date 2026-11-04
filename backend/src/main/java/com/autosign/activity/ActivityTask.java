package com.autosign.activity;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class ActivityTask {
    public static final String PENDING = "PENDING";
    public static final String RUNNING = "RUNNING";
    public static final String SUCCESS = "SUCCESS";
    public static final String FAILED = "FAILED";
    public static final String PAUSED = "PAUSED";
    public static final String UNKNOWN = "UNKNOWN";
    public static final String EXPIRED = "EXPIRED";

    public static final String ACTION_SIGN = "sign";
    public static final String ACTION_POST = "post";
    public static final String ACTION_REPLY = "reply";
    public static final String ACTION_FORWARD = "forward";
    public static final String ACTION_VIEW = "view";

    private Long id;
    private Long planId;
    private Long userId;
    private Long accountId;
    private Long topicId;
    private LocalDate taskDate;
    private String actionType;
    private String status;
    private String content;
    private String postId;
    private String message;
    private Integer ordinal;
    private Integer attempts;
    private LocalDateTime dueAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String accountName;
    private String topicName;
}
