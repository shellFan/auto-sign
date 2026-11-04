package com.autosign.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.autosign.entity.PlatformAccount;
import com.autosign.entity.WeiboSuperTopic;
import com.autosign.service.BrowserService;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Request;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.LoadState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/** Executes one conservative, bounded activity against an isolated account context. */
@Component
public class WeiboActivityAdapter {

    private static final Logger log = LoggerFactory.getLogger(WeiboActivityAdapter.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final double DEFAULT_TIMEOUT_MS = 15_000;
    private static final double NAVIGATION_TIMEOUT_MS = 45_000;
    private static final double UI_WAIT_MS = 8_000;
    private static final double POLL_MS = 250;
    private static final Pattern POST_ID = Pattern.compile("[A-Za-z0-9]{5,32}");
    private static final Pattern NUMERIC_USER_ID = Pattern.compile("[0-9]{3,24}");
    private static final Pattern NUMERIC_POST_ID = Pattern.compile("[0-9]{5,32}");
    private static final String BASE62 = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

    static final String POST_COMPOSER_SELECTOR =
            ".woo-modal-main textarea[placeholder='掀指一算，此贴必火'], " +
            ".woo-modal-main .wbpro-form textarea, " +
            "div[role='dialog'] textarea, div[role='dialog'] [contenteditable='true'], " +
            "textarea[placeholder*='分享新鲜事'], textarea[placeholder*='发微博'], " +
            "[contenteditable='true'][aria-label*='发微博'], [class*='composer'] [contenteditable='true']";
    static final String POST_SUBMIT_SELECTOR =
            "button:has-text('发布'), button:has-text('发送'), button:has-text('发帖')";

    private static final String SUCCESS_NOTICE_SELECTOR =
            "[role='alert'], [class*='toast'], [class*='Toast'], [class*='message'], [class*='Message']";
    private static final String CAPTCHA_SELECTOR =
            "iframe[src*='captcha'], iframe[src*='verify'], [class*='captcha'], [id*='captcha'], " +
            "[class*='verify'][class*='dialog'], [class*='security'][class*='verify']";
    private static final String LOGGED_IN_MARKER_SELECTOR =
            "a[class*='_alink_'][href^='/u/'], a[href*='/profile'] [class*='avatar'], a[href*='/logout']";
    private static final String FEED_SELECTOR =
            "article[class*='woo-panel-main']";
    private static final String FEED_LINK_SELECTOR =
            "article[class*='woo-panel-main'] a[href]";
    private static final String DETAIL_POST_SELECTOR =
            "article[class*='woo-panel-main']";
    private static final String SIGN_BUTTON_SELECTOR = "[class*='signBtnOverlay']";
    private static final String COMMENT_ICON_SELECTOR =
            "i.woo-font--comment[title='评论'], i[class*='comment'][title='评论']";
    private static final String RETWEET_ICON_SELECTOR =
            "i.woo-font--retweet[title='转发'], i[class*='retweet'][title='转发']";

    private final BrowserService browserService;
    private final WeiboSignInAdapter signInAdapter;
    private final Random random;

    @Autowired
    public WeiboActivityAdapter(BrowserService browserService, WeiboSignInAdapter signInAdapter) {
        this(browserService, signInAdapter, new Random());
    }

    WeiboActivityAdapter(BrowserService browserService, WeiboSignInAdapter signInAdapter, Random random) {
        this.browserService = browserService;
        this.signInAdapter = signInAdapter;
        this.random = random;
    }

    public WeiboActivityResult execute(PlatformAccount account, WeiboSuperTopic topic,
                                       String action, String content, String reservedPostId,
                                       Set<String> excludedPostIds,
                                       Consumer<String> reservePostId) {
        if (account == null || topic == null) {
            return WeiboActivityResult.failed("账号和超话不能为空");
        }
        String normalizedAction = action == null ? "" : action.trim().toLowerCase(Locale.ROOT);
        if (!isSupportedAction(normalizedAction)) {
            return WeiboActivityResult.failed("不支持的活动类型: " + normalizedAction);
        }
        if (topic.getTopicUrl() == null || !isAllowedWeiboUrl(topic.getTopicUrl())) {
            return WeiboActivityResult.failed("超话地址不是受信任的微博地址");
        }

        Set<String> exclusions = excludedPostIds == null
                ? Collections.emptySet() : new LinkedHashSet<>(excludedPostIds);
        Consumer<String> reservation = reservePostId == null ? id -> { } : reservePostId;
        String reserved = null;
        if (reservedPostId != null && !reservedPostId.trim().isEmpty()) {
            reserved = normalizePostId(reservedPostId);
            if (reserved == null) {
                return WeiboActivityResult.failed("保留的微博 ID 无效");
            }
            // A retry must reserve its stable target before opening or reading it.
            reservation.accept(reserved);
        }

        final String target = reserved;
        return browserService.executeSerialized(() -> executeSerialized(
                account, topic, normalizedAction, content, target, exclusions, reservation));
    }

    public WeiboActivityResult checkLogin(PlatformAccount account) {
        if (account == null) {
            return WeiboActivityResult.failed("账号不能为空");
        }
        return browserService.executeSerialized(() -> checkLoginSerialized(account));
    }

    private WeiboActivityResult executeSerialized(PlatformAccount account, WeiboSuperTopic topic,
                                                   String action, String content, String reservedPostId,
                                                   Set<String> exclusions,
                                                   Consumer<String> reservePostId) {
        BrowserContext context = null;
        Page page = null;
        Attempt attempt = new Attempt(reservedPostId);
        try {
            if ("sign".equals(action)) {
                return signOnTopicPage(account, topic, attempt);
            }
            context = browserService.loadWeiboContext(account);
            if (context == null) {
                return WeiboActivityResult.failed("无法创建微博账号浏览器环境");
            }
            context.setDefaultTimeout(DEFAULT_TIMEOUT_MS);
            context.setDefaultNavigationTimeout(NAVIGATION_TIMEOUT_MS);
            page = context.newPage();
            page.setDefaultTimeout(DEFAULT_TIMEOUT_MS);
            page.setDefaultNavigationTimeout(NAVIGATION_TIMEOUT_MS);

            if ("post".equals(action)) {
                navigate(page, topic.getTopicUrl());
                WeiboActivityResult gate = gate(page, attempt);
                return gate == null ? post(page, topic, content, attempt) : gate;
            }

            if (attempt.postId == null) {
                navigate(page, topic.getTopicUrl());
                WeiboActivityResult gate = gate(page, attempt);
                if (gate != null) return gate;
                if (waitForVisible(page, page.locator(FEED_SELECTOR), UI_WAIT_MS) == null) {
                    return WeiboActivityResult.failed("超话微博列表未能加载");
                }
                attempt.postId = discoverPostId(page, exclusions);
                if (attempt.postId == null) {
                    return WeiboActivityResult.failed("没有找到尚未使用的微博目标");
                }
                try {
                    reservePostId.accept(attempt.postId);
                } catch (RuntimeException e) {
                    throw new ReservationFailure(e);
                }
            }

            navigate(page, detailUrl(attempt.postId));
            WeiboActivityResult gate = gate(page, attempt);
            if (gate != null) return gate;
            if (!isLoadedPostDetail(page, attempt.postId)) {
                return WeiboActivityResult.failed("目标微博详情未能可靠加载");
            }
            switch (action) {
                case "view":
                    return view(page, attempt);
                case "reply":
                    return reply(page, content, attempt);
                case "forward":
                    return forward(page, content, attempt);
                default:
                    return WeiboActivityResult.failed("不支持的活动类型: " + action);
            }
        } catch (ReservationFailure e) {
            throw e.original;
        } catch (Exception e) {
            log.warn("微博活动执行失败: action={}, submitted={}, reason={}",
                    action, attempt.submitted, safeMessage(e));
            if (attempt.submitted) {
                return unknownAfterSubmit("提交后连接异常，结果无法确认", attempt);
            }
            return WeiboActivityResult.failed("活动未提交: " + safeMessage(e));
        } finally {
            close(page, context);
        }
    }

    private WeiboActivityResult checkLoginSerialized(PlatformAccount account) {
        BrowserContext context = null;
        Page page = null;
        try {
            context = browserService.loadWeiboContext(account);
            if (context == null) {
                return WeiboActivityResult.failed("无法创建微博账号浏览器环境");
            }
            context.setDefaultTimeout(DEFAULT_TIMEOUT_MS);
            context.setDefaultNavigationTimeout(NAVIGATION_TIMEOUT_MS);
            page = context.newPage();
            page.setDefaultTimeout(DEFAULT_TIMEOUT_MS);
            page.setDefaultNavigationTimeout(NAVIGATION_TIMEOUT_MS);
            navigate(page, "https://weibo.com/");
            if (hasCaptcha(page)) return WeiboActivityResult.needVerification();
            if (needsLogin(page)) return WeiboActivityResult.needLogin();
            Locator loggedInMarker = waitForVisible(page, page.locator(LOGGED_IN_MARKER_SELECTOR), 5_000);
            if (hasCaptcha(page)) return WeiboActivityResult.needVerification();
            if (needsLogin(page)) return WeiboActivityResult.needLogin();
            String visibleText = visibleBodyText(page).trim();
            if (loggedInMarker == null || visibleText.isEmpty() || containsVisibleError(visibleText)) {
                return WeiboActivityResult.failed("无法确认微博登录状态");
            }
            return WeiboActivityResult.success("登录状态有效", null);
        } catch (Exception e) {
            log.warn("检查微博登录状态失败: {}", safeMessage(e));
            return WeiboActivityResult.failed("登录状态检查失败: " + safeMessage(e));
        } finally {
            close(page, context);
        }
    }

    private WeiboActivityResult signOnTopicPage(PlatformAccount account,
                                                WeiboSuperTopic topic,
                                                Attempt attempt) {
        BrowserContext context = null;
        Page page = null;
        try {
            context = browserService.loadWeiboContext(account);
            if (context == null) {
                return WeiboActivityResult.failed("无法创建微博账号浏览器环境");
            }
            context.setDefaultTimeout(DEFAULT_TIMEOUT_MS);
            context.setDefaultNavigationTimeout(NAVIGATION_TIMEOUT_MS);
            page = context.newPage();
            page.setDefaultTimeout(DEFAULT_TIMEOUT_MS);
            page.setDefaultNavigationTimeout(NAVIGATION_TIMEOUT_MS);
            navigate(page, topic.getTopicUrl());
            WeiboActivityResult gate = gate(page, attempt);
            if (gate != null) return gate;
            Locator signWidget = waitForVisible(page, page.locator(SIGN_BUTTON_SELECTOR), UI_WAIT_MS);
            if (signWidget == null) {
                return WeiboActivityResult.failed("未找到当前超话的签到按钮");
            }
            if (isConfirmedSignWidget(signWidget)) {
                return WeiboActivityResult.success("今日已签到", null);
            }
            Locator signButton = firstVisibleEnabled(signWidget);
            if (signButton == null) {
                return WeiboActivityResult.failed("未找到当前超话的签到按钮");
            }
            Set<String> notices = successNotices(page);
            attempt.submitted = true;
            signButton.click();
            int checks = (int) Math.ceil(UI_WAIT_MS / POLL_MS);
            for (int i = 0; i < checks; i++) {
                if (hasCaptcha(page)) {
                    return WeiboActivityResult.unknown("需要人工验证");
                }
                if (needsLogin(page)) {
                    return WeiboActivityResult.unknown("需要重新登录");
                }
                if (hasConfirmedSignState(page) || hasNewSuccessNotice(page, notices)) {
                    return WeiboActivityResult.success("签到成功", null);
                }
                if (i + 1 < checks) page.waitForTimeout(POLL_MS);
            }
            return WeiboActivityResult.unknown("签到已点击，但微博未明确确认结果");
        } finally {
            close(page, context);
        }
    }

    private WeiboActivityResult post(Page page, WeiboSuperTopic topic, String content, Attempt attempt) {
        WeiboMutationAcknowledgement acknowledgement = observeMutation(page, "post", attempt);
        String body = content == null ? "" : content.trim();
        String tag = "#" + topic.getTopicName() + "[超话]#";
        if (!body.contains(tag)) {
            body = tag + (body.isEmpty() ? "" : " " + body);
        }
        Locator composer = waitForVisible(page, page.locator(POST_COMPOSER_SELECTOR), 2_000);
        if (composer == null) {
            Locator trigger = firstVisibleEnabled(page.getByText(
                    "发帖", new Page.GetByTextOptions().setExact(true)));
            if (trigger == null) {
                return WeiboActivityResult.failed("未找到超话发帖入口");
            }
            trigger.click();
            composer = waitForVisible(page, page.locator(POST_COMPOSER_SELECTOR), UI_WAIT_MS);
        }
        if (composer == null) {
            return WeiboActivityResult.failed("未找到超话发帖输入框");
        }
        composer.fill(body);
        Locator scope = firstVisible(composer.locator(
                "xpath=ancestor-or-self::*[self::form or @role='dialog' or contains(@class,'composer') " +
                        "or contains(@class,'post') or contains(@class,'woo-modal-main') " +
                        "or contains(@class,'wbpro-layer')][1]"));
        if (scope == null) {
            return WeiboActivityResult.failed("无法确定超话发帖框范围");
        }
        Locator submit = waitForVisibleEnabled(page, scope.locator(POST_SUBMIT_SELECTOR), UI_WAIT_MS);
        if (submit == null) {
            return WeiboActivityResult.failed("未找到当前发帖框内的发布按钮");
        }
        Set<String> notices = successNotices(page);
        attempt.submitted = true;
        submit.click();
        return waitForSubmitOutcome(page, notices, acknowledgement, attempt, "发帖成功",
                "发帖已提交，但微博未明确确认结果");
    }

    private WeiboActivityResult reply(Page page, String content, Attempt attempt) {
        WeiboMutationAcknowledgement acknowledgement = observeMutation(page, "reply", attempt);
        Locator article = firstVisible(page.locator(DETAIL_POST_SELECTOR));
        if (article == null) {
            return WeiboActivityResult.failed("未找到目标微博");
        }
        Locator icon = waitForVisible(page, article.locator(COMMENT_ICON_SELECTOR), UI_WAIT_MS);
        Locator opener = clickableAncestor(icon);
        if (opener == null) {
            return WeiboActivityResult.failed("未找到目标微博的评论按钮");
        }
        opener.click();
        Locator composer = waitForVisible(page, page.locator(
                "textarea[placeholder='发布你的评论'], textarea[placeholder*='评论'], " +
                "[contenteditable='true'][aria-label*='评论']"), UI_WAIT_MS);
        if (composer == null) {
            return WeiboActivityResult.failed("未找到目标微博的评论输入框");
        }
        composer.fill(content == null ? "" : content.trim());
        Locator scope = firstVisible(composer.locator(
                "xpath=ancestor::*[.//button[normalize-space(.)='评论' or normalize-space(.)='回复' " +
                        "or normalize-space(.)='发送']][1]"));
        if (scope == null) {
            return WeiboActivityResult.failed("无法确定目标评论框范围");
        }
        Locator submit = waitForVisibleEnabled(page, scope.locator(
                "button:has-text('评论'), button:has-text('回复'), button:has-text('发送')"), UI_WAIT_MS);
        if (submit == null) {
            return WeiboActivityResult.failed("未找到目标评论框内的提交按钮");
        }
        Set<String> notices = successNotices(page);
        attempt.submitted = true;
        submit.click();
        return waitForSubmitOutcome(page, notices, acknowledgement, attempt, "评论成功",
                "评论已提交，但微博未明确确认结果");
    }

    private WeiboActivityResult forward(Page page, String content, Attempt attempt) {
        WeiboMutationAcknowledgement acknowledgement = observeMutation(page, "forward", attempt);
        Locator article = firstVisible(page.locator(DETAIL_POST_SELECTOR));
        if (article == null) {
            return WeiboActivityResult.failed("未找到目标微博");
        }
        Locator icon = waitForVisible(page, article.locator(RETWEET_ICON_SELECTOR), UI_WAIT_MS);
        Locator opener = clickableAncestor(icon);
        if (opener == null) {
            return WeiboActivityResult.failed("未找到目标微博的转发按钮");
        }
        opener.click();
        Locator composer = waitForVisible(page, page.locator(
                "textarea[placeholder='说说分享心得'], textarea[placeholder*='分享心得'], " +
                "[contenteditable='true'][aria-label*='转发']"), UI_WAIT_MS);
        if (composer == null) {
            return WeiboActivityResult.failed("未找到目标微博的转发输入框");
        }
        String body = content == null ? "" : content.trim();
        composer.fill(body.isEmpty() ? "转发微博" : body);
        Locator scope = firstVisible(composer.locator(
                "xpath=ancestor::*[.//button[normalize-space(.)='转发' or normalize-space(.)='发送' " +
                        "or normalize-space(.)='确定']][1]"));
        if (scope == null) {
            return WeiboActivityResult.failed("无法确定目标转发框范围");
        }
        Locator submit = waitForVisibleEnabled(page, scope.locator(
                "button:has-text('转发'), button:has-text('发送'), button:has-text('确定')"), UI_WAIT_MS);
        if (submit == null) {
            return WeiboActivityResult.failed("转发对话框内未找到提交按钮");
        }
        Set<String> notices = successNotices(page);
        attempt.submitted = true;
        submit.click();
        return waitForSubmitOutcome(page, notices, acknowledgement, attempt, "转发成功",
                "转发已提交，但微博未明确确认结果");
    }

    private WeiboActivityResult view(Page page, Attempt attempt) {
        page.waitForTimeout(1_000 + random.nextInt(1_001));
        page.mouse().wheel(0, 500 + random.nextInt(301));
        page.waitForTimeout(500);
        WeiboActivityResult gate = gate(page, attempt);
        if (gate != null) return gate;
        if (!isLoadedPostDetail(page, attempt.postId)) {
            return WeiboActivityResult.failed("浏览期间目标微博详情失效");
        }
        return WeiboActivityResult.success("浏览完成", attempt.postId);
    }

    private String discoverPostId(Page page, Set<String> exclusions) {
        Set<String> hrefs = new LinkedHashSet<>();
        for (int pass = 0; pass < 10 && hrefs.size() < 50; pass++) {
            for (Locator link : safeAll(page.locator(FEED_LINK_SELECTOR))) {
                try {
                    String href = link.getAttribute("href");
                    if (href != null && href.startsWith("/")) href = "https://weibo.com" + href;
                    if (canonicalPostId(href) != null) hrefs.add(href);
                } catch (Exception ignored) {
                    // Detached feed entries are skipped.
                }
            }
            if (pass < 9 && hrefs.size() < 50) {
                page.mouse().wheel(0, 900);
                page.waitForTimeout(500);
            }
        }
        return choosePostId(hrefs, exclusions, random);
    }

    static String canonicalPostId(String href) {
        if (href == null || href.trim().isEmpty()) return null;
        try {
            URI uri = URI.create(href.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || !isAllowedWeiboHost(uri.getHost())) return null;
            String[] raw = uri.getPath() == null ? new String[0] : uri.getPath().split("/");
            List<String> segments = new ArrayList<>();
            for (String value : raw) if (!value.isEmpty()) segments.add(value);
            if (segments.size() == 2 && ("detail".equals(segments.get(0)) || "status".equals(segments.get(0)))) {
                return normalizePostId(segments.get(1));
            }
            if (segments.size() == 2 && NUMERIC_USER_ID.matcher(segments.get(0)).matches()) {
                return normalizePostId(segments.get(1));
            }
            return null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    static String choosePostId(Collection<String> hrefs, Set<String> exclusions, Random random) {
        Set<String> canonicalExclusions = new LinkedHashSet<>();
        if (exclusions != null) {
            for (String exclusion : exclusions) {
                String canonical = normalizePostId(exclusion);
                if (canonical != null) canonicalExclusions.add(canonical);
            }
        }
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        if (hrefs != null) {
            for (String href : hrefs) {
                String id = canonicalPostId(href);
                if (id != null && !canonicalExclusions.contains(id)) ids.add(id);
            }
        }
        if (ids.isEmpty()) return null;
        List<String> candidates = new ArrayList<>(ids);
        return candidates.get(random.nextInt(candidates.size()));
    }

    private static String normalizePostId(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        if (!POST_ID.matcher(normalized).matches()) return null;
        if (NUMERIC_POST_ID.matcher(normalized).matches()) {
            return new BigInteger(normalized).toString();
        }
        return bidToMid(normalized);
    }

    private static String bidToMid(String bid) {
        StringBuilder mid = new StringBuilder();
        for (int end = bid.length(); end > 0; end -= 4) {
            int start = Math.max(0, end - 4);
            String chunk = bid.substring(start, end);
            BigInteger decoded = BigInteger.ZERO;
            for (int i = 0; i < chunk.length(); i++) {
                int value = BASE62.indexOf(chunk.charAt(i));
                if (value < 0) return null;
                decoded = decoded.multiply(BigInteger.valueOf(62)).add(BigInteger.valueOf(value));
            }
            String decimal = decoded.toString();
            if (start > 0) {
                while (decimal.length() < 7) decimal = "0" + decimal;
            }
            mid.insert(0, decimal);
        }
        try {
            return new BigInteger(mid.toString()).toString();
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String detailUrl(String postId) {
        return "https://weibo.com/detail/" + postId;
    }

    private static boolean isAllowedWeiboUrl(String value) {
        try {
            URI uri = URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme()) && isAllowedWeiboHost(uri.getHost());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static boolean isAllowedWeiboHost(String host) {
        if (host == null) return false;
        String normalized = host.toLowerCase(Locale.ROOT);
        return "weibo.com".equals(normalized) || normalized.endsWith(".weibo.com") ||
                "weibo.cn".equals(normalized) || normalized.endsWith(".weibo.cn");
    }

    private static boolean isSupportedAction(String action) {
        return "sign".equals(action) || "post".equals(action) || "reply".equals(action) ||
                "forward".equals(action) || "view".equals(action);
    }

    private static void navigate(Page page, String url) {
        page.navigate(url, new Page.NavigateOptions().setTimeout(NAVIGATION_TIMEOUT_MS));
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
    }

    private WeiboActivityResult gate(Page page, Attempt attempt) {
        if (hasCaptcha(page)) {
            return attempt.submitted
                    ? unknownAfterSubmit("需要人工验证；当前提交结果无法确认", attempt)
                    : WeiboActivityResult.needVerification();
        }
        if (needsLogin(page)) {
            return attempt.submitted
                    ? unknownAfterSubmit("需要重新登录；当前提交结果无法确认", attempt)
                    : WeiboActivityResult.needLogin();
        }
        return null;
    }

    private static boolean needsLogin(Page page) {
        String url = safeUrl(page).toLowerCase(Locale.ROOT);
        if (url.contains("passport.weibo") || url.contains("/login") || url.contains("visitor")) return true;
        String visibleText = visibleBodyText(page);
        return visibleText.contains("Sina Visitor System") || visibleText.contains("请先登录") ||
                visibleText.contains("登录后查看") || visibleText.contains("微博登录");
    }

    private static boolean hasCaptcha(Page page) {
        String visibleText = visibleBodyText(page);
        if (visibleText.contains("需要人工验证") || visibleText.contains("安全验证") ||
                visibleText.contains("访问异常") || visibleText.contains("请输入验证码") ||
                visibleText.contains("请先验证身份") || visibleText.contains("行为存在异常")) return true;
        try {
            Locator marker = page.locator(CAPTCHA_SELECTOR);
            int count = marker.count();
            for (int i = 0; i < Math.min(count, 5); i++) {
                if (marker.nth(i).isVisible()) return true;
            }
        } catch (Exception ignored) {
            // Text and URL checks still apply when the DOM changes during inspection.
        }
        return false;
    }

    private static boolean isLoadedPostDetail(Page page, String expectedId) {
        String actual = canonicalPostId(safeUrl(page));
        if (!expectedId.equals(actual)) return false;
        Locator article = waitForVisible(page, page.locator(DETAIL_POST_SELECTOR), UI_WAIT_MS);
        if (article == null) return false;
        try {
            String text = article.innerText().trim();
            return !text.isEmpty() && !containsVisibleError(text);
        } catch (Exception e) {
            return false;
        }
    }

    private static Set<String> successNotices(Page page) {
        Set<String> values = new LinkedHashSet<>();
        try {
            Locator notices = page.locator(SUCCESS_NOTICE_SELECTOR);
            int count = Math.min(notices.count(), 20);
            for (int i = 0; i < count; i++) {
                Locator notice = notices.nth(i);
                if (!notice.isVisible()) continue;
                String text = notice.innerText().trim();
                if (isExplicitSuccessText(text)) values.add(text);
            }
        } catch (Exception ignored) {
            // Absence of a readable notice is deliberately not treated as success.
        }
        return values;
    }

    private static boolean hasNewSuccessNotice(Page page, Set<String> before) {
        Set<String> after = successNotices(page);
        after.removeAll(before);
        return !after.isEmpty();
    }

    private static boolean isExplicitSuccessText(String text) {
        return text.contains("发布成功") || text.contains("发送成功") ||
                text.contains("评论成功") || text.contains("回复成功") ||
                text.contains("转发成功") || text.contains("签到成功");
    }

    private static Locator firstVisible(Locator candidates) {
        if (candidates == null) return null;
        try {
            int count = Math.min(candidates.count(), 20);
            for (int i = 0; i < count; i++) {
                Locator candidate = candidates.nth(i);
                if (candidate.isVisible()) return candidate;
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    private static Locator firstVisibleEnabled(Locator candidates) {
        Locator candidate = firstVisible(candidates);
        try {
            return candidate != null && candidate.isEnabled() ? candidate : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static Locator clickableAncestor(Locator icon) {
        if (icon == null) return null;
        Locator ancestor = firstVisibleEnabled(icon.locator(
                "xpath=ancestor::*[self::button or @role='button' or contains(@class,'iconWrap')][1]"));
        return ancestor == null ? firstVisibleEnabled(icon) : ancestor;
    }

    private static boolean hasConfirmedSignState(Page page) {
        Locator widget = firstVisible(page.locator(SIGN_BUTTON_SELECTOR));
        return widget != null && isConfirmedSignWidget(widget);
    }

    private static boolean isConfirmedSignWidget(Locator widget) {
        try {
            String text = widget.innerText();
            if (text == null) return false;
            String normalized = text.trim();
            return "已签到".equals(normalized) || "已打卡".equals(normalized);
        } catch (Exception e) {
            return false;
        }
    }

    private static Locator waitForVisible(Page page, Locator candidates, double timeoutMs) {
        int attempts = Math.max(1, (int) Math.ceil(timeoutMs / POLL_MS));
        for (int i = 0; i < attempts; i++) {
            Locator visible = firstVisible(candidates);
            if (visible != null) return visible;
            if (i + 1 < attempts) page.waitForTimeout(POLL_MS);
        }
        return null;
    }

    private static Locator waitForVisibleEnabled(Page page, Locator candidates, double timeoutMs) {
        int attempts = Math.max(1, (int) Math.ceil(timeoutMs / POLL_MS));
        for (int i = 0; i < attempts; i++) {
            Locator visible = firstVisibleEnabled(candidates);
            if (visible != null) return visible;
            if (i + 1 < attempts) page.waitForTimeout(POLL_MS);
        }
        return null;
    }

    private WeiboActivityResult waitForSubmitOutcome(Page page, Set<String> notices,
                                                      WeiboMutationAcknowledgement acknowledgement,
                                                      Attempt attempt, String successMessage,
                                                      String unknownMessage) {
        int attempts = (int) Math.ceil(UI_WAIT_MS / POLL_MS);
        for (int i = 0; i < attempts; i++) {
            WeiboMutationAcknowledgement.Outcome networkOutcome = acknowledgement.outcome();
            if (networkOutcome == WeiboMutationAcknowledgement.Outcome.CONFIRMED) {
                return WeiboActivityResult.success(successMessage, attempt.postId);
            }
            if (networkOutcome == WeiboMutationAcknowledgement.Outcome.NEED_VERIFICATION ||
                    networkOutcome == WeiboMutationAcknowledgement.Outcome.NEED_LOGIN) {
                return unknownAfterSubmit(acknowledgement.canonicalMessage(), attempt);
            }
            WeiboActivityResult postGate = gate(page, attempt);
            if (postGate != null) return postGate;
            if (hasNewSuccessNotice(page, notices)) {
                return WeiboActivityResult.success(successMessage, attempt.postId);
            }
            if (i + 1 < attempts) page.waitForTimeout(POLL_MS);
        }
        return unknownAfterSubmit(unknownMessage, attempt);
    }

    private static WeiboMutationAcknowledgement observeMutation(Page page, String action,
                                                                 Attempt attempt) {
        WeiboMutationAcknowledgement acknowledgement =
                WeiboMutationAcknowledgement.forAction(action);
        page.onResponse(response -> observeResponse(response, attempt, acknowledgement));
        return acknowledgement;
    }

    private static void observeResponse(Response response, Attempt attempt,
                                        WeiboMutationAcknowledgement acknowledgement) {
        try {
            Request request = response.request();
            String method = request == null ? null : request.method();
            if (!acknowledgement.shouldParse(
                    attempt.submitted, method, response.url(), response.status())) {
                return;
            }
            Object json = JSON.readValue(response.body(), Object.class);
            acknowledgement.observe(
                    attempt.submitted, method, response.url(), response.status(), json);
        } catch (Exception ignored) {
            // Unreadable or non-JSON responses cannot confirm a public write.
        }
    }

    private static List<Locator> safeAll(Locator candidates) {
        try {
            return candidates == null ? Collections.emptyList() : candidates.all();
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private static String visibleBodyText(Page page) {
        try {
            Locator body = page.locator("body");
            Locator visibleBody = firstVisible(body);
            if (visibleBody == null) return "";
            String value = visibleBody.innerText();
            return value == null ? "" : value;
        } catch (Exception e) {
            return "";
        }
    }

    private static boolean containsVisibleError(String text) {
        return text.contains("微博不存在") || text.contains("内容不存在") ||
                text.contains("暂无内容") || text.contains("页面出错") ||
                text.contains("访问受限") || text.contains("系统繁忙");
    }

    private static String safeUrl(Page page) {
        try {
            String value = page.url();
            return value == null ? "" : value;
        } catch (Exception e) {
            return "";
        }
    }

    private static String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.trim().isEmpty() ? e.getClass().getSimpleName() : message;
    }

    private static WeiboActivityResult unknownAfterSubmit(String message, Attempt attempt) {
        return WeiboActivityResult.of(WeiboActivityResult.Status.UNKNOWN, message, attempt.postId);
    }

    private static void close(Page page, BrowserContext context) {
        if (page != null) {
            try { page.close(); } catch (Exception e) { log.debug("关闭微博活动页面失败", e); }
        }
        if (context != null) {
            try { context.close(); } catch (Exception e) { log.debug("关闭微博活动 context 失败", e); }
        }
    }

    private static final class Attempt {
        private String postId;
        private volatile boolean submitted;

        private Attempt(String postId) {
            this.postId = postId;
        }
    }

    private static final class ReservationFailure extends RuntimeException {
        private final RuntimeException original;

        private ReservationFailure(RuntimeException original) {
            super(original);
            this.original = original;
        }
    }
}
