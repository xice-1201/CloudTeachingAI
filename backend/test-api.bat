@echo off
REM Windows 批处理脚本 - 用户注册和登录功能测试

setlocal enabledelayedexpansion

set BASE_URL_AUTH=http://localhost:8001
set BASE_URL_USER=http://localhost:8002

echo ==========================================
echo CloudTeachingAI 用户注册和登录功能测试
echo ==========================================
echo.

REM 测试步骤 1: 创建用户
echo [步骤 1] 创建测试用户
echo POST %BASE_URL_USER%/api/v1/admin/users
echo.

curl -X POST "%BASE_URL_USER%/api/v1/admin/users" ^
  -H "Content-Type: application/json" ^
  -d "{\"username\":\"测试学生\",\"email\":\"student@test.com\",\"password\":\"Test123456\",\"role\":\"STUDENT\"}"

echo.
echo.
timeout /t 2 /nobreak >nul

REM 测试步骤 2: 用户登录
echo [步骤 2] 用户登录
echo POST %BASE_URL_AUTH%/api/v1/auth/login
echo.

curl -X POST "%BASE_URL_AUTH%/api/v1/auth/login" ^
  -H "Content-Type: application/json" ^
  -d "{\"email\":\"student@test.com\",\"password\":\"Test123456\"}"

echo.
echo.
timeout /t 2 /nobreak >nul

REM 测试步骤 3: 测试错误密码
echo [步骤 3] 测试错误密码登录
echo.

curl -X POST "%BASE_URL_AUTH%/api/v1/auth/login" ^
  -H "Content-Type: application/json" ^
  -d "{\"email\":\"student@test.com\",\"password\":\"WrongPassword\"}"

echo.
echo.

echo ==========================================
echo 测试完成！
echo ==========================================
echo.
echo 请查看上面的响应结果
echo.

pause
