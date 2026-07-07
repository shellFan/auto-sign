# 自动签到系统

百度贴吧 + 微博超话自动签到系统

## 功能特性

- ✅ 百度贴吧自动签到
- ✅ 微博超话自动签到
- ✅ 微博超话发帖
- ✅ 微博超话回复帖子
- ✅ 网页后台查看任务状态
- ✅ 内嵌远程浏览器手动登录
- ✅ Cookie 登录支持
- ✅ 保存登录态后每日自动执行

## 技术栈

### 后端
- Spring Boot 2.7.x (JDK 8)
- MyBatis-Plus
- MySQL
- Redis
- Playwright Java
- JWT

### 前端
- Vue 3
- Element Plus
- Axios
- Pinia

### 浏览器自动化
- Playwright Chromium
- Chrome Remote Debugging (CDP)

## 快速开始

### 1. 配置数据库

复制配置文件模板：
```bash
cp backend/src/main/resources/application-example.yml backend/src/main/resources/application.yml
```

修改 `application.yml` 中的数据库和 Redis 配置。

### 2. 初始化数据库

执行 `sql/init.sql` 创建数据库表。

### 3. 启动后端

```bash
cd backend
mvn clean package -DskipTests
java -jar target/auto-sign-1.0.0.jar
```

### 4. 启动前端

```bash
cd frontend
npm install
npm run dev
```

### 5. 启动 Chrome 远程调试

```bash
chrome.exe --remote-debugging-port=9222 --user-data-dir=C:\temp\chrome-autosign
```

## 使用说明

### 登录系统

1. 打开 http://localhost:3000
2. 使用默认账号登录：admin / admin123

### 添加账号

1. 进入「平台账号」页面
2. 点击「添加账号」
3. 选择平台（百度贴吧/微博）

### 登录微博

**方式一：内嵌浏览器登录**
1. 点击「浏览器登录」按钮
2. 在弹出的浏览器中手动登录微博
3. 点击「保存登录态」

**方式二：Cookie 登录**
1. 在 Chrome 中登录微博
2. 按 F12 打开开发者工具
3. 在 Console 中输入 `document.cookie` 并复制
4. 点击「输入Cookie」按钮并粘贴

### 签到

1. 进入「微博超话」页面
2. 点击「签到」按钮

### 发帖/回复

1. 在 Chrome 中打开超话页面
2. 调用 API 发帖或回复

## API 接口

| 功能 | 方法 | 端点 |
|------|------|------|
| 登录 | POST | `/api/auth/login` |
| 签到 | POST | `/api/weibo/topic/{id}/sign` |
| 发帖 | POST | `/api/weibo/topic/{id}/post` |
| 回复 | POST | `/api/weibo/topic/{id}/reply` |

## 定时任务

- 百度贴吧签到：每天 08:10
- 微博超话签到：每天 08:20

## 注意事项

1. 不保存用户明文密码
2. 登录必须由用户手动完成
3. 不破解验证码
4. 不绕过风控
5. 出现验证码时任务立即暂停

## License

MIT
