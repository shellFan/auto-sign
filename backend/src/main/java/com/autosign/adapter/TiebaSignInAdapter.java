package com.autosign.adapter;

import com.autosign.entity.PlatformAccount;
import com.autosign.service.BrowserService;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Component
public class TiebaSignInAdapter implements SignInAdapter {

    private static final Logger log = LoggerFactory.getLogger(TiebaSignInAdapter.class);

    @Autowired
    private BrowserService browserService;

    private final Random random = new Random();

    @Override
    public String platform() {
        return "tieba";
    }

    @Override
    public LoginCheckResult checkLogin(PlatformAccount account) {
        return browserService.executeSerialized(() -> checkLoginSerialized(account));
    }

    private LoginCheckResult checkLoginSerialized(PlatformAccount account) {
        BrowserContext context = null;
        try {
            context = browserService.loadContext(account.getId(), account.getPlatform(), account.getUserId());
            Page page = context.newPage();
            page.navigate("https://tieba.baidu.com");
            page.waitForLoadState(LoadState.NETWORKIDLE);

            // 检查是否登录 - 查找用户头像或用户名
            Locator avatar = page.locator(".user-avatar, .j_userhead, [class*='avatar']");
            Locator loginBtn = page.locator("text=登录, a[href*='login']");

            if (avatar.count() > 0) {
                String screenshot = browserService.takeScreenshot(page, account.getId(),
                        LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE), "login_check");
                page.close();
                return LoginCheckResult.success();
            }

            if (loginBtn.count() > 0) {
                String screenshot = browserService.takeScreenshot(page, account.getId(),
                        LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE), "login_required");
                page.close();
                return LoginCheckResult.failed("未登录");
            }

            // 检查是否有验证码
            Locator captcha = page.locator("[class*='captcha'], [class*='verify'], text=验证码");
            if (captcha.count() > 0) {
                String screenshot = browserService.takeScreenshot(page, account.getId(),
                        LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE), "captcha");
                page.close();
                return LoginCheckResult.needVerification("出现验证码", screenshot);
            }

            page.close();
            return LoginCheckResult.success();
        } catch (Exception e) {
            log.error("检查百度登录状态失败", e);
            if (context != null) {
                context.close();
            }
            return LoginCheckResult.failed("检查失败: " + e.getMessage());
        }
    }

    @Override
    public SignInResult signIn(PlatformAccount account) {
        return browserService.executeSerialized(() -> signInSerialized(account));
    }

    private SignInResult signInSerialized(PlatformAccount account) {
        BrowserContext context = null;
        try {
            context = browserService.loadContext(account.getId(), account.getPlatform(), account.getUserId());
            Page page = context.newPage();
            page.navigate("https://tieba.baidu.com/mo/q/newtbforum/myforum");
            page.waitForLoadState(LoadState.NETWORKIDLE);

            // 检查是否需要登录
            Locator loginBtn = page.locator("text=登录");
            if (loginBtn.count() > 0) {
                page.close();
                context.close();
                return SignInResult.needRelogin();
            }

            // 检查是否有验证码
            if (hasCaptcha(page)) {
                String screenshot = browserService.takeScreenshot(page, account.getId(),
                        LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE), "captcha");
                page.close();
                context.close();
                return SignInResult.needVerification();
            }

            // 获取关注的贴吧列表
            List<Locator> forumItems = page.locator(".forum-item, .forum_item, [class*='forum-item']").all();

            int successCount = 0;
            int failCount = 0;
            List<SignInResult.TargetResult> details = new ArrayList<>();

            for (Locator item : forumItems) {
                try {
                    String forumName = item.locator(".forum-name, .forum_name, [class*='name']").innerText();
                    Locator signBtn = item.locator("text=签到, text=签到成功, [class*='sign']");

                    if (signBtn.count() > 0) {
                        String btnText = signBtn.first().innerText();
                        if (btnText.contains("已签到") || btnText.contains("签到成功")) {
                            details.add(SignInResult.TargetResult.builder()
                                    .targetName(forumName)
                                    .status(2)
                                    .message("已签到")
                                    .build());
                            successCount++;
                        } else {
                            // 点击签到
                            signBtn.first().click();
                            page.waitForTimeout(1000 + random.nextInt(2000));

                            // 检查是否出现验证码
                            if (hasCaptcha(page)) {
                                String screenshot = browserService.takeScreenshot(page, account.getId(),
                                        LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE), forumName);
                                details.add(SignInResult.TargetResult.builder()
                                        .targetName(forumName)
                                        .status(4)
                                        .message("需要人工验证")
                                        .screenshotPath(screenshot)
                                        .build());
                                failCount++;
                                break; // 出现验证码立即停止
                            }

                            details.add(SignInResult.TargetResult.builder()
                                    .targetName(forumName)
                                    .status(1)
                                    .message("签到成功")
                                    .build());
                            successCount++;
                        }
                    }

                    // 随机等待2-5秒
                    Thread.sleep(2000 + random.nextInt(3000));
                } catch (Exception e) {
                    log.error("贴吧签到失败", e);
                    failCount++;
                }
            }

            String screenshot = browserService.takeScreenshot(page, account.getId(),
                    LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE), "tieba_result");
            page.close();
            context.close();

            if (failCount > 0) {
                return SignInResult.builder()
                        .status(3)
                        .successCount(successCount)
                        .failCount(failCount)
                        .message("部分成功")
                        .details(details)
                        .build();
            }
            return SignInResult.success(successCount);
        } catch (Exception e) {
            log.error("百度贴吧签到失败", e);
            if (context != null) {
                context.close();
            }
            return SignInResult.failed("签到失败: " + e.getMessage());
        }
    }

    private boolean hasCaptcha(Page page) {
        Locator captcha = page.locator("[class*='captcha'], [class*='verify'], text=验证码, text=安全验证, text=短信验证, text=滑块验证");
        return captcha.count() > 0;
    }
}
