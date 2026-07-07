package com.autosign.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sign_task")
public class SignTask {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private Long accountId;

    private String platform;

    private String taskName;

    private String cronExpr;

    private Integer enableStatus;

    private Integer retryCount;

    private LocalDateTime lastRunTime;

    private LocalDateTime nextRunTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
