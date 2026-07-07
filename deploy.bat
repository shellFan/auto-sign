@echo off
REM 自动签到系统部署脚本 (Windows)

echo === 自动签到系统部署脚本 ===

REM 检查 Docker
where docker >nul 2>nul
if %errorlevel% neq 0 (
    echo 错误: 未安装 Docker
    exit /b 1
)

REM 创建必要的目录
echo 创建存储目录...
if not exist storage\profiles mkdir storage\profiles
if not exist storage\cookies mkdir storage\cookies
if not exist storage\screenshots mkdir storage\screenshots
if not exist storage\logs mkdir storage\logs

REM 检查配置文件
if not exist backend\src\main\resources\application.yml (
    echo 请先复制并修改配置文件:
    echo copy backend\src\main\resources\application-example.yml backend\src\main\resources\application.yml
    exit /b 1
)

REM 构建和启动服务
echo 构建和启动服务...
docker-compose up -d --build

echo === 部署完成 ===
echo 前端地址: http://localhost
echo 后端 API: http://localhost:8080
echo.
echo 默认管理员账号:
echo 用户名: admin
echo 密码: admin123
echo.
echo 请修改默认密码！
pause
