import request from './index'
import { getToken } from '@/utils/token'

// ==================== AI 对话 API ====================

/** 获取对话列表 */
export function getConversations() {
  return request.get('/ai/conversations')
}

/** 新建对话 */
export function createConversation(title) {
  return request.post('/ai/conversations', { title })
}

/** 获取对话消息 */
export function getConversationMessages(id) {
  return request.get(`/ai/conversations/${id}`)
}

/** 删除对话（移至回收站） */
export function deleteConversation(id) {
  return request.delete(`/ai/conversations/${id}`)
}

/** 获取回收站列表（30 天内已删除的对话） */
export function getDeletedConversations() {
  return request.get('/ai/conversations/trash')
}

/** 恢复已删除的对话 */
export function restoreConversation(id) {
  return request.post(`/ai/conversations/${id}/restore`)
}

/** 从回收站永久删除 */
export function permanentDeleteConversation(id) {
  return request.delete(`/ai/conversations/${id}/permanent`)
}

// ==================== 批量操作 ====================

/** 批量删除对话（移至回收站） */
export function batchDeleteConversations(ids) {
  return request.post('/ai/conversations/batch-delete', { ids })
}

/** 批量恢复已删除的对话 */
export function batchRestoreConversations(ids) {
  return request.post('/ai/conversations/batch-restore', { ids })
}

/** 批量永久删除对话 */
export function batchPermanentDeleteConversations(ids) {
  return request.post('/ai/conversations/batch-permanent-delete', { ids })
}

/**
 * SSE 流式对话
 * 使用原生 fetch 实现（Axios 不支持 SSE 流式读取），
 * 但通过现有拦截器链的 Authorization header 传递 JWT
 *
 * @param {string} message - 用户消息
 * @param {number|null} conversationId - 会话 ID（新建为 null）
 * @param {function} onToken - 收到新 token 时的回调
 * @param {function} onDone - 流结束时的回调
 * @param {function} onError - 出错时的回调
 * @returns {AbortController} 用于取消请求
 */
export function streamChat(message, conversationId, onToken, onDone, onError, provider, modelId) {
  const token = getToken()
  const controller = new AbortController()

  fetch('/ai/chat/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': token || '',
    },
    body: JSON.stringify({
      message,
      conversationId: conversationId || null,
      provider: provider || null,
      modelId: modelId || null,
    }),
    signal: controller.signal,
  })
    .then(async (response) => {
      // HTTP 401：Token 过期 → 由 Axios 拦截器自动刷新，此处中止流
      if (response.status === 401) {
        onError(new Error('Token 已过期，正在自动刷新…'))
        return
      }
      if (!response.ok) {
        const errorData = await response.json().catch(() => ({}))
        onError(new Error(errorData.msg || `请求失败 (${response.status})`))
        return
      }

      // 读取 SSE 流
      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''

      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })
        // SSE 格式：每行以 "data:" 开头，空行分隔事件
        const lines = buffer.split('\n')
        // 最后一个不完整的行保留在 buffer 中
        buffer = lines.pop() || ''

        for (const line of lines) {
          if (line.startsWith('data:')) {
            const raw = line.slice(5).trim()
            if (!raw) continue
            // 后端 JSON 编码 token（保护换行符安全传输）
            // 解码 JSON 字符串还原原始文本（含换行符）
            try {
              const decoded = JSON.parse(raw)
              if (typeof decoded === 'string') {
                onToken(decoded)
              }
            } catch {
              // 兼容非 JSON 格式（如 [DONE] 标记）
              onToken(raw)
            }
          }
        }
      }
      onDone()
    })
    .catch((err) => {
      if (err.name === 'AbortError') return
      onError(err)
    })

  return controller
}

// ==================== 模型管理（Phase 5.3） ====================

/** 获取可用 AI 模型列表 */
export function getModels() {
  return request.get('/ai/models')
}
