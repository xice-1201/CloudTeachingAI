# CloudTeachingAI 前端项目

基于 Vue 3 + TypeScript + Vite 构建的现代化教育平台前端应用。

## 技术栈

- **框架**: Vue 3.5+ (Composition API)
- **语言**: TypeScript 5.x
- **构建工具**: Vite 5.x
- **状态管理**: Pinia 2.x
- **路由**: Vue Router 4.x
- **UI 组件库**: Element Plus 2.x
- **HTTP 客户端**: Axios 1.x
- **可视化**: ECharts 5.x
- **视频播放**: Video.js 8.x
- **文件上传**: tus-js-client 4.x

## 项目结构

```
src/
├── api/              # API 接口定义
├── assets/           # 静态资源
├── components/       # 通用组件
├── composables/      # 组合式函数
├── config/           # 配置文件
├── layouts/          # 布局组件
├── router/           # 路由配置
├── store/            # 状态管理
├── types/            # TypeScript 类型定义
├── utils/            # 工具函数
├── views/            # 页面组件
├── App.vue           # 根组件
└── main.ts           # 入口文件
```

## 开发指南

### 安装依赖

```bash
npm install
```

### 启动开发服务器

```bash
npm run dev
```

访问 http://localhost:3000

### 构建生产版本

```bash
npm run build
```

### 预览生产构建

```bash
npm run preview
```

## 环境变量

项目使用 `.env` 文件管理环境变量：

- `.env` - 通用配置
- `.env.development` - 开发环境配置
- `.env.production` - 生产环境配置

## 核心功能模块

### P1.1 基础设施 ✅

- [x] Axios 请求拦截器（JWT 注入、Token 刷新、统一错误处理）
- [x] Pinia Store（用户状态、通知状态）
- [x] 路由守卫（登录校验、角色权限控制）
- [x] MainLayout（主布局、导航栏、侧边栏、通知铃铛）
- [x] WebSocket 连接管理（心跳、重连、降级轮询）
- [x] 通用组件（CardContainer、PageList、DialogForm、StatusBadge 等）
- [x] 工具函数（格式化、验证、权限检查、本地存储）
- [x] Composables（分页、加载状态、防抖节流等）
- [x] 环境变量配置
- [x] 全局样式

### 待实现模块

- [ ] P1.2 认证模块（登录页、密码重置）
- [ ] P1.3 学生端（Dashboard、课程、学习、作业、AI 助手）
- [ ] P1.4 教师端（课程管理、作业批改、学生管理）
- [ ] P1.5 管理员端（用户管理、知识点体系、数据统计）

## 代码规范

- 使用 TypeScript 严格模式
- 组件使用 Composition API + `<script setup>`
- 遵循 Vue 3 官方风格指南
- 使用 ESLint + Prettier 格式化代码

## 浏览器支持

- Chrome >= 90
- Firefox >= 88
- Safari >= 14
- Edge >= 90

## License

MIT
