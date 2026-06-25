import request from './index'

/**
 * 全文搜索 API
 * Phase 4 全文搜索引擎
 */
export const searchMessages = (q, page = 1, size = 20) =>
  request.get('/search/messages', { params: { q, page, size } })
