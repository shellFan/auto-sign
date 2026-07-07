package com.autosign.adapter;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WeiboPostResult {

    private boolean success;

    private String message;

    private String screenshotPath;

    public static WeiboPostResult success() {
        return WeiboPostResult.builder()
                .success(true)
                .message("发帖成功")
                .build();
    }

    public static WeiboPostResult success(String screenshotPath) {
        return WeiboPostResult.builder()
                .success(true)
                .message("发帖成功")
                .screenshotPath(screenshotPath)
                .build();
    }

    public static WeiboPostResult failed(String message) {
        return WeiboPostResult.builder()
                .success(false)
                .message(message)
                .build();
    }

    public static WeiboPostResult needVerification() {
        return WeiboPostResult.builder()
                .success(false)
                .message("需要人工验证")
                .build();
    }
}
