package com.autosign.service;

import com.autosign.browser.BrowserSession;
import com.autosign.entity.PlatformAccount;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Service
public class BrowserService {

    private static final Logger log = LoggerFactory.getLogger(BrowserService.class);

    @Value("${storage.profiles-path}")
    private String profilesPath;

    @Value("${storage.screenshots-path}")
    private String screenshotsPath;

    @Value("${browser.cdp-url:http://localhost:9222}")
    private String cdpUrl;

    @Value("${browser.vnc-url:http://localhost:6080/vnc.html?autoconnect=true}")
    private String vncUrl;

    private volatile Playwright playwright;
    private volatile Browser userBrowser;
    private volatile boolean initialized = false;
    private volatile boolean initFailed = false;
    private final Object lock = new Object();

    /**
     * 懒初始化 Playwright，失败不会崩溃
     */
    public Playwright getPlaywright() {
        if (initialized && playwright != null) return playwright;
        if (initFailed) return null;
        synchronized (lock) {
            if (initialized && playwright != null) return playwright;
            if (initFailed) return null;
            try {
                log.info("正在初始化 Playwright...");
                // 禁用 jul-to-slf4j 桥接以避免 NoClassDefFoundError
                java.util.logging.Logger rootLogger = java.util.logging.Logger.getLogger("");
                for (java.util.logging.Handler handler : rootLogger.getHandlers()) {
                    rootLogger.removeHandler(handler);
                }
                playwright = Playwright.create();
                initialized = true;
                log.info("Playwright 初始化完成");
                return playwright;
            } catch (Throwable e) {
                initFailed = true;
                log.error("Playwright 初始化失败: {}", e.getMessage());
                return null;
            }
        }
    }

    /**
     * 连接到用户 Chrome（CDP 方式）
     */
    public synchronized <T> T executeSerialized(Supplier<T> operation) {
        return operation.get();
    }

    public synchronized Browser connectToUserChrome() {
        Browser connected = userBrowser;
        if (connected != null && connected.isConnected()) {
            return connected;
        }
        Playwright pw = getPlaywright();
        if (pw == null) {
            log.warn("Playwright 未初始化");
            return null;
        }
        synchronized (lock) {
            connected = userBrowser;
            if (connected != null && connected.isConnected()) {
                return connected;
            }
            try {
                userBrowser = pw.chromium().connectOverCDP(cdpUrl);
                return userBrowser;
            } catch (Exception e) {
                log.warn("无法连接到浏览器 worker {}: {}", cdpUrl, e.getMessage());
                userBrowser = null;
                return null;
            }
        }
    }

    public synchronized BrowserContext loadWeiboContext(PlatformAccount account) {
        Browser browser = connectToUserChrome();
        if (browser == null) {
            return null;
        }

        String storagePath = profilesPath + "/" + account.getUserId() + "/weibo/" + account.getId() + "/state.json";
        java.nio.file.Path statePath = Paths.get(storagePath);
        Browser.NewContextOptions options = createContextOptions();
        if (Files.exists(statePath)) {
            options.setStorageStatePath(statePath);
            log.info("加载微博账号登录态: accountId={}", account.getId());
        } else {
            log.info("微博账号尚无保存的登录态: accountId={}", account.getId());
        }

        try {
            return browser.newContext(options);
        } catch (Exception e) {
            log.error("创建微博账号浏览器 context 失败: accountId={}", account.getId(), e);
            return null;
        }
    }

    private Browser.NewContextOptions createContextOptions() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
        return new Browser.NewContextOptions()
                .setViewportSize(1920, 1080)
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .setLocale("zh-CN")
                .setTimezoneId("Asia/Shanghai")
                .setExtraHTTPHeaders(headers);
    }

    public synchronized void destroy() {
        if (playwright != null) {
            try { playwright.close(); } catch (Exception e) { /* ignore */ }
        }
    }

    public synchronized BrowserContext loadContext(Long accountId, String platform, Long userId) {
        Playwright pw = getPlaywright();
        if (pw == null) {
            log.error("Playwright 未初始化");
            return null;
        }
        
        String storagePath = profilesPath + "/" + userId + "/" + platform + "/" + accountId + "/state.json";
        java.nio.file.Path path = Paths.get(storagePath);

        Map<String, String> headers = new HashMap<>();
        headers.put("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");

        Browser browser = pw.chromium().launch(new BrowserType.LaunchOptions()
                .setHeadless(false)
                .setArgs(Arrays.asList(
                        "--no-sandbox",
                        "--disable-setuid-sandbox",
                        "--disable-blink-features=AutomationControlled",
                        "--start-maximized",
                        "--disable-infobars",
                        "--window-size=1920,1080"
                )));

        if (Files.exists(path)) {
            log.info("加载已保存的登录态: accountId={}, path={}", accountId, storagePath);
            return browser.newContext(new Browser.NewContextOptions()
                    .setViewportSize(1920, 1080)
                    .setStorageStatePath(path)
                    .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .setLocale("zh-CN")
                    .setTimezoneId("Asia/Shanghai")
                    .setExtraHTTPHeaders(headers));
        }

        log.warn("未找到已保存的登录态: accountId={}", accountId);
        return browser.newContext(new Browser.NewContextOptions()
                .setViewportSize(1920, 1080)
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .setLocale("zh-CN")
                .setTimezoneId("Asia/Shanghai")
                .setExtraHTTPHeaders(headers));
    }

    public void saveCookie(PlatformAccount account, String cookieString) {
        try {
            String profilePath = profilesPath + "/" + account.getUserId() + "/" + account.getPlatform() + "/" + account.getId();
            Files.createDirectories(Paths.get(profilePath));
            Files.createDirectories(Paths.get(screenshotsPath));

            String domain = "weibo".equals(account.getPlatform()) ? ".weibo.com" : ".baidu.com";

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

            Map<String, Object> state = new HashMap<>();
            state.put("cookies", cookiesList);
            state.put("origins", new ArrayList<>());

            String storagePath = profilePath + "/state.json";
            ObjectMapper mapper = new ObjectMapper();
            mapper.writerWithDefaultPrettyPrinter().writeValue(new File(storagePath), state);

            log.info("Cookie 已保存: accountId={}, path={}, cookieCount={}", account.getId(), storagePath, cookiesList.size());
        } catch (Exception e) {
            log.error("保存 Cookie 失败: accountId={}", account.getId(), e);
            throw new RuntimeException("保存 Cookie 失败: " + e.getMessage());
        }
    }

    public String loadCookie(PlatformAccount account) {
        try {
            String storagePath = profilesPath + "/" + account.getUserId() + "/" + account.getPlatform() + "/" + account.getId() + "/state.json";
            File file = new File(storagePath);
            if (!file.exists()) {
                log.warn("Cookie 文件不存在: {}", storagePath);
                return null;
            }
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> state = mapper.readValue(file, Map.class);
            List<Map<String, Object>> cookies = (List<Map<String, Object>>) state.get("cookies");
            if (cookies == null || cookies.isEmpty()) {
                return null;
            }
            StringBuilder sb = new StringBuilder();
            for (Map<String, Object> cookie : cookies) {
                if (sb.length() > 0) sb.append("; ");
                sb.append(cookie.get("name")).append("=").append(cookie.get("value"));
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("加载 Cookie 失败: accountId={}", account.getId(), e);
            return null;
        }
    }

    private final Map<String, BrowserSession> sessions = new ConcurrentHashMap<>();

    public synchronized Map<String, Object> createSession(PlatformAccount account) {
        Playwright pw = getPlaywright();
        if (pw == null) {
            throw new RuntimeException("Playwright 未初始化");
        }
        
        String sessionId = UUID.randomUUID().toString();
        String profilePath = profilesPath + "/" + account.getUserId() + "/" + account.getPlatform() + "/" + account.getId();

        Browser browser;
        BrowserContext context;
        if ("weibo".equals(account.getPlatform())) {
            browser = connectToUserChrome();
            if (browser == null) {
                throw new RuntimeException("无法连接浏览器 worker，请检查 BROWSER_CDP_URL: " + cdpUrl);
            }
            String statePath = profilePath + "/state.json";
            Browser.NewContextOptions options = createContextOptions();
            if (Files.exists(Paths.get(statePath))) {
                options.setStorageStatePath(Paths.get(statePath));
            }
            context = browser.newContext(options);
        } else {
            browser = pw.chromium().launch(new BrowserType.LaunchOptions()
                    .setHeadless(false)
                    .setArgs(Arrays.asList(
                            "--no-sandbox",
                            "--disable-setuid-sandbox",
                            "--disable-blink-features=AutomationControlled",
                            "--start-maximized",
                            "--disable-infobars",
                            "--window-size=1920,1080"
                    )));
            context = browser.newContext(createContextOptions());
        }
        
        Page page = context.newPage();

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
        result.put("vncUrl", vncUrl);
        return result;
    }

    public Map<String, Object> getSession(String sessionId) {
        BrowserSession session = sessions.get(sessionId);
        Map<String, Object> result = new HashMap<>();
        if (session != null) {
            result.put("sessionId", session.getSessionId());
            result.put("active", session.isActive());
            result.put("vncUrl", vncUrl);
        }
        return result;
    }

    public synchronized Long saveSession(String sessionId) {
        BrowserSession session = sessions.get(sessionId);
        if (session == null || session.getContext() == null) {
            throw new IllegalArgumentException("浏览器会话不存在或已关闭");
        }

        try {
            String storagePath = session.getProfilePath() + "/state.json";
            Files.createDirectories(Paths.get(session.getProfilePath()));
            session.getContext().storageState(new BrowserContext.StorageStateOptions()
                    .setPath(Paths.get(storagePath)));
            Long accountId = session.getAccountId();
            closeSession(sessionId);
            log.info("浏览器登录态已保存: accountId={}", accountId);
            return accountId;
        } catch (Exception e) {
            log.error("保存浏览器会话失败: sessionId={}", sessionId, e);
            throw new RuntimeException("保存浏览器登录态失败: " + e.getMessage(), e);
        }
    }

    public synchronized void closeSession(String sessionId) {
        BrowserSession session = sessions.remove(sessionId);
        if (session != null) {
            try {
                if (session.getContext() != null) {
                    session.getContext().close();
                }
                session.setActive(false);
                log.info("浏览器会话已关闭: sessionId={}", sessionId);
            } catch (Exception e) {
                log.error("关闭浏览器会话失败", e);
            }
        }
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
}
