package com.autosign.adapter;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WeiboActivityResultTest {

    @Test
    void factoriesExposeImmutableOutcome() {
        WeiboActivityResult result = WeiboActivityResult.of(
                WeiboActivityResult.Status.UNKNOWN, "无法确认", "Nabc123");

        assertThat(result.getStatus()).isEqualTo(WeiboActivityResult.Status.UNKNOWN);
        assertThat(result.getMessage()).isEqualTo("无法确认");
        assertThat(result.getPostId()).isEqualTo("Nabc123");
        assertThat(WeiboActivityResult.needLogin().getStatus())
                .isEqualTo(WeiboActivityResult.Status.NEED_LOGIN);
        assertThat(WeiboActivityResult.needVerification().getStatus())
                .isEqualTo(WeiboActivityResult.Status.NEED_VERIFICATION);
    }
}
