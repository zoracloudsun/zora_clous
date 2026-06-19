import { getToken } from '@/utils/token'

// ==================== AI Agent SSE 流式对话 API（Phase 3）====================

/**
 * Agent SSE 流式对话
 * <p>
 * 使用原生 fetch 实现 SSE 流式读取（Axios 不支持流式响应）。
 * 与普通对话 ({@link streamChat}) 的区别：
 * <ul>
 * <li>端点不同：/agent/chat/stream vs /ai/chat/stream</li>
 * <li>事件结构：Agent 返回结构化事件（thinking/tool_call/tool_result/token/done）</li>
 * <li>支持工具调用可视化：前端可渲染推理过程面板</li>
 * </ul>
 * </p>
 *
 * <h3>SSE 事件类型</h3>
 * <table>
 * <tr><td>thinking</td><td>Agent 思考过程（content: 思考描述）</td></tr>
 * <tr><td>tool_call</td><td>工具调用（tool: 工具名, args: 参数对象）</td></tr>
 * <tr><td>tool_result</td><td>工具执行结果（tool: 工具名, content: 结果文本）</td></tr>
 * <tr><td>token</td><td>最终回答的文本片段（content: 文本）</td></tr>
 * <tr><td>done</td><td>对话完成（conversationId: 对话 ID）</td></tr>
 * <tr><td>error</td><td>错误信息（message: 错误描述）</td></tr>
 * </table>
 *
 * @param {string} message - 用户消息
 * @param {number|null} conversationId - 会话 ID（新建为 null）
 * @param {function} onThinking - 收到思考事件时的回调 (content: string) => void
 * @param {function} onToolCall - 收到工具调用事件时的回调 (tool: string, args: object) => void
 * @param {function} onToolResult - 收到工具结果事件时的回调 (tool: string, content: string) => void
 * @param {function} onToken - 收到回答 token 时的回调 (content: string) => void
 * @param {function} onDone - 流结束时的回调 (conversationId: number) => void
 * @param {function} onError - 出错时的回调 (error: Error | string) => void
 * @returns {AbortController} 用于取消请求
 */
export function streamAgentChat(
  message,
  conversationId,
  onThinking,
  onToolCall,
  onToolResult,
  onToken,
  onDone,
  onError
) {
  const token = getToken()
  const controller = new AbortController()

  fetch('/agent/chat/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': token || '',
    },
    body: JSON.stringify({
      message,
      conversationId: conversationId || null,
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

            try {
              // 解析结构化 Agent 事件 JSON
              const event = JSON.parse(raw)
              dispatchEvent(event, onThinking, onToolCall, onToolResult, onToken, onDone, onError)
            } catch {
              // 兼容非 JSON 格式（作为纯文本 token 处理）
              onToken(raw)
            }
          }
        }
      }
      // 流正常结束但没有收到 done 事件
      onDone(null)
    })
    .catch((err) => {
      if (err.name === 'AbortError') return
      onError(err)
    })

  return controller
}

/**
 * 分发 SSE 事件到对应的回调
 * <p>
 * 根据事件的 type 字段路由到对应的回调函数。
 * </p>
 *
 * @param {object} event - 解析后的 JSON 事件对象
 * @param {...function} callbacks - 各类型回调
 */
function dispatchEvent(event, onThinking, onToolCall, onToolResult, onToken, onDone, onError) {
  switch (event.type) {
    case 'thinking':
      if (onThinking) onThinking(event.content || '')
      break

    case 'tool_call':
      if (onToolCall) onToolCall(event.tool || 'unknown', event.args || {})
      break

    case 'tool_result':
      if (onToolResult) onToolResult(event.tool || 'unknown', event.content || '')
      break

    case 'token':
      if (onToken) onToken(event.content || '')
      break

    case 'done':
      if (onDone) onDone(event.conversationId || null)
      break

    case 'error':
      if (onError) onError(event.message || '未知错误')
      break

    default:
      // 未知事件类型 → 作为 token 处理（向后兼容）
      if (onToken && event.content) onToken(event.content)
  }
}
