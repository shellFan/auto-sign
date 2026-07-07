package com.autosign.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("weibo_super_topic")
public class WeiboSuperTopic {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private Long accountId;

    private String topicName;

    private String topicUrl;

    private Integer enableStatus;

    private Integer sort;

    private String remark;

    private String lastSignDate;

    private Integer lastSignStatus;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
