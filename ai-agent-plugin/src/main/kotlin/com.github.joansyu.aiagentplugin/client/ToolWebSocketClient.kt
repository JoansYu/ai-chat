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
class ToolWebSocketClient(private val executor: IdeToolExecutor, private val onDisconnected: () -> Unit) {

    private val gson = Gson()
    private val httpClient = HttpClient.newHttpClient()
    @Volatile private var webSocket: WebSocket? = null
    @Volatile private var connected = false

    fun isConnected(): Boolean = connected

    /** Connect the tool channel for one IDEA project root, blocking until the handshake completes. */
    fun connect(serverUrl: String, token: String, clientId: String, projectRoot: String) {
        disconnect()
        fun encode(value: String) = java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8)
        val wsUrl = serverUrl
            .replace("http://", "ws://")
            .replace("https://", "wss://")
            .trimEnd('/')
            .plus("/ws/agent-tools?token=${encode(token)}&clientId=${encode(clientId)}&workspace=${encode(projectRoot)}")

        thisLogger().info("AI Chat WebSocket 正在连接当前项目")
        val future: CompletableFuture<WebSocket> = httpClient.newWebSocketBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .buildAsync(URI.create(wsUrl), WsListener())

        webSocket = future.get(15, java.util.concurrent.TimeUnit.SECONDS)
        connected = webSocket?.isInputClosed == false && webSocket?.isOutputClosed == false
        check(connected) { "项目工具连接已被服务端关闭" }
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
        com.intellij.openapi.application.ApplicationManager.getApplication().executeOnPooledThread {
            if (ws !== webSocket || !connected) return@executeOnPooledThread
            var result = ""
            var error = false
            try {
                thisLogger().info("📥 收到工具请求: requestId=$requestId tool=$tool ")
                result = executor.execute(tool, args)
                error = result.startsWith("错误") || result.startsWith("沙箱拦截") || result.contains("异常:") || result.startsWith("未知工具")
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
                synchronized(ws) { ws.sendText(gson.toJson(resp), true).get(10, java.util.concurrent.TimeUnit.SECONDS) }
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
            if (webSocket === this@ToolWebSocketClient.webSocket) {
                connected = false
                onDisconnected()
            }
            thisLogger().warn("AI Chat WebSocket 关闭: code=$statusCode reason=$reason")
            return null
        }

        override fun onError(webSocket: WebSocket, error: Throwable) {
            if (webSocket === this@ToolWebSocketClient.webSocket) {
                connected = false
                onDisconnected()
            }
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
