# AI 智能对话前端

基于 Vue 3 + Vite + Element Plus 构建的 AI 对话应用前端。

## API 模块

| 文件 | 功能 |
|------|------|
| `src/api/index.js` | Axios 实例 + 拦截器（自动刷新 Token） |
| `src/api/user.js` | 用户认证 API |
| `src/api/ai.js` | AI 对话 API（含 SSE 流式对话 + 对话管理 + 批量操作） |
| `src/api/rag.js` | RAG 知识库 API（含 SSE RAG 对话 + 文档管理） |
| `src/api/agent.js` | Agent 智能体 SSE 流式对话 API |

### `api/ai.js` — 批量操作（新增）

| 函数 | HTTP | 说明 |
|------|------|------|
| `batchDeleteConversations(ids)` | POST `/ai/conversations/batch-delete` | 批量软删除 |
| `batchRestoreConversations(ids)` | POST `/ai/conversations/batch-restore` | 批量恢复 |
| `batchPermanentDeleteConversations(ids)` | POST `/ai/conversations/batch-permanent-delete` | 批量永久删除 |

## 目录结构

```
src/
├── api/                                   # API 封装
│   ├── index.js                           #   Axios 实例 + 请求/响应拦截器
│   ├── user.js                            #   用户认证 API（登录/注册/验证码/微信）
│   ├── ai.js                              #   AI 对话 API（SSE 流式 + 对话管理 + 批量操作）
│   ├── rag.js                             #   RAG 知识库 API（SSE RAG + 文档管理）
│   └── agent.js                           #   Agent 智能体 SSE 流式对话 API
├── router/
│   └── index.js                           #   路由配置 + 导航守卫（requiresAuth）
├── utils/
│   └── token.js                           #   localStorage 双 Token 存取
├── views/                                 # 页面组件（7 个）
│   ├── Home.vue                           #   主页（入口导航）
│   ├── Login.vue                          #   登录页
│   ├── Register.vue                       #   注册页
│   ├── ForgotPassword.vue                 #   忘记密码页
│   ├── Chat.vue                           #   AI 对话页（含 RAG/Agent 开关 + 批量操作）
│   ├── KnowledgeBase.vue                  #   知识库管理页（文档上传/检索/回收站）
│   └── Admin.vue                          #   管理员页
├── App.vue                                # 根组件
├── main.js                                # 入口文件（挂载 Vue + Element Plus + Router）
└── style.css                              # 全局样式
```

## 启动

```bash
npm install          # 安装依赖
npm run dev          # Vite 开发服务器（http://localhost:3000，HMR 热更新）
npm run build        # 生产构建 → dist/
```

API 请求通过 Vite proxy 转发至 `http://localhost:8080`（配置在 `vite.config.js`）。

## 技术栈

- **Vue 3** Composition API（`<script setup>`）
- **Vite** 开发服务器 + HMR
- **Element Plus** UI 组件库
- **marked** + **highlight.js** + **DOMPurify** Markdown 渲染
- **KaTeX** 数学公式渲染
