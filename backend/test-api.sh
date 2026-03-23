#!/bin/bash

# 测试脚本 - 用户注册和登录功能

BASE_URL_AUTH="http://localhost:8001"
BASE_URL_USER="http://localhost:8002"

echo "=========================================="
echo "CloudTeachingAI 用户注册和登录功能测试"
echo "=========================================="
echo ""

# 颜色定义
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 测试步骤 1: 创建用户
echo -e "${YELLOW}[步骤 1] 创建测试用户${NC}"
echo "POST $BASE_URL_USER/api/v1/admin/users"
echo ""

CREATE_USER_RESPONSE=$(curl -s -X POST "$BASE_URL_USER/api/v1/admin/users" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "测试学生",
    "email": "student@test.com",
    "password": "Test123456",
    "role": "STUDENT"
  }')

echo "$CREATE_USER_RESPONSE" | jq '.'
echo ""

# 检查是否成功
if echo "$CREATE_USER_RESPONSE" | jq -e '.code == 0' > /dev/null; then
    echo -e "${GREEN}✓ 用户创建成功${NC}"
    USER_ID=$(echo "$CREATE_USER_RESPONSE" | jq -r '.data.id')
    echo "用户 ID: $USER_ID"
else
    echo -e "${RED}✗ 用户创建失败${NC}"
    exit 1
fi
echo ""
echo "=========================================="
echo ""

# 等待 1 秒
sleep 1

# 测试步骤 2: 用户登录
echo -e "${YELLOW}[步骤 2] 用户登录${NC}"
echo "POST $BASE_URL_AUTH/api/v1/auth/login"
echo ""

LOGIN_RESPONSE=$(curl -s -X POST "$BASE_URL_AUTH/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "student@test.com",
    "password": "Test123456"
  }')

echo "$LOGIN_RESPONSE" | jq '.'
echo ""

# 检查是否成功
if echo "$LOGIN_RESPONSE" | jq -e '.code == 0' > /dev/null; then
    echo -e "${GREEN}✓ 登录成功${NC}"
    ACCESS_TOKEN=$(echo "$LOGIN_RESPONSE" | jq -r '.data.accessToken')
    REFRESH_TOKEN=$(echo "$LOGIN_RESPONSE" | jq -r '.data.refreshToken')
    echo "Access Token: ${ACCESS_TOKEN:0:50}..."
    echo "Refresh Token: $REFRESH_TOKEN"
else
    echo -e "${RED}✗ 登录失败${NC}"
    exit 1
fi
echo ""
echo "=========================================="
echo ""

# 等待 1 秒
sleep 1

# 测试步骤 3: 获取用户信息
echo -e "${YELLOW}[步骤 3] 获取用户信息${NC}"
echo "GET $BASE_URL_USER/api/v1/users/$USER_ID"
echo ""

USER_INFO_RESPONSE=$(curl -s -X GET "$BASE_URL_USER/api/v1/users/$USER_ID")

echo "$USER_INFO_RESPONSE" | jq '.'
echo ""

if echo "$USER_INFO_RESPONSE" | jq -e '.code == 0' > /dev/null; then
    echo -e "${GREEN}✓ 获取用户信息成功${NC}"
else
    echo -e "${RED}✗ 获取用户信息失败${NC}"
fi
echo ""
echo "=========================================="
echo ""

# 等待 1 秒
sleep 1

# 测试步骤 4: 刷新 Token
echo -e "${YELLOW}[步骤 4] 刷新 Token${NC}"
echo "POST $BASE_URL_AUTH/api/v1/auth/refresh?refreshToken=$REFRESH_TOKEN"
echo ""

REFRESH_RESPONSE=$(curl -s -X POST "$BASE_URL_AUTH/api/v1/auth/refresh?refreshToken=$REFRESH_TOKEN")

echo "$REFRESH_RESPONSE" | jq '.'
echo ""

if echo "$REFRESH_RESPONSE" | jq -e '.code == 0' > /dev/null; then
    echo -e "${GREEN}✓ Token 刷新成功${NC}"
    NEW_ACCESS_TOKEN=$(echo "$REFRESH_RESPONSE" | jq -r '.data.accessToken')
    echo "新的 Access Token: ${NEW_ACCESS_TOKEN:0:50}..."
else
    echo -e "${RED}✗ Token 刷新失败${NC}"
fi
echo ""
echo "=========================================="
echo ""

# 等待 1 秒
sleep 1

# 测试步骤 5: 登出
echo -e "${YELLOW}[步骤 5] 用户登出${NC}"
echo "POST $BASE_URL_AUTH/api/v1/auth/logout?refreshToken=$REFRESH_TOKEN"
echo ""

LOGOUT_RESPONSE=$(curl -s -X POST "$BASE_URL_AUTH/api/v1/auth/logout?refreshToken=$REFRESH_TOKEN" \
  -H "Authorization: Bearer $ACCESS_TOKEN")

echo "$LOGOUT_RESPONSE" | jq '.'
echo ""

if echo "$LOGOUT_RESPONSE" | jq -e '.code == 0' > /dev/null; then
    echo -e "${GREEN}✓ 登出成功${NC}"
else
    echo -e "${RED}✗ 登出失败${NC}"
fi
echo ""
echo "=========================================="
echo ""

# 测试步骤 6: 测试登录失败锁定
echo -e "${YELLOW}[步骤 6] 测试登录失败锁定机制${NC}"
echo "连续 5 次错误密码登录..."
echo ""

for i in {1..5}; do
    echo "尝试 $i/5..."
    FAIL_RESPONSE=$(curl -s -X POST "$BASE_URL_AUTH/api/v1/auth/login" \
      -H "Content-Type: application/json" \
      -d '{
        "email": "student@test.com",
        "password": "WrongPassword"
      }')

    echo "$FAIL_RESPONSE" | jq -r '.message'
    sleep 0.5
done

echo ""
echo "第 6 次尝试（应该被锁定）..."
LOCKED_RESPONSE=$(curl -s -X POST "$BASE_URL_AUTH/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "student@test.com",
    "password": "WrongPassword"
  }')

echo "$LOCKED_RESPONSE" | jq '.'
echo ""

if echo "$LOCKED_RESPONSE" | jq -r '.message' | grep -q "锁定"; then
    echo -e "${GREEN}✓ 登录失败锁定机制正常工作${NC}"
else
    echo -e "${RED}✗ 登录失败锁定机制未生效${NC}"
fi
echo ""
echo "=========================================="
echo ""

# 测试总结
echo -e "${GREEN}=========================================="
echo "测试完成！"
echo "==========================================${NC}"
echo ""
echo "测试覆盖："
echo "  ✓ 用户注册（管理员创建）"
echo "  ✓ 用户登录"
echo "  ✓ 获取用户信息"
echo "  ✓ Token 刷新"
echo "  ✓ 用户登出"
echo "  ✓ 登录失败锁定"
echo ""
echo "注意：测试账号已被锁定 15 分钟，请等待后再次测试登录功能"
echo ""
