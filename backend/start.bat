@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

echo === CloudTeachingAI 快速启动 ===

REM 检查 Docker 是否运行
docker info >nul 2>&1
if errorlevel 1 (
    echo 错误: Docker 未运行，请先启动 Docker Desktop
    exit /b 1
)

REM 切换到 backend 目录
cd /d "%~dp0"

REM 启动基础设施
echo.
echo 1. 启动基础设施...
docker compose up -d

REM 等待数据库就绪
echo    等待数据库就绪...
timeout /t 5 /nobreak >nul

echo    基础设施已就绪

REM 启动后端服务
echo.
echo 2. 启动后端服务...
echo    auth-service ^(端口 8001^)
echo    user-service ^(端口 8002^)

REM 启动 auth-service
start "auth-service" cmd /c "cd auth-service && mvn spring-boot:run"
timeout /t 3 /nobreak >nul

REM 启动 user-service
start "user-service" cmd /c "cd user-service && mvn spring-boot:run"

echo.
echo === 服务启动中 ===
echo.
echo 等待服务就绪 ^(约 30 秒^)...
timeout /t 30 /nobreak >nul

echo.
echo === 启动完成 ===
echo.
echo 服务地址:
echo   - auth-service:  http://localhost:8001
echo   - user-service:  http://localhost:8002
echo.
echo 测试命令:
echo   # 创建用户
echo   curl -X POST http://localhost:8002/api/v1/admin/users -H "Content-Type: application/json" -d "{\"username\":\"测试学生\",\"email\":\"student@test.com\",\"password\":\"Test123456\",\"role\":\"STUDENT\"}"
echo.
echo   # 登录
echo   curl -X POST http://localhost:8001/api/v1/auth/login -H "Content-Type: application/json" -d "{\"email\":\"student@test.com\",\"password\":\"Test123456\"}"
echo.
echo 关闭此窗口不会停止服务
echo 要停止服务，请运行: docker compose down
echo.

pause