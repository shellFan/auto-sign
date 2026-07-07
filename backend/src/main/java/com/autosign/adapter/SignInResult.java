package com.autosign.adapter;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class SignInResult {

    private Integer status;

    private Integer successCount;

    private Integer failCount;

    private String message;

    private List<TargetResult> details;

    @Data
    @Builder
    public static class TargetResult {
        private String targetName;
        private String targetUrl;
        private Integer status;
        private String message;
        private String screenshotPath;
    }

    public static SignInResult success(Integer successCount) {
        return SignInResult.builder()
                .status(2)
                .successCount(successCount)
                .failCount(0)
                .message("签到成功")
                .build();
    }

    public static SignInResult partialSuccess(Integer successCount, Integer failCount) {
        return SignInResult.builder()
                .status(3)
                .successCount(successCount)
                .failCount(failCount)
                .message("部分成功")
                .build();
    }

    public static SignInResult failed(String message) {
        return SignInResult.builder()
                .status(4)
                .successCount(0)
                .failCount(1)
                .message(message)
                .build();
    }

    public static SignInResult needRelogin() {
        return SignInResult.builder()
                .status(5)
                .successCount(0)
                .failCount(0)
                .message("需要重新登录")
                .build();
    }

    public static SignInResult needVerification() {
        return SignInResult.builder()
                .status(6)
                .successCount(0)
                .failCount(0)
                .message("需要人工验证")
                .build();
    }
}
