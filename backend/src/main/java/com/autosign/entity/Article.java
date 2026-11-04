package com.autosign.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("article")
public class Article {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private String title;
    private String content;
    private String summary;
    private String coverImage;
    private Integer status; // 0草稿 1已发布 2已推送公众号
    private String wechatMediaId;
    private LocalDateTime publishTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
