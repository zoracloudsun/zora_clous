import request from './index'

/**
 * 数据统计 API
 * Phase 4 对话数据分析仪表盘
 */
export const getOverview = () => request.get('/statistics/overview')
export const getMessageTrend = (days = 30) =>
  request.get('/statistics/message-trend', { params: { days } })
export const getActiveHours = () => request.get('/statistics/active-hours')
export const getConversationTrend = (days = 30) =>
  request.get('/statistics/conversation-trend', { params: { days } })
export const getMessageRatio = () => request.get('/statistics/message-ratio')
export const getKnowledgeBaseStats = () => request.get('/statistics/knowledge-stats')
export const getActionRanking = () => request.get('/statistics/action-ranking')
export const getWeeklyActivity = () => request.get('/statistics/weekly-activity')
