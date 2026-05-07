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
  taggingStatus?: 'UNTAGGED' | 'SUGGESTED' | 'CONFIRMED'
  taggingUpdatedAt?: string | null
  knowledgePoints?: ResourceKnowledgePoint[]
  tags?: ResourceTag[]
  duration?: number
  size?: number
  orderIndex: number
  createdAt: string
}

export interface ResourceTag {
  id: number
  label: string
  confidence?: number
  source?: 'AI' | 'MANUAL'
  knowledgePointId?: number | null
  knowledgePointPath?: string | null
}

export interface KnowledgePointNode {
  id: number
  parentId?: number | null
  name: string
  description?: string | null
  keywords?: string | null
  nodeType: 'SUBJECT' | 'DOMAIN' | 'POINT'
  active: boolean
  orderIndex: number
  path: string
  children: KnowledgePointNode[]
}

export interface ResourceKnowledgePoint {
  id: number
  name: string
  nodeType: 'SUBJECT' | 'DOMAIN' | 'POINT'
  path: string
  confidence?: number
  source?: 'AI' | 'MANUAL'
}

export interface ResourceTagSuggestion {
  label: string
  kind: 'EXISTING' | 'GENERATED'
  knowledgePointId?: number | null
  knowledgePointName?: string | null
  path?: string | null
  confidence: number
  reason: string
}

export interface Announcement {
  id: number
  courseId: number
  authorId: number
  authorName: string
  title: string
  content: string
  pinned: boolean
  publishedAt: string
  createdAt: string
  updatedAt: string
}

export interface DiscussionPost {
  id: number
  courseId: number
  resourceId?: number | null
  parentId?: number | null
  authorId: number
  authorName: string
  title?: string | null
  content: string
  createdAt: string
  updatedAt: string
  replies: DiscussionPost[]
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
  knowledgePointPath?: string | null
  masteryLevel: number
  confidence: number
  testScore: number
  progressScore: number
  resourceCount: number
  source: 'TEST' | 'TEST_AND_PROGRESS' | 'LEARNING_PROGRESS' | 'COURSE_TAGS'
  lastTestedAt?: string | null
}

export interface AbilityTestQuestionOption {
  key: 'A' | 'B' | 'C' | 'D'
  text: string
}

export interface AbilityTestQuestion {
  id: number
  knowledgePointId: number
  knowledgePointName: string
  orderIndex: number
  totalQuestions: number
  content: string
  options: AbilityTestQuestionOption[]
}

export interface AbilityTestStartResponse {
  sessionId: number
  rootKnowledgePointName: string
  totalQuestions: number
  question: AbilityTestQuestion
}

export interface AbilityTestAnswerResponse {
  sessionId: number
  answeredCount: number
  totalQuestions: number
  completed: boolean
  nextQuestion?: AbilityTestQuestion | null
  abilityMap?: AbilityMap[]
}

export interface TeacherCourseAnalytics {
  courseId: number
  courseTitle: string
  courseStatus: 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'
  totalResources: number
  activeStudents: number
  averageProgress: number
  completionRate: number
  learningRecordCount: number
  hottestResourceTitle?: string | null
  hottestResourceLearningCount: number
  lastLearnedAt?: string | null
}

export interface TeacherKnowledgePointAnalytics {
  knowledgePointId: number
  knowledgePointName: string
  knowledgePointPath?: string | null
  averageProgress: number
  activeStudents: number
  relatedResources: number
}

export interface TeacherStudentRisk {
  courseId: number
  courseTitle: string
  activeStudents: number
  lowProgressStudents: number
  inactiveStudents: number
  completedStudents: number
  riskLevel: 'NO_DATA' | 'LOW' | 'MEDIUM' | 'HIGH'
  insight: string
}

export interface TeacherDashboard {
  totalCourses: number
  publishedCourses: number
  totalResources: number
  activeStudents: number
  averageProgress: number
  courses: TeacherCourseAnalytics[]
  weakKnowledgePoints: TeacherKnowledgePointAnalytics[]
  studentRisks: TeacherStudentRisk[]
}

export interface LearningPath {
  studentId: number
  generatedAt: string
  focusKnowledgePoints: LearningPathFocus[]
  resources: PathResource[]
}

export interface LearningPathFocus {
  knowledgePointId: number
  knowledgePointName: string
  knowledgePointPath?: string | null
  masteryLevel: number
}

export interface PathResource {
  resourceId: number
  courseId: number
  chapterId: number
  resourceTitle: string
  chapterTitle?: string | null
  courseTitle: string
  reason: string
  orderIndex: number
  currentProgress: number
  focusKnowledgePointId: number
  focusKnowledgePointName: string
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
  aiScore?: number | null
  aiFeedback?: string | null
  finalScore?: number | null
  finalFeedback?: string | null
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
  targetType?: string | null
  targetId?: number | null
  targetUrl?: string | null
  read: boolean
  createdAt: string
}

export interface ChatMessage {
  id: number
  role: 'user' | 'assistant'
  content: string
  timestamp: string
}

export interface ChatContext {
  courseId?: number | string | null
  courseTitle?: string | null
  resourceId?: number | string | null
  resourceTitle?: string | null
  knowledgePointId?: number | string | null
  knowledgePointName?: string | null
  returnUrl?: string | null
  returnLabel?: string | null
}

export interface ChatSession {
  id: number
  userId: number | string
  title: string
  context: ChatContext
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

export interface AdminAuditLog {
  id: number
  actorId?: number | null
  actorName?: string | null
  action: string
  targetType: string
  targetId?: number | null
  targetName?: string | null
  detail?: string | null
  createdAt: string
}
