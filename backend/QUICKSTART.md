# 快速启动指南

## 前置要求

- Java 21
- Maven 3.8+
- Docker 和 Docker Compose
- curl（用于测试）

## 启动步骤

### 1. 启动基础设施

```bash
cd backend
docker compose up -d
```

等待容器启动完成（约 10-20 秒）。

验证容器状态：
```bash
docker compose ps
```

应该看到三个容器都是 `Up` 状态：
- auth-db (PostgreSQL)
- user-db (PostgreSQL)
- redis

### 2. 启动 auth-service

打开新的终端窗口：

```bash
cd backend/auth-service
mvn clean spring-boot:run
```

等待看到日志：
```
Started AuthServiceApplication in X.XXX seconds
```

### 3. 启动 user-service

再打开一个新的终端窗口：

```bash
cd backend/user-service
mvn clean spring-boot:run
```

等待看到日志：
```
Started UserServiceApplication in X.XXX seconds
```

### 4. 运行测试

#### Windows 用户：

```bash
cd backend
test-api.bat
```

#### Linux/Mac 用户：

```bash
cd backend
chmod +x test-api.sh
./test-api.sh
```

## 手动测试

### 创建用户

```bash
curl -X POST http://localhost:8002/api/v1/admin/users \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"张三\",\"email\":\"zhangsan@example.com\",\"password\":\"Test123456\",\"role\":\"STUDENT\"}"
```

### 登录

```bash
curl -X POST http://localhost:8001/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"zhangsan@example.com\",\"password\":\"Test123456\"}"
```

保存返回的 `accessToken` 和 `refreshToken`。

### 获取用户信息

```bash
curl http://localhost:8002/api/v1/users/1
```

### 刷新 Token

```bash
curl -X POST "http://localhost:8001/api/v1/auth/refresh?refreshToken=YOUR_REFRESH_TOKEN"
```

### 登出

```bash
curl -X POST "http://localhost:8001/api/v1/auth/logout?refreshToken=YOUR_REFRESH_TOKEN" \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN"
```

## 故障排查

### 端口被占用

如果看到 `Port already in use` 错误：

```bash
# Windows
netstat -ano | findstr :8001
netstat -ano | findstr :8002

# Linux/Mac
lsof -i :8001
lsof -i :8002
```

### 数据库连接失败

检查 Docker 容器：

```bash
docker compose logs auth-db
docker compose logs user-db
```

重启容器：

```bash
docker compose restart
```

### Maven 构建失败

清理并重新构建：

```bash
cd backend/auth-service
mvn clean install -DskipTests

cd ../user-service
mvn clean install -DskipTests
```

### Redis 连接失败

检查 Redis 容器：

```bash
docker compose logs redis
```

测试 Redis 连接：

```bash
docker exec -it redis redis-cli ping
```

应该返回 `PONG`。

## 停止服务

### 停止 Spring Boot 应用

在运行服务的终端窗口按 `Ctrl+C`。

### 停止 Docker 容器

```bash
cd backend
docker compose down
```

如果需要清除数据：

```bash
docker compose down -v
```

## 下一步

- 查看 `backend/README.md` 了解完整的 API 文档
- 查看 `backend/IMPLEMENTATION_SUMMARY.md` 了解实现细节
- 前端集成：修改前端 `.env.development` 文件中的 API 地址
