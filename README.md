<div align="center">

# 🤖 AI Chat

> 支持多轮对话、Agent 智能体与本地代码操作的一体化 AI 应用

前后端分离的多轮 AI 对话平台，内置 **ReAct Agent 引擎** 与 **MCP 工具生态**，
通过 **SSE 流式输出** 提供打字机体验，支持在网页上授权并操作**用户本地代码文件**。

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.4-brightgreen)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-21-orange)](https://www.java.com/)
[![Vue](https://img.shields.io/badge/Vue-3.5-42b883)](https://vuejs.org/)
[![Vite](https://img.shields.io/badge/Vite-6.0-646cff)](https://vitejs.dev/)
[![Python](https://img.shields.io/badge/Python-3.10+-3776AB)](https://www.python.org/)
[![FastAPI](https://img.shields.io/badge/FastAPI-0.95+-009688)](https://fastapi.tiangolo.com/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

</div>

---

## 📖 目录

- [✨ 功能特性](#-功能特性)
- [🏗️ 系统架构](#️-系统架构)
- [🧩 技术栈](#-技术栈)
- [📁 目录结构](#-目录结构)
- [🚀 快速开始](#-快速开始)
- [⚙️ 配置说明](#️-配置说明)
  - [环境变量速查表](#环境变量速查表)
- [💬 对话与 Agent](#-对话与-agent)
- [📂 本地代码操作（方案 B）](#-本地代码操作方案-b)
- [📡 API 概览](#-api-概览)
- [🗄️ 数据库设计](#️-数据库设计)
- [🔒 安全与沙箱](#-安全与沙箱)
- [❓ FAQ 常见问题](#-faq-常见问题)
- [🤝 参与贡献](#-参与贡献)
- [📄 许可证](#-许可证)

---

## ✨ 功能特性

- 💬 **多轮对话**：会话管理、历史消息持久化（MySQL）、上下文智能截断
- ⚡ **SSE 流式输出**：逐 token 推送，前端打字机效果，支持随时停止生成
- 🧠 **Agent 智能体**：意图识别 → 任务规划 → 工具调用循环（ReAct 范式）
- 🔧 **MCP 工具生态**：接入 MCP filesystem Server，动态发现与调用工具
- 📁 **本地代码操作**：通过本地 Agent 客户端，网页授权后可对用户本机代码做**增删改查**
- 🔐 **认证鉴权**：Sa-Token 登录认证、会话级权限、单用户限流
- 🛡️ **沙箱隔离**：工作区路径白名单 + 扩展名白名单 + 文件大小限制
- 🎨 **Markdown 渲染**：前端基于 marked + DOMPurify 安全渲染
- 🗄️ **数据持久化**：用户/会话/消息/Agent 记忆全量落库，重启不丢失

---

## 🏗️ 系统架构

```
┌────────────────────────────────────────────────────────────────────────┐
│                              用户浏览器 (Vue 3)                          │
│        登录 / 会话管理 / 流式聊天窗 / 工作区授权设置 / Markdown 渲染        │
└──────────────────────────────┬─────────────────────────────────────────┘
                               │ HTTP + SSE（text/event-stream）
┌──────────────────────────────▼─────────────────────────────────────────┐
│                         Java 后端 (Spring Boot :8080)                    │
│  ┌────────────┐  ┌─────────────┐  ┌──────────────┐  ┌────────────────┐ │
│  │ 认证/会话   │  │ 对话服务     │  │ Agent 编排    │  │ 工具执行代理    │ │
│  │ Sa-Token   │  │ SSE 流式    │  │ 记忆/转发     │  │ WebSocket 桥接 │ │
│  └────────────┘  └─────────────┘  └──────────────┘  └───────┬────────┘ │
└──────────────────────────────┬─────────────────────────────────────────┘
                               │ HTTP 回调 (工具执行) / SSE (Agent 流)
┌──────────────────────────────▼─────────────────────────────────────────┐
│                   Python Agent 引擎 (FastAPI :8000)                     │
│   意图识别 ──► 任务规划 ──► Agent 循环 (ReAct) ──► 工具路由               │
│                                        │            │                  │
│                                   MCP 工具    本地/Java 回调工具           │
│                              (filesystem 等)   (search/edit/write...)    │
└──────────────────────────────┬─────────────────────────────────────────┘
                               │ WebSocket (ws://)
┌──────────────────────────────▼─────────────────────────────────────────┐
│                    local-agent-client (用户本机)                         │
│   登录认证 ──► 工作区授权 ──► 接收工具请求 ──► 本地文件增删改查（沙箱校验）   │
└────────────────────────────────────────────────────────────────────────┘
```

### 核心流程

**普通对话**：`前端 → Java(SSE) → LLM(OpenAI 兼容) → 逐 token 回推`

**Agent 任务**：
```
用户提问 → 意图识别 → 生成执行计划 → 注入系统提示
  → Agent 循环：思考 → 调用工具 → 观察结果 → 再思考
    ├─ 时间/天气 → 本地工具
    ├─ 文件读取 → MCP filesystem / Java 回调
    └─ 代码增删改查 → Java 回调 → WebSocket → 本地客户端执行
  → 流式汇总结果 → 保存对话记忆
```

---

## 🧩 技术栈

| 层 | 技术 |
|---|---|
| **后端** | Java 21 · Spring Boot 3.5.4 · MyBatis-Plus · MySQL · Sa-Token · WebSocket · SSE |
| **前端** | Vue 3 · Vite 6 · JavaScript · marked · DOMPurify · axios |
| **Agent 引擎** | Python 3.10+ · FastAPI · OpenAI SDK（异步）· MCP SDK |
| **本地客户端** | Spring Boot · WebSocket（JDK HttpClient + StandardWebSocketClient） |
| **模型接口** | OpenAI 兼容协议（Ollama / DeepSeek / 通义 / 华为 Ascend 等均可） |

---

## 📁 目录结构

```
ai-chat/
├── backend/                    # Java 后端（Spring Boot）
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/aichat/
│       │   ├── controller/     # REST / SSE 接口
│       │   ├── core/           # Agent 编排、模型客户端
│       │   ├── dto/            # 请求/响应模型
│       │   ├── entity/         # 数据库实体
│       │   ├── exception/      # 全局异常处理
│       │   ├── guard/          # 限流等防护
│       │   ├── mapper/         # MyBatis-Plus Mapper
│       │   ├── memory/         # Agent 多轮记忆
│       │   ├── model/          # 领域模型
│       │   ├── service/        # 业务服务（含工具执行器）
│       │   ├── websocket/      # 本地客户端桥接（WebSocket）
│       │   └── config/         # 配置类
│       └── resources/
│           ├── application.yml # 应用配置
│           └── db/schema.sql   # 建库建表脚本
│
├── frontend/                   # Vue 3 前端
│   └── src/
│       ├── api/                # 接口封装（SSE 解析）
│       ├── components/         # 登录/会话列表/聊天窗/设置
│       ├── utils/              # 认证、Markdown 渲染
│       └── App.vue             # 根组件
│
├── local-agent-client/         # 本地 Agent 客户端（运行在用户机器）
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/aichat/client/
│       │   ├── config/         # 登录/授权/连接配置
│       │   ├── service/        # 本地文件工具执行器（沙箱）
│       │   └── ws/             # WebSocket 客户端
│       └── resources/application.yml
│
└── README.md
```

> **Python Agent 引擎**（`agent-service`）为独立部署组件，负责意图识别、任务规划与 Agent 循环。

---

## 🚀 快速开始

### 环境要求

| 依赖 | 版本 |
|---|---|
| JDK | 21+ |
| Maven | 3.8+ |
| Node.js | 18+ |
| MySQL | 8.0+ |
| Python | 3.10+ |
| npx | 9+（MCP filesystem Server 依赖） |

### 1️⃣ 初始化数据库

执行 `backend/src/main/resources/db/schema.sql`，自动创建 `ai_chat` 库与全部数据表。

### 2️⃣ 启动 Java 后端（端口 8080）

```bash
cd backend
mvn spring-boot:run
```

### 3️⃣ 启动前端（端口 5173）

```bash
cd frontend
npm install
npm run dev
```

浏览器访问 http://localhost:5173

### 4️⃣ 启动 Python Agent 引擎（端口 8000）

```bash
cd agent-service
pip install -r requirements.txt
uvicorn main:app --host 0.0.0.0 --port 8000
```

### 5️⃣（可选）启动本地 Agent 客户端

用于在网页上操作**用户本机**的代码文件：

```bash
cd local-agent-client
mvn spring-boot:run
```

---

## ⚙️ 配置说明

### 数据库（必填）

编辑 `backend/src/main/resources/application.yml`，**建议使用环境变量**：

```yaml
spring:
  datasource:
    url: jdbc:mysql://${MYSQL_HOST}:${MYSQL_PORT}/ai_chat?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
    username: ${MYSQL_USER}
    password: ${MYSQL_PASSWORD}
```

### 大模型（必填）

```yaml
ai:
  chat:
    llm:
      enabled: true
      base-url: ${LLM_BASE_URL}      # 任意 OpenAI 兼容接口
      api-key: ${LLM_API_KEY}        # 如 Ollama 可填任意占位
      model: ${LLM_MODEL}            # 如 deepseek-v4-flash / qwen2.5:7b
```

### Python Agent 地址

```yaml
agent:
  python:
    url: ${AGENT_PYTHON_URL}         # 如 http://localhost:8000
```

### 本地客户端

编辑 `local-agent-client/src/main/resources/application.yml`：

```yaml
agent:
  server-url: http://localhost:8080  # Java 后端地址
  username: your-username
  password: your-password
  workspace-path: /path/to/your/code # 授权的工作区路径
```

### 环境变量速查表

| 变量 | 默认值 | 说明 | 必填 |
|---|---|---|---|
| `MYSQL_HOST` | `localhost` | MySQL 服务器地址 | ✅ |
| `MYSQL_PORT` | `3306` | MySQL 端口 | ✅ |
| `MYSQL_USER` | `root` | 数据库用户名 | ✅ |
| `MYSQL_PASSWORD` | - | 数据库密码 | ✅ |
| `LLM_BASE_URL` | - | OpenAI 兼容接口地址 | ✅ |
| `LLM_API_KEY` | - | 模型 API Key | ✅ |
| `LLM_MODEL` | `deepseek-v4-flash` | 模型名称 | ✅ |
| `AGENT_PYTHON_URL` | `http://localhost:8000` | Python Agent 引擎地址 | ✅ |
| `AGENT_CALLBACK_URL` | 自动探测本机 IP | Java 回调地址（供 Python 调用工具） | ❌ |
| `AGENT_MAX_STEPS` | `10` | Agent 最大工具调用步数 | ❌ |
| `AGENT_RATE_LIMIT_PER_MINUTE` | `20` | 单用户每分钟请求上限 | ❌ |
| `AGENT_MEMORY_MAX_MESSAGES` | `24` | 单会话保留最大消息数 | ❌ |

> **安全提示**：生产环境务必通过环境变量注入敏感信息（数据库密码、API Key），切勿硬编码进配置文件或提交到仓库。

---

## 💬 对话与 Agent

### SSE 流式协议

请求：

```json
POST /api/chat/stream
{ "sessionId": "可选，为空自动创建", "message": "你好" }
```

响应事件（`text/event-stream`）：

```json
{"type":"token","content":"你"}
{"type":"token","content":"好"}
{"type":"done","content":"你好！有什么可以帮你？"}
{"type":"error","message":"..."}
```

### Agent 事件流

Agent 任务额外推送：

```json
{"type":"intent","intent":"TASK_NEW"}
{"type":"plan","plan":"1. 先查xx\n2. 再算xx"}
{"type":"tool_result","name":"get_current_time","content":"2026-09-15 15:04:10 (UTC+8)"}
{"type":"done","content":"最终回复","sessionId":"sxxx"}
```

### 测试示例

| 场景 | 提问 |
|---|---|
| 工具调用 | `帮我查一下当前服务器的系统时间` |
| MCP 文件读取 | `看看 /tmp/agent_workspace 里有什么` |
| 本地代码操作 | `帮我在 src/test 下创建一个 Hello.java` |

---

## 📂 本地代码操作（方案 B）

### 工作原理

网页本身受浏览器沙箱限制无法访问本地文件系统，本方案通过**本地 Agent 客户端**桥接：

```
浏览器提问 ──► Java 后端 ──► Python Agent ──► 工具回调
                                      │
                                 WebSocket 转发
                                      ▼
                           local-agent-client（用户本机）
                              沙箱校验 ──► 文件增删改查
```

### 支持的本地工具

| 工具 | 说明 | 安全约束 |
|---|---|---|
| `read_file` | 读取文件（带行号） | 扩展名白名单 + ≤1MB |
| `list_directory` | 目录树 | 最大深度 5 |
| `search_code` / `glob_files` | 代码搜索 / 文件名匹配 | 结果条数限制 |
| `write_file` | 创建/覆盖文件 | 内容 ≤1MB |
| `edit_file` | 精确替换代码片段 | old_string 必须唯一匹配 |
| `delete_file` | 删除文件/目录 | 禁止删除工作区根目录 |
| `create_directory` | 创建目录 | — |
| `move_file` | 移动/重命名 | 源/目标均校验 |
| `get_file_info` | 文件元信息 | 只读 |

### 使用流程

1. 用户在前端设置页（🔧）输入并授权**本地目录路径**
2. 本地客户端启动时自动：登录 → 授权工作区 → 建立 WebSocket 连接
3. 对话中 Agent 的文件工具请求经 Java 后端转发至本地客户端执行
4. 未连接本地客户端时自动降级为服务器本地执行（开发模式）

---

## 📡 API 概览

### 认证

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/login` | 登录 |
| POST | `/api/register` | 注册 |

### 会话

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/sessions` | 创建会话 |
| GET | `/api/sessions` | 会话列表 |
| GET | `/api/sessions/{id}/messages` | 历史消息 |
| DELETE | `/api/sessions/{id}` | 删除会话 |

### 对话

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/chat/stream` | 普通对话（SSE 流式） |
| POST | `/api/agent/chat` | Agent 对话（SSE 流式） |

### 工作区 / 工具

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/workspace` | 查询授权状态 |
| POST | `/api/workspace/authorize` | 授权工作区路径 |
| POST | `/api/workspace/revoke` | 撤销授权 |
| POST | `/api/agent/tools/execute` | Agent 工具执行入口（内部回调） |

### Agent 引擎（Python）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/health` | 健康检查 |
| POST | `/api/v1/agent/stream` | Agent 流式任务（SSE） |
| POST | `/api/v1/agent/chat` | Agent 任务（非流式） |

---

## 🗄️ 数据库设计

> 建表脚本见 `backend/src/main/resources/db/schema.sql`，字符集 `utf8mb4`。

### 表关系

```
t_user ─┬─< t_chat_session ──< t_chat_message
        └─< t_user_workspace        （普通对话历史）
t_user ──< t_agent_message          （Agent 多轮记忆，按 userId + sessionId）
```

### 表结构

| 表 | 用途 | 关键字段 |
|---|---|---|
| `t_user` | 用户账号 | `id`, `username`, `password` |
| `t_chat_session` | 对话会话 | `id`(业务主键), `user_id`, `title`, `created_at` |
| `t_chat_message` | 普通对话消息 | `session_id`, `role`, `content`, `msg_timestamp` |
| `t_agent_message` | Agent 记忆消息 | `user_id`, `session_id`, `role`, `content`, `tool_calls`(JSON), `tool_call_id`, `seq` |
| `t_user_workspace` | 工作区授权 | `user_id`, `workspace_path`, `authorized` |

### 设计要点

- **会话双通道**：普通对话存 `t_chat_session/message`，Agent 任务存 `t_agent_message`，职责分离
- **Agent 记忆**：`tool_calls` 以 JSON 存储，`seq` 保证消息顺序；按 `user_id + session_id` 隔离
- **工作区授权**：`authorized=1` 表示当前生效授权，授权路径做 `../` 逃逸校验
- **上下文截断**：单会话超过 `AGENT_MEMORY_MAX_MESSAGES`（默认 24）时仅保留最近 N 条

---

## 🔒 安全与沙箱

| 防护层 | 实现 |
|---|---|
| **路径隔离** | 工作区前缀校验，`../` 逃逸直接拒绝 |
| **扩展名白名单** | 仅允许代码/配置文件（30+ 种） |
| **大小限制** | 单文件读写 ≤ 1MB |
| **登录认证** | Sa-Token Token 机制（header/cookie 双通道） |
| **限流** | 单用户每分钟请求限制 |
| **会话隔离** | 用户与会话绑定，防越权访问 |

---

## ❓ FAQ 常见问题

### 1. 启动后端报「未授权工作区」？

Agent 需要代码读取能力时，需先在网页右下角 🔧 设置中授权工作区路径；或确认 `local-agent-client` 已启动并授权。未授权时工具会返回「沙箱拦截：未设置授权工作区」。

### 2. 网页上无法操作本地文件？

浏览器受沙箱限制无法直接读本机文件，必须满足：
1. `local-agent-client` 已在本机启动（日志显示 `✅ WebSocket 已连接`）
2. `application.yml` 中的 `workspace-path` 指向目标目录
3. 后端 `agent.callback-url` 能正确回调（默认自动探测本机 IP）

### 3. 对话时前端"卡住"没有响应？

- 检查 Python Agent 引擎是否启动（`/health` 返回 UP）
- 检查大模型接口是否可达（`LLM_BASE_URL`）
- 前端强刷 `Ctrl+F5` 确保加载最新代码（本项目 SSE 使用原生 fetch 解析，无自动重连）

### 4. Agent 反复调用同一个工具？

旧版本流式循环存在上下文丢失 bug，已修复。请确认 Python 端 `agent_engine.py` 中 `assistant_msg` 与 tool 消息正确 `append` 回 `messages`。升级后建议**新开会话**验证。

### 5. 如何切换大模型？

修改 `LLM_BASE_URL` + `LLM_MODEL` 即可，支持任意 OpenAI 兼容接口（Ollama/DeepSeek/通义/华为 Ascend 等）。未配置时后端自动降级为内置模拟引擎。

### 6. MCP 工具无法调用？

- 确认 `npx` 已安装且网络可达（filesystem Server 依赖）
- 启动日志应有 `✅ [MCP] Filesystem Server 已挂载至: /tmp/agent_workspace`
- Agent 循环日志中 `工具数=17` 表示 3 个本地工具 + 14 个 MCP 工具已就绪

### 7. 会话重启后历史丢失？

检查 MySQL 连接是否正常。所有用户/会话/消息/Agent 记忆均已落库，重启不丢失。若 `t_chat_message.msg_timestamp` 为空，请执行最新版 `schema.sql`。

---

## 🤝 参与贡献

欢迎贡献代码、提交 Issue 或改进文档！流程：

1. Fork 本仓库
2. 创建特性分支：`git checkout -b feat/your-feature`
3. 提交改动：`git commit -m 'feat: add xxx'`
4. 推送分支：`git push origin feat/your-feature`
5. 提交 Pull Request

### 开发路线

- [ ] 代码知识图谱（Tree-sitter 符号索引）
- [ ] Plan-and-Revise 动态规划
- [ ] 编译/测试验证闭环（Reflexion）
- [ ] diff 确认式写操作
- [ ] RAG 代码检索

---

## 📄 许可证

[Apache License 2.0](LICENSE)

Copyright © 2026 AI Chat Contributors

---

<div align="center">
  Made with ❤️ by AI Chat Contributors
</div>
