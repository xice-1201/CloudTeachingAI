/**
 * 常量定义
 */

// 用户角色
export const USER_ROLES = {
  STUDENT: 'STUDENT',
  TEACHER: 'TEACHER',
  ADMIN: 'ADMIN',
} as const

export type UserRole = (typeof USER_ROLES)[keyof typeof USER_ROLES]

// 课程状态
export const COURSE_STATUS = {
  DRAFT: 'DRAFT',
  PUBLISHED: 'PUBLISHED',
  ARCHIVED: 'ARCHIVED',
} as const

export type CourseStatus = (typeof COURSE_STATUS)[keyof typeof COURSE_STATUS]

// 资源类型
export const RESOURCE_TYPES = {
  VIDEO: 'VIDEO',
  DOCUMENT: 'DOCUMENT',
  SLIDE: 'SLIDE',
} as const

export type ResourceType = (typeof RESOURCE_TYPES)[keyof typeof RESOURCE_TYPES]

// 作业提交状态
export const SUBMISSION_STATUS = {
  SUBMITTED: 'SUBMITTED',
  AI_GRADING: 'AI_GRADING',
  AI_GRADED: 'AI_GRADED',
  GRADING_FAILED: 'GRADING_FAILED',
  PENDING_MANUAL: 'PENDING_MANUAL',
  REVIEWED: 'REVIEWED',
} as const

export type SubmissionStatus = (typeof SUBMISSION_STATUS)[keyof typeof SUBMISSION_STATUS]

// 通知类型
export const NOTIFICATION_TYPES = {
  SYSTEM: 'SYSTEM',
  COURSE: 'COURSE',
  ASSIGNMENT: 'ASSIGNMENT',
  GRADE: 'GRADE',
  MENTOR: 'MENTOR',
  LEARNING_PATH: 'LEARNING_PATH',
} as const

export type NotificationType = (typeof NOTIFICATION_TYPES)[keyof typeof NOTIFICATION_TYPES]

// 掌握程度
export const MASTERY_LEVELS = {
  NONE: 'NONE',
  PARTIAL: 'PARTIAL',
  MASTERED: 'MASTERED',
} as const

export type MasteryLevel = (typeof MASTERY_LEVELS)[keyof typeof MASTERY_LEVELS]

// 状态文本映射
export const STATUS_TEXT_MAP = {
  // 课程状态
  [COURSE_STATUS.DRAFT]: '草稿',
  [COURSE_STATUS.PUBLISHED]: '已发布',
  [COURSE_STATUS.ARCHIVED]: '已归档',

  // 作业状态
  [SUBMISSION_STATUS.SUBMITTED]: '已提交',
  [SUBMISSION_STATUS.AI_GRADING]: 'AI 批改中',
  [SUBMISSION_STATUS.AI_GRADED]: 'AI 已批改',
  [SUBMISSION_STATUS.GRADING_FAILED]: '批改失败',
  [SUBMISSION_STATUS.PENDING_MANUAL]: '待人工批改',
  [SUBMISSION_STATUS.REVIEWED]: '已复核',

  // 掌握程度
  [MASTERY_LEVELS.NONE]: '未掌握',
  [MASTERY_LEVELS.PARTIAL]: '部分掌握',
  [MASTERY_LEVELS.MASTERED]: '已掌握',

  // 资源类型
  [RESOURCE_TYPES.VIDEO]: '视频',
  [RESOURCE_TYPES.DOCUMENT]: '文档',
  [RESOURCE_TYPES.SLIDE]: '课件',
} as const

// 状态类型映射（用于 StatusBadge）
export const STATUS_TYPE_MAP: Record<string, 'success' | 'warning' | 'danger' | 'info'> = {
  // 课程状态
  [COURSE_STATUS.DRAFT]: 'info',
  [COURSE_STATUS.PUBLISHED]: 'success',
  [COURSE_STATUS.ARCHIVED]: 'warning',

  // 作业状态
  [SUBMISSION_STATUS.SUBMITTED]: 'info',
  [SUBMISSION_STATUS.AI_GRADING]: 'warning',
  [SUBMISSION_STATUS.AI_GRADED]: 'success',
  [SUBMISSION_STATUS.GRADING_FAILED]: 'danger',
  [SUBMISSION_STATUS.PENDING_MANUAL]: 'warning',
  [SUBMISSION_STATUS.REVIEWED]: 'success',

  // 掌握程度
  [MASTERY_LEVELS.NONE]: 'danger',
  [MASTERY_LEVELS.PARTIAL]: 'warning',
  [MASTERY_LEVELS.MASTERED]: 'success',
}

// 文件类型 MIME 映射
export const FILE_MIME_TYPES = {
  VIDEO: 'video/mp4,video/webm,video/ogg',
  DOCUMENT: '.pdf,.doc,.docx,.txt',
  SLIDE: '.ppt,.pptx',
  IMAGE: 'image/jpeg,image/png,image/gif,image/webp',
  ALL: '*',
} as const

// 文件大小限制（MB）
export const FILE_SIZE_LIMITS = {
  VIDEO: 2048, // 2GB
  DOCUMENT: 100,
  SLIDE: 200,
  IMAGE: 10,
  ASSIGNMENT: 50,
} as const
