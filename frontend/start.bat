@echo off
chcp 65001 >nul

echo === CloudTeachingAI 前端启动 ===

cd /d "%~dp0"

REM 检查 node_modules 是否存在
if not exist "node_modules" (
    echo 安装依赖...
    call npm install
)

echo.
echo 启动开发服务器...
echo 前端地址: http://localhost:3000
echo.
echo 确保后端服务已启动:
echo   - auth-service:  http://localhost:8001
echo   - user-service:  http://localhost:8002
echo.

call npm run dev

pause