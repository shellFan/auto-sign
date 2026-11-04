# 微博超话签到 Docker 部署

本文说明微博超话签到在 Docker Compose 环境中的浏览器连接、登录态持久化和首次登录流程。

## Linux 服务器部署

服务器专用配置为 `docker-compose.server.yml`，会创建独立的 MariaDB 和 Redis，不连接或修改主机上已有数据库。按该文件构建时，后端会将 `application-example.yml` 作为 Spring Boot 配置加载。

首次部署前，在部署目录创建权限为 `600` 的 `.env`，设置 `MYSQL_ROOT_PASSWORD`、`DB_PASSWORD`、`REDIS_PASSWORD` 和至少 32 字节的随机 `JWT_SECRET`，再运行：

```bash
docker compose -f docker-compose.server.yml up -d --build
```

服务内存上限：后端 384 MB、Chromium 640 MB、MariaDB 320 MB、Redis 80 MB、前端 96 MB。前端只绑定服务器 `127.0.0.1:13000`，noVNC 只绑定 `127.0.0.1:6080`，不会将登录页面、远程浏览器或 CDP 端口直接发布到公网。

从管理员电脑建立 SSH 隧道：

```bash
ssh -L 13000:127.0.0.1:13000 -L 6080:127.0.0.1:6080 root@<server>
```

然后打开 `http://127.0.0.1:13000`。默认应用账号为 `admin`，初始密码为 `admin123`；首次登录后应立即更换密码。

## 浏览器地址

后端需要以下两个环境变量：

| 变量 | 用途 | Compose 建议值 |
| --- | --- | --- |
| `BROWSER_CDP_URL` | 后端通过 Chrome DevTools Protocol 连接 Chromium worker | `http://playwright-worker:9222` |
| `BROWSER_VNC_URL` | 管理员在账号页面进行手动登录时打开的 noVNC 地址 | 默认 `http://localhost:6080/vnc.html?autoconnect=true` |

`BROWSER_CDP_URL` 使用 Compose 服务名 `playwright-worker`，只供 Compose 内部网络访问。无需把 9222 端口发布到宿主机或公网。

Compose 默认只把 noVNC 绑定到服务器的 `127.0.0.1:6080`，以免把无密码的浏览器控制端口直接暴露到公网。管理员可用 SSH 本地端口转发访问：

```bash
ssh -L 6080:127.0.0.1:6080 <user>@<server>
```

保持隧道连接，并在账号页面使用默认的 `BROWSER_VNC_URL`。若通过 HTTPS 反向代理提供 noVNC，应配置 WebSocket 转发与访问控制，并将 `BROWSER_VNC_URL` 设为该受保护的 HTTPS 地址。

## 持久化登录态

后端和 `playwright-worker` 必须把同一个宿主机目录挂载到 `/app/storage`，例如：

```yaml
services:
  backend:
    volumes:
      - ./storage:/app/storage
  playwright-worker:
    volumes:
      - ./storage:/app/storage
```

每个微博账号使用独立的登录态文件：

```text
/app/storage/profiles/{userId}/weibo/{accountId}/state.json
```

对应的宿主机路径为：

```text
./storage/profiles/{userId}/weibo/{accountId}/state.json
```

`userId` 和 `accountId` 共同隔离账号状态。请持久化整个 `./storage` 目录，并限制该目录的访问权限；其中的 `state.json` 包含登录凭据，不应提交到版本控制或复制到日志。

## 首次登录

1. 启动 Compose 服务，并确认管理端浏览器能访问 `BROWSER_VNC_URL`。
2. 在账号管理页面为目标微博账号发起“浏览器登录”。
3. 打开页面中的 noVNC 会话，在 Chromium 中手动完成微博登录及必要验证。
4. 返回账号管理页面，点击“保存登录态”。后端会保存当前浏览器 context，关闭临时登录会话，并重新加载登录态进行验证；通过后账号状态更新为“已登录”。
5. 确认对应账号目录下已生成 `state.json`。之后的登录检查和超话签到会加载该账号自己的登录态。

账号登录失效或微博要求人工验证时，重复上述流程并重新保存。不要尝试绕过微博的验证流程。

浏览器 worker 使用 Playwright 镜像自带的 Chromium，并由容器启动脚本自动定位可执行文件。若自定义了浏览器镜像目录，可通过 `CHROME_BIN` 指定 Chromium 的完整路径。

## 调度与时区

微博每日自动任务在 `Asia/Shanghai` 的 `08:00–22:00` 分散执行，包含每个启用超话的签到、发帖、评论、转发和浏览。后端自动生成持久化计划，每次调度只执行一个到期动作；重启不重抽数量。部署时应将后端和浏览器容器的时区都设置为 `Asia/Shanghai`。每日额度、模板和暂停恢复详见 [每日自动任务](weibo-daily-automation.md)。

## 部署后检查

部署后可检查以下项目：

- 后端容器能通过 `http://playwright-worker:9222` 连接 Chromium worker。
- 宿主机没有对公网暴露 CDP 的 9222 端口。
- 管理员电脑能打开 `BROWSER_VNC_URL` 并操作 noVNC。
- 保存登录态后，后端和 worker 容器都能看到同一个账号 `state.json`。
- 重启容器后，登录检查仍能加载该账号的已保存状态。
- 不同账号的状态文件位于各自的 `{userId}/weibo/{accountId}` 目录中。
- 「任务控制」能显示当天各超话目标与分散安排的任务，重启后数量和进度保持。

这些检查需要在实际部署环境中执行；本文不表示该部署已经完成运行验证。
