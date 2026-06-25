import request from './index'

/**
 * 智能推荐 API
 * Phase 4 智能推荐
 */
export const getRecommendations = () => request.get('/recommend/suggestions')
