package com.autosign.adapter;

import com.autosign.entity.PlatformAccount;
import com.autosign.entity.WeiboSuperTopic;
import com.autosign.service.BrowserService;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Mouse;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Random;
import java.util.function.Supplier;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class WeiboActivityAdapterTest {

    private BrowserService browserService;
    private WeiboSignInAdapter signInAdapter;
    private WeiboActivityAdapter adapter;
    private PlatformAccount account;
    private WeiboSuperTopic topic;

    @BeforeEach
    void setUp() {
        browserService = mock(BrowserService.class);
        signInAdapter = mock(WeiboSignInAdapter.class);
        adapter = new WeiboActivityAdapter(browserService, signInAdapter, new Random(7));
        account = new PlatformAccount();
        account.setId(10L);
        topic = new WeiboSuperTopic();
        topic.setTopicUrl("https://weibo.com/p/100808abc/super_index");
        topic.setTopicName("测试超话");
        when(browserService.executeSerialized(any())).thenAnswer(invocation ->
                ((Supplier<?>) invocation.getArgument(0)).get());
    }

    @Test
    void canonicalPostIdAcceptsOnlyRealWeiboDetailRoutes() {
        assertThat(WeiboActivityAdapter.canonicalPostId("https://weibo.com/detail/3501756485200075?from=feed"))
                .isEqualTo("3501756485200075");
        assertThat(WeiboActivityAdapter.canonicalPostId("https://www.weibo.com/status/z0JH2lOMb"))
                .isEqualTo("3501756485200075");
        assertThat(WeiboActivityAdapter.canonicalPostId("https://weibo.com/1234567890/z0JH2lOMb#comment"))
                .isEqualTo("3501756485200075");

        assertThat(WeiboActivityAdapter.canonicalPostId("https://evil.example/?next=https://weibo.com/detail/Nabc123"))
                .isNull();
        assertThat(WeiboActivityAdapter.canonicalPostId("https://weibo.com/login.php"))
                .isNull();
        assertThat(WeiboActivityAdapter.canonicalPostId("javascript:alert(1)"))
                .isNull();
    }

    @Test
    void selectionDeduplicatesIdsAndHonorsExclusions() {
        String selected = WeiboActivityAdapter.choosePostId(
                Arrays.asList(
                        "https://weibo.com/detail/3501756485200075",
                        "https://weibo.com/status/z0JH2lOMb",
                        "https://weibo.com/12345/3501756485200088",
                        "https://example.com/detail/CCC333"),
                new HashSet<>(Collections.singletonList("z0JH2lOMb")),
                new Random(1));

        assertThat(selected).isEqualTo("3501756485200088");
    }

    @Test
    void bidAndMidAliasesCollapseToOneCanonicalReservationId() {
        String selected = WeiboActivityAdapter.choosePostId(
                Arrays.asList(
                        "https://weibo.com/detail/3501756485200075",
                        "https://weibo.com/1234567890/z0JH2lOMb"),
                Collections.emptySet(), new Random(2));

        assertThat(selected).isEqualTo("3501756485200075");
    }

    @Test
    void reservationFailurePreventsAnyBrowserEffect() {
        assertThatThrownBy(() -> adapter.execute(account, topic, "reply", "内容", "Nabc123",
                Collections.emptySet(), id -> { throw new IllegalStateException("db unavailable"); }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("db unavailable");

        verify(browserService, never()).loadWeiboContext(any());
    }

    @Test
    void checkLoginAlwaysClosesPageAndIsolatedContext() {
        BrowserContext context = mock(BrowserContext.class);
        Page page = mock(Page.class);
        when(browserService.loadWeiboContext(account)).thenReturn(context);
        when(context.newPage()).thenReturn(page);
        when(page.navigate(anyString(), any(Page.NavigateOptions.class)))
                .thenThrow(new RuntimeException("navigation failed"));

        WeiboActivityResult result = adapter.checkLogin(account);

        assertThat(result.getStatus()).isEqualTo(WeiboActivityResult.Status.FAILED);
        verify(page).close();
        verify(context).close();
    }

    @Test
    void hiddenLoginAndCaptchaMarkupDoesNotOverrideVisibleLoggedInUi() {
        BrowserContext context = mock(BrowserContext.class);
        Page page = mock(Page.class);
        Locator body = visibleLocator("首页 消息 测试用户");
        Locator loggedIn = visibleLocator("测试用户");
        Locator empty = emptyLocator();
        when(browserService.loadWeiboContext(account)).thenReturn(context);
        when(context.newPage()).thenReturn(page);
        when(page.url()).thenReturn("https://weibo.com/");
        when(page.content()).thenReturn("<script>captcha 微博登录 Sina Visitor System</script>");
        when(page.locator(anyString())).thenAnswer(invocation -> {
            String selector = invocation.getArgument(0);
            if ("body".equals(selector)) return body;
            if (selector.contains("avatar") || selector.contains("Avatar")) return loggedIn;
            return empty;
        });

        WeiboActivityResult result = adapter.checkLogin(account);

        assertThat(result.getStatus()).isEqualTo(WeiboActivityResult.Status.SUCCESS);
    }

    @Test
    void visibleCaptchaPausesLoginCheck() {
        BrowserContext context = mock(BrowserContext.class);
        Page page = mock(Page.class);
        Locator body = visibleLocator("安全验证 请输入验证码");
        Locator empty = emptyLocator();
        when(browserService.loadWeiboContext(account)).thenReturn(context);
        when(context.newPage()).thenReturn(page);
        when(page.url()).thenReturn("https://weibo.com/");
        when(page.content()).thenReturn("<html><body>application shell</body></html>");
        when(page.locator(anyString())).thenAnswer(invocation ->
                "body".equals(invocation.getArgument(0)) ? body : empty);

        WeiboActivityResult result = adapter.checkLogin(account);

        assertThat(result.getStatus()).isEqualTo(WeiboActivityResult.Status.NEED_VERIFICATION);
    }

    @Test
    void emptyShellCannotBeReportedAsLoggedIn() {
        BrowserContext context = mock(BrowserContext.class);
        Page page = mock(Page.class);
        Locator body = visibleLocator("");
        Locator empty = emptyLocator();
        when(browserService.loadWeiboContext(account)).thenReturn(context);
        when(context.newPage()).thenReturn(page);
        when(page.url()).thenReturn("https://weibo.com/");
        when(page.content()).thenReturn("<html><body></body></html>");
        when(page.locator(anyString())).thenAnswer(invocation ->
                "body".equals(invocation.getArgument(0)) ? body : empty);

        WeiboActivityResult result = adapter.checkLogin(account);

        assertThat(result.getStatus()).isEqualTo(WeiboActivityResult.Status.FAILED);
    }

    @Test
    void genericStreakCopyDoesNotCountAsSignedAndRealOverlayIsClicked() {
        BrowserContext context = mock(BrowserContext.class);
        Page page = mock(Page.class);
        AtomicBoolean clicked = new AtomicBoolean();
        Locator body = visibleLocator("一条微博正文写着已签到 连续签到 7 天 签到天数");
        Locator signButton = dynamicTextLocator(clicked, "签到", "已签到");
        Locator empty = emptyLocator();
        when(browserService.loadWeiboContext(account)).thenReturn(context);
        when(context.newPage()).thenReturn(page);
        when(page.url()).thenReturn(topic.getTopicUrl());
        when(page.locator(anyString())).thenAnswer(invocation -> {
            String selector = invocation.getArgument(0);
            if ("body".equals(selector)) return body;
            if (selector.contains("signBtnOverlay")) return signButton;
            return empty;
        });
        doAnswer(invocation -> { clicked.set(true); return null; }).when(signButton).click();

        WeiboActivityResult result = adapter.execute(account, topic, "sign", null,
                null, Collections.emptySet(), id -> {});

        assertThat(result.getStatus()).isEqualTo(WeiboActivityResult.Status.SUCCESS);
        verify(signButton).click();
        verifyNoInteractions(signInAdapter);
        verify(page).close();
        verify(context).close();
    }

    @Test
    void exactSignedTextOnVisibleSignWidgetSkipsClick() {
        BrowserContext context = mock(BrowserContext.class);
        Page page = mock(Page.class);
        Locator body = visibleLocator("测试超话");
        Locator signWidget = visibleLocator("已签到");
        Locator empty = emptyLocator();
        when(browserService.loadWeiboContext(account)).thenReturn(context);
        when(context.newPage()).thenReturn(page);
        when(page.url()).thenReturn(topic.getTopicUrl());
        when(page.locator(anyString())).thenAnswer(invocation -> {
            String selector = invocation.getArgument(0);
            if ("body".equals(selector)) return body;
            if (selector.contains("signBtnOverlay")) return signWidget;
            return empty;
        });

        WeiboActivityResult result = adapter.execute(account, topic, "sign", null,
                null, Collections.emptySet(), id -> {});

        assertThat(result.getStatus()).isEqualTo(WeiboActivityResult.Status.SUCCESS);
        verify(signWidget, never()).click();
        verifyNoInteractions(signInAdapter);
    }

    @Test
    void captchaAfterSignClickIsUnknownWithCanonicalPauseMessage() {
        BrowserContext context = mock(BrowserContext.class);
        Page page = mock(Page.class);
        AtomicBoolean clicked = new AtomicBoolean();
        Locator body = dynamicTextLocator(clicked, "测试超话",
                "你最近的行为存在异常，请先验证身份后再进行操作");
        Locator signButton = visibleLocator("签到");
        Locator empty = emptyLocator();
        when(browserService.loadWeiboContext(account)).thenReturn(context);
        when(context.newPage()).thenReturn(page);
        when(page.url()).thenReturn(topic.getTopicUrl());
        when(page.getByText(anyString(), any(Page.GetByTextOptions.class))).thenReturn(empty);
        when(page.locator(anyString())).thenAnswer(invocation -> {
            String selector = invocation.getArgument(0);
            if ("body".equals(selector)) return body;
            if (selector.contains("signBtnOverlay")) return signButton;
            return empty;
        });
        doAnswer(invocation -> { clicked.set(true); return null; }).when(signButton).click();

        WeiboActivityResult result = adapter.execute(account, topic, "sign", null,
                null, Collections.emptySet(), id -> {});

        assertThat(result.getStatus()).isEqualTo(WeiboActivityResult.Status.UNKNOWN);
        assertThat(result.getMessage()).isEqualTo("需要人工验证");
        verify(signButton).click();
        verifyNoInteractions(signInAdapter);
    }

    @Test
    void missingSignControlBeforeClickIsSafelyRetryableFailure() {
        BrowserContext context = mock(BrowserContext.class);
        Page page = mock(Page.class);
        Locator body = visibleLocator("测试超话");
        Locator empty = emptyLocator();
        when(browserService.loadWeiboContext(account)).thenReturn(context);
        when(context.newPage()).thenReturn(page);
        when(page.url()).thenReturn(topic.getTopicUrl());
        when(page.getByText(anyString(), any(Page.GetByTextOptions.class))).thenReturn(empty);
        when(page.locator(anyString())).thenAnswer(invocation ->
                "body".equals(invocation.getArgument(0)) ? body : empty);

        WeiboActivityResult result = adapter.execute(account, topic, "sign", null,
                null, Collections.emptySet(), id -> {});

        assertThat(result.getStatus()).isEqualTo(WeiboActivityResult.Status.FAILED);
        assertThat(result.getMessage()).isEqualTo("未找到当前超话的签到按钮");
        verifyNoInteractions(signInAdapter);
    }

    @Test
    void modernArticleFeedAndDetailWorkWithoutMainElement() {
        BrowserContext context = mock(BrowserContext.class);
        Page page = mock(Page.class);
        Mouse mouse = mock(Mouse.class);
        AtomicReference<String> url = new AtomicReference<>(topic.getTopicUrl());
        Locator body = visibleLocator("测试超话 微博正文");
        Locator article = visibleLocator("微博正文");
        Locator links = mock(Locator.class);
        Locator link = mock(Locator.class);
        Locator empty = emptyLocator();
        when(browserService.loadWeiboContext(account)).thenReturn(context);
        when(context.newPage()).thenReturn(page);
        when(page.mouse()).thenReturn(mouse);
        when(page.url()).thenAnswer(invocation -> url.get());
        doAnswer(invocation -> { url.set(invocation.getArgument(0)); return null; })
                .when(page).navigate(anyString(), any(Page.NavigateOptions.class));
        when(link.getAttribute("href")).thenReturn("https://weibo.com/7597808886/RnU5l7bMp");
        when(links.all()).thenReturn(Collections.singletonList(link));
        when(page.locator(anyString())).thenAnswer(invocation -> {
            String selector = invocation.getArgument(0);
            if ("body".equals(selector)) return body;
            if (selector.startsWith("main") || selector.contains("[role='main']")) return empty;
            if (selector.contains("article") && selector.contains("a[href]")) return links;
            if (selector.contains("article")) return article;
            return empty;
        });

        WeiboActivityResult result = adapter.execute(account, topic, "view", null,
                null, Collections.emptySet(), id -> {});

        assertThat(result.getStatus()).isEqualTo(WeiboActivityResult.Status.SUCCESS);
        assertThat(result.getPostId()).isEqualTo("5356971391713581");
    }

    @Test
    void replyUsesCommentIconAndComposerScopeOutsideArticle() {
        ActionFixture fixture = actionFixture("发布你的评论", "评论");

        WeiboActivityResult result = adapter.execute(account, topic, "reply", "评论内容",
                "5356971391713581", Collections.emptySet(), id -> {});

        assertThat(result.getStatus()).isEqualTo(WeiboActivityResult.Status.UNKNOWN);
        verify(fixture.opener).click();
        verify(fixture.composer).fill("评论内容");
        verify(fixture.submit).click();
    }

    @Test
    void forwardUsesRetweetIconAndInlineComposerOutsideArticle() {
        ActionFixture fixture = actionFixture("说说分享心得", "转发");

        WeiboActivityResult result = adapter.execute(account, topic, "forward", "",
                "5356971391713581", Collections.emptySet(), id -> {});

        assertThat(result.getStatus()).isEqualTo(WeiboActivityResult.Status.UNKNOWN);
        verify(fixture.opener).click();
        verify(fixture.composer).fill("转发微博");
        verify(fixture.submit).click();
    }

    @Test
    void shellAtCanonicalUrlIsNotAcceptedAsLoadedPostDetail() {
        BrowserContext context = mock(BrowserContext.class);
        Page page = mock(Page.class);
        Locator body = visibleLocator("微博首页外壳");
        Locator empty = emptyLocator();
        when(browserService.loadWeiboContext(account)).thenReturn(context);
        when(context.newPage()).thenReturn(page);
        when(page.url()).thenReturn("https://weibo.com/detail/3501756485200075");
        when(page.content()).thenReturn("<html><body>large application shell</body></html>");
        when(page.locator(anyString())).thenAnswer(invocation ->
                "body".equals(invocation.getArgument(0)) ? body : empty);

        WeiboActivityResult result = adapter.execute(account, topic, "view", null,
                "3501756485200075", Collections.emptySet(), id -> {});

        assertThat(result.getStatus()).isEqualTo(WeiboActivityResult.Status.FAILED);
        assertThat(result.getMessage()).isEqualTo("目标微博详情未能可靠加载");
    }

    @Test
    void exceptionFromSubmitClickIsUncertainBecauseWriteMayHaveReachedWeibo() {
        BrowserContext context = mock(BrowserContext.class);
        Page page = mock(Page.class);
        Locator empty = mock(Locator.class);
        Locator composer = mock(Locator.class);
        Locator scope = mock(Locator.class);
        Locator send = mock(Locator.class);

        when(browserService.loadWeiboContext(account)).thenReturn(context);
        when(context.newPage()).thenReturn(page);
        when(page.url()).thenReturn(topic.getTopicUrl());
        when(page.content()).thenReturn("超话内容");
        when(page.locator(anyString())).thenReturn(empty);
        when(empty.count()).thenReturn(0);
        when(page.locator(WeiboActivityAdapter.POST_COMPOSER_SELECTOR)).thenReturn(composer);
        when(composer.count()).thenReturn(1);
        when(composer.nth(0)).thenReturn(composer);
        when(composer.isVisible()).thenReturn(true);
        when(composer.locator(anyString())).thenReturn(scope);
        when(scope.count()).thenReturn(1);
        when(scope.nth(0)).thenReturn(scope);
        when(scope.isVisible()).thenReturn(true);
        when(scope.locator(WeiboActivityAdapter.POST_SUBMIT_SELECTOR)).thenReturn(send);
        when(send.count()).thenReturn(1);
        when(send.nth(0)).thenReturn(send);
        when(send.isVisible()).thenReturn(true);
        when(send.isEnabled()).thenReturn(true);
        doThrow(new RuntimeException("transport closed")).when(send).click();

        WeiboActivityResult result = adapter.execute(account, topic, "post", "今天也来打卡",
                null, Collections.emptySet(), id -> {});

        assertThat(result.getStatus()).isEqualTo(WeiboActivityResult.Status.UNKNOWN);
        verify(page).close();
        verify(context).close();
    }

    @Test
    void postFindsObservedNoRoleWooModalComposerAndScopesItsSendButton() {
        BrowserContext context = mock(BrowserContext.class);
        Page page = mock(Page.class);
        Locator body = visibleLocator("发布到测试超话");
        Locator composer = visibleLocator("");
        Locator modal = visibleLocator("");
        Locator send = visibleLocator("发送");
        Locator empty = emptyLocator();
        when(browserService.loadWeiboContext(account)).thenReturn(context);
        when(context.newPage()).thenReturn(page);
        when(page.url()).thenReturn(topic.getTopicUrl());
        when(page.getByText(anyString(), any(Page.GetByTextOptions.class))).thenReturn(empty);
        when(page.locator(anyString())).thenAnswer(invocation -> {
            String selector = invocation.getArgument(0);
            if ("body".equals(selector)) return body;
            if (selector.contains("掀指一算，此贴必火")) return composer;
            return empty;
        });
        when(composer.locator(anyString())).thenAnswer(invocation ->
                ((String) invocation.getArgument(0)).contains("woo-modal-main") ? modal : empty);
        when(modal.locator(WeiboActivityAdapter.POST_SUBMIT_SELECTOR)).thenReturn(send);
        doThrow(new RuntimeException("transport closed")).when(send).click();

        WeiboActivityResult result = adapter.execute(account, topic, "post", "今天也来打卡",
                null, Collections.emptySet(), id -> {});

        assertThat(result.getStatus()).isEqualTo(WeiboActivityResult.Status.UNKNOWN);
        verify(composer).fill("#测试超话[超话]# 今天也来打卡");
        verify(send).click();
    }

    private static Locator visibleLocator(String text) {
        Locator locator = mock(Locator.class);
        when(locator.count()).thenReturn(1);
        when(locator.nth(0)).thenReturn(locator);
        when(locator.isVisible()).thenReturn(true);
        when(locator.isEnabled()).thenReturn(true);
        when(locator.innerText()).thenReturn(text);
        return locator;
    }

    private static Locator dynamicVisibleLocator(AtomicBoolean visible, String text) {
        Locator locator = mock(Locator.class);
        when(locator.count()).thenReturn(1);
        when(locator.nth(0)).thenReturn(locator);
        when(locator.isVisible()).thenAnswer(invocation -> visible.get());
        when(locator.isEnabled()).thenReturn(true);
        when(locator.innerText()).thenReturn(text);
        return locator;
    }

    private static Locator dynamicTextLocator(AtomicBoolean changed, String before, String after) {
        Locator locator = visibleLocator(before);
        when(locator.innerText()).thenAnswer(invocation -> changed.get() ? after : before);
        return locator;
    }

    private ActionFixture actionFixture(String placeholder, String buttonText) {
        BrowserContext context = mock(BrowserContext.class);
        Page page = mock(Page.class);
        Locator body = visibleLocator("微博正文");
        Locator article = visibleLocator("微博正文");
        Locator icon = visibleLocator(buttonText);
        Locator opener = visibleLocator(buttonText);
        Locator composer = visibleLocator("");
        Locator scope = visibleLocator("");
        Locator submit = visibleLocator(buttonText);
        Locator empty = emptyLocator();
        when(browserService.loadWeiboContext(account)).thenReturn(context);
        when(context.newPage()).thenReturn(page);
        when(page.url()).thenReturn("https://weibo.com/detail/5356971391713581");
        when(page.locator(anyString())).thenAnswer(invocation -> {
            String selector = invocation.getArgument(0);
            if ("body".equals(selector)) return body;
            if (selector.startsWith("main") || selector.contains("[role='main']")) return empty;
            if (selector.contains("article")) return article;
            if (selector.contains("textarea") && selector.contains(placeholder)) return composer;
            return empty;
        });
        when(article.locator(anyString())).thenAnswer(invocation -> {
            String selector = invocation.getArgument(0);
            if (selector.contains(buttonText)) return icon;
            return empty;
        });
        when(icon.locator(anyString())).thenReturn(opener);
        when(composer.locator(anyString())).thenReturn(scope);
        when(scope.locator(anyString())).thenAnswer(invocation -> {
            String selector = invocation.getArgument(0);
            return selector.contains(buttonText) ? submit : empty;
        });
        return new ActionFixture(opener, composer, submit);
    }

    private static final class ActionFixture {
        private final Locator opener;
        private final Locator composer;
        private final Locator submit;

        private ActionFixture(Locator opener, Locator composer, Locator submit) {
            this.opener = opener;
            this.composer = composer;
            this.submit = submit;
        }
    }

    private static Locator emptyLocator() {
        Locator locator = mock(Locator.class);
        when(locator.count()).thenReturn(0);
        return locator;
    }
}
