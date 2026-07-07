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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
public class WeiboSignInAdapter {

    private static final Logger log = LoggerFactory.getLogger(WeiboSignInAdapter.class);

    @Autowired
    private BrowserService browserService;

    @Autowired
    private WeiboTopicService topicService;

    private final Random random = new Random();

    private Browser connectToUserChrome() {
        try {
            return browserService.getPlaywright().chromium().connectOverCDP("http://localhost:9222");
        } catch (Exception e) {
            log.warn("无法连接到用户 Chrome: {}", e.getMessage());
            return null;
        }
    }

    public SignInResult signTopic(PlatformAccount account, WeiboSuperTopic topic) {
        try {
            log.info("========== 开始签到超话 ==========");
            log.info("topicId={}, topicName={}", topic.getId(), topic.getTopicName());

            Browser userBrowser = connectToUserChrome();
            if (userBrowser == null) {
                return SignInResult.failed("无法连接到浏览器");
            }

            try {
                List<BrowserContext> contexts = userBrowser.contexts();
                if (contexts.isEmpty()) {
                    return SignInResult.failed("浏览器没有打开的页面");
                }

                BrowserContext context = contexts.get(0);
                Page page = context.newPage();

                String url = topic.getTopicUrl();
                log.info("打开超话页面: url={}", url);
                page.navigate(url, new Page.NavigateOptions().setTimeout(60000));
                page.waitForLoadState(LoadState.DOMCONTENTLOADED);
                page.waitForTimeout(3000);

                String content = page.content();
                log.info("页面标题: {}", page.title());

                if (content.contains("Sina Visitor System") || content.contains("微博登录")) {
                    log.warn("需要登录微博");
                    page.close();
                    return SignInResult.needRelogin();
                }

                log.info("检查是否已签到...");
                if (content.contains("已签到") || content.contains("已打卡") || content.contains("连续签到")) {
                    log.info("已签到");
                    page.close();
                    return SignInResult.builder().status(2).successCount(1).failCount(0).message("已签到").build();
                }

                log.info("查找签到按钮...");
                Locator signBtn = findSignButton(page);

                if (signBtn == null) {
                    log.warn("未找到签到按钮");
                    page.close();
                    return SignInResult.failed("未找到签到按钮");
                }

                log.info("点击签到按钮...");
                signBtn.scrollIntoViewIfNeeded();
                page.waitForTimeout(500);
                signBtn.click();

                log.info("等待页面更新...");
                page.waitForTimeout(5000);

                log.info("重新加载页面...");
                page.reload();
                page.waitForLoadState(LoadState.DOMCONTENTLOADED);
                page.waitForTimeout(3000);

                String newContent = page.content();
                log.info("检查签到结果...");

                if (newContent.contains("已签到") || newContent.contains("已打卡") || 
                    newContent.contains("连续签到") || newContent.contains("签到天数")) {
                    log.info("签到成功");
                    page.close();
                    return SignInResult.success(1);
                }

                log.warn("签到后未检测到成功状态");
                page.close();
                return SignInResult.failed("签到后未检测到成功状态");
            } catch (Exception e) {
                log.error("签到执行失败", e);
                return SignInResult.failed("签到失败: " + e.getMessage());
            }
        } catch (Exception e) {
            log.error("微博超话签到失败", e);
            return SignInResult.failed("签到失败: " + e.getMessage());
        }
    }

    private Locator findSignButton(Page page) {
        List<Locator> allButtons = page.locator("button, [role='button'], a[class*='btn']").all();
        for (Locator btn : allButtons) {
            try {
                String text = btn.innerText().trim();
                if (text.equals("签到") || text.equals("打卡")) {
                    return btn;
                }
            } catch (Exception e) {
                // 忽略
            }
        }
        return null;
    }

    /**
     * 在超话发帖 - 使用用户已打开的浏览器
     */
    public WeiboPostResult postInTopic(PlatformAccount account, WeiboSuperTopic topic, String content) {
        try {
            log.info("========== 开始发帖 ==========");
            log.info("topicId={}, topicName={}, content={}", topic.getId(), topic.getTopicName(), content);

            Browser userBrowser = connectToUserChrome();
            if (userBrowser == null) {
                return WeiboPostResult.failed("无法连接到浏览器");
            }

            try {
                List<BrowserContext> contexts = userBrowser.contexts();
                if (contexts.isEmpty()) {
                    return WeiboPostResult.failed("浏览器没有打开的页面");
                }

                // 使用第一个上下文
                BrowserContext context = contexts.get(0);

                // 查找用户已打开的超话页面
                Page targetPage = null;
                for (Page p : context.pages()) {
                    if (p.url().contains("weibo.com/p/") || p.url().contains("weibo.com/home")) {
                        targetPage = p;
                        break;
                    }
                }

                if (targetPage == null) {
                    // 如果没有找到已打开的页面，创建新页面
                    targetPage = context.newPage();
                    String url = topic.getTopicUrl();
                    log.info("打开超话页面: url={}", url);
                    targetPage.navigate(url, new Page.NavigateOptions().setTimeout(60000));
                    targetPage.waitForLoadState(LoadState.DOMCONTENTLOADED);
                    targetPage.waitForTimeout(5000);
                }

                log.info("使用页面: url={}", targetPage.url());

                // 等待页面加载完成
                targetPage.waitForTimeout(2000);

                // 查找并点击"发帖"按钮
                log.info("查找发帖按钮...");
                Locator postBtn = null;

                // 遍历所有按钮和可点击元素
                List<Locator> allElements = targetPage.locator("button, [role='button'], a, span, div").all();
                for (Locator elem : allElements) {
                    try {
                        String text = elem.innerText().trim();
                        if (text.equals("发帖") || text.equals("发微博") || text.equals("写微博")) {
                            postBtn = elem;
                            log.info("找到发帖按钮: text={}", text);
                            break;
                        }
                    } catch (Exception e) {
                        // 忽略
                    }
                }

                if (postBtn == null || postBtn.count() == 0) {
                    log.warn("未找到发帖按钮");
                    return WeiboPostResult.failed("未找到发帖按钮");
                }

                // 点击发帖按钮
                log.info("点击发帖按钮...");
                postBtn.click();
                targetPage.waitForTimeout(5000);

                // 查找输入框
                log.info("查找输入框...");
                Locator postInput = null;

                String[] selectors = {
                    "textarea",
                    "[contenteditable='true']",
                    "[placeholder*='说点什么']",
                    "[placeholder*='分享']"
                };

                for (String selector : selectors) {
                    postInput = targetPage.locator(selector).first();
                    if (postInput.count() > 0) {
                        log.info("找到输入框: selector={}", selector);
                        break;
                    }
                }

                if (postInput == null || postInput.count() == 0) {
                    log.warn("未找到发帖输入框");
                    return WeiboPostResult.failed("未找到发帖输入框");
                }

                // 输入内容
                log.info("输入发帖内容...");
                postInput.click();
                targetPage.waitForTimeout(500);
                postInput.fill(content);
                targetPage.waitForTimeout(1000);

                // 查找发布按钮
                log.info("查找发布按钮...");
                Locator submitBtn = findSubmitButton(targetPage);

                if (submitBtn == null) {
                    log.warn("未找到发布按钮");
                    return WeiboPostResult.failed("未找到发布按钮");
                }

                // 点击发布
                log.info("点击发布...");
                submitBtn.click();
                targetPage.waitForTimeout(5000);

                log.info("发帖完成");
                return WeiboPostResult.success();
            } catch (Exception e) {
                log.error("发帖执行失败", e);
                return WeiboPostResult.failed("发帖失败: " + e.getMessage());
            }
        } catch (Exception e) {
            log.error("微博发帖失败", e);
            return WeiboPostResult.failed("发帖失败: " + e.getMessage());
        }
    }

    private Locator findSubmitButton(Page page) {
        List<Locator> allBtns = page.locator("button, [role='button']").all();
        for (Locator btn : allBtns) {
            try {
                String text = btn.innerText().trim();
                if (text.contains("发布") || text.contains("发送") || text.contains("发帖")) {
                    return btn;
                }
            } catch (Exception e) {
                // 忽略
            }
        }
        return null;
    }

    /**
     * 随机回复超话下的帖子
     */
    public WeiboPostResult replyToRandomPost(PlatformAccount account, WeiboSuperTopic topic, String replyContent) {
        try {
            log.info("========== 开始回复帖子 ==========");
            log.info("topicId={}, topicName={}, replyContent={}", topic.getId(), topic.getTopicName(), replyContent);

            Browser userBrowser = connectToUserChrome();
            if (userBrowser == null) {
                return WeiboPostResult.failed("无法连接到浏览器");
            }

            try {
                List<BrowserContext> contexts = userBrowser.contexts();
                if (contexts.isEmpty()) {
                    return WeiboPostResult.failed("浏览器没有打开的页面");
                }

                BrowserContext context = contexts.get(0);
                Page page = context.newPage();

                String url = topic.getTopicUrl();
                log.info("打开超话页面: url={}", url);
                page.navigate(url, new Page.NavigateOptions().setTimeout(60000));
                page.waitForLoadState(LoadState.DOMCONTENTLOADED);
                page.waitForTimeout(5000);

                String pageContent = page.content();

                if (pageContent.contains("Sina Visitor System") || pageContent.contains("微博登录")) {
                    log.warn("需要登录微博");
                    page.close();
                    return WeiboPostResult.failed("需要重新登录");
                }

                // 查找评论按钮
                log.info("查找评论按钮...");
                Locator commentBtn = null;

                List<Locator> allButtons = page.locator("button, [role='button'], a[class*='btn'], span[class*='btn']").all();
                for (Locator btn : allButtons) {
                    try {
                        String text = btn.innerText().trim();
                        if (text.contains("评论") || text.contains("回复")) {
                            commentBtn = btn;
                            log.info("找到评论按钮: text={}", text);
                            break;
                        }
                    } catch (Exception e) {
                        // 忽略
                    }
                }

                if (commentBtn == null) {
                    log.warn("未找到评论按钮");
                    page.close();
                    return WeiboPostResult.failed("未找到评论按钮");
                }

                // 点击评论按钮
                log.info("点击评论按钮...");
                commentBtn.click();
                page.waitForTimeout(2000);

                // 查找评论输入框
                log.info("查找评论输入框...");
                Locator replyInput = page.locator("textarea").first();
                if (replyInput.count() == 0) {
                    replyInput = page.locator("[contenteditable='true']").first();
                }

                if (replyInput.count() == 0) {
                    log.warn("未找到评论输入框");
                    page.close();
                    return WeiboPostResult.failed("未找到评论输入框");
                }

                // 输入回复内容
                log.info("输入回复内容...");
                replyInput.click();
                page.waitForTimeout(500);
                replyInput.fill(replyContent);
                page.waitForTimeout(1000);

                // 查找发送按钮
                log.info("查找发送按钮...");
                Locator sendBtn = findSubmitButton(page);

                if (sendBtn == null) {
                    log.warn("未找到发送按钮");
                    page.close();
                    return WeiboPostResult.failed("未找到发送按钮");
                }

                // 点击发送
                log.info("点击发送...");
                sendBtn.click();
                page.waitForTimeout(5000);

                log.info("回复完成");
                page.close();
                return WeiboPostResult.success();
            } catch (Exception e) {
                log.error("回复执行失败", e);
                return WeiboPostResult.failed("回复失败: " + e.getMessage());
            }
        } catch (Exception e) {
            log.error("微博回复帖子失败", e);
            return WeiboPostResult.failed("回复失败: " + e.getMessage());
        }
    }

    public SignInResult signAllTopics(PlatformAccount account) {
        List<WeiboSuperTopic> topics = topicService.listByAccountId(account.getId());
        if (topics.isEmpty()) {
            return SignInResult.failed("没有配置超话");
        }

        int successCount = 0;
        int failCount = 0;
        List<SignInResult.TargetResult> details = new ArrayList<>();

        for (WeiboSuperTopic topic : topics) {
            if (topic.getLastSignDate() != null &&
                    topic.getLastSignDate().equals(LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE))) {
                details.add(SignInResult.TargetResult.builder()
                        .targetName(topic.getTopicName()).status(2).message("今日已签到").build());
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

            try {
                Thread.sleep(3000 + random.nextInt(5000));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        if (failCount > 0 && successCount > 0) {
            return SignInResult.builder().status(3).successCount(successCount).failCount(failCount).message("部分成功").details(details).build();
        } else if (failCount > 0) {
            return SignInResult.builder().status(4).successCount(0).failCount(failCount).message("签到失败").details(details).build();
        }
        return SignInResult.builder().status(2).successCount(successCount).failCount(0).message("全部成功").details(details).build();
    }

    public LoginCheckResult checkLogin(PlatformAccount account) {
        try {
            log.info("检查账号登录状态: accountId={}", account.getId());
            Browser userBrowser = connectToUserChrome();
            if (userBrowser != null) {
                try {
                    List<BrowserContext> contexts = userBrowser.contexts();
                    if (!contexts.isEmpty()) {
                        BrowserContext context = contexts.get(0);
                        Page page = context.newPage();
                        page.navigate("https://weibo.com", new Page.NavigateOptions().setTimeout(30000));
                        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
                        page.waitForTimeout(3000);

                        String content = page.content();
                        if (!content.contains("Sina Visitor System") && !content.contains("微博登录")) {
                            log.info("账号已登录");
                            page.close();
                            return LoginCheckResult.success();
                        }
                        page.close();
                    }
                } catch (Exception e) {
                    log.error("检查失败", e);
                }
            }
            return LoginCheckResult.failed("未登录");
        } catch (Exception e) {
            log.error("检查微博登录状态失败", e);
            return LoginCheckResult.failed("检查失败: " + e.getMessage());
        }
    }
}
