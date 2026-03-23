# 前后端集成指南

## 后端 API 地址配置

后端服务地址：
- auth-service: `http://localhost:8001`
- user-service: `http://localhost:8002`

## 前端配置修改

### 1. 修改环境变量

编辑 `frontend/.env.development`：

```env
VITE_API_BASE_URL=http://localhost:8001
VITE_USER_API_BASE_URL=http://localhost:8002
```

### 2. API 调用示例

前端已经实现了完整的 API 接口定义，位于 `frontend/src/api/`：

#### 登录

```typescript
import { authApi } from '@/api'

const response = await authApi.login({
  email: 'student@test.com',
  password: 'Test123456'
})

// 保存 token
localStorage.setItem('accessToken', response.data.accessToken)
localStorage.setItem('refreshToken', response.data.refreshToken)
```

#### 创建用户（管理员）

```typescript
import { userApi } from '@/api'

const response = await userApi.createUser({
  username: '张三',
  email: 'zhangsan@example.com',
  password: 'Test123456',
  role: 'STUDENT'
})
```

## 跨域配置

后端已配置 CORS，允许以下前端地址：
- `http://localhost:3000` (Vite 默认端口)
- `http://localhost:5173` (Vite 备用端口)

如果前端使用其他端口，需要修改后端配置：

**auth-service/src/main/java/com/cloudteachingai/auth/config/WebConfig.java**
**user-service/src/main/java/com/cloudteachingai/user/config/WebConfig.java**

```java
.allowedOrigins("http://localhost:3000", "http://localhost:5173", "http://localhost:YOUR_PORT")
```

## Token 管理

### 前端已实现的功能

前端 `frontend/src/utils/request.ts` 已经实现了：

1. **自动注入 Token**
   - 每个请求自动添加 `Authorization: Bearer <token>` 头

2. **Token 刷新**
   - 当 access token 过期（401 错误）时自动刷新
   - 使用队列机制防止并发刷新

3. **登出处理**
   - Token 刷新失败时自动跳转登录页

### 后端 Token 机制

- **Access Token**：有效期 2 小时，用于 API 认证
- **Refresh Token**：有效期 7 天，用于刷新 access token
- **Token 黑名单**：登出后的 token 会被加入黑名单（Redis）

## 完整的登录流程

### 1. 用户登录

```typescript
// 前端调用
const response = await authApi.login({
  email: 'student@test.com',
  password: 'Test123456'
})

// 后端返回
{
  "code": 0,
  "message": "success",
  "data": {
    "accessToken": "eyJhbGciOiJSUzI1NiJ9...",
    "refreshToken": "550e8400-e29b-41d4-a716-446655440000",
    "role": "STUDENT",
    "userId": 1
  }
}

// 前端保存
localStorage.setItem('accessToken', response.data.accessToken)
localStorage.setItem('refreshToken', response.data.refreshToken)
localStorage.setItem('userRole', response.data.role)
localStorage.setItem('userId', response.data.userId)
```

### 2. 访问受保护的 API

```typescript
// 前端自动添加 Authorization 头
const response = await userApi.getUserInfo(userId)

// 后端验证 JWT 并返回数据
```

### 3. Token 过期自动刷新

```typescript
// 当 access token 过期时，前端自动调用
const response = await authApi.refreshToken(refreshToken)

// 更新 token
localStorage.setItem('accessToken', response.data.accessToken)

// 重试原始请求
```

### 4. 用户登出

```typescript
// 前端调用
await authApi.logout(refreshToken)

// 清除本地存储
localStorage.removeItem('accessToken')
localStorage.removeItem('refreshToken')
localStorage.removeItem('userRole')
localStorage.removeItem('userId')

// 跳转登录页
router.push('/login')
```

## 错误处理

### 后端错误码

| 错误码 | HTTP 状态 | 说明 |
|--------|----------|------|
| 0 | 200 | 成功 |
| 40001 | 400 | 参数验证失败 |
| 40101 | 401 | 未登录或 Token 失效 |
| 40102 | 401 | Token 已过期 |
| 40301 | 403 | 权限不足 |
| 40302 | 403 | 账号已被禁用 |
| 40303 | 403 | 账号已锁定 |
| 40401 | 404 | 资源不存在 |
| 40901 | 409 | 数据冲突（如邮箱已注册） |
| 50001 | 500 | 服务器内部错误 |

### 前端错误处理

前端 `request.ts` 已实现统一错误处理：

```typescript
// 401 错误 - 自动刷新 token
if (error.response?.status === 401) {
  // 尝试刷新 token
  // 失败则跳转登录页
}

// 403 错误 - 权限不足
if (error.response?.status === 403) {
  ElMessage.error('权限不足')
}

// 其他错误 - 显示错误消息
ElMessage.error(error.response?.data?.message || '请求失败')
```

## 测试前后端集成

### 1. 启动后端

```bash
# 启动基础设施
cd backend
docker compose up -d

# 启动 auth-service
cd auth-service
mvn spring-boot:run

# 启动 user-service（新终端）
cd user-service
mvn spring-boot:run
```

### 2. 启动前端

```bash
cd frontend
npm run dev
```

### 3. 测试流程

1. 访问 `http://localhost:3000`
2. 点击"注册"或使用测试脚本创建用户
3. 使用创建的账号登录
4. 验证 Token 自动注入
5. 测试登出功能

## 常见问题

### Q: 前端请求后端时出现 CORS 错误

A: 检查后端 `WebConfig.java` 中的 `allowedOrigins` 是否包含前端地址。

### Q: Token 刷新失败

A: 检查 refresh token 是否正确保存，以及是否在 7 天有效期内。

### Q: 登录后立即提示 Token 失效

A: 检查前端和后端的时间是否同步，JWT 依赖时间戳验证。

### Q: 创建用户失败

A: 检查邮箱是否已被注册，密码是否符合要求。

## API 完整列表

### auth-service (8001)

- `POST /api/v1/auth/login` - 登录
- `POST /api/v1/auth/logout` - 登出
- `POST /api/v1/auth/refresh` - 刷新 Token
- `POST /api/v1/auth/internal/create-credential` - 创建凭证（内部接口）

### user-service (8002)

- `POST /api/v1/admin/users` - 创建用户（管理员）
- `GET /api/v1/users/{id}` - 获取用户信息
- `GET /api/v1/internal/users/by-email` - 根据邮箱查询用户（内部接口）

## 下一步

- 实现前端注册页面（调用 `POST /api/v1/admin/users`）
- 完善前端登录页面的错误提示
- 实现用户信息展示页面
- 添加角色权限控制
