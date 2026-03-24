#!/bin/bash

# CloudTeachingAI 前端启动脚本

set -e

echo "=== CloudTeachingAI 前端启动 ==="

cd "$(dirname "$0")"

# 检查 node_modules 是否存在
if [ ! -d "node_modules" ]; then
    echo "安装依赖..."
    npm install
fi

echo ""
echo "启动开发服务器..."
echo "前端地址: http://localhost:3000"
echo ""
echo "确保后端服务已启动:"
echo "  - auth-service:  http://localhost:8001"
echo "  - user-service:  http://localhost:8002"
echo ""

npm run dev