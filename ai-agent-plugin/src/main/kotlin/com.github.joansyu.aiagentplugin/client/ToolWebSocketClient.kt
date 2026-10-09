package com.github.joansyu.aiagentplugin.client

import com.github.joansyu.aiagentplugin.tools.IdeToolExecutor
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.diagnostic.thisLogger
import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

/**
 * IDE 侧 WebSocket 工具客户端：替代原 local-agent-client 的 ToolWebSocketClient。
 *
 * 连接 ws://<server>/ws/agent-tools?token=<token>，与后端 AgentToolWebSocketHandler 通信：
 *   收到: {"requestId":"...","tool":"read_file","args":{...}}
 *   回传: {"requestId":"...","result":"...","error":false}
 *
 * 后台执行工具（避免阻塞 WS 接收），结果异步发回。
 */
class ToolWebSocketClient(private val executor: IdeToolExecutor = IdeToolExecutor()) {

    private val gson = Gson()
    private val httpClient = HttpClient.newHttpClient()
    @Volatile private var webSocket: WebSocket? = null
    @Volatile private var connected = false

    fun isConnected(): Boolean = connected

    /** 连接后端 WS，阻塞直到握手完成或失败 */
    fun connect(serverUrl: String, token: String) {
        disconnect()
        val wsUrl = serverUrl
            .replace("http://", "ws://")
            .replace("https://", "wss://")
            .trimEnd('/')
            .plus("/ws/agent-tools?token=$token")

        thisLogger().info("AI Chat WebSocket 正在连接: $wsUrl")
        val future: CompletableFuture<WebSocket> = httpClient.newWebSocketBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .buildAsync(URI.create(wsUrl), WsListener())

        webSocket = future.get(15, java.util.concurrent.TimeUnit.SECONDS)
        connected = true
        thisLogger().info("AI Chat WebSocket 已连接")
    }

    fun disconnect() {
        connected = false
        webSocket?.let {
            try { it.sendClose(WebSocket.NORMAL_CLOSURE, "bye") } catch (_: Exception) {}
        }
        webSocket = null
    }

    /** 在后台线程执行工具，避免阻塞 WS 接收线程 */
    private fun executeAndReply(ws: WebSocket, requestId: String, tool: String, args: Map<String, Any?>) {
        CompletableFuture.runAsync {
            var result = ""
            var error = false
            try {
                thisLogger().info("📥 收到工具请求: requestId=$requestId tool=$tool args=$args")
                result = executor.execute(tool, args)
                thisLogger().info("📤 工具执行完成: requestId=$requestId tool=$tool")
            } catch (e: Exception) {
                result = e.message ?: "工具执行异常"
                error = true
                thisLogger().error("工具执行异常: tool=$tool", e)
            }
            val resp = JsonObject().apply {
                addProperty("requestId", requestId)
                addProperty("result", result)
                addProperty("error", error)
            }
            try {
                ws.sendText(gson.toJson(resp), true).get()
            } catch (e: Exception) {
                thisLogger().warn("回传工具结果失败: $requestId", e)
            }
        }
    }

    private inner class WsListener : WebSocket.Listener {
        private val buffer = StringBuilder()

        override fun onOpen(webSocket: WebSocket) {
            thisLogger().info("AI Chat WebSocket 握手成功")
            webSocket.request(1)
        }

        override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
            buffer.append(data)
            if (last) {
                val message = buffer.toString()
                buffer.setLength(0)
                handleMessage(webSocket, message)
            }
            webSocket.request(1)
            return null
        }

        override fun onClose(webSocket: WebSocket, statusCode: Int, reason: String): CompletionStage<*>? {
            connected = false
            thisLogger().warn("AI Chat WebSocket 关闭: code=$statusCode reason=$reason")
            return null
        }

        override fun onError(webSocket: WebSocket, error: Throwable) {
            connected = false
            thisLogger().warn("AI Chat WebSocket 错误", error)
        }

        private fun handleMessage(ws: WebSocket, payload: String) {
            try {
                val node = JsonParser.parseString(payload).asJsonObject
                val requestId = node.get("requestId")?.asString ?: return
                val tool = node.get("tool")?.asString ?: return
                val argsNode = node.get("args")
                @Suppress("UNCHECKED_CAST")
                val args: Map<String, Any?> = if (argsNode != null && argsNode.isJsonObject)
                    gson.fromJson(argsNode, Map::class.java) as Map<String, Any?> else emptyMap()
                executeAndReply(ws, requestId, tool, args)
            } catch (e: Exception) {
                thisLogger().warn("WS 消息解析失败: $payload", e)
            }
        }
    }
}
