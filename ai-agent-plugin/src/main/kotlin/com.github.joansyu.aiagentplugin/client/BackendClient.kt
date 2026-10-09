package com.github.joansyu.aiagentplugin.client

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.diagnostic.thisLogger
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * 后端 HTTP 客户端：封装登录、会话管理（同步接口）。
 * SSE 流式对话见 [agentChat] —— 直接在该方法内解析 text/event-stream。
 *
 * 后端约定：
 *  - 登录 POST /api/login  -> {"code":200,"data":{"tokenName":"satoken","tokenValue":"xxx"}}
 *  - 鉴权 header：satoken: <tokenValue>
 *  - 会话 GET/POST/DELETE /api/sessions
 *  - Agent 流式 POST /api/agent/chat (text/event-stream)
 */
class BackendClient(private val serverUrl: String, private val clientId: String) {

    private val gson: Gson = Gson()
    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    /** 登录，返回 tokenValue */
    fun login(username: String, password: String): String {
        val body = gson.toJson(mapOf("username" to username, "password" to password))
        val resp = send("/api/login", "POST", null, body)
        val node = JsonParser.parseString(resp).asJsonObject
        val data = node.getAsJsonObject("data")
        val token = data?.get("tokenValue")?.asString ?: ""
        if (token.isEmpty()) {
            throw RuntimeException("登录失败，响应: $resp")
        }
        return token
    }

    /** 会话列表，返回原始 JsonElement（结构由后端决定） */
    fun listSessions(token: String): JsonElement {
        val resp = send("/api/sessions", "GET", token, null)
        return unwrapData(resp)
    }

    /** 创建会话，返回 sessionId（后端字段名为 id） */
    fun createSession(token: String): String {
        val resp = send("/api/sessions", "POST", token, gson.toJson(mapOf<String, Any>()))
        val data = unwrapData(resp)
        if (data.isJsonObject) {
            val obj = data.asJsonObject
            obj.get("sessionId")?.asString?.let { return it }
            obj.get("id")?.asString?.let { return it }
        }
        throw RuntimeException("创建会话失败，后端返回: $resp")
    }

    /** 删除会话 */
    fun deleteSession(token: String, sessionId: String) {
        send("/api/sessions/$sessionId", "DELETE", token, null)
    }

    /** 历史消息 */
    fun getMessages(token: String, sessionId: String): JsonElement {
        val resp = send("/api/sessions/$sessionId/messages", "GET", token, null)
        return unwrapData(resp)
    }

    /**
     * Agent 流式对话（SSE）。逐事件回调，阻塞直到流结束或中断。
     * 调用方应在后台线程调用。
     *
     * @param onEvent 收到每个 JSON 事件对象（type: token/tool_call/tool_result/done/error）
     * @param isCancelled 由调用方控制，返回 true 时中断读取
     */
    fun agentChat(
        token: String, sessionId: String?, message: String,
        onEvent: (JsonObject) -> Unit,
        isCancelled: () -> Boolean
    ) {
        val reqBody = gson.toJson(
            mapOf("sessionId" to (sessionId ?: ""), "message" to message, "clientId" to clientId)
        )
        val req = HttpRequest.newBuilder()
            .uri(URI.create(serverUrl + "/api/agent/chat"))
            .timeout(Duration.ofMinutes(5))
            .header("Content-Type", "application/json")
            .header("satoken", token)
            .header("Accept", "text/event-stream")
            .POST(HttpRequest.BodyPublishers.ofString(reqBody))
            .build()

        val resp = http.send(req, HttpResponse.BodyHandlers.ofInputStream())
        thisLogger().info("Agent SSE 请求: sessionId=$sessionId, HTTP=${resp.statusCode()}")
        if (resp.statusCode() == 401) {
            throw UnauthorizedException("登录已过期，请重新登录")
        }
        if (resp.statusCode() != 200) {
            val err = resp.body().bufferedReader().use { it.readText() }
            throw RuntimeException("Agent 请求失败 (HTTP ${resp.statusCode()}): $err")
        }

        // 解析 SSE：每个事件以 "data: " 开头，事件之间用空行分隔
        resp.body().bufferedReader().use { reader ->
            val buffer = StringBuilder()
            while (true) {
                if (isCancelled()) {
                    thisLogger().info("Agent SSE 读取被用户中断")
                    break
                }
                val line = reader.readLine() ?: break
                if (line.isEmpty()) {
                    if (buffer.isNotEmpty()) {
                        dispatchEvent(buffer.toString(), onEvent)
                        buffer.setLength(0)
                    }
                    continue
                }
                if (line.startsWith("data:")) {
                    buffer.append(line.substring(5).trim())
                }
            }
            if (buffer.isNotEmpty()) {
                dispatchEvent(buffer.toString(), onEvent)
            }
        }
    }

    private fun dispatchEvent(data: String, onEvent: (JsonObject) -> Unit) {
        if (data.isEmpty() || data == "[DONE]") return
        try {
            val evt = JsonParser.parseString(data).asJsonObject
            onEvent(evt)
        } catch (e: Exception) {
            thisLogger().warn("SSE 事件解析失败: $data", e)
        }
    }

    private fun send(path: String, method: String, token: String?, body: String?): String {
        val builder = HttpRequest.newBuilder()
            .uri(URI.create(serverUrl + path))
            .timeout(Duration.ofSeconds(15))
            .header("Content-Type", "application/json")
        if (token != null) builder.header("satoken", token)
        when (method) {
            "GET" -> builder.GET()
            "DELETE" -> builder.DELETE()
            "POST" -> builder.POST(HttpRequest.BodyPublishers.ofString(body ?: "{}"))
        }
        val resp = http.send(builder.build(), HttpResponse.BodyHandlers.ofString())
        if (resp.statusCode() == 401) {
            throw UnauthorizedException("登录已过期")
        }
        if (resp.statusCode() != 200) {
            throw RuntimeException("请求 $path 失败 (HTTP ${resp.statusCode()}): ${resp.body()}")
        }
        return resp.body()
    }

    /** 解包后端响应：优先取 {code, message, data} 的 data；对象/数组直接返回 */
    private fun unwrapData(resp: String): JsonElement {
        val node = JsonParser.parseString(resp)
        if (node.isJsonObject) {
            val obj = node.asJsonObject
            return if (obj.has("data")) obj.get("data") else obj
        }
        return node
    }
}

class UnauthorizedException(message: String) : RuntimeException(message)
