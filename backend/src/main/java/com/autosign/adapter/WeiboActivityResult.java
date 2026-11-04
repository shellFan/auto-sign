package com.autosign.adapter;

/** Outcome of one bounded Weibo activity attempt. */
public final class WeiboActivityResult {

    public enum Status {
        SUCCESS,
        FAILED,
        NEED_LOGIN,
        NEED_VERIFICATION,
        UNKNOWN
    }

    private final Status status;
    private final String message;
    private final String postId;

    private WeiboActivityResult(Status status, String message, String postId) {
        this.status = status;
        this.message = message;
        this.postId = postId;
    }

    public static WeiboActivityResult of(Status status, String message, String postId) {
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        return new WeiboActivityResult(status, message, postId);
    }

    public static WeiboActivityResult success(String message, String postId) {
        return of(Status.SUCCESS, message, postId);
    }

    public static WeiboActivityResult failed(String message) {
        return of(Status.FAILED, message, null);
    }

    public static WeiboActivityResult unknown(String message) {
        return of(Status.UNKNOWN, message, null);
    }

    public static WeiboActivityResult needLogin() {
        return of(Status.NEED_LOGIN, "需要重新登录", null);
    }

    public static WeiboActivityResult needVerification() {
        return of(Status.NEED_VERIFICATION, "需要人工验证", null);
    }

    public Status getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public String getPostId() {
        return postId;
    }
}
