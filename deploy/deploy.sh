#!/bin/bash

# CloudTeachingAI 手动部署脚本
# 用于手动触发部署或更新服务

set -e

DEPLOY_PATH="/opt/cloudteachingai"
COMPOSE_FILE="docker-compose.prod.yml"

echo "=== CloudTeachingAI 部署脚本 ==="

# 检查部署目录
if [ ! -d "$DEPLOY_PATH" ]; then
    echo "错误: 部署目录不存在: $DEPLOY_PATH"
    echo "请先运行 init-server.sh 初始化服务器"
    exit 1
fi

cd $DEPLOY_PATH

# 检查 .env 文件
if [ ! -f .env ]; then
    echo "错误: .env 文件不存在"
    echo "请创建 .env 文件并配置环境变量"
    exit 1
fi

# 拉取最新代码
echo ""
echo "1. 拉取最新代码..."
if [ -d .git ]; then
    git pull origin main
else
    echo "警告: 不是 Git 仓库，跳过代码拉取"
fi

# 停止旧容器
echo ""
echo "2. 停止旧容器..."
docker-compose -f $COMPOSE_FILE down --remove-orphans || true

# 构建镜像
echo ""
echo "3. 构建 Docker 镜像..."
docker-compose -f $COMPOSE_FILE build

# 启动服务
echo ""
echo "4. 启动服务..."
docker-compose -f $COMPOSE_FILE up -d

# 等待服务启动
echo ""
echo "5. 等待服务启动..."
sleep 15

# 显示服务状态
echo ""
echo "=== 服务状态 ==="
docker-compose -f $COMPOSE_FILE ps

# 显示日志
echo ""
echo "=== 最近日志 ==="
docker-compose -f $COMPOSE_FILE logs --tail=20

echo ""
echo "=== 部署完成 ==="
echo ""
echo "访问地址:"
echo "  前端: http://$(curl -s ifconfig.me):3000"
echo "  auth-service: http://$(curl -s ifconfig.me):8001"
echo "  user-service: http://$(curl -s ifconfig.me):8002"
echo ""
echo "常用命令:"
echo "  查看日志: docker-compose -f $COMPOSE_FILE logs -f"
echo "  重启服务: docker-compose -f $COMPOSE_FILE restart"
echo "  停止服务: docker-compose -f $COMPOSE_FILE down"