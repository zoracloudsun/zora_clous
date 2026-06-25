<template>
  <div class="search-page">
    <div class="search-container">
      <!-- 搜索框 -->
      <div class="search-header">
        <h2 class="page-title">
          <el-icon><Search /></el-icon>
          全文搜索
        </h2>
        <p class="page-desc">搜索所有对话中的消息内容，支持中文分词和关键词高亮</p>
        <div class="search-input-row">
          <el-input
            v-model="keyword"
            placeholder="输入关键词搜索消息内容…"
            size="large"
            clearable
            @keyup.enter="handleSearch"
            @clear="handleClear"
            class="search-input"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
          <el-button type="primary" size="large" @click="handleSearch" :loading="loading">
            <el-icon><Search /></el-icon>
            搜索
          </el-button>
        </div>
      </div>

      <!-- 搜索结果 -->
      <div class="search-results" v-if="searched">
        <!-- 结果统计 -->
        <div class="results-header" v-if="total > 0">
          <span class="results-count">
            找到 <strong>{{ total }}</strong> 条相关消息
          </span>
          <span class="results-time">（关键词：{{ searchedKeyword }}）</span>
        </div>

        <!-- 空状态 -->
        <el-empty
          v-if="total === 0 && !loading"
          description="未找到相关消息，试试其他关键词"
          :image-size="120"
        />

        <!-- 结果列表 -->
        <div class="result-list" v-if="results.length > 0">
          <div
            class="result-card"
            v-for="item in results"
            :key="item.messageId"
            @click="goToConversation(item.conversationId)"
          >
            <div class="result-header">
              <el-tag
                :type="item.role === 'user' ? '' : 'success'"
                size="small"
                class="role-tag"
              >
                {{ item.role === 'user' ? '用户' : 'AI' }}
              </el-tag>
              <span class="result-title">{{ item.conversationTitle || '未命名对话' }}</span>
              <span class="result-score">
                相关度: {{ formatScore(item.relevanceScore) }}
              </span>
            </div>
            <div
              class="result-content"
              v-html="sanitizeHtml(item.highlightContent || item.content)"
            ></div>
            <div class="result-footer">
              <span class="result-time">
                <el-icon><Clock /></el-icon>
                {{ formatTime(item.createdAt) }}
              </span>
              <el-button type="primary" link size="small">
                查看对话 <el-icon><ArrowRight /></el-icon>
              </el-button>
            </div>
          </div>
        </div>

        <!-- 分页 -->
        <div class="pagination-wrapper" v-if="total > size">
          <el-pagination
            v-model:current-page="currentPage"
            :page-size="size"
            :total="total"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next"
            @current-change="handlePageChange"
            @size-change="handleSizeChange"
          />
        </div>
      </div>

      <!-- 初始状态：还没有搜索过 -->
      <div class="search-placeholder" v-if="!searched && !loading">
        <el-empty description="输入关键词开始搜索" :image-size="160">
          <template #image>
            <el-icon :size="80" color="#c0c4cc"><Search /></el-icon>
          </template>
        </el-empty>
        <div class="search-tips">
          <p class="tips-title">搜索技巧</p>
          <ul>
            <li>搜索你之前问过的任何问题</li>
            <li>支持中文关键词自动分词</li>
            <li>支持多词搜索，用空格分隔</li>
            <li>结果会高亮匹配的关键词</li>
          </ul>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search, Clock, ArrowRight } from '@element-plus/icons-vue'
import DOMPurify from 'dompurify'
import { searchMessages } from '@/api/search'

const router = useRouter()

const keyword = ref('')
const searchedKeyword = ref('')
const searched = ref(false)
const loading = ref(false)
const results = ref([])
const total = ref(0)
const currentPage = ref(1)
const size = ref(20)

// 300ms 防抖
let debounceTimer = null
watch(keyword, (val) => {
  if (debounceTimer) clearTimeout(debounceTimer)
  if (val && val.trim()) {
    debounceTimer = setTimeout(() => {
      currentPage.value = 1
      doSearch()
    }, 300)
  }
})

/**
 * 执行搜索
 */
async function doSearch() {
  const q = keyword.value.trim()
  if (!q) return

  loading.value = true
  searched.value = true
  searchedKeyword.value = q

  try {
    const res = await searchMessages(q, currentPage.value, size.value)
    results.value = res.data.list || []
    total.value = res.data.total || 0
    if (total.value === 0 && results.value.length === 0) {
      // 无结果
    }
  } catch (err) {
    ElMessage.error('搜索失败，请稍后重试')
    results.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  currentPage.value = 1
  doSearch()
}

function handleClear() {
  searched.value = false
  results.value = []
  total.value = 0
}

function handlePageChange(page) {
  currentPage.value = page
  doSearch()
  // 滚动到顶部
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function handleSizeChange(newSize) {
  size.value = newSize
  currentPage.value = 1
  doSearch()
}

/**
 * 跳转到对应对话
 */
function goToConversation(conversationId) {
  router.push({ path: '/chat', query: { conversationId } })
}

/**
 * 净化 HTML（防止 XSS），同时保留 <mark> 标签
 */
function sanitizeHtml(html) {
  if (!html) return ''
  return DOMPurify.sanitize(html, {
    ALLOWED_TAGS: ['mark'],
  })
}

/**
 * 格式化相关性分数（保留两位小数）
 */
function formatScore(score) {
  if (score == null) return '0'
  return Number(score).toFixed(2)
}

/**
 * 格式化时间
 */
function formatTime(time) {
  if (!time) return ''
  const d = new Date(time)
  const now = new Date()
  const diff = now - d
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return Math.floor(diff / 60000) + ' 分钟前'
  if (diff < 86400000) return Math.floor(diff / 3600000) + ' 小时前'
  if (d.getFullYear() === now.getFullYear()) {
    return `${d.getMonth() + 1}月${d.getDate()}日 ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
  }
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}
</script>

<style scoped>
.search-page {
  min-height: 100vh;
  background: #faf8f5;
  padding: 24px;
}

.search-container {
  max-width: 900px;
  margin: 0 auto;
}

.search-header {
  text-align: center;
  margin-bottom: 32px;
}

.page-title {
  font-size: 28px;
  color: #303133;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-bottom: 8px;
}

.page-desc {
  color: #909399;
  font-size: 14px;
  margin-bottom: 24px;
}

.search-input-row {
  display: flex;
  gap: 12px;
  max-width: 700px;
  margin: 0 auto;
}

.search-input {
  flex: 1;
}

/* 结果区域 */
.results-header {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 16px;
  padding: 0 4px;
}

.results-count {
  font-size: 15px;
  color: #606266;
}

.results-count strong {
  color: #1677ff;
  font-size: 18px;
}

.results-time {
  font-size: 13px;
  color: #909399;
}

/* 结果卡片 */
.result-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.result-card {
  background: #fff;
  border-radius: 12px;
  padding: 20px;
  cursor: pointer;
  transition: all 0.2s ease;
  border: 1px solid #ebeef5;
}

.result-card:hover {
  border-color: #1677ff;
  box-shadow: 0 2px 12px rgba(22, 119, 255, 0.1);
  transform: translateY(-1px);
}

.result-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.role-tag {
  flex-shrink: 0;
}

.result-title {
  font-size: 15px;
  font-weight: 500;
  color: #303133;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.result-score {
  font-size: 12px;
  color: #909399;
  flex-shrink: 0;
}

.result-content {
  font-size: 14px;
  color: #606266;
  line-height: 1.7;
  margin-bottom: 12px;
  max-height: 120px;
  overflow: hidden;
}

.result-content :deep(mark) {
  background: #fff3cd;
  color: #856404;
  padding: 1px 4px;
  border-radius: 3px;
}

.result-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.result-time {
  font-size: 12px;
  color: #c0c4cc;
  display: flex;
  align-items: center;
  gap: 4px;
}

/* 分页 */
.pagination-wrapper {
  display: flex;
  justify-content: center;
  margin-top: 32px;
}

/* 搜索占位 & 提示 */
.search-placeholder {
  margin-top: 40px;
}

.search-tips {
  max-width: 400px;
  margin: 24px auto 0;
  background: #fff;
  border-radius: 12px;
  padding: 20px 28px;
  border: 1px solid #ebeef5;
}

.tips-title {
  font-size: 15px;
  font-weight: 500;
  color: #303133;
  margin-bottom: 12px;
}

.search-tips ul {
  list-style: none;
  padding: 0;
  margin: 0;
}

.search-tips li {
  font-size: 13px;
  color: #909399;
  padding: 4px 0;
  position: relative;
  padding-left: 16px;
}

.search-tips li::before {
  content: '•';
  position: absolute;
  left: 0;
  color: #1677ff;
}

@media (max-width: 768px) {
  .search-page {
    padding: 16px;
  }

  .search-input-row {
    flex-direction: column;
  }

  .page-title {
    font-size: 22px;
  }
}
</style>
