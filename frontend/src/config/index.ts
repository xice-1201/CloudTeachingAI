/**
 * 全局配置
 */

export const APP_CONFIG = {
  // 应用名称
  APP_NAME: import.meta.env.VITE_APP_TITLE || 'CloudTeachingAI',

  // API 配置
  API_BASE_URL: import.meta.env.VITE_API_BASE_URL || '/api/v1',
  WS_BASE_URL: import.meta.env.VITE_WS_BASE_URL || 'ws://localhost:8080',

  // 分页配置
  DEFAULT_PAGE_SIZE: 20,
  PAGE_SIZES: [10, 20, 50, 100],

  // 文件上传配置
  UPLOAD_URL: '/api/v1/uploads',
  MAX_FILE_SIZE: {
    VIDEO: 2048, // 2GB
    DOCUMENT: 100, // 100MB
    SLIDE: 200, // 200MB
    IMAGE: 10, // 10MB
    ASSIGNMENT: 50, // 50MB
  },

  // Token 配置
  TOKEN_KEY: 'token',
  REFRESH_TOKEN_KEY: 'refreshToken',
  TOKEN_EXPIRE_TIME: 2 * 60 * 60 * 1000, // 2小时

  // WebSocket 配置
  WS_HEARTBEAT_INTERVAL: 30000, // 30秒
  WS_RECONNECT_DELAY: 1000, // 1秒
  WS_MAX_RECONNECT_ATTEMPTS: 5,

  // 视频播放器配置
  VIDEO_PLAYER: {
    PROGRESS_REPORT_INTERVAL: 10000, // 10秒上报一次进度
    PLAYBACK_RATES: [0.75, 1.0, 1.25, 1.5, 2.0],
  },

  // 能力测试配置
  ABILITY_TEST: {
    MIN_QUESTIONS_PER_POINT: 3,
    MAX_QUESTIONS_PER_POINT: 7,
    INITIAL_DIFFICULTY: 3,
  },

  // 通知配置
  NOTIFICATION: {
    DURATION: 4500, // 4.5秒
    POLLING_INTERVAL: 30000, // 降级轮询间隔 30秒
  },

  // 缓存配置
  CACHE: {
    LEARNING_PATH_TTL: 24 * 60 * 60 * 1000, // 24小时
    KNOWLEDGE_TREE_TTL: 60 * 60 * 1000, // 1小时
  },
} as const

export default APP_CONFIG
