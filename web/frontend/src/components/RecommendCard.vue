<template>
  <div class="recommend-card" v-if="hasContent">
    <div class="recommend-header">
      <el-icon><MagicStick /></el-icon>
      <span>智能推荐</span>
    </div>

    <el-tabs v-model="activeTab" class="recommend-tabs">
      <!-- 相关对话 -->
      <el-tab-pane label="相关对话" name="conversations">
        <div class="tab-content" v-if="relatedConversations.length > 0">
          <div
            class="recommend-item"
            v-for="item in relatedConversations"
            :key="item.conversationId"
            @click="openConversation(item.conversationId)"
          >
            <el-icon class="item-icon"><ChatDotRound /></el-icon>
            <span class="item-text">{{ item.title || '未命名对话' }}</span>
          </div>
        </div>
        <el-empty
          v-else
          description="暂无推荐"
          :image-size="48"
          class="mini-empty"
        />
      </el-tab-pane>

      <!-- 建议问题 -->
      <el-tab-pane label="建议问题" name="questions">
        <div class="tab-content" v-if="suggestedQuestions.length > 0">
          <div
            class="recommend-item"
            v-for="(q, idx) in suggestedQuestions"
            :key="idx"
            @click="askQuestion(q)"
          >
            <el-icon class="item-icon"><QuestionFilled /></el-icon>
            <span class="item-text">{{ q }}</span>
          </div>
        </div>
        <el-empty
          v-else
          description="暂无推荐"
          :image-size="48"
          class="mini-empty"
        />
      </el-tab-pane>

      <!-- 热门知识 -->
      <el-tab-pane label="热门知识" name="knowledge">
        <div class="tab-content" v-if="popularKnowledge.length > 0">
          <div
            class="recommend-item"
            v-for="item in popularKnowledge"
            :key="item.id"
            @click="openKnowledge(item.id)"
          >
            <el-icon class="item-icon"><FolderOpened /></el-icon>
            <span class="item-text">{{ item.name }}</span>
            <el-tag size="small" type="info" class="doc-count">
              {{ item.documentCount }} 篇
            </el-tag>
          </div>
        </div>
        <el-empty
          v-else
          description="暂无推荐"
          :image-size="48"
          class="mini-empty"
        />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { MagicStick, ChatDotRound, QuestionFilled, FolderOpened } from '@element-plus/icons-vue'
import { getRecommendations } from '@/api/recommend'

const emit = defineEmits(['askQuestion'])
const router = useRouter()

const activeTab = ref('conversations')
const relatedConversations = ref([])
const suggestedQuestions = ref([])
const popularKnowledge = ref([])

const hasContent = computed(() =>
  relatedConversations.value.length > 0 ||
  suggestedQuestions.value.length > 0 ||
  popularKnowledge.value.length > 0
)

onMounted(async () => {
  try {
    const res = await getRecommendations()
    const data = res.data
    relatedConversations.value = data.relatedConversations || []
    suggestedQuestions.value = data.suggestedQuestions || []
    popularKnowledge.value = data.popularKnowledge || []
  } catch (e) {
    // 静默失败，推荐不是关键功能
  }
})

function openConversation(id) {
  router.push({ path: '/chat', query: { conversationId: id } })
}

function askQuestion(question) {
  emit('askQuestion', question)
}

function openKnowledge(id) {
  router.push('/knowledge')
}
</script>

<style scoped>
.recommend-card {
  background: #eeebe5;
  border-radius: 10px;
  padding: 12px;
  margin: 8px;
  border: 1px solid #ebeef5;
}

.recommend-header {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 500;
  color: #606266;
  margin-bottom: 8px;
  padding: 0 4px;
}

.recommend-tabs {
  --el-tabs-header-height: 32px;
}

.recommend-tabs :deep(.el-tabs__header) {
  margin-bottom: 8px;
}

.recommend-tabs :deep(.el-tabs__item) {
  font-size: 12px;
  padding: 0 12px;
  height: 32px;
  line-height: 32px;
}

.tab-content {
  max-height: 210px;
  overflow-y: auto;
}

.recommend-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s;
  margin-bottom: 2px;
}

.recommend-item:hover {
  background: #f5f7fa;
}

.item-icon {
  font-size: 14px;
  color: #909399;
  flex-shrink: 0;
}

.item-text {
  font-size: 13px;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}

.doc-count {
  flex-shrink: 0;
}

.mini-empty {
  padding: 16px 0;
}

.mini-empty :deep(.el-empty__description) {
  font-size: 12px;
  margin-top: 6px;
}
</style>
