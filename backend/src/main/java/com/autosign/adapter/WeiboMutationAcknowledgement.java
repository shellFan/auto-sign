package com.autosign.adapter;

import java.net.URI;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/** Tracks a narrowly correlated acknowledgement for one Weibo mutation. */
final class WeiboMutationAcknowledgement {

    enum Outcome {
        PENDING,
        CONFIRMED,
        NEED_VERIFICATION,
        NEED_LOGIN
    }

    private final String expectedPath;
    private final AtomicReference<Outcome> outcome = new AtomicReference<>(Outcome.PENDING);

    private WeiboMutationAcknowledgement(String expectedPath) {
        this.expectedPath = expectedPath;
    }

    static WeiboMutationAcknowledgement forAction(String action) {
        if ("post".equals(action)) {
            return new WeiboMutationAcknowledgement("/ajax/statuses/update");
        }
        if ("reply".equals(action)) {
            return new WeiboMutationAcknowledgement("/ajax/comments/create");
        }
        if ("forward".equals(action)) {
            return new WeiboMutationAcknowledgement("/ajax/statuses/repost");
        }
        throw new IllegalArgumentException("Unsupported mutation action: " + action);
    }

    void observe(boolean submitted, String method, String url, int status, Object json) {
        if (!shouldParse(submitted, method, url, status) || !(json instanceof Map)) {
            return;
        }
        Map<?, ?> body = (Map<?, ?>) json;
        Object ok = body.get("ok");
        if (status >= 200 && status < 300 && isOne(ok) && hasReturnedId(body.get("data"))) {
            outcome.compareAndSet(Outcome.PENDING, Outcome.CONFIRMED);
            return;
        }
        if (!isZero(ok)) return;
        String message = message(body);
        if (isVerificationMessage(message)) {
            outcome.compareAndSet(Outcome.PENDING, Outcome.NEED_VERIFICATION);
        } else if (isLoginMessage(message)) {
            outcome.compareAndSet(Outcome.PENDING, Outcome.NEED_LOGIN);
        }
    }

    Outcome outcome() {
        return outcome.get();
    }

    boolean shouldParse(boolean submitted, String method, String url, int status) {
        return submitted && outcome.get() == Outcome.PENDING && "POST".equalsIgnoreCase(method) &&
                status >= 200 && status < 600 && matchesExpectedEndpoint(url);
    }

    String canonicalMessage() {
        Outcome current = outcome();
        if (current == Outcome.NEED_VERIFICATION) return "需要人工验证";
        if (current == Outcome.NEED_LOGIN) return "需要重新登录";
        return null;
    }

    private boolean matchesExpectedEndpoint(String value) {
        if (value == null) return false;
        try {
            URI uri = URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme()) && trustedHost(uri.getHost()) &&
                    expectedPath.equals(uri.getPath());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static boolean trustedHost(String host) {
        if (host == null) return false;
        String normalized = host.toLowerCase(Locale.ROOT);
        return "weibo.com".equals(normalized) || normalized.endsWith(".weibo.com") ||
                "weibo.cn".equals(normalized) || normalized.endsWith(".weibo.cn");
    }

    private static boolean hasReturnedId(Object data) {
        if (!(data instanceof Map)) return false;
        Map<?, ?> values = (Map<?, ?>) data;
        return nonEmpty(values.get("id")) || nonEmpty(values.get("idstr")) ||
                nonEmpty(values.get("mid"));
    }

    private static boolean nonEmpty(Object value) {
        return value != null && !String.valueOf(value).trim().isEmpty();
    }

    private static boolean isOne(Object value) {
        return numberEquals(value, 1);
    }

    private static boolean isZero(Object value) {
        return numberEquals(value, 0);
    }

    private static boolean numberEquals(Object value, int expected) {
        if (value instanceof Number) {
            return Double.compare(((Number) value).doubleValue(), expected) == 0;
        }
        return String.valueOf(expected).equals(value);
    }

    private static String message(Map<?, ?> body) {
        Object value = body.get("msg");
        if (value == null) value = body.get("message");
        return value == null ? "" : String.valueOf(value);
    }

    private static boolean isVerificationMessage(String message) {
        return message.contains("请先验证身份") || message.contains("安全验证") ||
                message.contains("验证码");
    }

    private static boolean isLoginMessage(String message) {
        return message.contains("登录已过期") || message.contains("登录过期") ||
                message.contains("登录失效") || message.contains("请先登录") ||
                message.contains("需要重新登录") || message.contains("请重新登录");
    }
}
