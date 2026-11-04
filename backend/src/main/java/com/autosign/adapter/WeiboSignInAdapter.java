package com.autosign.adapter;

import com.autosign.entity.PlatformAccount;
import com.autosign.entity.WeiboSuperTopic;
import com.autosign.service.BrowserService;
import com.autosign.service.WeiboTopicService;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitUntilState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Component
public class WeiboSignInAdapter {

    private static final Logger log = LoggerFactory.getLogger(WeiboSignInAdapter.class);

    @Autowired
    private BrowserService browserService;

    @Autowired
    private WeiboTopicService topicService;

    private final Random random = new Random();

    private Browser connectToUserChrome() {
        return browserService.connectToUserChrome();
    }

    private void waitRandom(int minMs, int maxMs) {
        try {
            Thread.sleep(minMs + random.nextInt(maxMs - minMs));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public SignInResult signTopic(PlatformAccount account, WeiboSuperTopic topic) {
        return browserService.executeSerialized(() -> signTopicSerialized(account, topic));
    }

    private SignInResult signTopicSerialized(PlatformAccount account, WeiboSuperTopic topic) {
        BrowserContext context = null;
        Page page = null;
        try {
            log.info("========== sign topic ==========");
            log.info("topicId={}, topicName={}", topic.getId(), topic.getTopicName());

            context = browserService.loadWeiboContext(account);
            if (context == null) {
                return SignInResult.failed("Cannot connect to browser");
            }

            page = context.newPage();
            String url = topic.getTopicUrl();
            log.info("open topic page: url={}", url);
            page.navigate(url, new Page.NavigateOptions().setTimeout(60000));
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
            page.waitForTimeout(5000);

            String content = page.content();
            log.info("page title: {}", page.title());

            if (requiresWeiboLogin(page, content)) {
                log.warn("need login");
                return SignInResult.needRelogin();
            }

            if (content.contains("\u5df2\u7b7e\u5230") || content.contains("\u5df2\u6253\u5361") || content.contains("\u8fde\u7eed\u7b7e\u5230")) {
                log.info("already signed");
                return SignInResult.builder().status(2).successCount(1).failCount(0).message("Already signed").build();
            }

            log.info("find sign button...");
            Locator signBtn = findSignButton(page);
            if (signBtn == null) {
                log.warn("sign button not found");
                return SignInResult.failed("Sign button not found");
            }

            signBtn.scrollIntoViewIfNeeded();
            page.waitForTimeout(500);
            signBtn.click();
            page.waitForTimeout(8000);

            String afterClickContent = page.content();
            if (containsSignedText(afterClickContent)) {
                log.info("sign success");
                return SignInResult.success(1);
            }

            page.reload(new Page.ReloadOptions()
                    .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                    .setTimeout(60000));
            page.waitForTimeout(5000);
            String afterReloadContent = page.content();
            if (containsSignedText(afterReloadContent)) {
                log.info("sign success after reload");
                return SignInResult.success(1);
            }

            log.warn("sign button clicked but cannot confirm result; button text after reload={}",
                    findSignButtonText(page));
            return SignInResult.failed("Sign was clicked but Weibo did not confirm it");
        } catch (Exception e) {
            log.error("topic sign failed", e);
            return SignInResult.failed("Sign failed: " + e.getMessage());
        } finally {
            if (page != null) {
                try { page.close(); } catch (Exception e) { log.debug("close sign page failed", e); }
            }
            if (context != null) {
                try { context.close(); } catch (Exception e) { log.debug("close sign context failed", e); }
            }
        }
    }

    private boolean containsSignedText(String content) {
        return content.contains("\u5df2\u7b7e\u5230") || content.contains("\u5df2\u6253\u5361") ||
                content.contains("\u8fde\u7eed\u7b7e\u5230") || content.contains("\u7b7e\u5230\u5929\u6570") ||
                content.contains("\u7b7e\u5230\u6210\u529f");
    }

    private boolean requiresWeiboLogin(Page page, String content) {
        if (content.contains("Sina Visitor System") || content.contains("\u5fae\u535a\u767b\u5f55")) {
            return true;
        }
        try {
            String visibleText = page.locator("body").innerText().trim();
            return visibleText.isEmpty() || visibleText.startsWith("\u767b\u5f55\n\u6ce8\u518c") ||
                    visibleText.startsWith("\u767b\u5f55\r\n\u6ce8\u518c");
        } catch (Exception e) {
            return true;
        }
    }

    private Locator findSignButton(Page page) {
        List<Locator> candidates = page.locator(
                "button, [role='button'], a[class*='btn'], div[class*='signBtnOverlay']").all();
        for (Locator candidate : candidates) {
            try {
                String text = candidate.innerText().trim();
                if (("\u7b7e\u5230".equals(text) || "\u6253\u5361".equals(text)) &&
                        candidate.isVisible() && candidate.isEnabled()) {
                    return candidate;
                }
            } catch (Exception ignored) {
                // Ignore detached or non-interactive candidates.
            }
        }
        return null;
    }

    private String findSignButtonText(Page page) {
        Locator signButton = findSignButton(page);
        if (signButton == null) return "<not found>";
        try {
            return signButton.innerText().trim();
        } catch (Exception e) {
            return "<unavailable>";
        }
    }

    public SignInResult signAllTopics(PlatformAccount account) {
        return browserService.executeSerialized(() -> signAllTopicsSerialized(account));
    }

    private SignInResult signAllTopicsSerialized(PlatformAccount account) {
        List<WeiboSuperTopic> topics = topicService.listByAccountId(account.getId());
        if (topics.isEmpty()) {
            return SignInResult.failed("No topics configured");
        }

        int successCount = 0;
        int failCount = 0;
        List<SignInResult.TargetResult> details = new ArrayList<>();

        for (WeiboSuperTopic topic : topics) {
            if (topic.getLastSignDate() != null &&
                    topic.getLastSignDate().equals(LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE))) {
                details.add(SignInResult.TargetResult.builder()
                        .targetName(topic.getTopicName()).status(2).message("Already signed today").build());
                successCount++;
                continue;
            }

            SignInResult result = signTopic(account, topic);
            if (result.getStatus() == 2) {
                topicService.updateSignResult(topic.getId(), 1);
                successCount++;
            } else {
                topicService.updateSignResult(topic.getId(), 3);
                failCount++;
            }

            details.add(SignInResult.TargetResult.builder()
                    .targetName(topic.getTopicName()).status(result.getStatus()).message(result.getMessage()).build());

            waitRandom(3000, 8000);
        }

        if (failCount > 0 && successCount > 0) {
            return SignInResult.builder().status(3).successCount(successCount).failCount(failCount).message("Partial success").details(details).build();
        } else if (failCount > 0) {
            return SignInResult.builder().status(4).successCount(0).failCount(failCount).message("Sign failed").details(details).build();
        }
        return SignInResult.builder().status(2).successCount(successCount).failCount(0).message("All success").details(details).build();
    }

    public WeiboPostResult postInTopic(PlatformAccount account, WeiboSuperTopic topic, String content) {
        return browserService.executeSerialized(() -> postInTopicSerialized(account, topic, content));
    }

    private WeiboPostResult postInTopicSerialized(PlatformAccount account, WeiboSuperTopic topic, String content) {
        try {
            // Add topic tag to content
            String topicTag = "#" + topic.getTopicName() + "#";
            String fullContent = content + " " + topicTag;
            
            log.info("========== post in topic ==========");
            log.info("topicId={}, topicName={}, content={}", topic.getId(), topic.getTopicName(), fullContent);

            Browser userBrowser = connectToUserChrome();
            if (userBrowser == null) {
                return WeiboPostResult.failed("Cannot connect to browser");
            }

            try {
                List<BrowserContext> contexts = userBrowser.contexts();
                if (contexts.isEmpty()) {
                    return WeiboPostResult.failed("No browser pages open");
                }

                BrowserContext context = contexts.get(0);
                Page page = context.newPage();

                String url = topic.getTopicUrl();
                log.info("open topic page: url={}", url);
                page.navigate(url, new Page.NavigateOptions().setTimeout(60000));
                page.waitForLoadState(LoadState.DOMCONTENTLOADED);
                page.waitForTimeout(5000);

                String pageContent = page.content();
                log.info("page title: {}", page.title());

                if (pageContent.contains("Sina Visitor System") || pageContent.contains("\u5fae\u535a\u767b\u5f55")) {
                    log.warn("need login");
                    page.close();
                    return WeiboPostResult.failed("Need login");
                }

                // Find and click post button
                log.info("find post button...");
                Locator postBtn = page.getByText("\u53d1\u5e16", new Page.GetByTextOptions().setExact(true)).first();

                if (postBtn.count() == 0) {
                    log.warn("post button not found");
                    page.close();
                    return WeiboPostResult.failed("Post button not found");
                }

                log.info("click post button...");
                postBtn.evaluate("el => el.click()");
                page.waitForTimeout(3000);

                // Find input field
                log.info("find input field...");
                Locator postInput = page.locator("textarea, [contenteditable='true']").first();

                if (postInput.count() == 0) {
                    log.warn("input field not found");
                    page.close();
                    return WeiboPostResult.failed("Input field not found");
                }

                // Input content
                log.info("input post content...");
                postInput.click();
                page.waitForTimeout(500);
                postInput.fill(fullContent);
                page.waitForTimeout(1000);

                // Find submit button
                log.info("find submit button...");
                Locator submitBtn = null;
                String[] submitTexts = {"\u53d1\u5e03", "\u53d1\u9001", "\u63d0\u4ea4", "\u53d1\u5e16"};
                for (String text : submitTexts) {
                    Locator btn = page.getByText(text, new Page.GetByTextOptions().setExact(true)).first();
                    if (btn.count() > 0) {
                        submitBtn = btn;
                        log.info("found submit button: text={}", text);
                        break;
                    }
                }

                if (submitBtn == null || submitBtn.count() == 0) {
                    log.warn("submit button not found, try Enter key...");
                    page.keyboard().press("Enter");
                    page.waitForTimeout(3000);
                    log.info("post completed (Enter key)");
                    page.close();
                    return WeiboPostResult.success();
                }

                // Click submit
                log.info("click submit...");
                submitBtn.evaluate("el => el.click()");
                page.waitForTimeout(5000);

                log.info("post completed");
                page.close();
                return WeiboPostResult.success();
            } catch (Exception e) {
                log.error("post execution failed", e);
                return WeiboPostResult.failed("Post failed: " + e.getMessage());
            }
        } catch (Exception e) {
            log.error("topic post failed", e);
            return WeiboPostResult.failed("Post failed: " + e.getMessage());
        }
    }

    public WeiboPostResult replyToRandomPost(PlatformAccount account, WeiboSuperTopic topic, String replyContent) {
        return browserService.executeSerialized(() -> replyToRandomPostSerialized(account, topic, replyContent));
    }

    private WeiboPostResult replyToRandomPostSerialized(PlatformAccount account, WeiboSuperTopic topic, String replyContent) {
        try {
            log.info("========== reply to post ==========");
            log.info("topicId={}, topicName={}, replyContent={}", topic.getId(), topic.getTopicName(), replyContent);

            Browser userBrowser = connectToUserChrome();
            if (userBrowser == null) {
                return WeiboPostResult.failed("Cannot connect to browser");
            }

            try {
                List<BrowserContext> contexts = userBrowser.contexts();
                if (contexts.isEmpty()) {
                    return WeiboPostResult.failed("No browser pages open");
                }

                BrowserContext context = contexts.get(0);
                Page page = context.newPage();

                String url = topic.getTopicUrl();
                log.info("open topic page: url={}", url);
                page.navigate(url, new Page.NavigateOptions().setTimeout(60000));
                page.waitForLoadState(LoadState.DOMCONTENTLOADED);
                page.waitForTimeout(8000);

                String pageContent = page.content();
                log.info("page title: {}", page.title());

                if (pageContent.contains("Sina Visitor System") || pageContent.contains("\u5fae\u535a\u767b\u5f55")) {
                    log.warn("need login");
                    page.close();
                    return WeiboPostResult.failed("Need login");
                }

                // Click comment icon using dispatchEvent
                log.info("click comment icon...");
                Locator commentIcon = page.locator("i._commentIcon_198pe_122, i[class*='woo-font--comment']").first();
                if (commentIcon.count() > 0) {
                    log.info("found comment icon, use dispatchEvent...");
                    page.evaluate("() => {" +
                        "  const icon = document.querySelector('i._commentIcon_198pe_122, i[class*=\"woo-font--comment\"]');" +
                        "  if (icon) {" +
                        "    const rect = icon.getBoundingClientRect();" +
                        "    const x = rect.left + rect.width / 2;" +
                        "    const y = rect.top + rect.height / 2;" +
                        "    icon.dispatchEvent(new MouseEvent('mousedown', {bubbles: true, cancelable: true, clientX: x, clientY: y}));" +
                        "    icon.dispatchEvent(new MouseEvent('mouseup', {bubbles: true, cancelable: true, clientX: x, clientY: y}));" +
                        "    icon.dispatchEvent(new MouseEvent('click', {bubbles: true, cancelable: true, clientX: x, clientY: y}));" +
                        "  }" +
                        "}");
                    page.waitForTimeout(5000);
                    log.info("dispatchEvent done");
                } else {
                    log.warn("comment icon not found");
                }

                // Find comment input
                log.info("find comment input...");
                Locator replyInput = page.locator("textarea, [contenteditable='true']").first();

                if (replyInput.count() == 0) {
                    log.info("comment input not found, try click again...");
                    page.evaluate("() -> {" +
                        "  const items = document.querySelectorAll('[class*=\"_item_\"]');" +
                        "  for (const item of items) {" +
                        "    const icon = item.querySelector('i[class*=\"comment\"]');" +
                        "    if (icon) {" +
                        "      item.click();" +
                        "      return 'clicked item with comment icon';" +
                        "    }" +
                        "  }" +
                        "  return 'no item found';" +
                        "}");
                    page.waitForTimeout(5000);
                    replyInput = page.locator("textarea, [contenteditable='true']").first();
                }

                if (replyInput.count() == 0) {
                    log.warn("comment input not found");
                    page.close();
                    return WeiboPostResult.failed("Comment input not found");
                }

                // Input reply content
                log.info("input reply content...");
                replyInput.click();
                page.waitForTimeout(500);
                replyInput.fill(replyContent);
                page.waitForTimeout(1000);

                // Find send button
                log.info("find send button...");
                Locator sendBtn = null;
                String[] sendTexts = {"\u53d1\u9001", "\u53d1\u5e03", "\u56de\u590d", "\u63d0\u4ea4", "\u8bc4\u8bba"};
                for (String text : sendTexts) {
                    Locator btn = page.getByText(text, new Page.GetByTextOptions().setExact(true)).first();
                    if (btn.count() > 0) {
                        sendBtn = btn;
                        log.info("found send button: text={}", text);
                        break;
                    }
                }

                if (sendBtn == null || sendBtn.count() == 0) {
                    sendBtn = page.locator("button[type='submit'], [class*='submit'], [class*='send']").first();
                    if (sendBtn.count() > 0) {
                        log.info("found submit button");
                    }
                }

                if (sendBtn == null || sendBtn.count() == 0) {
                    log.warn("send button not found, try Enter key...");
                    page.keyboard().press("Enter");
                    page.waitForTimeout(3000);
                    try {
                        String inputText = replyInput.inputValue();
                        if (inputText.isEmpty()) {
                            log.info("submit via Enter key success");
                            page.close();
                            return WeiboPostResult.success();
                        }
                    } catch (Exception ignored) {}
                    log.warn("Enter key submit failed");
                    page.close();
                    return WeiboPostResult.failed("Send button not found");
                }

                log.info("click send...");
                sendBtn.click();
                page.waitForTimeout(5000);

                log.info("reply completed");
                page.close();
                return WeiboPostResult.success();
            } catch (Exception e) {
                log.error("reply execution failed", e);
                return WeiboPostResult.failed("Reply failed: " + e.getMessage());
            }
        } catch (Exception e) {
            log.error("topic reply failed", e);
            return WeiboPostResult.failed("Reply failed: " + e.getMessage());
        }
    }

    /**
     * Forward/repost a post in the topic
     */
    public WeiboPostResult forwardPost(PlatformAccount account, WeiboSuperTopic topic) {
        return browserService.executeSerialized(() -> forwardPostSerialized(account, topic));
    }

    private WeiboPostResult forwardPostSerialized(PlatformAccount account, WeiboSuperTopic topic) {
        try {
            log.info("========== forward post ==========");
            log.info("topicId={}, topicName={}", topic.getId(), topic.getTopicName());

            Browser userBrowser = connectToUserChrome();
            if (userBrowser == null) {
                return WeiboPostResult.failed("Cannot connect to browser");
            }

            try {
                List<BrowserContext> contexts = userBrowser.contexts();
                if (contexts.isEmpty()) {
                    return WeiboPostResult.failed("No browser pages open");
                }

                BrowserContext context = contexts.get(0);
                Page page = context.newPage();

                String url = topic.getTopicUrl();
                log.info("open topic page: url={}", url);
                page.navigate(url, new Page.NavigateOptions().setTimeout(60000));
                page.waitForLoadState(LoadState.DOMCONTENTLOADED);
                page.waitForTimeout(8000);

                String pageContent = page.content();
                log.info("page title: {}", page.title());

                if (pageContent.contains("Sina Visitor System") || pageContent.contains("\u5fae\u535a\u767b\u5f55")) {
                    log.warn("need login");
                    page.close();
                    return WeiboPostResult.failed("Need login");
                }

                // Find forward/repost button using dispatchEvent
                log.info("find forward button...");
                Locator forwardIcon = page.locator("i._retweetIcon_198pe_119, i[class*='woo-font--retweet']").first();
                if (forwardIcon.count() > 0) {
                    log.info("found forward icon, use dispatchEvent...");
                    page.evaluate("() => {" +
                        "  const icon = document.querySelector('i._retweetIcon_198pe_119, i[class*=\"woo-font--retweet\"]');" +
                        "  if (icon) {" +
                        "    const rect = icon.getBoundingClientRect();" +
                        "    const x = rect.left + rect.width / 2;" +
                        "    const y = rect.top + rect.height / 2;" +
                        "    icon.dispatchEvent(new MouseEvent('mousedown', {bubbles: true, cancelable: true, clientX: x, clientY: y}));" +
                        "    icon.dispatchEvent(new MouseEvent('mouseup', {bubbles: true, cancelable: true, clientX: x, clientY: y}));" +
                        "    icon.dispatchEvent(new MouseEvent('click', {bubbles: true, cancelable: true, clientX: x, clientY: y}));" +
                        "  }" +
                        "}");
                    page.waitForTimeout(5000);
                    log.info("dispatchEvent done");
                } else {
                    log.warn("forward icon not found");
                    page.close();
                    return WeiboPostResult.failed("Forward button not found");
                }

                // Find forward/confirm button
                log.info("find forward confirm button...");
                Locator forwardBtn = null;
                String[] forwardTexts = {"\u8f6c\u53d1", "\u786e\u5b9a", "\u53d1\u9001"};
                for (String text : forwardTexts) {
                    Locator btn = page.getByText(text, new Page.GetByTextOptions().setExact(true)).first();
                    if (btn.count() > 0) {
                        forwardBtn = btn;
                        log.info("found forward button: text={}", text);
                        break;
                    }
                }

                if (forwardBtn == null || forwardBtn.count() == 0) {
                    log.warn("forward confirm button not found, try Enter key...");
                    page.keyboard().press("Enter");
                    page.waitForTimeout(3000);
                    log.info("forward completed (Enter key)");
                    page.close();
                    return WeiboPostResult.success();
                }

                log.info("click forward confirm...");
                forwardBtn.click();
                page.waitForTimeout(5000);

                log.info("forward completed");
                page.close();
                return WeiboPostResult.success();
            } catch (Exception e) {
                log.error("forward execution failed", e);
                return WeiboPostResult.failed("Forward failed: " + e.getMessage());
            }
        } catch (Exception e) {
            log.error("topic forward failed", e);
            return WeiboPostResult.failed("Forward failed: " + e.getMessage());
        }
    }

    /**
     * View/read posts in the topic (browse slowly)
     */
    public WeiboPostResult viewPosts(PlatformAccount account, WeiboSuperTopic topic, int count) {
        return browserService.executeSerialized(() -> viewPostsSerialized(account, topic, count));
    }

    private WeiboPostResult viewPostsSerialized(PlatformAccount account, WeiboSuperTopic topic, int count) {
        try {
            log.info("========== view posts ==========");
            log.info("topicId={}, topicName={}, count={}", topic.getId(), topic.getTopicName(), count);

            Browser userBrowser = connectToUserChrome();
            if (userBrowser == null) {
                return WeiboPostResult.failed("Cannot connect to browser");
            }

            try {
                List<BrowserContext> contexts = userBrowser.contexts();
                if (contexts.isEmpty()) {
                    return WeiboPostResult.failed("No browser pages open");
                }

                BrowserContext context = contexts.get(0);
                Page page = context.newPage();

                String url = topic.getTopicUrl();
                log.info("open topic page: url={}", url);
                page.navigate(url, new Page.NavigateOptions().setTimeout(60000));
                page.waitForLoadState(LoadState.DOMCONTENTLOADED);
                page.waitForTimeout(5000);

                String pageContent = page.content();
                log.info("page title: {}", page.title());

                if (pageContent.contains("Sina Visitor System") || pageContent.contains("\u5fae\u535a\u767b\u5f55")) {
                    log.warn("need login");
                    page.close();
                    return WeiboPostResult.failed("Need login");
                }

                // Find all post links
                log.info("find posts...");
                List<Locator> postLinks = page.locator("a[href*='/detail/'], a[class*='_time_']").all();
                log.info("found {} posts", postLinks.size());

                int viewed = 0;
                for (int i = 0; i < Math.min(count, postLinks.size()); i++) {
                    try {
                        Locator post = postLinks.get(i);
                        if (post.count() > 0) {
                            String postText = post.innerText().trim();
                            log.info("viewing post {}: {}", i + 1, postText.substring(0, Math.min(30, postText.length())));
                            
                            // Scroll to post
                            post.scrollIntoViewIfNeeded();
                            page.waitForTimeout(2000);
                            
                            // Click post to view detail
                            post.click();
                            page.waitForTimeout(5000);
                            
                            // Scroll slowly through the post
                            for (int scroll = 0; scroll < 3; scroll++) {
                                page.mouse().wheel(0, 300 + random.nextInt(200));
                                waitRandom(2000, 4000);
                            }
                            
                            // Go back
                            page.goBack();
                            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
                            page.waitForTimeout(3000);
                            
                            viewed++;
                            log.info("viewed post {}", i + 1);
                            
                            // Wait between posts
                            waitRandom(5000, 10000);
                        }
                    } catch (Exception e) {
                        log.warn("failed to view post {}: {}", i + 1, e.getMessage());
                    }
                }

                log.info("view completed, viewed {} posts", viewed);
                page.close();
                return WeiboPostResult.success();
            } catch (Exception e) {
                log.error("view execution failed", e);
                return WeiboPostResult.failed("View failed: " + e.getMessage());
            }
        } catch (Exception e) {
            log.error("topic view failed", e);
            return WeiboPostResult.failed("View failed: " + e.getMessage());
        }
    }

    public LoginCheckResult checkLogin(PlatformAccount account) {
        return browserService.executeSerialized(() -> checkLoginSerialized(account));
    }

    private LoginCheckResult checkLoginSerialized(PlatformAccount account) {
        BrowserContext context = null;
        Page page = null;
        try {
            log.info("check account login status: accountId={}", account.getId());
            context = browserService.loadWeiboContext(account);
            if (context == null) {
                return LoginCheckResult.failed("Cannot connect to browser");
            }

            page = context.newPage();
            page.navigate("https://weibo.com", new Page.NavigateOptions().setTimeout(30000));
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
            page.waitForTimeout(3000);

            String content = page.content();
            if (!requiresWeiboLogin(page, content)) {
                log.info("account logged in");
                return LoginCheckResult.success();
            }
            return LoginCheckResult.failed("Not logged in");
        } catch (Exception e) {
            log.error("check login status failed", e);
            return LoginCheckResult.failed("Check failed: " + e.getMessage());
        } finally {
            if (page != null) {
                try { page.close(); } catch (Exception e) { log.debug("close login check page failed", e); }
            }
            if (context != null) {
                try { context.close(); } catch (Exception e) { log.debug("close login check context failed", e); }
            }
        }
    }
}
