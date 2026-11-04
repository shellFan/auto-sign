# 微博超话签到服务器运行实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 Linux Docker 部署中让微博超话签到和手动登录使用服务器 Chromium worker，并按账号持久化登录态。

**Architecture:** Spring Boot 后端通过配置的 CDP URL 连接 Playwright Chromium worker。微博签到和登录检查用账号独立 context 加载共享 storage 卷中的 state.json；微博手动登录会话也在 worker 上创建并写回相同路径。贴吧与微博内容互动任务保持其当前执行方式。

**Tech Stack:** Spring Boot 2.7.18、Java 8、Playwright Java 1.35.0、Docker Compose、Chromium/noVNC。

## Global Constraints

- 只调整微博签到、微博登录检查及其手动登录会话的服务器执行链路。
- 账号登录态路径为 `storage/profiles/{userId}/weibo/{accountId}/state.json`。
- 每日微博签到仍为 08:20，使用 Asia/Shanghai 时区。
- CDP 仅供 Compose 内网访问；共享 storage 卷须在后端和浏览器 worker 中一致挂载。
- 浏览器任务通过 `BrowserService` 的串行入口执行，避免共享 Playwright 连接被并发调用。
- 不把真实 Cookie 或登录态写入日志、文档或版本控制。

---

### Task 1: 配置后端的远端 Chromium 连接和账号 context

**Files:**
- Modify: `backend/src/main/java/com/autosign/service/BrowserService.java`
- Modify: `backend/src/main/resources/application-example.yml`
- Modify: `backend/src/main/java/com/autosign/scheduler/SignScheduler.java`
- Modify: `backend/src/main/java/com/autosign/adapter/WeiboSignInAdapter.java`
- Modify: `backend/src/main/java/com/autosign/adapter/TiebaSignInAdapter.java`

**Interfaces:**
- `BrowserService.connectToUserChrome()` 读取 `browser.cdp-url`，本机缺省为 `http://localhost:9222`。
- `BrowserService` 的 noVNC URL 读取 `browser.vnc-url`，缺省 `http://localhost:6080/vnc.html?autoconnect=true`。
- 所有 adapter 的完整浏览器操作通过 `BrowserService.executeSerialized(Supplier<T>)` 串行执行。
- 新增 `BrowserService.loadWeiboContext(PlatformAccount account)`，创建独立 context 并从账号 state 文件加载登录态；无 state 文件时创建空 context。
- `WeiboSignInAdapter.signTopic` 与 `checkLogin` 使用该账号 context，并在 finally 中关闭 context 和 page。

- [x] **Step 1: 让 CDP URL 可配置并增加按账号加载的远端 context**

为 BrowserService 注入 `@Value("${browser.cdp-url:http://localhost:9222}")` 字段；将硬编码的 CDP 地址替换为该字段。增加 `loadWeiboContext(PlatformAccount account)`：先连接 CDP，连接失败时返回 `null`；根据 `profilesPath/{userId}/weibo/{accountId}/state.json` 是否存在，使用 `Browser.newContext(NewContextOptions)` 创建 context，存在时设置 storage state 路径。保留现有 `loadContext` 给贴吧使用。

- [x] **Step 2: 将微博签到和登录检查切换到账号 context**

在 `signTopic` 和 `checkLogin` 中使用 `loadWeiboContext(account)`，对 null context 返回现有失败结果；所有控制流在 finally 中关闭 page（若仍打开）和 context。删除这两个入口中对 `contexts().get(0)` 的读取；不修改发帖、回复、转发和浏览方法。

- [x] **Step 3: 配置示例中的浏览器地址**

在 `application-example.yml` 增加：

```yaml
browser:
  cdp-url: ${BROWSER_CDP_URL:http://localhost:9222}
  vnc-url: ${BROWSER_VNC_URL:http://localhost:6080/vnc.html?autoconnect=true}
```

在微博签到 `@Scheduled` 注解上显式设置 `zone = "Asia/Shanghai"`，避免服务器默认时区改变签到时间。

运行 `cd backend; mvn -q -DskipTests package`，确认 Java 8 代码编译成功。

### Task 2: 将微博手动登录会话放到 Chromium worker

**Files:**
- Modify: `backend/src/main/java/com/autosign/service/BrowserService.java`
- Modify: `docker-compose.yml`
- Modify: `backend/src/main/resources/application-example.yml`

**Interfaces:**
- 对 `platform=weibo` 的 `createSession` 使用已连接的 CDP Browser 创建 context；其他平台保留现有本地 launch 分支。
- 手动会话关闭时只关闭该会话的 context，不关闭共享 worker 浏览器。
- 保存会话时将 state 写入当前 profile 路径；所有容器均挂载同一 `storage`。

- [x] **Step 1: 为微博登录会话选择 CDP worker**

在 `createSession` 中，如果平台为 `weibo`，调用 `connectToUserChrome()`，并通过该 Browser 新建 context；如果连接失败，抛出包含 CDP URL 的清晰错误。平台不是微博时保留现有本地 Chromium launch 逻辑。共享 context 默认使用现有 viewport、语言、时区和 user agent。创建微博会话时打开 `https://weibo.com`。将 `getSession` 和 `createSession` 返回的 noVNC 地址改为 `browser.vnc-url` 配置值，允许服务器管理员配置可从浏览器访问的主机名或 HTTPS 代理地址。

- [x] **Step 2: 让 Docker Compose 内网连通并持久化登录态**

后端设置 `BROWSER_CDP_URL=http://playwright-worker:9222`。通过 `BROWSER_VNC_URL` 配置服务器用户可访问的 noVNC URL。删除 playwright-worker 的 `9222:9222` 主机端口映射；noVNC 默认只绑定服务器 loopback，使用 SSH 隧道或受保护的反向代理访问。确认后端与 worker 都将 `./storage:/app/storage` 挂载。将后端和浏览器容器时区设为 `Asia/Shanghai`；定时任务本身也显式指定该时区。

- [x] **Step 3: 检查 Compose 展开配置**

运行 `docker compose config`，确认 CDP URL、服务名、端口和卷映射有效；若本机未装 Docker Compose，记录为环境限制并继续做静态检查。

结果：未能运行，当前工作环境没有 Docker CLI。

### Task 3: 验证行为与部署说明

**Files:**
- Create: `docs/deployment/weibo-docker.md`
- Inspect: `docker-compose.yml`
- Verify: backend Maven package and Docker Compose config

- [x] **Step 1: 更新中文部署说明**

创建独立部署说明，避免触碰当前处于 merge 冲突状态的 README。说明服务器 Docker 部署下 `BROWSER_VNC_URL`、需持久化的 storage 路径、登录态按账号隔离以及 Asia/Shanghai 调度。注明首次部署需打开 noVNC 手动登录微博并保存登录态。

- [x] **Step 2: 执行构建和 Compose 静态验证**

运行 `cd backend; mvn -q -DskipTests package` 和 `docker compose config`。若 Docker daemon 可用，再执行 `docker compose build backend playwright-worker`；不要运行会访问微博的实际签到任务。

### Task 4: 代码差异与安全检查

- [x] **Step 1: 检查最终 diff**

确认变更不包含 `backend/storage` 下的 profile、Cookie、截图或 state 文件；确认只改动本次范围涉及的文件和说明文档，不覆盖已有用户更改。

- [x] **Step 2: 核对计划覆盖**

逐项核对 spec 的远端 CDP、账号隔离、登录态持久化、noVNC、08:20 Asia/Shanghai 定时以及失败时不绕过验证要求；记录无法验证的服务器运行条件。
