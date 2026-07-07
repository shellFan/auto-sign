package com.autosign.adapter;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginCheckResult {

    private boolean loggedIn;

    private String message;

    private String screenshotPath;

    public static LoginCheckResult success() {
        return LoginCheckResult.builder().loggedIn(true).message("已登录").build();
    }

    public static LoginCheckResult failed(String message) {
        return LoginCheckResult.builder().loggedIn(false).message(message).build();
    }

    public static LoginCheckResult needVerification(String message, String screenshotPath) {
        return LoginCheckResult.builder().loggedIn(false).message(message).screenshotPath(screenshotPath).build();
    }
}
