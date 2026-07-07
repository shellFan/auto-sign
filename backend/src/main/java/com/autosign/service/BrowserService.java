package com.autosign.service;

import com.autosign.browser.BrowserSession;
import com.autosign.entity.PlatformAccount;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.Cookie;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class BrowserService {

    private static final Logger log = LoggerFactory.getLogger(BrowserService.class);

    @Value("${storage.profiles-path}")
    private String profilesPath;

    @Value("${storage.cookies-path}")
    private String cookiesPath;

    @Value("${storage.screenshots-path}")
    private String screenshotsPath;

    private Playwright playwright;

    private Browser browser;

    private final Map<String, BrowserSession> sessions = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        playwright = Playwright.create();

        BrowserType.LaunchOptions options = new BrowserType.LaunchOptions()
                .setHeadless(false)
                .setChannel("chrome")
                .setArgs(java.util.Arrays.asList(
                        "--no-sandbox",
                        "--disable-setuid-sandbox",
                        "--disable-blink-features=AutomationControlled",
                        "--disable-features=IsolateOrigins,site-per-process",
                        "--disable-web-security",
                        "--allow-running-insecure-content",
                        "--start-maximized",
                        "--disable-infobars",
                        "--disable-dev-shm-usage",
                        "--no-first-run",
                        "--no-default-browser-check",
                        "--disable-extensions",
                        "--window-size=1920,1080"
                ));

        browser = playwright.chromium().launch(options);
        log.info("Playwright 浏览器初始化完成");
    }

    public Playwright getPlaywright() {
        return playwright;
    }

    @PreDestroy
    public void destroy() {
        sessions.values().forEach(session -> {
            try {
                if (session.getContext() != null) {
                    session.getContext().close();
                }
            } catch (Exception e) {
                log.error("关闭浏览器会话失败", e);
            }
        });
        if (browser != null) {
            browser.close();
        }
        if (playwright != null) {
            playwright.close();
        }
    }

    public Map<String, Object> createSession(PlatformAccount account) {
        String sessionId = UUID.randomUUID().toString();
        String profilePath = profilesPath + "/" + account.getUserId() + "/" + account.getPlatform() + "/" + account.getId();

        BrowserContext context = createStealthContext();
        Page page = context.newPage();
        injectStealthScripts(page);

        BrowserSession session = new BrowserSession();
        session.setSessionId(sessionId);
        session.setAccountId(account.getId());
        session.setPlatform(account.getPlatform());
        session.setContext(context);
        session.setPage(page);
        session.setProfilePath(profilePath);
        session.setCreateTime(System.currentTimeMillis());
        session.setActive(true);

        sessions.put(sessionId, session);

        if ("tieba".equals(account.getPlatform())) {
            page.navigate("https://tieba.baidu.com");
        } else if ("weibo".equals(account.getPlatform())) {
            page.navigate("https://weibo.com");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("sessionId", sessionId);
        result.put("vncUrl", "http://localhost:6080/vnc.html?autoconnect=true");
        return result;
    }

    public Map<String, Object> getSession(String sessionId) {
        BrowserSession session = sessions.get(sessionId);
        Map<String, Object> result = new HashMap<>();
        if (session != null) {
            result.put("sessionId", session.getSessionId());
            result.put("active", session.isActive());
            result.put("vncUrl", "http://localhost:6080/vnc.html?autoconnect=true");
        }
        return result;
    }

    public void saveSession(String sessionId) {
        BrowserSession session = sessions.get(sessionId);
        if (session != null && session.getContext() != null) {
            try {
                String storagePath = session.getProfilePath() + "/state.json";

                java.nio.file.Path profileDir = Paths.get(session.getProfilePath());
                Files.createDirectories(profileDir);
                Files.createDirectories(Paths.get(cookiesPath));
                Files.createDirectories(Paths.get(screenshotsPath));

                session.getContext().storageState(new BrowserContext.StorageStateOptions()
                        .setPath(Paths.get(storagePath)));

                if (Files.exists(Paths.get(storagePath))) {
                    log.info("浏览器会话已保存成功: sessionId={}, path={}", sessionId, storagePath);
                } else {
                    log.error("浏览器会话保存失败: 文件不存在 {}", storagePath);
                }
            } catch (Exception e) {
                log.error("保存浏览器会话失败: sessionId={}", sessionId, e);
            }
        }
    }

    public void closeSession(String sessionId) {
        BrowserSession session = sessions.remove(sessionId);
        if (session != null) {
            try {
                if (session.getContext() != null) {
                    session.getContext().close();
                }
                session.setActive(false);
                log.info("浏览器会话已关闭: sessionId={}", sessionId);
            } catch (Exception e) {
                log.error("关闭浏览器会话失败: sessionId={}", sessionId, e);
            }
        }
    }

    public BrowserContext loadContext(Long accountId, String platform, Long userId) {
        String storagePath = profilesPath + "/" + userId + "/" + platform + "/" + accountId + "/state.json";
        java.nio.file.Path path = Paths.get(storagePath);

        BrowserContext context;
        if (Files.exists(path)) {
            log.info("加载已保存的登录态: accountId={}, path={}", accountId, storagePath);
            context = browser.newContext(new Browser.NewContextOptions()
                    .setViewportSize(1920, 1080)
                    .setStorageStatePath(path)
                    .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .setLocale("zh-CN")
                    .setTimezoneId("Asia/Shanghai"));
        } else {
            log.warn("未找到已保存的登录态: accountId={}, path={}", accountId, storagePath);
            context = createStealthContext();
        }

        return context;
    }

    private BrowserContext createStealthContext() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
        headers.put("sec-ch-ua", "\"Not_A Brand\";v=\"8\", \"Chromium\";v=\"120\", \"Google Chrome\";v=\"120\"");
        headers.put("sec-ch-ua-mobile", "?0");
        headers.put("sec-ch-ua-platform", "\"Windows\"");

        return browser.newContext(new Browser.NewContextOptions()
                .setViewportSize(1920, 1080)
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .setLocale("zh-CN")
                .setTimezoneId("Asia/Shanghai")
                .setExtraHTTPHeaders(headers));
    }

    private void injectStealthScripts(Page page) {
        String script = "Object.defineProperty(navigator, 'webdriver', { get: () => undefined });" +
                "window.chrome = { runtime: {}, loadTimes: function() {}, csi: function() {}, app: {} };" +
                "Object.defineProperty(navigator, 'plugins', { get: () => [1, 2, 3, 4, 5] });" +
                "Object.defineProperty(navigator, 'languages', { get: () => ['zh-CN', 'zh', 'en'] });" +
                "Object.defineProperty(navigator, 'platform', { get: () => 'Win32' });" +
                "delete window.__playwright;" +
                "delete window.__pw_manual;";
        page.addInitScript(script);
    }

    public String takeScreenshot(Page page, Long accountId, String signDate, String targetName) {
        try {
            String screenshotDir = screenshotsPath + "/" + signDate;
            Files.createDirectories(Paths.get(screenshotDir));
            String filename = accountId + "_" + targetName + "_" + System.currentTimeMillis() + ".png";
            String path = screenshotDir + "/" + filename;
            page.screenshot(new Page.ScreenshotOptions().setPath(Paths.get(path)));
            return path;
        } catch (Exception e) {
            log.error("截图失败", e);
            return null;
        }
    }

    /**
     * 直接保存 Cookie 字符串到状态文件（不使用浏览器）
     */
    public void saveCookie(PlatformAccount account, String cookieString) {
        try {
            String profilePath = profilesPath + "/" + account.getUserId() + "/" + account.getPlatform() + "/" + account.getId();
            Files.createDirectories(Paths.get(profilePath));

            String domain = "weibo".equals(account.getPlatform()) ? ".weibo.com" : ".baidu.com";
            String url = "weibo".equals(account.getPlatform()) ? "https://weibo.com" : "https://tieba.baidu.com";

            // 解析 Cookie 并构建 Playwright storage state 格式
            List<Map<String, Object>> cookiesList = new ArrayList<>();
            String[] cookieParts = cookieString.split(";");
            for (String cookiePart : cookieParts) {
                cookiePart = cookiePart.trim();
                if (cookiePart.isEmpty()) continue;

                int eqIndex = cookiePart.indexOf('=');
                if (eqIndex > 0) {
                    String name = cookiePart.substring(0, eqIndex).trim();
                    String value = cookiePart.substring(eqIndex + 1).trim();

                    Map<String, Object> cookieMap = new HashMap<>();
                    cookieMap.put("name", name);
                    cookieMap.put("value", value);
                    cookieMap.put("domain", domain);
                    cookieMap.put("path", "/");
                    cookieMap.put("secure", true);
                    cookieMap.put("sameSite", "None");
                    cookieMap.put("httpOnly", false);
                    cookiesList.add(cookieMap);
                }
            }

            if (cookiesList.isEmpty()) {
                throw new RuntimeException("未解析到有效的 Cookie");
            }

            // 构建 storage state JSON
            Map<String, Object> state = new HashMap<>();
            state.put("cookies", cookiesList);
            state.put("origins", new ArrayList<>());

            // 写入文件
            String storagePath = profilePath + "/state.json";
            ObjectMapper mapper = new ObjectMapper();
            mapper.writerWithDefaultPrettyPrinter().writeValue(new File(storagePath), state);

            log.info("Cookie 已保存: accountId={}, path={}, cookieCount={}", account.getId(), storagePath, cookiesList.size());
        } catch (Exception e) {
            log.error("保存 Cookie 失败: accountId={}", account.getId(), e);
            throw new RuntimeException("保存 Cookie 失败: " + e.getMessage());
        }
    }
}
