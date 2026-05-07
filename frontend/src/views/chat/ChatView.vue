<template>
  <div class="chat-page">
    <div class="chat-sidebar">
      <div class="sidebar-header">
        <span>对话列表</span>
        <el-button :icon="Plus" circle size="small" :loading="creating" @click="createSession" />
      </div>
      <div v-loading="loadingSessions" class="session-list">
        <div
          v-for="s in sessions"
          :key="s.id"
          class="session-item"
          :class="{ active: currentSessionId === s.id }"
          @click="selectSession(s.id)"
        >
          <el-icon><ChatDotRound /></el-icon>
          <span class="session-date">{{ formatDate(s.updatedAt || s.createdAt) }}</span>
          <el-button
            class="delete-btn"
            :icon="Delete"
            circle
            size="small"
            text
            @click.stop="deleteSession(s.id)"
          />
        </div>
        <div v-if="sessions.length === 0 && !loadingSessions" class="empty-tip">暂无对话</div>
      </div>
    </div>

    <div class="chat-main">
      <div v-if="!currentSessionId" class="chat-welcome">
        <el-icon :size="64" color="#c0c4cc"><ChatDotRound /></el-icon>
        <p>选择或创建一个对话开始提问</p>
        <el-button type="primary" @click="createSession">新建对话</el-button>
      </div>

      <template v-else>
        <div v-if="activeContextLabel" class="context-bar">
          <div>
            <div class="context-title">当前围绕：{{ activeContextLabel }}</div>
            <div class="context-subtitle">AI 会优先结合该教学上下文回答。</div>
          </div>
          <el-button link type="primary" @click="clearContext">切换为通用问答</el-button>
        </div>

        <div v-loading="loadingMessages" class="messages" ref="messagesEl">
          <div
            v-for="msg in messages"
            :key="msg.id"
            class="message"
            :class="msg.role"
          >
            <el-avatar v-if="msg.role === 'assistant'" :size="32" style="background: #409eff; flex-shrink: 0">AI</el-avatar>
            <div class="message-bubble">{{ msg.content }}</div>
            <el-avatar v-if="msg.role === 'user'" :size="32" style="background: #67c23a; flex-shrink: 0">我</el-avatar>
          </div>
          <div v-if="streaming || streamingText" class="message assistant">
            <el-avatar :size="32" style="background: #409eff; flex-shrink: 0">AI</el-avatar>
            <div class="message-bubble streaming">{{ streamingText }}<span class="cursor">|</span></div>
          </div>
        </div>

        <div class="input-area">
          <el-input
            v-model="inputText"
            type="textarea"
            :rows="3"
            placeholder="输入问题，按 Ctrl+Enter 发送..."
            resize="none"
            @keydown.ctrl.enter="sendMessage"
          />
          <el-button
            type="primary"
            :disabled="!inputText.trim() || streaming"
            :loading="streaming"
            @click="sendMessage"
          >
            发送
          </el-button>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, nextTick, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Plus, Delete, ChatDotRound } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { chatApi, type ChatContextParams } from '@/api/chat'
import type { ChatSession, ChatMessage } from '@/types'

const route = useRoute()
const router = useRouter()
const sessions = ref<ChatSession[]>([])
const currentSessionId = ref<number | null>(null)
const messages = ref<ChatMessage[]>([])
const inputText = ref('')
const loadingSessions = ref(false)
const loadingMessages = ref(false)
const creating = ref(false)
const streaming = ref(false)
const streamingText = ref('')
const messagesEl = ref<HTMLElement>()
let eventSource: EventSource | null = null

function readQueryValue(key: string) {
  const value = route.query[key]
  return Array.isArray(value) ? value[0] : value
}

const activeContext = computed<ChatContextParams>(() => ({
  courseId: readQueryValue('courseId'),
  courseTitle: readQueryValue('courseTitle'),
  resourceId: readQueryValue('resourceId'),
  resourceTitle: readQueryValue('resourceTitle'),
  knowledgePointId: readQueryValue('knowledgePointId'),
  knowledgePointName: readQueryValue('knowledgePointName'),
}))

const activeContextLabel = computed(() => {
  const context = activeContext.value
  return context.resourceTitle || context.knowledgePointName || context.courseTitle || ''
})

function formatDate(d: string) {
  return new Date(d).toLocaleDateString('zh-CN')
}

async function createSession() {
  if (creating.value) return

  creating.value = true
  try {
    const session = await chatApi.createSession()
    sessions.value = [session, ...sessions.value.filter((item) => item.id !== session.id)]
    await selectSession(session.id)
  } catch {
    ElMessage.error('创建对话失败，请稍后重试')
  } finally {
    creating.value = false
  }
}

async function selectSession(id: number) {
  if (streaming.value) {
    ElMessage.warning('当前回复仍在生成，请稍后切换对话')
    return
  }

  currentSessionId.value = id
  loadingMessages.value = true
  try {
    const session = await chatApi.getSession(id)
    messages.value = session.messages
    await scrollToBottom()
  } catch {
    ElMessage.error('加载对话失败，请稍后重试')
  } finally {
    loadingMessages.value = false
  }
}

async function deleteSession(id: number) {
  if (streaming.value && currentSessionId.value === id) {
    ElMessage.warning('当前回复仍在生成，请稍后删除对话')
    return
  }

  try {
    await chatApi.deleteSession(id)
    sessions.value = sessions.value.filter((s) => s.id !== id)
    if (currentSessionId.value === id) {
      currentSessionId.value = null
      messages.value = []
    }
  } catch {
    ElMessage.error('删除对话失败，请稍后重试')
  }
}

async function sendMessage() {
  const text = inputText.value.trim()
  if (!text || streaming.value || currentSessionId.value === null) return

  const userMsg: ChatMessage = {
    id: Date.now(),
    role: 'user',
    content: text,
    timestamp: new Date().toISOString(),
  }
  messages.value.push(userMsg)
  inputText.value = ''
  streaming.value = true
  streamingText.value = ''
  await scrollToBottom()

  const sessionId = currentSessionId.value
  const es = new EventSource(chatApi.buildMessageStreamUrl(sessionId, text, activeContext.value))
  eventSource = es

  es.onmessage = async (event) => {
    if (event.data === '[DONE]') {
      es.close()
      eventSource = null
      const completedText = streamingText.value
      streaming.value = false
      streamingText.value = ''
      messages.value.push(createLocalMessage('assistant', completedText))
      await syncSession(sessionId)
      await scrollToBottom()
      return
    }
    streamingText.value += event.data
    await scrollToBottom()
  }

  es.onerror = () => {
    es.close()
    eventSource = null
    streaming.value = false
    if (streamingText.value) {
      messages.value.push(createLocalMessage('assistant', streamingText.value))
      streamingText.value = ''
    }
    ElMessage.error('连接中断，请稍后重试')
  }
}

function createLocalMessage(role: ChatMessage['role'], content: string): ChatMessage {
  return {
    id: Date.now() + Math.floor(Math.random() * 1000),
    role,
    content,
    timestamp: new Date().toISOString(),
  }
}

async function syncSession(sessionId: number) {
  try {
    const session = await chatApi.getSession(sessionId)
    messages.value = session.messages
    sessions.value = [session, ...sessions.value.filter((item) => item.id !== session.id)]
  } catch {
    // The optimistic messages are already visible, so keep the UI usable.
  }
}

function clearContext() {
  router.replace({ name: 'Chat' })
}

async function scrollToBottom() {
  await nextTick()
  if (messagesEl.value) {
    messagesEl.value.scrollTop = messagesEl.value.scrollHeight
  }
}

onMounted(async () => {
  loadingSessions.value = true
  try {
    sessions.value = await chatApi.listSessions()
    if (sessions.value.length > 0) {
      await selectSession(sessions.value[0].id)
    }
  } catch {
    sessions.value = []
  } finally {
    loadingSessions.value = false
  }
})

onUnmounted(() => {
  eventSource?.close()
})
</script>

<style scoped>
.chat-page {
  display: flex;
  height: calc(100vh - 60px);
  background: #fff;
}

.chat-sidebar {
  width: 240px;
  border-right: 1px solid #e4e7ed;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.session-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

.sidebar-header {
  height: 52px;
  padding: 0 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 14px;
  font-weight: 600;
  border-bottom: 1px solid #e4e7ed;
}

.session-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  cursor: pointer;
  font-size: 13px;
  color: #606266;
  border-bottom: 1px solid #f5f7fa;
}

.session-item:hover,
.session-item.active {
  background: #ecf5ff;
  color: #409eff;
}

.session-date { flex: 1; }

.delete-btn { opacity: 0; }
.session-item:hover .delete-btn { opacity: 1; }

.empty-tip {
  padding: 20px;
  text-align: center;
  color: #c0c4cc;
  font-size: 13px;
}

.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.context-bar {
  min-height: 56px;
  padding: 10px 20px;
  border-bottom: 1px solid #e4e7ed;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  background: #f8fafc;
}

.context-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  overflow-wrap: anywhere;
}

.context-subtitle {
  margin-top: 3px;
  font-size: 12px;
  color: #909399;
}

.chat-welcome {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  color: #909399;
}

.messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.message {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}

.message.user {
  flex-direction: row-reverse;
}

.message-bubble {
  max-width: 70%;
  padding: 12px 16px;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

.message.assistant .message-bubble {
  background: #f5f7fa;
  color: #303133;
  border-top-left-radius: 4px;
}

.message.user .message-bubble {
  background: #409eff;
  color: #fff;
  border-top-right-radius: 4px;
}

.streaming .cursor {
  animation: blink 1s infinite;
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}

.input-area {
  padding: 16px 20px;
  border-top: 1px solid #e4e7ed;
  display: flex;
  gap: 12px;
  align-items: flex-end;
}

.input-area .el-button {
  height: 72px;
  width: 80px;
}
</style>
