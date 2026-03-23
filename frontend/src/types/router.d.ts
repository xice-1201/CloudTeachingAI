/**
 * Vue Router 类型扩展
 */

import 'vue-router'

declare module 'vue-router' {
  interface RouteMeta {
    title?: string
    requiresAuth?: boolean
    roles?: string[]
    icon?: string
    hidden?: boolean
  }
}
