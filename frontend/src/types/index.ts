// 用户相关类型
export interface User {
  id: string
  username: string
  email: string
  role: 'STUDENT' | 'TEACHER' | 'ADMIN'
  avatar?: string
  createdAt: string
}

export interface LoginRequest {
  email: string
  password: string
}

export interface LoginResponse {
  accessToken: string
  refreshToken: string
  user: User
  role: 'STUDENT' | 'TEACHER' | 'ADMIN'
}

// 课程相关类型
export interface Course {
  id: string
  title: string
  description: string
  coverImage?: string
  teacherId: string
  teacherName: string
  status: 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'
  createdAt: string
  updatedAt: string
}

export interface Chapter {
  id: string
  courseId: string
  title: string
  description?: string
  orderIndex: number
  createdAt: string
}

export interface Resource {
  id: string
  chapterId: string
  title: string
  type: 'VIDEO' | 'DOCUMENT' | 'SLIDE'
  url: string
  duration?: number
  size?: number
  orderIndex: number
  createdAt: string
}

// 学习相关类型
export interface LearningProgress {
  resourceId: string
  progress: number
  lastPosition?: number
  completed: boolean
  lastAccessedAt: string
}

export interface AbilityMap {
  knowledgePointId: string
  knowledgePointName: string
  masteryLevel: number
  lastTestedAt?: string
}

export interface LearningPath {
  id: string
  studentId: string
  resources: PathResource[]
  createdAt: string
}

export interface PathResource {
  resourceId: string
  resourceTitle: string
  courseTitle: string
  reason: string
  orderIndex: number
}

// 作业相关类型
export interface Assignment {
  id: string
  courseId: string
  title: string
  description: string
  dueDate: string
  maxScore: number
  createdAt: string
}

export interface Submission {
  id: string
  assignmentId: string
  studentId: string
  content: string
  attachments?: string[]
  score?: number
  feedback?: string
  status: 'PENDING' | 'GRADED' | 'REVIEWED'
  submittedAt: string
  gradedAt?: string
}

// 通知相关类型
export interface Notification {
  id: string
  userId: string
  type: 'SYSTEM' | 'COURSE' | 'ASSIGNMENT' | 'GRADE'
  title: string
  content: string
  read: boolean
  createdAt: string
}

// AI 助手相关类型
export interface ChatMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  timestamp: string
}

export interface ChatSession {
  id: string
  userId: string
  messages: ChatMessage[]
  createdAt: string
  updatedAt: string
}

// API 响应类型
export interface ApiResponse<T = any> {
  code: number
  message: string
  data: T
}

export interface PageResponse<T> {
  items: T[]
  total: number
  page: number
  pageSize: number
}
