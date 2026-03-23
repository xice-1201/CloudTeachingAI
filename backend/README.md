# CloudTeachingAI Backend

后端微服务实现，包含用户认证和用户管理功能。

## 项目结构

```
backend/
├── auth-service/          # 认证服务（端口 8001）
├── user-service/          # 用户管理服务（端口 8002）
└── docker-compose.yml     # 基础设施配置
```

## 技术栈

- Java 21
- Spring Boot 3.3.0
- Spring Cloud 2023.0.2
- PostgreSQL 16
- Redis 7
- JWT (jjwt 0.12.5)

## 快速开始

### 1. 启动基础设施

```bash
cd backend
docker compose up -d
```

这将启动：
- PostgreSQL (auth-db): localhost:5432
- PostgreSQL (user-db): localhost:5433
- Redis: localhost:6379

### 2. 启动 auth-service

```bash
cd auth-service
mvn spring-boot:run
```

服务将在 http://localhost:8001 启动

### 3. 启动 user-service

```bash
cd user-service
mvn spring-boot:run
```

服务将在 http://localhost:8002 启动

## API 文档

### auth-service (端口 8001)

#### 登录
```http
POST /api/v1/auth/login
Content-Type: application/json

{
  "email": "student@example.com",
  "password": "password123"
}
```

响应：
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "accessToken": "eyJhbGciOiJSUzI1NiJ9...",
    "refreshToken": "uuid-string",
    "role": "STUDENT",
    "userId": 1
  }
}
```

#### 登出
```http
POST /api/v1/auth/logout?refreshToken=uuid-string
Authorization: Bearer <access_token>
```

#### 刷新 Token
```http
POST /api/v1/auth/refresh?refreshToken=uuid-string
```

### user-service (端口 8002)

#### 创建用户（管理员）
```http
POST /api/v1/admin/users
Content-Type: application/json

{
  "username": "张三",
  "email": "zhangsan@example.com",
  "password": "password123",
  "role": "STUDENT"
}
```

响应：
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": 1,
    "username": "张三",
    "email": "zhangsan@example.com",
    "role": "STUDENT",
    "isActive": true,
    "createdAt": "2026-03-23T12:00:00"
  }
}
```

#### 获取用户信息
```http
GET /api/v1/users/{id}
```

## 数据库结构

### auth-db

- `auth_credential`: 用户凭证（邮箱、密码哈希、登录失败次数）
- `refresh_token`: 刷新令牌
- `password_reset_token`: 密码重置令牌

### user-db

- `user`: 用户基本信息（用户名、邮箱、角色、状态）

## 功能特性

### 已实现 ✅

- [x] 用户注册（管理员创建）
- [x] 用户登录
- [x] JWT Token 签发（RS256）
- [x] Token 刷新
- [x] 登出（Token 黑名单）
- [x] 登录失败锁定（5 次失败锁定 15 分钟）
- [x] 密码加密（bcrypt，cost factor 12）
- [x] 数据库迁移（Flyway）
- [x] 统一异常处理
- [x] 参数验证

### 待实现 ⏳

- [ ] 密码重置（邮件）
- [ ] 批量导入用户
- [ ] 用户角色管理
- [ ] 用户状态管理（禁用/启用）
- [ ] Kafka 事件发布（login.event）
- [ ] Nacos 服务注册
- [ ] API Gateway 集成

## 测试

### 创建测试用户

```bash
curl -X POST http://localhost:8002/api/v1/admin/users \
  -H "Content-Type: application/json" \
  -d '{
    "username": "测试学生",
    "email": "student@test.com",
    "password": "Test123456",
    "role": "STUDENT"
  }'
```

### 登录测试

```bash
curl -X POST http://localhost:8001/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "student@test.com",
    "password": "Test123456"
  }'
```

## 配置说明

### auth-service

- `jwt.private-key`: RSA 私钥（用于签发 JWT）
- `jwt.public-key`: RSA 公钥（用于验证 JWT）
- `jwt.access-token-expiration`: Access Token 有效期（秒，默认 7200 = 2 小时）
- `jwt.refresh-token-expiration`: Refresh Token 有效期（秒，默认 604800 = 7 天）

### user-service

- `auth-service.url`: auth-service 地址（默认 http://localhost:8001）

## 安全特性

- 密码使用 bcrypt 加密（cost factor 12）
- JWT 使用 RS256 非对称签名
- 登录失败 5 次锁定 15 分钟
- Token 黑名单机制（Redis）
- 参数验证和统一异常处理

## 开发注意事项

1. 确保 Docker 容器正常运行
2. 首次启动会自动执行 Flyway 数据库迁移
3. JWT 密钥对已内置在配置文件中（生产环境需替换）
4. 目前未集成 Nacos，服务间通过直接 HTTP 调用
5. Kafka 事件发布功能已预留接口，待实现

## 故障排查

### 数据库连接失败

检查 Docker 容器状态：
```bash
docker compose ps
```

查看日志：
```bash
docker compose logs auth-db
docker compose logs user-db
```

### Redis 连接失败

检查 Redis 容器：
```bash
docker compose logs redis
```

### 服务启动失败

查看应用日志，检查端口是否被占用：
```bash
lsof -i :8001  # auth-service
lsof -i :8002  # user-service
```

## 下一步计划

1. 实现密码重置功能（邮件发送）
2. 集成 Nacos 服务注册与发现
3. 实现 API Gateway
4. 添加 Kafka 事件发布
5. 完善单元测试和集成测试
6. 添加 Docker 镜像构建
