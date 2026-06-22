# 本地热加载 vs Docker 部署：代理差异与踩坑指南

> 创建日期：2026-06-21
> 问题现象：Agent 模式在本地热加载正常，Docker 部署后发送消息返回 **HTTP 405 Method Not Allowed**

---

## 1. 问题根因

### 现象

| 环境 | Agent 发送消息 | 结果 |
|------|--------------|------|
| 本地热加载（`npm run dev` + `mvn spring-boot:run`） | ✅ 正常 | SSE 流式返回 |
| Docker（`docker compose up -d --build`） | ❌ 405 Method Not Allowed | 请求被拒绝 |

### 根因分析

两种模式下，前端请求到达后端的**路径完全不同**：

```
┌─────────────────────────────────────────────────────────────────┐
│  本地热加载模式                                                   │
│                                                                 │
│  浏览器 → Vite Dev Server (port 4000)                           │
│            ├── /agent/chat/stream → proxy → localhost:8080 ✅   │
│            ├── /ai/chat/stream   → proxy → localhost:8080       │
│            ├── /user/**          → proxy → localhost:8080       │
│            └── /rag/**           → proxy → localhost:8080       │
│                                                                 │
│  Vite 的 proxy 是"前缀匹配"，/agent 自动代理到后端               │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│  Docker 模式                                                     │
│                                                                 │
│  浏览器 → Nginx (port 80)                                       │
│            ├── /user/**    → proxy_pass backend:8080  ✅        │
│            ├── /ai/**      → proxy_pass backend:8080  ✅        │
│            ├── /rag/**     → proxy_pass backend:8080  ✅        │
│            ├── /agent/**   → ❌ 没有 location 规则！             │
│            └── /           → 静态文件 (SPA fallback)             │
│                                                                 │
│  /agent 请求落入 location / → Nginx 用静态文件服务响应 POST      │
│  → 静态文件服务器不支持 POST → 返回 405 Method Not Allowed       │
└─────────────────────────────────────────────────────────────────┘
```

**核心区别**：

| 对比项 | 本地热加载 (Vite) | Docker (Nginx) |
|--------|------------------|----------------|
| 代理方式 | `server.proxy` 前缀匹配 | `location` 精确前缀声明 |
| 新路径处理 | **自动代理**到后端 | 必须**手动添加** `location` 块 |
| 未匹配路径 | Vite 不处理 → 后端返回 | 落入 `location /` → SPA 静态文件 |
| 超时配置 | 默认（无限制） | 每个 `location` 独立配置 `proxy_read_timeout` |
| SSE 缓冲 | 默认关闭 | 需要显式 `proxy_buffering off` |

---

## 2. 两种模式的完整架构对比

### 2.1 请求代理链路

```
本地热加载：
  浏览器 → Vite HMR (port 4000) → proxy → Spring Boot (port 8080)
                                            ↕
                                    MySQL (Docker:13306)
                                    Redis (Docker:6379)

Docker 全栈：
  浏览器 → Nginx (port 80) → proxy_pass → Spring Boot (container:8080)
                                            ↕
                                    MySQL (container:3306)
                                    Redis (container:6379)
```

### 2.2 配置文件对照

| 功能 | 本地热加载 | Docker |
|------|-----------|--------|
| 前端代理 | `web/frontend/vite.config.js` → `server.proxy` | `web/frontend/nginx.conf` → `location` 块 |
| 后端地址 | `http://localhost:8080` | `http://backend:8080`（Docker 内部 DNS） |
| 环境变量 | `application.yml` 本地值 或 `.env` | `docker-compose.yml` → `environment` 映射 |
| 数据库迁移 | 手动执行 SQL | `docker-entrypoint-initdb.d` 自动执行（仅首次） |
| 热重载 | Spring DevTools + Vite HMR | 需要 `docker compose up -d --build` 重建 |

---

## 3. 已知差异清单

### 3.1 代理路径必须同步

**规则：新增 API 路径前缀时，必须同时更新两个文件。**

| 文件 | 作用 | 格式 |
|------|------|------|
| `web/frontend/vite.config.js` | 本地开发代理 | `'/agent': { target: 'http://localhost:8080' }` |
| `web/frontend/nginx.conf` | Docker 生产代理 | `location /agent/ { proxy_pass http://backend:8080; ... }` |

当前已注册的路径前缀：

| 路径前缀 | vite.config.js | nginx.conf | 用途 |
|----------|---------------|------------|------|
| `/user` | ✅ | ✅ | 用户认证 |
| `/ai` | ✅ | ✅ | AI 对话 |
| `/rag` | ✅ | ✅ | RAG 知识库 |
| `/agent` | ✅ | ✅ | Agent 智能体 |

### 3.2 SSE 流式响应需要特殊配置

Agent 和 AI 对话使用 **SSE (Server-Sent Events)** 流式传输。Nginx 默认会**缓冲响应**再一次性发给客户端，导致：
- 前端收不到实时 token，体验变成"等很久然后一次性出结果"
- 长时间无数据传输可能触发超时断开

**必须在 nginx.conf 中配置**：

```nginx
location /agent/ {
    proxy_pass http://backend:8080;
    proxy_buffering off;          # 关闭缓冲，实时推送
    proxy_cache off;              # 关闭缓存
    chunked_transfer_encoding on; # 分块传输
    proxy_read_timeout 300s;      # Agent 有工具调用，需要更长超时
}
```

**超时对比**：

| 端点类型 | `proxy_read_timeout` | 原因 |
|---------|---------------------|------|
| 普通 API (`/user`, `/rag`) | 60s | 同步请求，快速返回 |
| AI 对话 (`/ai`) | 120s | LLM 流式生成，通常 30-60s |
| Agent 对话 (`/agent`) | 300s | 多轮工具调用（搜索、计算），可能 2-3 分钟 |

### 3.3 数据库迁移：首次 vs 增量

| 场景 | 行为 | 解决方案 |
|------|------|---------|
| Docker 首次启动（空数据卷） | `initdb.d` 按文件名顺序执行所有 SQL | 自动 ✅ |
| Docker 已有数据卷 | `initdb.d` **不会重复执行** | 手动执行新 SQL |
| 本地开发 | 手动执行迁移 SQL | `mysql -u root -p springboot_zyt < V4__xxx.sql` |

**检查数据卷状态**：

```bash
# 查看 MySQL 数据卷
docker volume ls | grep mysql

# 强制重新初始化（⚠️ 会丢失所有数据）
docker compose down -v
docker compose up -d --build
```

### 3.4 环境变量传递链路

```
本地热加载：
  application.yml  ←  直接读取文件中的值
  .env             ←  手动 source 或 IDE 加载

Docker：
  docker-compose.yml environment  ←  映射环境变量到容器
  .env                            ←  docker compose 自动读取
  application.yml ${VAR:default}  ←  Spring 从环境变量取值
```

**常见遗漏**：在 `application.yml` 中新增了 `${NEW_VAR:default}`，但忘记在 `docker-compose.yml` 的 `environment` 中添加映射 → Docker 中该变量始终取默认值。

### 3.5 文件路径差异

| 路径 | 本地 | Docker 容器 |
|------|------|------------|
| RAG 上传目录 | `./uploads/rag`（项目根目录） | `/data/uploads/rag`（Volume 挂载） |
| 日志文件 | 控制台输出 | `docker logs auth-backend` |
| 静态资源 | Vite dev server 内存 | `/usr/share/nginx/html` |

---

## 4. 新增 API 端点检查清单

每次新增 API 路径前缀（如 `/agent`、`/payment`、`/notification`），按以下清单检查：

```
□ 1. vite.config.js — 添加 proxy 规则
      server.proxy['/new-path'] = { target: 'http://localhost:8080', changeOrigin: true }

□ 2. nginx.conf — 添加 location 块
      location /new-path/ {
          proxy_pass http://backend:8080;
          proxy_set_header Host $host;
          proxy_set_header X-Real-IP $remote_addr;
          # 如果是 SSE 端点，还需要：
          proxy_buffering off;
          proxy_cache off;
          chunked_transfer_encoding on;
          proxy_read_timeout 300s;  # 按需调整
      }

□ 3. application.yml — springdoc paths-to-match 添加新路径
      springdoc.group-configs[0].paths-to-match:
        - /new-path/**

□ 4. WebConfig.java — 如果是公开端点，添加到 excludePathPatterns
      .excludePathPatterns("/new-path/public/**")

□ 5. docker-compose.yml — 如果有新环境变量，添加到 backend.environment

□ 6. .env — 添加新环境变量的示例值

□ 7. 双模式测试 — 分别在本地和 Docker 环境验证
      本地: npm run dev + mvn spring-boot:run
      Docker: docker compose up -d --build
```

---

## 5. 调试指南

### 5.1 快速定位"本地可以但 Docker 不行"

```bash
# 1. 检查 Nginx 是否有对应的 location 规则
docker exec auth-frontend cat /etc/nginx/conf.d/default.conf | grep "你的路径"

# 2. 检查后端容器是否正常运行
docker compose ps
docker logs auth-backend --tail 50

# 3. 从 Nginx 容器内部测试后端连通性
docker exec auth-frontend curl -s http://backend:8080/actuator/health

# 4. 检查环境变量是否传入
docker exec auth-backend env | grep AGENT
docker exec auth-backend env | grep TAVILY

# 5. 检查数据库表是否存在
docker exec -it auth-mysql mysql -u root -proot123 springboot_zyt -e "SHOW TABLES;"
```

### 5.2 Nginx 405 排查流程

```
收到 405 Method Not Allowed
    │
    ├── 是 POST/PUT/DELETE 请求？
    │   └── 是 → 大概率落入了静态文件 location /
    │            → 检查 nginx.conf 是否有对应路径的 location 块
    │
    └── 是 GET 请求？
        └── 罕见 → 可能是后端 @RequestMapping 限制了 HTTP 方法
```

### 5.3 SSE 流式响应不实时

```
症状：Agent/AI 对话在 Docker 中"卡住很久然后一次性出结果"
    │
    ├── 检查 nginx.conf 是否有 proxy_buffering off
    │   └── 没有 → 添加
    │
    ├── 检查 proxy_read_timeout 是否足够
    │   └── 太短 → 增大（Agent 建议 300s）
    │
    └── 检查后端是否返回正确的 Content-Type
        └── 应该是 text/event-stream
```

---

## 6. 配置速查表

### vite.config.js 代理格式

```javascript
proxy: {
  '/路径前缀': {
    target: 'http://localhost:8080',
    changeOrigin: true,
  },
}
```

### nginx.conf location 格式（普通 API）

```nginx
location /路径前缀/ {
    proxy_pass http://backend:8080;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_read_timeout 60s;
    proxy_connect_timeout 10s;
}
```

### nginx.conf location 格式（SSE 流式）

```nginx
location /路径前缀/ {
    proxy_pass http://backend:8080;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_read_timeout 300s;
    proxy_buffering off;
    proxy_cache off;
    chunked_transfer_encoding on;
}
```

---

## 7. 历史问题记录

| 日期 | 问题 | 根因 | 修复文件 |
|------|------|------|---------|
| 2026-06-21 | Agent 模式 Docker 下 405 | nginx.conf 缺少 `/agent/` location 块 | `nginx.conf`, `docker-compose.yml` |
| 2026-06-21 | Agent 数据库表不存在 | V4 迁移未挂载到 Docker MySQL | `docker-compose.yml` |
| 2026-06-21 | Agent 环境变量未生效 | docker-compose.yml 缺少 Agent 环境变量映射 | `docker-compose.yml` |
