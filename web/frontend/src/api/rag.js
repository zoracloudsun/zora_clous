import request from './index'
import { getToken } from '@/utils/token'

// ==================== 知识库管理 ====================

/** 创建知识库 */
export function createKnowledgeBase(name, description) {
  return request.post('/rag/knowledge-bases', { name, description })
}

/** 获取知识库列表 */
export function listKnowledgeBases() {
  return request.get('/rag/knowledge-bases')
}

/** 获取知识库详情（含文档列表） */
export function getKnowledgeBase(id) {
  return request.get(`/rag/knowledge-bases/${id}`)
}

/** 更新知识库 */
export function updateKnowledgeBase(id, name, description) {
  return request.put(`/rag/knowledge-bases/${id}`, { name, description })
}

/** 删除知识库 */
export function deleteKnowledgeBase(id) {
  return request.delete(`/rag/knowledge-bases/${id}`)
}

// ==================== 文档管理 ====================

/** 上传文档（multipart/form-data） */
export function uploadDocument(kbId, file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post(`/rag/knowledge-bases/${kbId}/documents`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60000, // 上传可能需要较长时间
  })
}

/** 获取文档列表 */
export function listDocuments(kbId) {
  return request.get(`/rag/knowledge-bases/${kbId}/documents`)
}

/** 删除文档 */
export function deleteDocument(kbId, docId) {
  return request.delete(`/rag/knowledge-bases/${kbId}/documents/${docId}`)
}

// ==================== 检索测试 ====================

/** 在知识库中检索 */
export function queryKnowledgeBase(kbId, query, maxResults = 5, minScore = 0.3) {
  return request.post(`/rag/knowledge-bases/${kbId}/query`, { query, maxResults, minScore })
}

// ==================== 回收站 ====================

// ---- 知识库级别 ----

/** 获取知识库回收站列表（已删除的知识库） */
export function listDeletedKnowledgeBases() {
  return request.get('/rag/recycle-bin')
}

/** 恢复知识库及其所有文档 */
export function restoreKnowledgeBase(kbId) {
  return request.put(`/rag/recycle-bin/${kbId}/restore`)
}

/** 永久删除知识库（不可逆） */
export function permanentlyDeleteKnowledgeBase(kbId) {
  return request.delete(`/rag/recycle-bin/${kbId}`)
}

// ---- 文档级别（按知识库）----

/** 获取指定知识库的文档回收站列表 */
export function listDeletedDocuments(kbId) {
  return request.get(`/rag/knowledge-bases/${kbId}/recycle-bin`)
}

/** 从回收站恢复文档（重新嵌入向量） */
export function restoreDocument(kbId, docId) {
  return request.put(`/rag/knowledge-bases/${kbId}/recycle-bin/${docId}/restore`)
}

/** 永久删除文档（不可逆） */
export function permanentlyDeleteDocument(kbId, docId) {
  return request.delete(`/rag/knowledge-bases/${kbId}/recycle-bin/${docId}`)
}

/** 清空指定知识库的文档回收站 */
export function emptyDocumentRecycleBin(kbId) {
  return request.delete(`/rag/knowledge-bases/${kbId}/recycle-bin`)
}

// ==================== RAG 增强对话（SSE 流式）====================

/**
 * SSE 流式 RAG 对话
 * 与 ai.js 中的 streamChat 类似，但额外传递 knowledgeBaseId
 *
 * @param {string} message - 用户消息
 * @param {number|null} conversationId - 会话 ID
 * @param {number|null} knowledgeBaseId - 知识库 ID
 * @param {function} onToken - 收到新 token 时的回调
 * @param {function} onDone - 流结束时的回调
 * @param {function} onError - 出错时的回调
 * @returns {AbortController} 用于取消请求
 */
export function streamRagChat(message, conversationId, knowledgeBaseId, onToken, onDone, onError, provider, modelId) {
  const token = getToken()
  const controller = new AbortController()

  fetch('/ai/chat/rag-stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': token || '',
    },
    body: JSON.stringify({
      message,
      conversationId: conversationId || null,
      knowledgeBaseId: knowledgeBaseId || null,
      provider: provider || null,
      modelId: modelId || null,
    }),
    signal: controller.signal,
  })
    .then(async (response) => {
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
        const lines = buffer.split('\n')
        buffer = lines.pop() || ''

        for (const line of lines) {
          if (line.startsWith('data:')) {
            const raw = line.slice(5).trim()
            if (!raw) continue
            try {
              const decoded = JSON.parse(raw)
              if (typeof decoded === 'string') {
                onToken(decoded)
              }
            } catch {
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
