/**
 * 全局组件注册
 */

import type { App } from 'vue'
import CardContainer from './CardContainer.vue'
import PageList from './PageList.vue'
import DialogForm from './DialogForm.vue'
import StatusBadge from './StatusBadge.vue'
import NotificationBell from './NotificationBell.vue'
import LoadingSkeleton from './LoadingSkeleton.vue'
import FileUpload from './FileUpload.vue'
import Breadcrumb from './Breadcrumb.vue'
import ErrorBoundary from './ErrorBoundary.vue'

const components = {
  CardContainer,
  PageList,
  DialogForm,
  StatusBadge,
  NotificationBell,
  LoadingSkeleton,
  FileUpload,
  Breadcrumb,
  ErrorBoundary,
}

export function registerGlobalComponents(app: App) {
  Object.entries(components).forEach(([name, component]) => {
    app.component(name, component)
  })
}

export default components
