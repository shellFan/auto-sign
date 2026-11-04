package com.autosign.adapter;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WeiboMutationAcknowledgementTest {

    @Test
    void acceptsOnlySubmittedPostToOwnActionRouteWithReturnedId() {
        WeiboMutationAcknowledgement acknowledgement =
                WeiboMutationAcknowledgement.forAction("reply");

        acknowledgement.observe(false, "POST", "https://weibo.com/ajax/comments/create", 200,
                successBody("101"));
        assertThat(acknowledgement.outcome())
                .isEqualTo(WeiboMutationAcknowledgement.Outcome.PENDING);

        acknowledgement.observe(true, "POST", "https://weibo.com/ajax/comments/create", 200,
                successBody("101"));

        assertThat(acknowledgement.outcome())
                .isEqualTo(WeiboMutationAcknowledgement.Outcome.CONFIRMED);
    }

    @Test
    void ignoresGetUnrelatedEndpointAndUntrustedHost() {
        WeiboMutationAcknowledgement acknowledgement =
                WeiboMutationAcknowledgement.forAction("forward");

        acknowledgement.observe(true, "GET", "https://weibo.com/ajax/statuses/repost", 200,
                successBody("102"));
        acknowledgement.observe(true, "POST", "https://weibo.com/ajax/comments/create", 200,
                successBody("102"));
        acknowledgement.observe(true, "POST", "https://evil.example/ajax/statuses/repost", 200,
                successBody("102"));

        assertThat(acknowledgement.outcome())
                .isEqualTo(WeiboMutationAcknowledgement.Outcome.PENDING);
    }

    @Test
    void matchingChallengeProducesCanonicalVerificationOutcome() {
        WeiboMutationAcknowledgement acknowledgement =
                WeiboMutationAcknowledgement.forAction("post");
        Map<String, Object> body = new HashMap<>();
        body.put("ok", 0);
        body.put("msg", "你最近的行为存在异常，请先验证身份后再进行操作");

        acknowledgement.observe(true, "POST", "https://weibo.com/ajax/statuses/update", 200, body);

        assertThat(acknowledgement.outcome())
                .isEqualTo(WeiboMutationAcknowledgement.Outcome.NEED_VERIFICATION);
        assertThat(acknowledgement.canonicalMessage()).isEqualTo("需要人工验证");
    }

    @Test
    void matchingExpiredLoginProducesCanonicalLoginOutcome() {
        WeiboMutationAcknowledgement acknowledgement =
                WeiboMutationAcknowledgement.forAction("reply");
        Map<String, Object> body = new HashMap<>();
        body.put("ok", 0);
        body.put("message", "登录已过期，请重新登录");

        acknowledgement.observe(true, "POST", "https://weibo.com/ajax/comments/create", 200, body);

        assertThat(acknowledgement.outcome())
                .isEqualTo(WeiboMutationAcknowledgement.Outcome.NEED_LOGIN);
        assertThat(acknowledgement.canonicalMessage()).isEqualTo("需要重新登录");
    }

    @Test
    void successWithoutReturnedIdRemainsPending() {
        WeiboMutationAcknowledgement acknowledgement =
                WeiboMutationAcknowledgement.forAction("post");
        Map<String, Object> body = new HashMap<>();
        body.put("ok", 1);
        body.put("data", Collections.singletonMap("text", "created"));

        acknowledgement.observe(true, "POST", "https://weibo.com/ajax/statuses/update", 200, body);

        assertThat(acknowledgement.outcome())
                .isEqualTo(WeiboMutationAcknowledgement.Outcome.PENDING);
    }

    @Test
    void forbiddenChallengeJsonProducesCanonicalVerificationOutcome() {
        WeiboMutationAcknowledgement acknowledgement =
                WeiboMutationAcknowledgement.forAction("forward");
        Map<String, Object> body = new HashMap<>();
        body.put("ok", 0);
        body.put("msg", "安全验证：请输入验证码");

        acknowledgement.observe(true, "POST", "https://weibo.com/ajax/statuses/repost", 403, body);

        assertThat(acknowledgement.outcome())
                .isEqualTo(WeiboMutationAcknowledgement.Outcome.NEED_VERIFICATION);
        assertThat(acknowledgement.canonicalMessage()).isEqualTo("需要人工验证");
    }

    @Test
    void unauthorizedExpiredLoginJsonProducesCanonicalLoginOutcome() {
        WeiboMutationAcknowledgement acknowledgement =
                WeiboMutationAcknowledgement.forAction("reply");
        Map<String, Object> body = new HashMap<>();
        body.put("ok", 0);
        body.put("message", "登录已过期，请重新登录");

        acknowledgement.observe(true, "POST", "https://weibo.com/ajax/comments/create", 401, body);

        assertThat(acknowledgement.outcome())
                .isEqualTo(WeiboMutationAcknowledgement.Outcome.NEED_LOGIN);
        assertThat(acknowledgement.canonicalMessage()).isEqualTo("需要重新登录");
    }

    @Test
    void nonSuccessfulHttpStatusCannotConfirmEvenWithOkAndId() {
        WeiboMutationAcknowledgement acknowledgement =
                WeiboMutationAcknowledgement.forAction("post");

        acknowledgement.observe(true, "POST", "https://weibo.com/ajax/statuses/update", 403,
                successBody("103"));

        assertThat(acknowledgement.outcome())
                .isEqualTo(WeiboMutationAcknowledgement.Outcome.PENDING);
    }

    private static Map<String, Object> successBody(String id) {
        Map<String, Object> body = new HashMap<>();
        body.put("ok", 1);
        body.put("data", Collections.singletonMap("id", id));
        return body;
    }
}
