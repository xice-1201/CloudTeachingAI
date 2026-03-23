/**
 * 导出所有 API 接口
 */

export * from './auth'
export * from './user'
export * from './course'
export * from './learn'
export * from './assign'
export * from './notify'
export * from './chat'
export * from './media'

// 默认导出
import { authApi } from './auth'
import { userApi } from './user'
import { courseApi } from './course'
import { learnApi } from './learn'
import { assignApi } from './assign'
import { notifyApi } from './notify'
import { chatApi } from './chat'
import { mediaApi } from './media'

export default {
  auth: authApi,
  user: userApi,
  course: courseApi,
  learn: learnApi,
  assign: assignApi,
  notify: notifyApi,
  chat: chatApi,
  media: mediaApi,
}
