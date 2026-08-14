# AI Chat - 多轮对话 Web 应用

前后端分离的多轮 AI 对话应用。

- **后端**：Java 21 + Spring Boot 3.5（SSE 流式输出，OpenAI 兼容接口，内置模拟引擎兜底）
- **前端**：Vue 3 + Vite + JavaScript（多会话管理、流式打字效果、Markdown 渲染）

## 项目结构

```
ai-chat/
├── backend/    # Spring Boot 后端
│   └── src/main/java/com/aichat/
│       ├── config/       # 跨域、LLM 配置
│       ├── controller/   # 对话、会话接口
│       ├── dto/          # 请求模型
│       ├── exception/    # 全局异常
│       ├── model/        # 消息、会话实体
│       └── service/      # 对话服务 + LLM 客户端（OpenAI/Mock）
└── frontend/   # Vue 3 前端
    └── src/
        ├── api/          # 接口封装（含 SSE 解析）
        ├── components/   # 会话列表、聊天窗口
        ├── utils/        # Markdown 渲染
        └── styles/       # 全局样式
```

## 快速启动

### 1. 启动后端（端口 8080）

```bash
cd backend
mvn spring-boot:run
```

### 2. 启动前端（端口 5173）

```bash
cd frontend
npm install
npm run dev
```

浏览器访问 http://localhost:5173

## 接口说明

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/sessions` | 创建会话 |
| GET | `/api/sessions` | 会话列表 |
| GET | `/api/sessions/{id}/messages` | 会话历史消息 |
| DELETE | `/api/sessions/{id}` | 删除会话 |
| POST | `/api/chat/stream` | 流式对话（SSE） |

流式对话请求体：

```json
{
  "sessionId": "可选，为空自动创建",
  "message": "你好"
}
```

SSE 事件数据格式：`{"type":"token","content":"增量"}` / `{"type":"done","content":"完整回复"}` / `{"type":"error","message":"..."}`

## 接入大模型

### 方式一：本地 Ollama（当前已配置）

编辑 `backend/src/main/resources/application.yml`：

```yaml
ai:
  chat:
    llm:
      enabled: true
      base-url: http://localhost:11434/v1   # Ollama OpenAI 兼容接口
      api-key: ollama                       # 任意占位值
      model: qwen1.5:1.8b                   # 模型名，见 ollama list
```

本机 Ollama 已安装模型：
- `qwen3.6:latest`（36B，约 23GB，本机内存不足无法加载）
- `qwen1.5:1.8b`（1.8B，轻量，当前使用）

### 方式二：云端 OpenAI 兼容接口

```yaml
ai:
  chat:
    llm:
      enabled: true
      base-url: https://api.openai.com/v1   # 或 DeepSeek/通义等兼容服务
      api-key: sk-xxxxxxx
      model: gpt-4o-mini
```

未配置（`enabled: false`）时后端自动使用内置模拟引擎，同样支持流式输出。
