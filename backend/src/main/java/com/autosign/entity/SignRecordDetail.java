package com.autosign.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sign_record_detail")
public class SignRecordDetail {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long recordId;

    private String targetType;

    private String targetName;

    private String targetUrl;

    private Integer status;

    private String message;

    private String screenshotPath;

    private LocalDateTime createTime;
}
