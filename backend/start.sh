#!/bin/bash

# CloudTeachingAI 快速启动脚本
# 用于启动后端服务

set -e

echo "=== CloudTeachingAI 快速启动 ==="

# 检查 Docker 是否运行
if ! docker info > /dev/null 2>&1; then
    echo "错误: Docker 未运行，请先启动 Docker"
    exit 1
fi

# 切换到 backend 目录
cd "$(dirname "$0")"

# 启动基础设施
echo ""
echo "1. 启动基础设施..."
docker compose up -d

# 等待数据库就绪
echo "   等待数据库就绪..."
sleep 5

# 检查数据库连接
until docker exec auth-db pg_isready -U postgres > /dev/null 2>&1; do
    echo "   等待 auth-db..."
    sleep 2
done
until docker exec user-db pg_isready -U postgres > /dev/null 2>&1; do
    echo "   等待 user-db..."
    sleep 2
done

echo "   基础设施已就绪"

# 启动后端服务
echo ""
echo "2. 启动后端服务..."
echo "   auth-service (端口 8001)"
echo "   user-service (端口 8002)"

# 使用后台进程启动服务
(cd auth-service && mvn spring-boot:run -q > /tmp/auth-service.log 2>&1) &
AUTH_PID=$!
echo "   auth-service PID: $AUTH_PID"

(cd user-service && mvn spring-boot:run -q > /tmp/user-service.log 2>&1) &
USER_PID=$!
echo "   user-service PID: $USER_PID"

echo ""
echo "=== 服务启动中 ==="
echo "auth-service 日志: /tmp/auth-service.log"
echo "user-service 日志: /tmp/user-service.log"
echo ""
echo "等待服务就绪..."

# 等待服务就绪
sleep 10

# 检查服务是否启动成功
AUTH_READY=false
USER_READY=false

for i in {1..30}; do
    if curl -s http://localhost:8001/actuator/health > /dev/null 2>&1 || curl -s http://localhost:8001/api/v1/auth/login -X POST -H "Content-Type: application/json" -d '{}' > /dev/null 2>&1; then
        AUTH_READY=true
        echo "   ✓ auth-service 已就绪"
        break
    fi
    sleep 2
done

for i in {1..30}; do
    if curl -s http://localhost:8002/api/v1/users/1 > /dev/null 2>&1; then
        USER_READY=true
        echo "   ✓ user-service 已就绪"
        break
    fi
    sleep 2
done

echo ""
echo "=== 启动完成 ==="
echo ""
echo "服务地址:"
echo "  - auth-service:  http://localhost:8001"
echo "  - user-service:  http://localhost:8002"
echo ""
echo "测试命令:"
echo "  # 创建用户"
echo "  curl -X POST http://localhost:8002/api/v1/admin/users \\"
echo "    -H 'Content-Type: application/json' \\"
echo "    -d '{\"username\":\"测试学生\",\"email\":\"student@test.com\",\"password\":\"Test123456\",\"role\":\"STUDENT\"}'"
echo ""
echo "  # 登录"
echo "  curl -X POST http://localhost:8001/api/v1/auth/login \\"
echo "    -H 'Content-Type: application/json' \\"
echo "    -d '{\"email\":\"student@test.com\",\"password\":\"Test123456\"}'"
echo ""
echo "按 Ctrl+C 停止服务"

# 等待用户中断
wait