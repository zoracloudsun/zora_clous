<template>
  <div class="chat-container">
    <!-- 侧边栏 -->
    <aside class="sidebar" :class="{ collapsed: sidebarCollapsed }">
      <!-- 侧边栏头部：Logo + 新对话 -->
      <div class="sidebar-top">
        <div class="sidebar-brand" v-if="!sidebarCollapsed">
          <div class="brand-icon">
            <el-icon :size="20"><ChatDotRound /></el-icon>
          </div>
          <span class="brand-text">AI 对话</span>
          <button class="collapse-trigger" @click="sidebarCollapsed = true" title="收起侧边栏">
            <el-icon :size="16"><Fold /></el-icon>
          </button>
        </div>
        <button
          v-if="!sidebarCollapsed"
          class="new-chat-btn"
          @click="handleNewChat"
        >
          <el-icon :size="16"><Plus /></el-icon>
          <span>新建对话</span>
        </button>
        <button
          v-else
          class="expand-trigger"
          @click="sidebarCollapsed = false"
          title="展开侧边栏"
        >
          <el-icon :size="18"><Expand /></el-icon>
        </button>
      </div>

      <!-- 搜索框 -->
      <div class="sidebar-search" v-if="!sidebarCollapsed">
        <div class="search-input-wrap">
          <el-icon class="search-icon" :size="14"><Search /></el-icon>
          <input
            v-model="searchQuery"
            class="search-input"
            placeholder="搜索对话..."
            @input="handleSearch"
          />
        </div>
      </div>

      <!-- 对话列表（正常模式 / 回收站模式） -->
      <div class="sidebar-list" v-if="!sidebarCollapsed">
        <!-- 正常对话列表 -->
        <template v-if="!showTrash">
          <template v-if="groupedConversations.length > 0">
            <div
              v-for="group in groupedConversations"
              :key="group.label"
              class="conv-group"
            >
              <div class="group-label">{{ group.label }}</div>
              <div
                v-for="conv in group.items"
                :key="conv.id"
                class="conv-item"
                :class="{ active: conv.id === currentConversationId }"
                @click="handleSelectConversation(conv.id)"
              >
                <div class="conv-item-content">
                  <div class="conv-item-title">{{ conv.title }}</div>
                </div>
                <button
                  class="conv-item-delete"
                  @click.stop="handleDeleteConversation(conv.id)"
                  title="删除对话"
                >
                  <el-icon :size="14"><Delete /></el-icon>
                </button>
              </div>
            </div>
          </template>
          <div v-else class="empty-state">
            <el-icon :size="32" class="empty-icon"><ChatLineRound /></el-icon>
            <span class="empty-text">暂无对话</span>
          </div>
        </template>

        <!-- 回收站列表 -->
        <template v-else>
          <div class="trash-header">
            <span class="trash-title">回收站</span>
            <span class="trash-hint">30 天后自动清除</span>
          </div>
          <template v-if="deletedConversations.length > 0">
            <div
              v-for="conv in deletedConversations"
              :key="conv.id"
              class="conv-item trash-item"
            >
              <div class="conv-item-content">
                <div class="conv-item-title">{{ conv.title }}</div>
                <div class="conv-item-meta">{{ formatDeletedTime(conv.deletedAt) }}</div>
              </div>
              <div class="trash-actions">
                <button
                  class="trash-action-btn restore"
                  @click.stop="handleRestore(conv.id)"
                  title="恢复"
                >
                  <el-icon :size="14"><RefreshLeft /></el-icon>
                </button>
                <button
                  class="trash-action-btn danger"
                  @click.stop="handlePermanentDelete(conv.id)"
                  title="永久删除"
                >
                  <el-icon :size="14"><Delete /></el-icon>
                </button>
              </div>
            </div>
          </template>
          <div v-else class="empty-state">
            <el-icon :size="32" class="empty-icon"><Delete /></el-icon>
            <span class="empty-text">回收站为空</span>
          </div>
        </template>
      </div>

      <!-- 侧边栏底部 -->
      <div class="sidebar-footer" v-if="!sidebarCollapsed">
        <button
          class="footer-btn"
          :class="{ active: showTrash }"
          @click="toggleTrash"
        >
          <el-icon :size="16"><Delete /></el-icon>
          <span>回收站</span>
          <span v-if="deletedConversations.length" class="trash-badge">
            {{ deletedConversations.length }}
          </span>
        </button>
        <button class="footer-btn" @click="$router.push('/knowledge')">
          <el-icon :size="16"><Collection /></el-icon>
          <span>知识库</span>
        </button>
        <button class="footer-btn" @click="$router.push('/home')">
          <el-icon :size="16"><HomeFilled /></el-icon>
          <span>返回主页</span>
        </button>
      </div>
    </aside>

    <!-- 主聊天区 -->
    <main class="chat-main">
      <!-- 顶部栏 -->
      <header class="chat-header">
        <div class="header-left">
          <button
            v-if="sidebarCollapsed"
            class="header-menu-btn"
            @click="sidebarCollapsed = false"
          >
            <el-icon :size="18"><Expand /></el-icon>
          </button>
          <h1 class="header-title">{{ currentTitle || '新对话' }}</h1>
        </div>
        <div class="header-right">
          <!-- Phase 3.3: Agent 智能体开关 -->
          <el-switch
            v-model="agentMode"
            size="small"
            active-text="Agent"
            style="margin-right: 8px"
          />
          <!-- Phase 2: RAG 知识库选择器 -->
          <template v-if="knowledgeBases.length > 0">
            <el-switch
              v-model="ragEnabled"
              size="small"
              active-text="RAG"
              style="margin-right: 8px"
            />
            <el-select
              v-model="selectedKbId"
              placeholder="选择知识库"
              size="small"
              :disabled="!ragEnabled"
              style="width: 140px; margin-right: 8px"
              clearable
            >
              <el-option
                v-for="kb in knowledgeBases"
                :key="kb.id"
                :label="kb.name"
                :value="kb.id"
              />
            </el-select>
          </template>
          <span class="header-badge">DeepSeek</span>
        </div>
      </header>

      <!-- Phase 3.3: 推理面板已移入 AI 消息气泡内部 -->

      <!-- 消息列表 -->
      <div class="chat-messages" ref="messagesContainer">
        <!-- 欢迎页 -->
        <div v-if="messages.length === 0 && !isStreaming" class="welcome-screen">
          <div class="welcome-logo">
            <div class="logo-ring">
              <el-icon :size="36"><ChatDotRound /></el-icon>
            </div>
          </div>
          <h2 class="welcome-title">有什么我可以帮你的？</h2>
          <p class="welcome-sub">我可以帮你编写代码、分析数据、回答问题</p>
          <div class="suggestion-grid">
            <button
              v-for="q in suggestions"
              :key="q"
              class="suggestion-card"
              @click="handleSuggestion(q)"
            >
              <span>{{ q }}</span>
            </button>
          </div>
        </div>

        <!-- 消息列表 -->
        <div
          v-for="(msg, index) in messages"
          :key="index"
          class="message-row"
          :class="msg.role"
        >
          <div class="message-avatar">
            <div v-if="msg.role === 'user'" class="avatar avatar-user">
              <el-icon :size="16"><UserFilled /></el-icon>
            </div>
            <div v-else class="avatar avatar-ai">
              <el-icon :size="16"><ChatDotRound /></el-icon>
            </div>
          </div>
          <div class="message-body">
            <div class="message-sender">{{ msg.role === 'user' ? '你' : 'AI' }}</div>

            <!-- Agent 推理过程（嵌入 AI 消息气泡内） -->
            <div
              v-if="msg.role === 'assistant' && msg.reasoningSteps && msg.reasoningSteps.length > 0"
              class="reasoning-inline"
            >
              <button
                class="reasoning-toggle"
                @click="msg.showReasoning = !msg.showReasoning"
              >
                <el-icon :size="12"><Cpu /></el-icon>
                <span>推理过程 ({{ msg.reasoningSteps.length }} 步)</span>
                <el-icon :size="12"><ArrowUp v-if="msg.showReasoning" /><ArrowDown v-else /></el-icon>
              </button>
              <Transition name="reasoning-panel">
                <div v-if="msg.showReasoning" class="reasoning-steps-inline reasoning-done">
                  <div
                    v-for="(step, idx) in msg.reasoningSteps"
                    :key="idx"
                    class="reasoning-step"
                    :class="'step-' + step.type"
                  >
                    <template v-if="step.type === 'thinking'">
                      <span class="step-icon step-icon-think">
                        <el-icon :size="12"><Loading /></el-icon>
                      </span>
                      <span class="step-text">{{ step.content }}</span>
                    </template>
                    <template v-else-if="step.type === 'tool_call'">
                      <span class="step-icon step-icon-tool">
                        <el-icon :size="12"><Tools /></el-icon>
                      </span>
                      <span class="step-label">调用工具：</span>
                      <span class="step-tool-name">{{ step.tool }}</span>
                      <span class="step-args" v-if="step.args && Object.keys(step.args).length">
                        <code>{{ formatToolArgs(step.args) }}</code>
                      </span>
                    </template>
                    <template v-else-if="step.type === 'tool_result'">
                      <span class="step-icon step-icon-result">
                        <el-icon :size="12"><CircleCheck /></el-icon>
                      </span>
                      <span class="step-label">工具返回：</span>
                      <span class="step-result-text">{{ formatToolResult(step.content) }}</span>
                    </template>
                  </div>
                </div>
              </Transition>
            </div>

            <div
              v-if="msg.role === 'assistant'"
              class="message-text markdown-body"
              v-html="renderMarkdown(msg.content)"
            ></div>
            <div v-else class="message-text message-text-user">{{ msg.content }}</div>
            <div v-if="msg.role === 'assistant' && msg.content" class="message-actions">
              <button class="action-btn" @click="copyContent(msg.content)" title="复制">
                <el-icon :size="14"><CopyDocument /></el-icon>
              </button>
            </div>
          </div>
        </div>

        <!-- 流式生成中 -->
        <div v-if="isStreaming" class="message-row assistant">
          <div class="message-avatar">
            <div class="avatar avatar-ai">
              <el-icon :size="16"><ChatDotRound /></el-icon>
            </div>
          </div>
          <div class="message-body">
            <div class="message-sender">
              AI
              <span v-if="agentMode && !streamingContent" class="agent-thinking-label">
                <el-icon :size="12" class="spin-icon"><Cpu /></el-icon>
                思考中...
              </span>
              <span class="typing-indicator" v-if="!agentMode || streamingContent">
                <span></span><span></span><span></span>
              </span>
            </div>

            <!-- Agent 推理过程（流式消息气泡内，实时更新） -->
            <div
              v-if="agentMode && reasoningSteps.length > 0"
              class="reasoning-inline"
            >
              <button
                class="reasoning-toggle"
                @click="showReasoning = !showReasoning"
              >
                <el-icon :size="12"><Cpu /></el-icon>
                <span>推理过程 ({{ reasoningSteps.length }} 步)</span>
                <el-icon :size="12"><ArrowUp v-if="showReasoning" /><ArrowDown v-else /></el-icon>
              </button>
              <Transition name="reasoning-panel">
                <div v-if="showReasoning" class="reasoning-steps-inline">
                  <div
                    v-for="(step, idx) in reasoningSteps"
                    :key="idx"
                    class="reasoning-step"
                    :class="'step-' + step.type"
                  >
                    <template v-if="step.type === 'thinking'">
                      <span class="step-icon step-icon-think">
                        <el-icon :size="12"><Loading /></el-icon>
                      </span>
                      <span class="step-text">{{ step.content }}</span>
                    </template>
                    <template v-else-if="step.type === 'tool_call'">
                      <span class="step-icon step-icon-tool">
                        <el-icon :size="12"><Tools /></el-icon>
                      </span>
                      <span class="step-label">调用工具：</span>
                      <span class="step-tool-name">{{ step.tool }}</span>
                      <span class="step-args" v-if="step.args && Object.keys(step.args).length">
                        <code>{{ formatToolArgs(step.args) }}</code>
                      </span>
                    </template>
                    <template v-else-if="step.type === 'tool_result'">
                      <span class="step-icon step-icon-result">
                        <el-icon :size="12"><CircleCheck /></el-icon>
                      </span>
                      <span class="step-label">工具返回：</span>
                      <span class="step-result-text">{{ formatToolResult(step.content) }}</span>
                    </template>
                  </div>
                </div>
              </Transition>
            </div>

            <!-- Agent 推理中暂未输出 token 时显示占位提示 -->
            <div
              v-if="agentMode && !streamingContent && reasoningSteps.length > 0"
              class="message-text agent-thinking-text"
            >
              正在分析问题，请稍候...
            </div>
            <div
              v-else-if="streamingContent"
              class="message-text markdown-body"
              v-html="renderMarkdown(streamingContent)"
            ></div>
          </div>
        </div>
      </div>

      <!-- 输入区域 -->
      <div class="chat-input-area">
        <div class="input-box">
          <textarea
            v-model="inputMessage"
            class="input-textarea"
            placeholder="输入你的问题..."
            :disabled="isStreaming"
            @keydown.enter.exact="handleSend"
            @input="autoResize"
            rows="1"
            ref="textareaRef"
          ></textarea>
          <div class="input-bottom">
            <span class="input-hint">
              <span v-if="inputMessage.length > MAX_MESSAGE_LENGTH * 0.8"
                    :class="{ 'char-warn': inputMessage.length > MAX_MESSAGE_LENGTH }">
                {{ inputMessage.length }}/{{ MAX_MESSAGE_LENGTH }}
              </span>
              <span v-else>Enter 发送，Shift+Enter 换行</span>
            </span>
            <button
              v-if="!isStreaming"
              class="send-btn"
              :class="{ active: inputMessage.trim() }"
              :disabled="!inputMessage.trim()"
              @click="handleSend"
            >
              <el-icon :size="16"><Promotion /></el-icon>
            </button>
            <button
              v-else
              class="stop-btn"
              @click="handleStop"
            >
              <el-icon :size="14"><VideoPause /></el-icon>
              <span>停止</span>
            </button>
          </div>
        </div>
        <div class="input-footer">
          <template v-if="ragEnabled && selectedKbId">
            <el-icon :size="12" color="#67c23a"><CircleCheckFilled /></el-icon>
            已从知识库检索上下文增强回答 |
          </template>
          AI 由 DeepSeek 大模型驱动，内容仅供参考
        </div>
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref, nextTick, watch, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Plus, Fold, Expand, Delete, HomeFilled, ChatDotRound,
  CopyDocument, Promotion, VideoPause, Search, ChatLineRound,
  UserFilled, RefreshLeft, Collection, CircleCheckFilled,
  // Phase 3.3: Agent 推理面板图标
  Cpu, Loading, Tools, CircleCheck, ArrowUp, ArrowDown,
} from '@element-plus/icons-vue'
import { marked } from 'marked'
import hljs from 'highlight.js'
import DOMPurify from 'dompurify'
import 'highlight.js/styles/github.css'
import katex from 'katex'
import 'katex/dist/katex.min.css'
import {
  getConversations, createConversation, getConversationMessages,
  deleteConversation, streamChat,
  getDeletedConversations, restoreConversation, permanentDeleteConversation,
} from '@/api/ai'
import { streamRagChat, listKnowledgeBases } from '@/api/rag'
// Phase 3.3: Agent SSE 流式对话
import { streamAgentChat } from '@/api/agent'

const router = useRouter()
const messagesContainer = ref(null)
const textareaRef = ref(null)
const sidebarCollapsed = ref(false)

// 对话状态
const conversations = ref([])
const currentConversationId = ref(null)
const messages = ref([])
const inputMessage = ref('')
const isStreaming = ref(false)
const streamingContent = ref('')
const searchQuery = ref('')
let abortController = null

// ==================== Phase 2: RAG 知识库状态 ====================
const ragEnabled = ref(false)
const knowledgeBases = ref([])
const selectedKbId = ref(null)

// ==================== Phase 3.3: Agent 智能体状态 ====================
const agentMode = ref(false)          // Agent 模式开关
const reasoningSteps = ref([])        // 推理步骤数组 [{type, content/tool/args, ts}]
const showReasoning = ref(true)       // 推理面板展开/折叠

// 回收站状态
const showTrash = ref(false)
const deletedConversations = ref([])

// 欢迎页推荐问题
const suggestions = [
  '用 Java 写一个快速排序',
  'Redis 有哪些数据结构？',
  '帮我写一个 Python 爬虫',
  '解释什么是 RESTful API',
  'Vue 3 Composition API 优势',
  '如何优化 SQL 查询性能？',
]

const currentTitle = computed(() => {
  if (!currentConversationId.value) return null
  const conv = conversations.value.find((c) => c.id === currentConversationId.value)
  return conv?.title || null
})

// ==================== 对话分组 ====================

const groupedConversations = computed(() => {
  const filtered = searchQuery.value
    ? conversations.value.filter((c) =>
        c.title?.toLowerCase().includes(searchQuery.value.toLowerCase())
      )
    : conversations.value

  const groups = { today: [], yesterday: [], earlier: [] }
  const now = new Date()
  const todayStart = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  const yesterdayStart = new Date(todayStart.getTime() - 86400000)

  filtered.forEach((conv) => {
    const date = new Date(conv.updatedAt)
    if (date >= todayStart) {
      groups.today.push(conv)
    } else if (date >= yesterdayStart) {
      groups.yesterday.push(conv)
    } else {
      groups.earlier.push(conv)
    }
  })

  const result = []
  if (groups.today.length) result.push({ label: '今天', items: groups.today })
  if (groups.yesterday.length) result.push({ label: '昨天', items: groups.yesterday })
  if (groups.earlier.length) result.push({ label: '更早', items: groups.earlier })
  return result
})

// ==================== Markdown 渲染（含 LaTeX 数学公式） ====================

marked.setOptions({ breaks: true, gfm: true })

// 注册数学公式扩展
// 块级：$$...$$、\[...\]、\begin{...}...\end{...}
// 行内：$...$、\(...\)  （需放在 marked.use 中，让 marked 先处理）
marked.use({
  extensions: [
    // 块级数学公式
    {
      name: 'mathBlock',
      level: 'block',
      start(src) { return src.match(/\$\$|\\\[|\\begin/)?.index },
      tokenizer(src) {
        // \begin{...} ... \end{...}
        const beginMatch = src.match(/^\\begin\{([^}]+)\}([\s\S]+?)\\end\{\1\}/)
        if (beginMatch) return { type: 'mathBlock', raw: beginMatch[0], text: beginMatch[0].trim() }
        // $$ ... $$
        let match = src.match(/^\$\$([\s\S]+?)\$\$/)
        if (match) return { type: 'mathBlock', raw: match[0], text: match[1].trim() }
        // \[ ... \]
        match = src.match(/^\\\[([\s\S]+?)\\\]/)
        if (match) return { type: 'mathBlock', raw: match[0], text: match[1].trim() }
      },
      renderer(token) {
        try {
          return katex.renderToString(token.text, { displayMode: true, throwOnError: false, trust: true })
        } catch { return `<pre>${escapeHtml(token.text)}</pre>` }
      }
    },
    // 行内数学公式（仅 \(...\)，不用 $ 避免和 markdown 加粗/货币冲突）
    {
      name: 'mathInline',
      level: 'inline',
      start(src) { return src.match(/\\\(/)?.index },
      tokenizer(src) {
        const parenMatch = src.match(/^\\\(([\s\S]+?)\\\)/)
        if (parenMatch) return { type: 'mathInline', raw: parenMatch[0], text: parenMatch[1].trim() }
      },
      renderer(token) {
        try {
          return katex.renderToString(token.text, { displayMode: false, throwOnError: false, trust: true })
        } catch { return escapeHtml(token.text) }
      }
    }
  ]
})

function escapeHtml(str) {
  return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

/**
 * 预处理文本，在 CJK 字符与 ** 分隔符之间插入零宽空格
 * 修复 Marked v18 GFM 规范不识别 CJK 字符旁的 ** 加粗标记的问题
 *
 * 原理：GFM 的 left-flanking delimiter run 要求 ** 前面是 ASCII 标点或空格，
 * CJK 字符不在此列。插入零宽空格（U+200B）可绕过此限制，
 * 且零宽空格在渲染时不可见，不影响显示效果。
 */
function fixCjkBold(text) {
  // CJK 字符范围：一-鿿 (基本) + 　-〿 (标点) + ＀-￯ (全角)
  return text
    .replace(/([一-鿿　-〿＀-￯])(\*\*)/g, '$1​$2')
    .replace(/(\*\*)([一-鿿　-〿＀-￯])/g, '$1​$2')
}

const renderMarkdown = (text) => {
  if (!text) return ''
  try {
    const raw = marked.parse(fixCjkBold(text), {
      highlight: (code, lang) => {
        if (lang && hljs.getLanguage(lang)) {
          try { return hljs.highlight(code, { language: lang }).value } catch {}
        }
        try { return hljs.highlightAuto(code).value } catch {}
        return code
      },
    })
    // ADD_TAGS/ADD_ATTR: 允许 KaTeX 生成的 HTML 元素和属性通过 DOMPurify
    return DOMPurify.sanitize(raw, {
      ADD_TAGS: ['math', 'mrow', 'mi', 'mo', 'mn', 'msup', 'msub', 'mfrac', 'msqrt',
        'mover', 'munder', 'munderover', 'mspace', 'semantics', 'annotation', 'annotation-encoding'],
      ADD_ATTR: ['mathvariant', 'mathsize', 'mathcolor', 'mathbackground', 'dir', 'aria-hidden'],
    })
  } catch {
    return text
  }
}

// ==================== 对话管理 ====================

const loadConversations = async () => {
  try {
    const res = await getConversations()
    conversations.value = res.data || []
  } catch {}
}

const loadKbs = async () => {
  try {
    const res = await listKnowledgeBases()
    knowledgeBases.value = res.data || []
  } catch {}
}

const handleNewChat = () => {
  currentConversationId.value = null
  messages.value = []
  streamingContent.value = ''
  inputMessage.value = ''
}

const handleSelectConversation = async (id) => {
  if (isStreaming.value) { ElMessage.warning('请等待当前对话完成'); return }
  currentConversationId.value = id
  messages.value = []
  streamingContent.value = ''
  try {
    const res = await getConversationMessages(id)
    messages.value = res.data || []
    await scrollToBottom()
  } catch {
    ElMessage.error('加载对话失败')
  }
}

const handleDeleteConversation = async (id) => {
  try {
    await ElMessageBox.confirm('确定删除该对话？', '提示', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await deleteConversation(id)
    ElMessage.success('已删除')
    if (currentConversationId.value === id) handleNewChat()
    await loadConversations()
  } catch {}
}

const handleSearch = () => {}

// ==================== 回收站 ====================

const toggleTrash = async () => {
  showTrash.value = !showTrash.value
  if (showTrash.value) {
    await loadDeletedConversations()
  }
}

const loadDeletedConversations = async () => {
  try {
    const res = await getDeletedConversations()
    deletedConversations.value = res.data || []
  } catch {}
}

const handleRestore = async (id) => {
  try {
    await restoreConversation(id)
    ElMessage.success('已恢复')
    await loadDeletedConversations()
    await loadConversations()
  } catch {
    ElMessage.error('恢复失败')
  }
}

const handlePermanentDelete = async (id) => {
  try {
    await ElMessageBox.confirm('永久删除后无法恢复，确定继续？', '永久删除', {
      confirmButtonText: '永久删除',
      cancelButtonText: '取消',
      type: 'error',
    })
    await permanentDeleteConversation(id)
    ElMessage.success('已永久删除')
    await loadDeletedConversations()
  } catch {}
}

const formatDeletedTime = (dateStr) => {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  const now = new Date()
  const diff = now - date
  const days = Math.floor(diff / 86400000)
  if (days === 0) return '今天'
  if (days === 1) return '昨天'
  if (days < 30) return `${days} 天前`
  return date.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' })
}

// ==================== 消息发送与流式接收 ====================

const MAX_MESSAGE_LENGTH = 4000 // P1-2: 与后端一致

const handleSend = async (e) => {
  if (e && e.isComposing) return
  if (e && e.shiftKey) return
  if (e && e.preventDefault) e.preventDefault()
  const msg = inputMessage.value.trim()
  if (!msg || isStreaming.value) return

  // P1-2: 前端消息长度校验
  if (msg.length > MAX_MESSAGE_LENGTH) {
    ElMessage.warning(`消息长度不能超过 ${MAX_MESSAGE_LENGTH} 个字符`)
    return
  }

  inputMessage.value = ''
  // 重置输入框高度（textarea 不会自动收缩）
  nextTick(() => { if (textareaRef.value) textareaRef.value.style.height = 'auto' })
  isStreaming.value = true
  streamingContent.value = ''
  // Phase 3.3: 清空上一轮的推理步骤
  reasoningSteps.value = []
  showReasoning.value = true
  messages.value.push({ role: 'user', content: msg })
  await scrollToBottom()

  const convId = currentConversationId.value

  // ==================== Phase 3.3: Agent 模式 — 结构化 SSE 流式对话 ====================
  if (agentMode.value) {
    abortController = streamAgentChat(
      msg, convId,
      // onThinking: Agent 思考过程
      (content) => {
        reasoningSteps.value.push({ type: 'thinking', content, ts: Date.now() })
        scrollToBottom()
      },
      // onToolCall: Agent 调用工具
      (tool, args) => {
        reasoningSteps.value.push({ type: 'tool_call', tool, args, ts: Date.now() })
        scrollToBottom()
      },
      // onToolResult: 工具执行完毕
      (tool, content) => {
        reasoningSteps.value.push({ type: 'tool_result', tool, content, ts: Date.now() })
        scrollToBottom()
      },
      // onToken: 最终回答 token
      (token) => {
        streamingContent.value += token
        scrollToBottom()
      },
      // onDone: 对话完成
      async (conversationId) => {
        if (streamingContent.value) {
          messages.value.push({
            role: 'assistant',
            content: streamingContent.value,
            // 保存推理步骤到消息对象，支持后续展开/收起
            reasoningSteps: reasoningSteps.value.length > 0 ? [...reasoningSteps.value] : null,
            showReasoning: false,
          })
        }
        streamingContent.value = ''
        isStreaming.value = false
        abortController = null
        if (!currentConversationId.value) {
          await loadConversations()
          if (conversations.value.length > 0) {
            currentConversationId.value = conversations.value[0].id
          }
        } else {
          await loadConversations()
        }
        await scrollToBottom()
      },
      // onError: 出错
      (error) => {
        ElMessage.error(error.message || 'AI 请求失败')
        if (streamingContent.value) {
          messages.value.push({ role: 'assistant', content: streamingContent.value + '\n\n*[回复中断]*' })
        }
        streamingContent.value = ''
        isStreaming.value = false
        abortController = null
      },
    )
    return
  }

  // ==================== Phase 2: RAG / 标准模式（原有逻辑） ====================
  const kbId = ragEnabled.value ? selectedKbId.value : null
  const callChat = kbId
    ? (onT, onD, onE) => streamRagChat(msg, convId, kbId, onT, onD, onE)
    : (onT, onD, onE) => streamChat(msg, convId, onT, onD, onE)

  abortController = callChat(
    (token) => {
      streamingContent.value += token
      scrollToBottom()
    },
    async () => {
      if (streamingContent.value) {
        messages.value.push({ role: 'assistant', content: streamingContent.value })
      }
      streamingContent.value = ''
      isStreaming.value = false
      abortController = null
      if (!currentConversationId.value) {
        await loadConversations()
        if (conversations.value.length > 0) {
          currentConversationId.value = conversations.value[0].id
        }
      } else {
        await loadConversations()
      }
      await scrollToBottom()
    },
    (error) => {
      ElMessage.error(error.message || 'AI 请求失败')
      if (streamingContent.value) {
        messages.value.push({ role: 'assistant', content: streamingContent.value + '\n\n*[回复中断]*' })
      }
      streamingContent.value = ''
      isStreaming.value = false
      abortController = null
    },
  )
}

const handleStop = () => {
  if (abortController) {
    abortController.abort()
    if (streamingContent.value) {
      messages.value.push({ role: 'assistant', content: streamingContent.value + '\n\n*[已停止生成]*' })
    }
    streamingContent.value = ''
    isStreaming.value = false
    abortController = null
    ElMessage.info('已停止生成')
  }
}

const handleSuggestion = (text) => {
  inputMessage.value = text
  handleSend()
}

// ==================== 辅助功能 ====================

const scrollToBottom = async () => {
  await nextTick()
  if (messagesContainer.value) {
    messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
  }
}

const copyContent = (text) => {
  navigator.clipboard.writeText(text).then(() => {
    ElMessage.success('已复制')
  }).catch(() => ElMessage.error('复制失败'))
}

// ==================== Phase 3.3: Agent 推理面板工具函数 ====================

/**
 * 格式化工具调用参数为简洁的字符串显示
 * <p>
 * 将参数对象转为 "key=value" 格式，方便在推理面板中显示。
 * 对于嵌套对象，先尝试 JSON 序列化再截断。
 * </p>
 */
const formatToolArgs = (args) => {
  if (!args || typeof args !== 'object') return ''
  const entries = Object.entries(args)
  if (!entries.length) return ''
  return entries.map(([k, v]) => {
    const val = typeof v === 'object' ? JSON.stringify(v) : String(v)
    return `${k}=${val.length > 60 ? val.substring(0, 60) + '…' : val}`
  }).join(', ')
}

/**
 * 格式化工具返回结果为简短摘要
 * <p>
 * 工具返回内容可能很长（如搜索结果 JSON），在推理面板中只需显示摘要。
 * 截取前 120 字符，并尝试从 JSON 中提取有意义的信息。
 * </p>
 */
const formatToolResult = (content) => {
  if (!content) return '(空)'
  // 尝试作为 JSON 解析，提取摘要信息
  try {
    const obj = JSON.parse(content)
    if (obj.error) return `错误: ${obj.error}`
    if (obj.result !== undefined) return `结果: ${obj.result}`
    if (obj.expression && obj.result !== undefined) return `${obj.expression} = ${obj.result}`
    // 搜索结果
    if (obj.results && Array.isArray(obj.results)) return `找到 ${obj.results.length} 条结果`
    if (obj.answer) return String(obj.answer).substring(0, 80)
    // 代码执行结果
    if (obj.success !== undefined) {
      if (obj.stdout) return String(obj.stdout).substring(0, 80)
      return obj.success ? '执行成功' : `执行失败: ${obj.error || '未知错误'}`
    }
  } catch {}
  // 纯文本截断
  return content.length > 120 ? content.substring(0, 120) + '…' : content
}

const autoResize = () => {
  const el = textareaRef.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = Math.min(el.scrollHeight, 160) + 'px'
}

watch(() => streamingContent.value, () => scrollToBottom())

onMounted(() => { loadConversations(); loadKbs() })
</script>

<style scoped>
/* ================================================================
   腾讯元宝风格 — 极简侧边栏 + 现代对话界面
   设计语言：浅色侧边栏、微妙边框分隔、圆角卡片、克制的动效
   ================================================================ */

.chat-container {
  display: flex;
  height: 100vh;
  background: #f7f7f8;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC',
    'Hiragino Sans GB', 'Microsoft YaHei', sans-serif;
}

/* ==================== 侧边栏 ==================== */
.sidebar {
  width: 260px;
  min-width: 260px;
  background: #ffffff;
  border-right: 1px solid #e8e8ec;
  display: flex;
  flex-direction: column;
  transition: width 0.2s ease, min-width 0.2s ease;
  overflow: hidden;
}
.sidebar.collapsed {
  width: 0;
  min-width: 0;
  border-right: none;
}

/* 侧边栏顶部 */
.sidebar-top {
  padding: 16px 12px 8px;
  flex-shrink: 0;
}
.sidebar-brand {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 4px 12px;
}
.brand-icon {
  width: 28px;
  height: 28px;
  border-radius: 8px;
  background: linear-gradient(135deg, #1677ff, #4096ff);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  flex-shrink: 0;
}
.brand-text {
  font-size: 15px;
  font-weight: 600;
  color: #1d1d1f;
  flex: 1;
}
.collapse-trigger {
  width: 28px;
  height: 28px;
  border: none;
  background: transparent;
  border-radius: 6px;
  color: #8e8e93;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s;
}
.collapse-trigger:hover {
  background: #f0f0f2;
  color: #1d1d1f;
}

/* 新建对话按钮 */
.new-chat-btn {
  width: 100%;
  height: 40px;
  border: 1px solid #e0e0e4;
  border-radius: 10px;
  background: #fff;
  color: #1d1d1f;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  transition: all 0.15s;
}
.new-chat-btn:hover {
  background: #f5f5f7;
  border-color: #d0d0d4;
}

/* 展开按钮（折叠态） */
.expand-trigger {
  width: 36px;
  height: 36px;
  border: none;
  background: transparent;
  border-radius: 8px;
  color: #8e8e93;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto;
  transition: all 0.15s;
}
.expand-trigger:hover {
  background: #f0f0f2;
  color: #1d1d1f;
}

/* 搜索框 */
.sidebar-search {
  padding: 4px 12px 8px;
  flex-shrink: 0;
}
.search-input-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 34px;
  padding: 0 10px;
  background: #f5f5f7;
  border-radius: 8px;
  transition: all 0.15s;
}
.search-input-wrap:focus-within {
  background: #fff;
  box-shadow: 0 0 0 1px #1677ff;
}
.search-icon {
  color: #8e8e93;
  flex-shrink: 0;
}
.search-input {
  flex: 1;
  border: none;
  background: transparent;
  outline: none;
  font-size: 13px;
  color: #1d1d1f;
}
.search-input::placeholder {
  color: #b0b0b4;
}

/* 对话列表 */
.sidebar-list {
  flex: 1;
  overflow-y: auto;
  padding: 0 8px;
  scrollbar-width: thin;
  scrollbar-color: #d0d0d4 transparent;
}
.sidebar-list::-webkit-scrollbar {
  width: 4px;
}
.sidebar-list::-webkit-scrollbar-thumb {
  background: #d0d0d4;
  border-radius: 4px;
}

/* 时间分组 */
.conv-group {
  margin-bottom: 4px;
}
.group-label {
  padding: 8px 8px 4px;
  font-size: 11px;
  font-weight: 600;
  color: #8e8e93;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  user-select: none;
}

/* 对话项 */
.conv-item {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 8px 10px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.12s;
  position: relative;
  margin-bottom: 1px;
}
.conv-item:hover {
  background: #f5f5f7;
}
.conv-item.active {
  background: #e8f0fe;
}
.conv-item.active .conv-item-title {
  color: #1677ff;
}
.conv-item-content {
  flex: 1;
  min-width: 0;
}
.conv-item-title {
  font-size: 13px;
  color: #3c3c3e;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  line-height: 1.4;
}

/* 删除按钮（hover 显示） */
.conv-item-delete {
  width: 24px;
  height: 24px;
  border: none;
  background: transparent;
  border-radius: 6px;
  color: #b0b0b4;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: all 0.12s;
  flex-shrink: 0;
}
.conv-item:hover .conv-item-delete {
  opacity: 1;
}
.conv-item-delete:hover {
  background: #fee2e2;
  color: #ef4444;
}

/* 空状态 */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;
  gap: 8px;
}
.empty-icon {
  color: #d0d0d4;
}
.empty-text {
  font-size: 13px;
  color: #b0b0b4;
}

/* 侧边栏底部 */
.sidebar-footer {
  padding: 8px 12px 12px;
  border-top: 1px solid #f0f0f2;
  flex-shrink: 0;
}
.footer-btn {
  width: 100%;
  height: 36px;
  border: none;
  background: transparent;
  border-radius: 8px;
  color: #636366;
  font-size: 13px;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 8px;
  transition: all 0.15s;
}
.footer-btn:hover {
  background: #f5f5f7;
  color: #1d1d1f;
}
.footer-btn.active {
  background: #f0f0f2;
  color: #1677ff;
}
.trash-badge {
  background: #ef4444;
  color: #fff;
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 10px;
  margin-left: auto;
  line-height: 1.4;
}

/* 回收站 */
.trash-header {
  padding: 8px 8px 12px;
  display: flex;
  justify-content: space-between;
  align-items: baseline;
}
.trash-title {
  font-size: 13px;
  font-weight: 600;
  color: #1d1d1f;
}
.trash-hint {
  font-size: 11px;
  color: #b0b0b4;
}
.trash-item {
  opacity: 0.8;
}
.conv-item-meta {
  font-size: 11px;
  color: #b0b0b4;
  margin-top: 2px;
}
.trash-actions {
  display: flex;
  gap: 2px;
  opacity: 0;
  transition: opacity 0.12s;
}
.trash-item:hover .trash-actions {
  opacity: 1;
}
.trash-action-btn {
  width: 24px;
  height: 24px;
  border: none;
  background: transparent;
  border-radius: 6px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.12s;
}
.trash-action-btn.restore {
  color: #1677ff;
}
.trash-action-btn.restore:hover {
  background: #e8f0fe;
}
.trash-action-btn.danger {
  color: #8e8e93;
}
.trash-action-btn.danger:hover {
  background: #fee2e2;
  color: #ef4444;
}

/* ==================== 主聊天区 ==================== */
.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

/* 顶部栏 */
.chat-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 20px;
  height: 52px;
  background: #ffffff;
  border-bottom: 1px solid #e8e8ec;
  flex-shrink: 0;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
}
.header-menu-btn {
  width: 32px;
  height: 32px;
  border: none;
  background: transparent;
  border-radius: 8px;
  color: #636366;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s;
}
.header-menu-btn:hover {
  background: #f0f0f2;
}
.header-title {
  font-size: 15px;
  font-weight: 600;
  color: #1d1d1f;
  margin: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 300px;
}
.header-badge {
  font-size: 11px;
  color: #8e8e93;
  background: #f0f0f2;
  padding: 3px 8px;
  border-radius: 6px;
  font-weight: 500;
}

/* ==================== 消息区域 ==================== */
.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 24px 0;
  scroll-behavior: smooth;
}

/* 欢迎页 */
.welcome-screen {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 20px 40px;
  text-align: center;
}
.welcome-logo {
  margin-bottom: 20px;
}
.logo-ring {
  width: 72px;
  height: 72px;
  border-radius: 20px;
  background: linear-gradient(135deg, #1677ff, #4096ff);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  box-shadow: 0 8px 24px rgba(22, 119, 255, 0.2);
}
.welcome-title {
  font-size: 22px;
  font-weight: 600;
  color: #1d1d1f;
  margin: 0 0 6px;
}
.welcome-sub {
  font-size: 14px;
  color: #8e8e93;
  margin: 0 0 28px;
}
.suggestion-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
  max-width: 520px;
  width: 100%;
}
.suggestion-card {
  padding: 12px 16px;
  background: #fff;
  border: 1px solid #e8e8ec;
  border-radius: 10px;
  font-size: 13px;
  color: #3c3c3e;
  cursor: pointer;
  text-align: left;
  transition: all 0.15s;
  line-height: 1.5;
}
.suggestion-card:hover {
  border-color: #1677ff;
  color: #1677ff;
  background: #f0f6ff;
  box-shadow: 0 2px 8px rgba(22, 119, 255, 0.08);
}

/* 消息行 */
.message-row {
  display: flex;
  gap: 12px;
  padding: 8px 24px;
  max-width: 820px;
  margin: 0 auto;
  width: 100%;
  box-sizing: border-box;
}
.message-row.user {
  flex-direction: row-reverse;
}
.message-avatar {
  flex-shrink: 0;
  padding-top: 2px;
}
.avatar {
  width: 30px;
  height: 30px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.avatar-user {
  background: #e8e8ec;
  color: #636366;
}
.avatar-ai {
  background: linear-gradient(135deg, #1677ff, #4096ff);
  color: #fff;
}
.message-body {
  flex: 1;
  min-width: 0;
}
.message-sender {
  font-size: 12px;
  color: #8e8e93;
  margin-bottom: 4px;
  font-weight: 500;
  display: flex;
  align-items: center;
  gap: 8px;
}
.message-text {
  font-size: 14px;
  line-height: 1.7;
  color: #1d1d1f;
  border-radius: 12px;
  padding: 10px 14px;
  word-break: break-word;
}
.message-text-user {
  background: #1677ff;
  color: #fff;
  display: inline-block;
  max-width: 80%;
  float: right;
  border-radius: 12px 2px 12px 12px;
}
.message-row.assistant .message-text {
  background: #fff;
  border: 1px solid #e8e8ec;
  border-radius: 2px 12px 12px 12px;
}

/* 打字指示器 */
.typing-indicator {
  display: inline-flex;
  gap: 3px;
  align-items: center;
}
.typing-indicator span {
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: #1677ff;
  animation: typing-bounce 1.2s infinite;
}
.typing-indicator span:nth-child(2) { animation-delay: 0.2s; }
.typing-indicator span:nth-child(3) { animation-delay: 0.4s; }
@keyframes typing-bounce {
  0%, 60%, 100% { transform: translateY(0); opacity: 0.4; }
  30% { transform: translateY(-4px); opacity: 1; }
}

/* 消息操作（hover 显示） */
.message-actions {
  margin-top: 4px;
  opacity: 0;
  transition: opacity 0.12s;
}
.message-row:hover .message-actions {
  opacity: 1;
}
.action-btn {
  width: 28px;
  height: 28px;
  border: none;
  background: transparent;
  border-radius: 6px;
  color: #8e8e93;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: all 0.12s;
}
.action-btn:hover {
  background: #f0f0f2;
  color: #1d1d1f;
}

/* ==================== Markdown 渲染 ==================== */
.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3) {
  margin: 16px 0 8px;
  font-weight: 600;
  color: #1d1d1f;
}
.markdown-body :deep(h1) { font-size: 1.3em; }
.markdown-body :deep(h2) { font-size: 1.15em; }
.markdown-body :deep(h3) { font-size: 1.02em; }
.markdown-body :deep(p) { margin: 8px 0; }
.markdown-body :deep(p:first-child) { margin-top: 0; }
.markdown-body :deep(p:last-child) { margin-bottom: 0; }
.markdown-body :deep(ul),
.markdown-body :deep(ol) { padding-left: 20px; margin: 8px 0; }
.markdown-body :deep(li) { margin: 4px 0; }
.markdown-body :deep(code) {
  font-family: 'SF Mono', 'Fira Code', 'Consolas', monospace;
  font-size: 0.88em;
}
.markdown-body :deep(p > code),
.markdown-body :deep(li > code) {
  background: #f0f0f2;
  padding: 2px 6px;
  border-radius: 4px;
  color: #d63384;
}
.markdown-body :deep(pre) {
  background: #f6f8fa;
  border-radius: 8px;
  padding: 14px 16px;
  overflow-x: auto;
  margin: 10px 0;
  border: 1px solid #e8e8ec;
}
.markdown-body :deep(pre code) {
  background: none;
  padding: 0;
  font-size: 0.84em;
  line-height: 1.6;
  color: #1d1d1f;
}
.markdown-body :deep(table) {
  border-collapse: collapse;
  margin: 10px 0;
  width: 100%;
  font-size: 13px;
}
.markdown-body :deep(th),
.markdown-body :deep(td) {
  border: 1px solid #e8e8ec;
  padding: 7px 12px;
  text-align: left;
}
.markdown-body :deep(th) {
  background: #f6f8fa;
  font-weight: 600;
}
.markdown-body :deep(blockquote) {
  border-left: 3px solid #1677ff;
  margin: 8px 0;
  padding: 4px 14px;
  color: #636366;
  background: #f8faff;
  border-radius: 0 6px 6px 0;
}
.markdown-body :deep(a) {
  color: #1677ff;
  text-decoration: none;
}
.markdown-body :deep(a:hover) {
  text-decoration: underline;
}
.markdown-body :deep(hr) {
  border: none;
  border-top: 1px solid #e8e8ec;
  margin: 14px 0;
}

/* ==================== KaTeX 数学公式样式 ==================== */
.markdown-body :deep(.katex-display) {
  margin: 12px 0;
  overflow-x: auto;
  overflow-y: hidden;
  padding: 8px 0;
}
.markdown-body :deep(.katex) {
  font-size: 1.05em;
}
.markdown-body :deep(.katex-html) {
  white-space: nowrap;
}
.markdown-body :deep(.katex-display > .katex) {
  font-size: 1.15em;
}

/* ==================== 输入区域 ==================== */
.chat-input-area {
  padding: 12px 24px 16px;
  flex-shrink: 0;
}
.input-box {
  max-width: 820px;
  margin: 0 auto;
  background: #fff;
  border: 1px solid #e0e0e4;
  border-radius: 14px;
  padding: 10px 14px;
  transition: all 0.15s;
}
.input-box:focus-within {
  border-color: #1677ff;
  box-shadow: 0 0 0 2px rgba(22, 119, 255, 0.1);
}
.input-textarea {
  width: 100%;
  border: none;
  outline: none;
  resize: none;
  font-size: 14px;
  line-height: 1.6;
  color: #1d1d1f;
  background: transparent;
  font-family: inherit;
  min-height: 24px;
  max-height: 160px;
}
.input-textarea::placeholder {
  color: #b0b0b4;
}
.input-bottom {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 6px;
}
.input-hint {
  font-size: 11px;
  color: #b0b0b4;
}
.char-warn {
  color: #ef4444;
  font-weight: 500;
}
.send-btn {
  width: 32px;
  height: 32px;
  border: none;
  background: #e0e0e4;
  border-radius: 8px;
  color: #fff;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s;
}
.send-btn.active {
  background: #1677ff;
}
.send-btn.active:hover {
  background: #4096ff;
}
.send-btn:disabled {
  cursor: not-allowed;
}
.stop-btn {
  height: 30px;
  padding: 0 12px;
  border: 1px solid #e0e0e4;
  background: #fff;
  border-radius: 8px;
  color: #636366;
  font-size: 12px;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 4px;
  transition: all 0.15s;
}
.stop-btn:hover {
  border-color: #d0d0d4;
  background: #f5f5f7;
}
.input-footer {
  text-align: center;
  font-size: 11px;
  color: #b0b0b4;
  margin-top: 8px;
  max-width: 820px;
  margin-left: auto;
  margin-right: auto;
}

/* ==================== Phase 3.3: Agent 推理过程（内联于 AI 消息气泡） ==================== */

/* 推理过程容器（内联） */
.reasoning-inline {
  margin-bottom: 8px;
}

/* 展开/收起按钮 */
.reasoning-toggle {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  background: #fafbff;
  border: 1px solid #d6e4ff;
  border-radius: 8px;
  font-size: 12px;
  color: #1677ff;
  cursor: pointer;
  transition: all 0.15s;
  width: 100%;
}
.reasoning-toggle:hover {
  background: #f0f5ff;
  border-color: #1677ff;
}
.reasoning-toggle span {
  flex: 1;
  text-align: left;
}

/* 推理步骤列表（内联） */
.reasoning-steps-inline {
  padding: 6px 0 2px;
  max-height: 200px;
  overflow-y: auto;
  scrollbar-width: thin;
  scrollbar-color: #d0d0d4 transparent;
}
.reasoning-steps-inline::-webkit-scrollbar { width: 4px; }
.reasoning-steps-inline::-webkit-scrollbar-thumb {
  background: #d0d0d4;
  border-radius: 4px;
}

/* 推理步骤项 */
.reasoning-step {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  padding: 5px 8px;
  margin-bottom: 3px;
  border-radius: 6px;
  border-left: 3px solid transparent;
  font-size: 12px;
  line-height: 1.5;
  animation: step-enter 0.25s ease-out;
}
@keyframes step-enter {
  from { opacity: 0; transform: translateY(-6px); }
  to   { opacity: 1; transform: translateY(0); }
}

/* 步骤色彩编码 */
.step-thinking {
  border-left-color: #1677ff;
  background: #f0f5ff;
}
.step-tool_call {
  border-left-color: #f59e0b;
  background: #fffbeb;
}
.step-tool_result {
  border-left-color: #10b981;
  background: #f0fdf6;
}

/* 步骤图标 */
.step-icon {
  flex-shrink: 0;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-top: 1px;
}
.step-icon-think { background: #e8f0fe; color: #1677ff; }
.step-icon-tool  { background: #fef3c7; color: #f59e0b; }
.step-icon-result { background: #d1fae5; color: #10b981; }

/* 流式推理中的图标动画（完成后通过 .reasoning-done 停止） */
.reasoning-steps-inline:not(.reasoning-done) .step-icon-think { animation: spin 1.2s linear infinite; }
.reasoning-steps-inline:not(.reasoning-done) .step-icon-tool  { animation: pulse 1.5s ease-in-out infinite; }
.reasoning-steps-inline:not(.reasoning-done) .step-icon-result { animation: pop-in 0.3s ease-out; }

.step-text {
  color: #3c3c3e;
  flex: 1;
}
.step-label {
  color: #8e8e93;
  flex-shrink: 0;
}
.step-tool-name {
  font-weight: 600;
  color: #f59e0b;
  font-family: 'SF Mono', 'Fira Code', 'Consolas', monospace;
  font-size: 11px;
}
.step-args code {
  font-size: 11px;
  color: #636366;
  background: #f0f0f2;
  padding: 1px 6px;
  border-radius: 4px;
  font-family: 'SF Mono', 'Fira Code', 'Consolas', monospace;
  word-break: break-all;
}
.step-result-text {
  color: #3c3c3e;
  word-break: break-word;
}

/* 推理面板过渡动画 */
.reasoning-panel-enter-active,
.reasoning-panel-leave-active {
  transition: all 0.25s ease;
}
.reasoning-panel-enter-from,
.reasoning-panel-leave-to {
  opacity: 0;
  max-height: 0;
  margin-top: 0;
  margin-bottom: 0;
}
.reasoning-panel-enter-to,
.reasoning-panel-leave-from {
  opacity: 1;
  max-height: 300px;
}

/* Agent 思考标签 */
.agent-thinking-label {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: #1677ff;
  font-weight: 500;
}
.spin-icon {
  animation: spin 1.2s linear infinite;
}
@keyframes spin {
  from { transform: rotate(0deg); }
  to   { transform: rotate(360deg); }
}
@keyframes pulse {
  0%, 100% { transform: scale(1); opacity: 1; }
  50%      { transform: scale(1.15); opacity: 0.8; }
}
@keyframes pop-in {
  from { transform: scale(0.5); opacity: 0; }
  to   { transform: scale(1); opacity: 1; }
}

/* Agent 思考中占位文本 */
.agent-thinking-text {
  color: #8e8e93 !important;
  font-style: italic;
  font-size: 13px !important;
  background: #fafbff !important;
  border: 1px dashed #d6e4ff !important;
}
</style>
