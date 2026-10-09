package com.github.joansyu.aiagentplugin.service

import com.github.joansyu.aiagentplugin.client.BackendClient
import com.github.joansyu.aiagentplugin.client.ToolWebSocketClient
import com.github.joansyu.aiagentplugin.client.UnauthorizedException
import com.github.joansyu.aiagentplugin.settings.AiChatSettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 项目级连接生命周期服务：
 *  登录 → 绑定当前项目 → 建立 WebSocket（工具执行通道）。
 * 同时对外提供已登录的 [BackendClient] 与 token，供聊天 UI 调用。
 *
 * 所有网络操作在 pooled thread 执行，回调在 EDT。
 */
@com.intellij.openapi.components.Service(com.intellij.openapi.components.Service.Level.PROJECT)
class AiChatConnectionService(private val project: com.intellij.openapi.project.Project) : com.intellij.openapi.Disposable {

    companion object {
        fun getInstance(project: com.intellij.openapi.project.Project): AiChatConnectionService =
            project.getService(AiChatConnectionService::class.java)
    }

    val clientId: String = java.util.UUID.randomUUID().toString()
    private val generation = java.util.concurrent.atomic.AtomicLong()

    enum class State { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

    fun interface StateListener {
        fun onStateChanged(state: State, message: String?)
    }

    @Volatile private var state: State = State.DISCONNECTED
    @Volatile private var backendClient: BackendClient? = null
    @Volatile private var token: String = ""
    private val wsClient = ToolWebSocketClient(com.github.joansyu.aiagentplugin.tools.IdeToolExecutor(project)) {
        if (!project.isDisposed) setState(State.ERROR, "项目工具连接已断开，请重新连接")
    }
    private val listeners = CopyOnWriteArrayList<StateListener>()

    fun getState(): State = state
    fun getToken(): String = token
    fun getBackendClient(): BackendClient? = backendClient.takeIf { state == State.CONNECTED }
    fun isWebSocketConnected(): Boolean = wsClient.isConnected()

    fun addListener(listener: StateListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: StateListener) {
        listeners.remove(listener)
    }

    private fun setState(newState: State, message: String? = null) {
        state = newState
        thisLogger().info("AI Chat 连接状态: $newState $message")
        ApplicationManager.getApplication().invokeLater {
            listeners.forEach { it.onStateChanged(newState, message) }
        }
    }

    /** 异步连接：登录 → 绑定当前 IDEA 项目 → WS。回调在 EDT。 */
    fun connect(onSuccess: (() -> Unit)? = null, onError: ((String) -> Unit)? = null) {
        if (state == State.CONNECTING || state == State.CONNECTED) return
        val attempt = generation.incrementAndGet()
        setState(State.CONNECTING)

        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val s = AiChatSettings.getInstance()
                if (s.serverUrl.isBlank() || s.username.isBlank()) {
                    throw RuntimeException("请先在 Settings | Tools | AI Chat Agent 中配置后端地址与账号")
                }
                val client = BackendClient(s.serverUrl, clientId)
                val tk = client.login(s.username, s.password)
                // The project root is the only workspace authority. Do not read a user supplied
                // path here: every ToolWebSocketClient belongs to this Project service.
                val workspace = java.nio.file.Path.of(
                    project.basePath ?: error("当前项目没有根目录")
                ).toRealPath().toString()
                if (project.isDisposed || generation.get() != attempt) return@executeOnPooledThread
                wsClient.connect(s.serverUrl, tk, clientId, workspace)
                if (project.isDisposed || generation.get() != attempt) {
                    wsClient.disconnect()
                    return@executeOnPooledThread
                }

                backendClient = client
                token = tk
                setState(State.CONNECTED, "已连接 ${s.serverUrl}")
                ApplicationManager.getApplication().invokeLater { onSuccess?.invoke() }
            } catch (e: UnauthorizedException) {
                if (project.isDisposed || generation.get() != attempt) return@executeOnPooledThread
                setState(State.ERROR, e.message)
                ApplicationManager.getApplication().invokeLater { onError?.invoke(e.message ?: "认证失败") }
            } catch (e: Exception) {
                if (project.isDisposed || generation.get() != attempt) return@executeOnPooledThread
                thisLogger().warn("AI Chat 连接失败", e)
                setState(State.ERROR, e.message)
                ApplicationManager.getApplication().invokeLater { onError?.invoke(e.message ?: "连接失败") }
            }
        }
    }

    override fun dispose() {
        disconnect()
        listeners.clear()
    }

    fun disconnect() {
        generation.incrementAndGet()
        try {
            wsClient.disconnect()
        } catch (_: Exception) {}
        backendClient = null
        token = ""
        setState(State.DISCONNECTED, "已断开")
    }

    /** token 失效时强制重置到 ERROR，提示重新登录 */
    fun handleUnauthorized() {
        disconnect()
        setState(State.ERROR, "登录已过期，请重新连接")
    }
}
