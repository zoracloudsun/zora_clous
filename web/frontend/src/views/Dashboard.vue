<template>
  <div class="dashboard-page">
    <div class="dashboard-container">
      <!-- 页面标题 -->
      <div class="page-header">
        <h2 class="page-title">
          <el-icon><DataAnalysis /></el-icon>
          数据仪表盘
        </h2>
        <p class="page-desc">查看你的 AI 对话使用统计和活跃分析</p>
      </div>

      <!-- 摘要卡片行 -->
      <div class="summary-cards">
        <div class="summary-card">
          <div class="card-icon card-icon-blue">
            <el-icon :size="24"><ChatDotRound /></el-icon>
          </div>
          <div class="card-info">
            <div class="card-value">{{ overview.totalConversations ?? '-' }}</div>
            <div class="card-label">对话总数</div>
          </div>
        </div>
        <div class="summary-card">
          <div class="card-icon card-icon-green">
            <el-icon :size="24"><Comment /></el-icon>
          </div>
          <div class="card-info">
            <div class="card-value">{{ overview.totalMessages ?? '-' }}</div>
            <div class="card-label">消息总数</div>
          </div>
        </div>
        <div class="summary-card">
          <div class="card-icon card-icon-orange">
            <el-icon :size="24"><Sunny /></el-icon>
          </div>
          <div class="card-info">
            <div class="card-value">{{ overview.activeDaysThisWeek ?? '-' }}</div>
            <div class="card-label">本周活跃天数</div>
          </div>
        </div>
        <div class="summary-card">
          <div class="card-icon card-icon-purple">
            <el-icon :size="24"><Cpu /></el-icon>
          </div>
          <div class="card-info">
            <div class="card-value">{{ overview.aiUsageRate != null ? overview.aiUsageRate + '%' : '-' }}</div>
            <div class="card-label">AI 使用率</div>
          </div>
        </div>
      </div>

      <!-- 图表区域 -->
      <div class="charts-grid">
        <!-- 消息趋势 -->
        <div class="chart-card">
          <LineChart
            title="消息趋势（最近 30 天）"
            :loading="trendLoading"
            :series="messageTrendSeries"
            :xLabels="messageTrendDates"
            :height="280"
          />
        </div>

        <!-- 对话创建趋势 -->
        <div class="chart-card">
          <LineChart
            title="新对话趋势（最近 30 天）"
            :loading="convTrendLoading"
            :series="convTrendSeries"
            :xLabels="convTrendDates"
            :height="280"
          />
        </div>

        <!-- 活跃时段 -->
        <div class="chart-card">
          <BarChart
            title="活跃时段分布"
            :loading="hoursLoading"
            :data="activeHoursData"
            :xLabels="activeHoursLabels"
            :height="280"
          />
        </div>

        <!-- 消息角色占比 -->
        <div class="chart-card">
          <PieChart
            title="消息角色占比"
            :loading="ratioLoading"
            :data="messageRatioData"
            :height="280"
          />
        </div>

        <!-- 功能使用排行 -->
        <div class="chart-card">
          <BarChart
            title="功能使用排行"
            :loading="rankingLoading"
            :data="actionRankingData"
            xKey="label"
            yKey="count"
            :xLabels="actionRankingLabels"
            :height="280"
          />
        </div>

        <!-- 周活跃度 -->
        <div class="chart-card">
          <LineChart
            title="最近 7 天活跃度"
            :loading="weeklyLoading"
            :series="weeklyActivitySeries"
            :xLabels="weeklyActivityDates"
            :height="280"
          />
        </div>
      </div>

      <!-- 知识库统计 -->
      <div class="kb-section" v-if="kbStats.knowledgeBaseCount > 0">
        <h3 class="section-title">
          <el-icon><FolderOpened /></el-icon>
          知识库概览
        </h3>
        <div class="summary-cards">
          <div class="summary-card">
            <div class="card-icon card-icon-blue">
              <el-icon :size="24"><Folder /></el-icon>
            </div>
            <div class="card-info">
              <div class="card-value">{{ kbStats.knowledgeBaseCount }}</div>
              <div class="card-label">知识库</div>
            </div>
          </div>
          <div class="summary-card">
            <div class="card-icon card-icon-green">
              <el-icon :size="24"><Document /></el-icon>
            </div>
            <div class="card-info">
              <div class="card-value">{{ kbStats.documentCount }}</div>
              <div class="card-label">文档</div>
            </div>
          </div>
          <div class="summary-card">
            <div class="card-icon card-icon-orange">
              <el-icon :size="24"><Grid /></el-icon>
            </div>
            <div class="card-info">
              <div class="card-value">{{ kbStats.chunkCount }}</div>
              <div class="card-label">文本块</div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import {
  DataAnalysis, ChatDotRound, Comment, Sunny, Cpu,
  FolderOpened, Folder, Document, Grid,
} from '@element-plus/icons-vue'
import LineChart from '@/components/charts/LineChart.vue'
import BarChart from '@/components/charts/BarChart.vue'
import PieChart from '@/components/charts/PieChart.vue'
import {
  getOverview, getMessageTrend, getActiveHours,
  getConversationTrend, getMessageRatio, getKnowledgeBaseStats,
  getActionRanking, getWeeklyActivity,
} from '@/api/statistics'

// 摘要数据
const overview = reactive({})

// 消息趋势
const trendLoading = ref(true)
const messageTrendDates = ref([])
const messageTrendSeries = ref([])

// 对话趋势
const convTrendLoading = ref(true)
const convTrendDates = ref([])
const convTrendSeries = ref([])

// 活跃时段
const hoursLoading = ref(true)
const activeHoursLabels = ref([])
const activeHoursData = ref([])

// 消息占比
const ratioLoading = ref(true)
const messageRatioData = ref([])

// 功能排行
const rankingLoading = ref(true)
const actionRankingLabels = ref([])
const actionRankingData = ref([])

// 周活跃度
const weeklyLoading = ref(true)
const weeklyActivityDates = ref([])
const weeklyActivitySeries = ref([])

// 知识库
const kbStats = reactive({ knowledgeBaseCount: 0, documentCount: 0, chunkCount: 0 })

onMounted(async () => {
  await Promise.allSettled([
    fetchOverview(),
    fetchMessageTrend(),
    fetchConvTrend(),
    fetchActiveHours(),
    fetchMessageRatio(),
    fetchActionRanking(),
    fetchWeeklyActivity(),
    fetchKbStats(),
  ])
})

async function fetchOverview() {
  try {
    const res = await getOverview()
    Object.assign(overview, res.data)
  } catch (e) { /* 静默失败 */ }
}

async function fetchMessageTrend() {
  try {
    const res = await getMessageTrend()
    messageTrendDates.value = res.data.dates || []
    messageTrendSeries.value = [
      { name: '用户消息', data: res.data.userCounts || [] },
      { name: 'AI 回复', data: res.data.aiCounts || [] },
    ]
  } catch (e) { /* */ }
  trendLoading.value = false
}

async function fetchConvTrend() {
  try {
    const res = await getConversationTrend()
    convTrendDates.value = res.data.dates || []
    convTrendSeries.value = [
      { name: '新对话数', data: res.data.counts || [] },
    ]
  } catch (e) { /* */ }
  convTrendLoading.value = false
}

async function fetchActiveHours() {
  try {
    const res = await getActiveHours()
    activeHoursLabels.value = (res.data.hours || []).map((h) => h + ':00')
    activeHoursData.value = (res.data.hours || []).map((h, i) => ({
      name: h + ':00',
      value: res.data.counts?.[i] || 0,
    }))
  } catch (e) { /* */ }
  hoursLoading.value = false
}

async function fetchMessageRatio() {
  try {
    const res = await getMessageRatio()
    messageRatioData.value = res.data.items || []
  } catch (e) { /* */ }
  ratioLoading.value = false
}

async function fetchActionRanking() {
  try {
    const res = await getActionRanking()
    const ranking = res.data.ranking || []
    actionRankingLabels.value = ranking.map((r) => r.label)
    actionRankingData.value = ranking.map((r) => ({
      label: r.label,
      name: r.label,
      count: r.count,
      value: r.count,
    }))
  } catch (e) { /* */ }
  rankingLoading.value = false
}

async function fetchWeeklyActivity() {
  try {
    const res = await getWeeklyActivity()
    weeklyActivityDates.value = res.data.dates || []
    weeklyActivitySeries.value = [
      { name: '操作次数', data: res.data.counts || [] },
    ]
  } catch (e) { /* */ }
  weeklyLoading.value = false
}

async function fetchKbStats() {
  try {
    const res = await getKnowledgeBaseStats()
    Object.assign(kbStats, res.data)
  } catch (e) { /* */ }
}
</script>

<style scoped>
.dashboard-page {
  min-height: 100vh;
  background: #faf8f5;
  padding: 24px;
}

.dashboard-container {
  max-width: 1100px;
  margin: 0 auto;
}

.page-header {
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
}

/* 摘要卡片 */
.summary-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 32px;
}

.summary-card {
  background: #fff;
  border-radius: 12px;
  padding: 20px;
  display: flex;
  align-items: center;
  gap: 16px;
  border: 1px solid #ebeef5;
  transition: box-shadow 0.2s;
}

.summary-card:hover {
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
}

.card-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  flex-shrink: 0;
}

.card-icon-blue { background: #1677ff; }
.card-icon-green { background: #10b981; }
.card-icon-orange { background: #f59e0b; }
.card-icon-purple { background: #8b5cf6; }

.card-value {
  font-size: 24px;
  font-weight: 600;
  color: #303133;
  line-height: 1.2;
}

.card-label {
  font-size: 13px;
  color: #909399;
  margin-top: 2px;
}

/* 图表网格 */
.charts-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
  margin-bottom: 32px;
}

.chart-card {
  background: #fff;
  border-radius: 12px;
  padding: 20px;
  border: 1px solid #ebeef5;
}

/* 知识库区域 */
.kb-section {
  background: #fff;
  border-radius: 12px;
  padding: 24px;
  border: 1px solid #ebeef5;
}

.section-title {
  font-size: 16px;
  color: #303133;
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
}

@media (max-width: 768px) {
  .summary-cards { grid-template-columns: repeat(2, 1fr); }
  .charts-grid { grid-template-columns: 1fr; }
  .dashboard-page { padding: 16px; }
}
</style>
