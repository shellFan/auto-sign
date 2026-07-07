#!/bin/bash

# 自动签到系统部署脚本

set -e

echo "=== 自动签到系统部署脚本 ==="

# 检查 Docker 和 Docker Compose
if ! command -v docker &> /dev/null; then
    echo "错误: 未安装 Docker"
    exit 1
fi

if ! command -v docker-compose &> /dev/null; then
    echo "错误: 未安装 Docker Compose"
    exit 1
fi

# 创建必要的目录
echo "创建存储目录..."
mkdir -p storage/profiles
mkdir -p storage/cookies
mkdir -p storage/screenshots
mkdir -p storage/logs

# 检查配置文件
if [ ! -f "backend/src/main/resources/application.yml" ]; then
    echo "请先复制并修改配置文件:"
    echo "cp backend/src/main/resources/application-example.yml backend/src/main/resources/application.yml"
    exit 1
fi

# 构建和启动服务
echo "构建和启动服务..."
docker-compose up -d --build

echo "=== 部署完成 ==="
echo "前端地址: http://localhost"
echo "后端 API: http://localhost:8080"
echo ""
echo "默认管理员账号:"
echo "用户名: admin"
echo "密码: admin123"
echo ""
echo "请修改默认密码！"
