export interface User {
  id: number
  username: string
  email: string
  role: 'STUDENT' | 'TEACHER' | 'ADMIN'
  avatar?: string
  createdAt: string
  isActive?: boolean
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
  userId: number
}

export interface Course {
  id: number
  title: string
  description: string
  coverImage?: string
  teacherId: number
  teacherName: string
  status: 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'
  visibilityType: 'PUBLIC' | 'SELECTED_STUDENTS'
  visibleStudentIds?: number[] | null
  visibleStudentCount?: number
  createdAt: string
  updatedAt: string
}

export interface Chapter {
  id: number
  courseId: number
  title: string
  description?: string
  orderIndex: number
  createdAt: string
}

export interface Resource {
  id: number
  chapterId: number
  title: string
  type: 'VIDEO' | 'DOCUMENT' | 'SLIDE'
  url: string
  sourceUrl?: string | null
  description?: string
  managedFile?: boolean
  duration?: number
  size?: number
  orderIndex: number
  createdAt: string
}

export interface LearningProgress {
  resourceId: number
  progress: number
  lastPosition?: number
  completed: boolean
  lastAccessedAt: string
}

export interface CourseProgress {
  courseId: number
  progress: number
  totalResources: number
  completedResources: number
  lastLearnedAt?: string | null
}

export interface AbilityMap {
  knowledgePointId: number
  knowledgePointName: string
  masteryLevel: number
  lastTestedAt?: string
}

export interface LearningPath {
  id: number
  studentId: number
  resources: PathResource[]
  createdAt: string
}

export interface PathResource {
  resourceId: number
  resourceTitle: string
  courseTitle: string
  reason: string
  orderIndex: number
}

export interface Assignment {
  id: number
  courseId: number
  courseTitle?: string
  teacherId?: number
  title: string
  description: string
  gradingCriteria?: string
  submitType?: 'TEXT' | 'FILE' | 'BOTH'
  dueDate: string
  maxScore: number
  createdAt: string
}

export interface Submission {
  id: number
  assignmentId: number
  studentId: number
  content: string
  attachments?: string[]
  score?: number
  feedback?: string
  status: 'SUBMITTED' | 'AI_GRADING' | 'AI_GRADED' | 'GRADING_FAILED' | 'PENDING_MANUAL' | 'REVIEWED'
  submittedAt: string
  gradedAt?: string
}

export interface Notification {
  id: number
  userId: number
  type: 'SYSTEM' | 'COURSE' | 'ASSIGNMENT' | 'GRADE'
  title: string
  content: string
  read: boolean
  createdAt: string
}

export interface ChatMessage {
  id: number
  role: 'user' | 'assistant'
  content: string
  timestamp: string
}

export interface ChatSession {
  id: number
  userId: number
  messages: ChatMessage[]
  createdAt: string
  updatedAt: string
}

export interface ApiResponse<T = any> {
  code: number
  message: string
  data: T
  stackTrace?: string
}

export interface PageResponse<T> {
  items: T[]
  total: number
  page: number
  pageSize: number
}

export interface TeacherRegistrationApplication {
  id: number
  username: string
  email: string
  status: 'PENDING' | 'APPROVED' | 'REJECTED'
  reviewNote?: string
  reviewedBy?: number
  createdUserId?: number
  requestedAt: string
  reviewedAt?: string
}
