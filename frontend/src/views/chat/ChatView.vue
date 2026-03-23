<template>
  <div class="chat-page">
    <div class="chat-sidebar">
      <div class="sidebar-header">
        <span>对话列表</span>
        <el-button :icon="Plus" circle size="small" @click="createSession" />
      </div>
      <div
        v-for="s in sessions"
        :key="s.id"
        class="session-item"
        :class="{ active: currentSessionId === s.id }"
        @click="selectSession(s.id)"
      >
        <el-icon><ChatDotRound /></el-icon>
        <span class="session-date">{{ formatDate(s.createdAt) }}</span>
        <el-button
          class="delete-btn"
          :icon="Delete"
          circle
          size="small"
          text
          @click.stop="deleteSession(s.id)"
        />
      </div>
      <div v-if="sessions.length === 0" class="empty-tip">暂无对话</div>
    </div>

    <div class="chat-main">
      <div v-if="!currentSessionId" class="chat-welcome">
        <el-icon :size="64" color="#c0c4cc"><ChatDotRound /></el-icon>
        <p>选择或创建一个对话开始提问</p>
        <el-button type="primary" @click="createSession">新建对话</el-button>
      </div>

      <template v-else>
        <div class="messages" ref="messagesEl">
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
          <div v-if="streaming" class="message assistant">
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
import { ref, nextTick, onMounted } from 'vue'
import { Plus, Delete, ChatDotRound } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { chatApi } from '@/api/chat'
import type { ChatSession, ChatMessage } from '@/types'

const sessions = ref<ChatSession[]>([])
const currentSessionId = ref('')
const messages = ref<ChatMessage[]>([])
const inputText = ref('')
const streaming = ref(false)
const streamingText = ref('')
const messagesEl = ref<HTMLElement>()

function formatDate(d: string) {
  return new Date(d).toLocaleDateString('zh-CN')
}

async function createSession() {
  const session = await chatApi.createSession()
  sessions.value.unshift(session)
  selectSession(session.id)
}

async function selectSession(id: string) {
  currentSessionId.value = id
  const session = await chatApi.getSession(id)
  messages.value = session.messages
  await scrollToBottom()
}

async function deleteSession(id: string) {
  await chatApi.deleteSession(id)
  sessions.value = sessions.value.filter((s) => s.id !== id)
  if (currentSessionId.value === id) {
    currentSessionId.value = ''
    messages.value = []
  }
}

async function sendMessage() {
  const text = inputText.value.trim()
  if (!text || streaming.value) return

  const userMsg: ChatMessage = {
    id: Date.now().toString(),
    role: 'user',
    content: text,
    timestamp: new Date().toISOString(),
  }
  messages.value.push(userMsg)
  inputText.value = ''
  streaming.value = true
  streamingText.value = ''
  await scrollToBottom()

  const token = localStorage.getItem('token')
  const url = chatApi.sendMessage(currentSessionId.value, text)
  const es = new EventSource(`${url}&Authorization=Bearer ${token}`)

  es.onmessage = async (event) => {
    if (event.data === '[DONE]') {
      es.close()
      messages.value.push({
        id: Date.now().toString(),
        role: 'assistant',
        content: streamingText.value,
        timestamp: new Date().toISOString(),
      })
      streaming.value = false
      streamingText.value = ''
      await scrollToBottom()
      return
    }
    streamingText.value += event.data
    await scrollToBottom()
  }

  es.onerror = () => {
    es.close()
    streaming.value = false
    ElMessage.error('连接中断，请重试')
  }
}

async function scrollToBottom() {
  await nextTick()
  if (messagesEl.value) {
    messagesEl.value.scrollTop = messagesEl.value.scrollHeight
  }
}

onMounted(async () => {
  sessions.value = await chatApi.listSessions().catch(() => [])
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
