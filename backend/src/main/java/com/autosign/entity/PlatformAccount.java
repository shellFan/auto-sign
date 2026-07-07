package com.autosign.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("platform_account")
public class PlatformAccount {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private String platform;

    private String accountName;

    private String remark;

    private Integer loginStatus;

    private String cookiePath;

    private String browserProfilePath;

    private LocalDateTime lastLoginTime;

    private LocalDateTime lastSignTime;

    private Integer enableStatus;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
