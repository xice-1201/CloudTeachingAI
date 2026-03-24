#!/bin/bash

# CloudTeachingAI 服务器初始化脚本
# 用于首次部署前初始化服务器环境

set -e

echo "=== CloudTeachingAI 服务器初始化 ==="
echo "此脚本将安装 Docker 和 Docker Compose"

# 检查是否为 root 用户
if [ "$EUID" -ne 0 ]; then
    echo "请使用 root 用户或 sudo 运行此脚本"
    exit 1
fi

# 更新系统
echo ""
echo "1. 更新系统包..."
apt-get update && apt-get upgrade -y

# 安装必要的工具
echo ""
echo "2. 安装必要工具..."
apt-get install -y \
    apt-transport-https \
    ca-certificates \
    curl \
    gnupg \
    lsb-release \
    git \
    vim

# 安装 Docker
echo ""
echo "3. 安装 Docker..."
if ! command -v docker &> /dev/null; then
    curl -fsSL https://get.docker.com | bash
    systemctl enable docker
    systemctl start docker
    echo "Docker 安装完成"
else
    echo "Docker 已安装，跳过"
fi

# 安装 Docker Compose
echo ""
echo "4. 安装 Docker Compose..."
if ! command -v docker-compose &> /dev/null; then
    curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
    chmod +x /usr/local/bin/docker-compose
    echo "Docker Compose 安装完成"
else
    echo "Docker Compose 已安装，跳过"
fi

# 创建部署目录
echo ""
echo "5. 创建部署目录..."
DEPLOY_PATH="/opt/cloudteachingai"
mkdir -p $DEPLOY_PATH
mkdir -p $DEPLOY_PATH/logs
mkdir -p $DEPLOY_PATH/postgres-data
mkdir -p $DEPLOY_PATH/redis-data

echo "部署目录创建完成: $DEPLOY_PATH"

# 配置防火墙
echo ""
echo "6. 配置防火墙..."
if command -v ufw &> /dev/null; then
    ufw allow 22/tcp    # SSH
    ufw allow 3000/tcp  # Frontend
    ufw allow 8001/tcp  # auth-service
    ufw allow 8002/tcp  # user-service
    ufw --force enable
    echo "防火墙配置完成"
else
    echo "ufw 未安装，跳过防火墙配置"
    echo "请手动配置防火墙开放端口: 22, 3000, 8001, 8002"
fi

# 显示版本信息
echo ""
echo "=== 安装完成 ==="
echo ""
echo "Docker 版本:"
docker --version
echo ""
echo "Docker Compose 版本:"
docker-compose --version
echo ""
echo "下一步:"
echo "1. 在 GitHub 仓库设置中添加 Secrets"
echo "2. 推送代码到 main 分支触发自动部署"
echo ""
echo "GitHub Secrets 列表:"
echo "  - SERVER_HOST: 服务器 IP 地址"
echo "  - SERVER_USER: SSH 用户名 (如 root)"
echo "  - SERVER_SSH_KEY: SSH 私钥内容"
echo "  - SERVER_PORT: SSH 端口 (默认 22)"
echo "  - DB_PASSWORD: 数据库密码"
echo "  - JWT_PRIVATE_KEY: JWT 私钥"
echo "  - JWT_PUBLIC_KEY: JWT 公钥"