package com.github.joansyu.aiagentplugin.ui

import com.github.joansyu.aiagentplugin.client.BackendClient
import com.github.joansyu.aiagentplugin.client.UnauthorizedException
import com.github.joansyu.aiagentplugin.service.AiChatConnectionService
import com.google.gson.JsonObject
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GridBagLayout
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JEditorPane
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.ScrollPaneConstants
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.border.EmptyBorder
import javax.swing.text.html.HTMLEditorKit

/**
 * 聊天主面板：专业级 UI 设计
 *
 * 布局：
 *  ┌──────────────────────────────────────┐
 *  │ TOP BAR: [icon] AI Chat  [session▾] [+] [↻] [● 已连接] │
 *  ├──────────────────────────────────────┤
 *  │                                      │
 *  │         MESSAGES AREA                 │
 *  │  ┌────────────┐                       │
 *  │  │ USER       │  right aligned        │
 *  │  └────────────┘                       │
 *  │  ┌────────────┐                       │
 *  │  │ ASSISTANT  │  left aligned         │
 *  │  └────────────┘                       │
 *  │                                      │
 *  ├──────────────────────────────────────┤
 *  │ INPUT: [textarea ...........] [Send]  │
 *  │        Ctrl+Enter to send            │
 *  └──────────────────────────────────────┘
 */
class ChatPanel(private val project: Project) : JPanel(BorderLayout()), com.intellij.openapi.Disposable {

    private val connection = AiChatConnectionService.getInstance(project)

    private data class SessionInfo(val id: String, val title: String) {
        override fun toString(): String = title
    }

    // ---- top bar components ----
    private val sessionBox = ComboBox<SessionInfo>().apply {
        prototypeDisplayValue = SessionInfo("", "新会话                    ")
        isEditable = false
    }
    private val statusDot = StatusDot()
    private val statusText = JBLabel("未连接").apply {
        foreground = JBColor.GRAY
        font = font.deriveFont(font.size2D - 1f)
    }

    // ---- messages area ----
    private val messagesPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        background = JBColor(0xFAFAFA, 0x1E1E1E)
    }
    private val messagesScroll = JBScrollPane(messagesPanel).apply {
        verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
        horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        border = JBUI.Borders.empty()
    }
    private val emptyHint = JBLabel(
        "<html><center><b>AI Chat Agent</b><br><br>点击上方「连接」开始对话<br><font size='2' color='gray'>Settings | Tools | AI Chat Agent 配置后端</font></center></html>"
    ).apply {
        horizontalAlignment = SwingConstants.CENTER
        foreground = JBColor.GRAY
        font = font.deriveFont(font.size2D + 2f)
    }

    // ---- input area ----
    private val inputArea = JTextArea().apply {
        lineWrap = true
        wrapStyleWord = true
        font = font.deriveFont(font.size2D)
    }
    private val inputScroll = JBScrollPane(inputArea).apply {
        verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
        border = JBUI.Borders.empty(4, 8)
    }
    private val sendButton = JButton("发送").apply {
        isEnabled = false
        toolTipText = "发送消息 (Ctrl+Enter)"
    }
    private val stopButton = JButton("停止").apply {
        isEnabled = false
    }
    private val hintLabel = JBLabel("项目：${project.name} · Ctrl+Enter 发送").apply {
        foreground = JBColor.GRAY
        font = font.deriveFont(font.size2D - 2f)
        horizontalAlignment = SwingConstants.RIGHT
    }

    private val sending = AtomicBoolean(false)
    private val cancelled = AtomicBoolean(false)

    @Volatile private var currentAssistantBubble: MessageBubble? = null
    @Volatile private var currentAssistantText = StringBuilder()

    private val stateListener = AiChatConnectionService.StateListener { state, message ->
        if (!project.isDisposed) updateConnectionState(state, message)
    }

    override fun dispose() {
        cancelled.set(true)
        connection.removeListener(stateListener)
    }

    init {
        buildUi()
        wireEvents()
        connection.addListener(stateListener)
        updateConnectionState(connection.getState(), null)
    }

    // ===================== UI 构建 =====================

    private fun buildUi() {
        border = JBUI.Borders.empty()

        // ---- top bar ----
        val topBar = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(6, 10)
            background = JBColor(0xF5F5F5, 0x252526)
        }
        val leftPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
            isOpaque = false
            add(JBLabel("AI Chat").apply {
                font = font.deriveFont(java.awt.Font.BOLD, font.size2D + 1f)
            })
            add(Box.createHorizontalStrut(8))
            add(JBLabel("会话").apply { foreground = JBColor.GRAY })
            add(sessionBox)
            add(createIconButton("新建", AllIcons.General.Add) { createSession() })
            add(createIconButton("刷新", AllIcons.Actions.Refresh) { refreshSessions() })
        }
        val rightPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0)).apply {
            isOpaque = false
            add(createIconButton("连接", AllIcons.Actions.Execute) { connection.connect() })
            add(Box.createHorizontalStrut(4))
            add(statusDot)
            add(statusText)
        }
        topBar.add(leftPanel, BorderLayout.WEST)
        topBar.add(rightPanel, BorderLayout.EAST)
        add(topBar, BorderLayout.NORTH)

        // ---- messages ----
        showEmptyHint()
        add(messagesScroll, BorderLayout.CENTER)

        // ---- bottom input ----
        val inputPanel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(6, 8, 8, 8)
            background = JBColor(0xF5F5F5, 0x252526)
        }
        inputPanel.add(inputScroll, BorderLayout.CENTER)

        val buttonPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            border = EmptyBorder(0, 6, 0, 0)
            add(sendButton)
            add(Box.createVerticalStrut(4))
            add(stopButton)
        }
        inputPanel.add(buttonPanel, BorderLayout.EAST)
        inputPanel.add(hintLabel, BorderLayout.SOUTH)
        inputScroll.preferredSize = Dimension(0, 60)
        add(inputPanel, BorderLayout.SOUTH)
    }

    private fun createIconButton(text: String, icon: javax.swing.Icon?, action: () -> Unit): JButton {
        return JButton(text).apply {
            if (icon != null) this.icon = icon
            isOpaque = false
            border = JBUI.Borders.empty(2, 6)
            font = font.deriveFont(font.size2D - 1f)
            addActionListener { action() }
        }
    }

    private fun wireEvents() {
        sendButton.addActionListener { send() }
        stopButton.addActionListener { cancel() }
        inputArea.addKeyListener(object : java.awt.event.KeyAdapter() {
            override fun keyPressed(e: java.awt.event.KeyEvent) {
                if (e.keyCode == java.awt.event.KeyEvent.VK_ENTER &&
                    (e.isControlDown || e.isMetaDown)) {
                    e.consume()
                    send()
                }
            }
        })
        sessionBox.addActionListener {
            val s = sessionBox.selectedItem as? SessionInfo ?: return@addActionListener
            if (!sending.get()) loadHistory(s.id)
        }
    }

    // ===================== 状态管理 =====================

    private fun showEmptyHint() {
        messagesPanel.removeAll()
        val wrapper = JPanel(GridBagLayout()).apply {
            isOpaque = false
            border = JBUI.Borders.empty(40)
        }
        wrapper.add(emptyHint)
        messagesPanel.add(wrapper)
        messagesPanel.revalidate()
        messagesPanel.repaint()
    }

    private fun updateConnectionState(state: AiChatConnectionService.State, message: String?) {
        statusDot.setState(state)
        statusText.text = when (state) {
            AiChatConnectionService.State.CONNECTED -> {
                statusText.foreground = JBColor(0x2E7D32, 0x81C784)
                sendButton.isEnabled = !sending.get()
                refreshSessions()
                "已连接"
            }
            AiChatConnectionService.State.CONNECTING -> {
                statusText.foreground = JBColor.ORANGE
                sendButton.isEnabled = false
                "连接中..."
            }
            AiChatConnectionService.State.ERROR -> {
                statusText.foreground = JBColor.RED
                sendButton.isEnabled = false
                message ?: "连接错误"
            }
            AiChatConnectionService.State.DISCONNECTED -> {
                statusText.foreground = JBColor.GRAY
                sendButton.isEnabled = false
                "未连接"
            }
        }
    }

    // ===================== 会话管理 =====================

    private fun withBackend(action: (BackendClient, String) -> Unit) {
        val client = connection.getBackendClient() ?: run {
            showStatusError("请先连接")
            return
        }
        val tk = connection.getToken()
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                action(client, tk)
            } catch (e: UnauthorizedException) {
                connection.handleUnauthorized()
                SwingUtilities.invokeLater { showStatusError("登录已过期，请重新连接") }
            } catch (e: Exception) {
                thisLogger().warn("会话操作失败", e)
                SwingUtilities.invokeLater { showStatusError("操作失败: ${e.message}") }
            }
        }
    }

    /** 顶部状态显示错误：红字 + 红点，错误原因完整展示 */
    private fun showStatusError(msg: String) {
        statusText.text = msg
        statusText.foreground = JBColor.RED
        statusDot.setState(AiChatConnectionService.State.ERROR)
    }

    private fun refreshSessions() {
        if (sending.get()) return
        withBackend { client, token ->
            val data = client.listSessions(token)
            val sessions = ArrayList<SessionInfo>()
            when {
                data.isJsonArray -> data.asJsonArray.forEach { el ->
                    if (el.isJsonObject) sessions.add(parseSession(el.asJsonObject))
                }
                data.isJsonObject && data.asJsonObject.has("records") ->
                    data.asJsonObject.getAsJsonArray("records").forEach { el ->
                        if (el.isJsonObject) sessions.add(parseSession(el.asJsonObject))
                    }
            }
            SwingUtilities.invokeLater {
                val prevSelected = sessionBox.selectedItem as? SessionInfo
                sessionBox.removeAllItems()
                sessions.forEach { sessionBox.addItem(it) }
                if (prevSelected != null) {
                    sessions.firstOrNull { it.id == prevSelected.id }?.let {
                        sessionBox.selectedItem = it
                    }
                }
            }
        }
    }

    private fun parseSession(obj: JsonObject): SessionInfo {
        val id = obj.get("sessionId")?.asString
            ?: obj.get("id")?.asString
            ?: obj.get("session_id")?.asString
            ?: ""
        val title = obj.get("title")?.asString
            ?: obj.get("name")?.asString
            ?: (if (id.isNotEmpty()) id.take(20) else "新会话")
        return SessionInfo(id, title)
    }

    private fun createSession() {
        if (sending.get()) return
        withBackend { client, token ->
            val id = client.createSession(token)
            SwingUtilities.invokeLater {
                val info = SessionInfo(id, "新会话")
                sessionBox.addItem(info)
                sessionBox.selectedItem = info
                showEmptyHint()
            }
        }
    }

    private fun loadHistory(sessionId: String) {
        withBackend { client, token ->
            val data = client.getMessages(token, sessionId)
            SwingUtilities.invokeLater {
                messagesPanel.removeAll()
                val arr = if (data.isJsonArray) data.asJsonArray else null
                arr?.forEach { el ->
                    if (el.isJsonObject) {
                        val msg = el.asJsonObject
                        val role = msg.get("role")?.asString ?: "assistant"
                        val content = msg.get("content")?.asString ?: ""
                        if (content.isNotBlank()) addBubble(role, content)
                    }
                }
                if (messagesPanel.componentCount == 0) showEmptyHint()
                refreshMessages()
            }
        }
    }

    // ===================== 发送 / 流式 =====================

    private fun send() {
        val text = inputArea.text.trim()
        if (text.isEmpty() || sending.get()) return
        if (!connection.isWebSocketConnected()) {
            showStatusError("项目工具连接已断开，请重新连接")
            return
        }
        val client = connection.getBackendClient() ?: run {
            showStatusError("请先连接")
            return
        }
        val token = connection.getToken()
        val session = sessionBox.selectedItem as? SessionInfo

        // 如果当前显示空提示，先清掉
        if (messagesPanel.componentCount == 1 && messagesPanel.getComponent(0) is JPanel) {
            val w = messagesPanel.getComponent(0) as JPanel
            if (w.componentCount > 0 && w.getComponent(0) == emptyHint) {
                messagesPanel.removeAll()
            }
        }

        inputArea.text = ""
        sending.set(true)
        sessionBox.isEnabled = false
        cancelled.set(false)
        sendButton.isEnabled = false
        stopButton.isEnabled = true
        hintLabel.text = "正在生成..."

        addBubble("user", text)
        val assistant = addBubble("assistant", "")
        currentAssistantBubble = assistant
        currentAssistantText = StringBuilder()

        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                client.agentChat(token, session?.id, text, ::onEvent) { cancelled.get() }
            } catch (e: UnauthorizedException) {
                connection.handleUnauthorized()
                onEvent(JsonObject().apply {
                    addProperty("type", "error")
                    addProperty("message", "登录已过期，请重新连接")
                })
            } catch (e: Exception) {
                thisLogger().warn("Agent 对话异常", e)
                onEvent(JsonObject().apply {
                    addProperty("type", "error")
                    addProperty("message", e.message ?: "对话异常")
                })
            } finally {
                SwingUtilities.invokeLater {
                    sending.set(false)
                    sessionBox.isEnabled = true
                    sendButton.isEnabled = connection.getState() ==
                            AiChatConnectionService.State.CONNECTED
                    stopButton.isEnabled = false
                    hintLabel.text = "Ctrl+Enter 发送"
                }
            }
        }
    }

    private fun cancel() {
        cancelled.set(true)
        stopButton.isEnabled = false
        hintLabel.text = "已停止"
    }

    private fun onEvent(evt: JsonObject) {
        val type = evt.get("type")?.asString
        SwingUtilities.invokeLater {
            when (type) {
                "token" -> {
                    val content = evt.get("content")?.asString ?: ""
                    currentAssistantText.append(content)
                    currentAssistantBubble?.setContent(currentAssistantText.toString())
                    scrollToBottom()
                }
                "tool_call" -> {
                    val name = evt.get("name")?.asString ?: ""
                    val args = evt.get("arguments")?.asString ?: ""
                    addBubble("tool", "[工具调用] $name\n$args")
                    refreshMessages()
                }
                "tool_result" -> {
                    val name = evt.get("name")?.asString ?: ""
                    val content = evt.get("content")?.asString ?: ""
                    val display = if (content.length > 500) content.take(500) + "\n..." else content
                    addBubble("tool", "[工具结果] $name\n$display")
                    refreshMessages()
                }
                "done" -> {
                    val sessionId = evt.get("sessionId")?.asString
                    if (!sessionId.isNullOrBlank() && sessionBox.selectedItem == null) {
                        sessionBox.addItem(SessionInfo(sessionId, "当前项目会话"))
                    }
                    val content = evt.get("content")?.asString
                    if (content != null && content.isNotBlank()) {
                        currentAssistantText = StringBuilder(content)
                        currentAssistantBubble?.setContent(content)
                    }
                    currentAssistantBubble = null
                }
                "error" -> {
                    val msg = evt.get("message")?.asString ?: "未知错误"
                    addBubble("error", msg)
                    refreshMessages()
                    currentAssistantBubble = null
                }
            }
        }
    }

    // ===================== 消息气泡 =====================

    private fun addBubble(role: String, content: String): MessageBubble {
        val bubble = MessageBubble(role, content)
        val wrapper = JPanel(BorderLayout()).apply {
            isOpaque = false
            border = JBUI.Borders.empty(4, 10)
        }
        // 整行宽度显示，避免 JEditorPane 未显示时宽度计算异常导致文字竖排
        wrapper.add(bubble, BorderLayout.CENTER)
        messagesPanel.add(wrapper)
        refreshMessages()
        return bubble
    }

    private fun refreshMessages() {
        messagesPanel.revalidate()
        messagesPanel.repaint()
        scrollToBottom()
    }

    private fun scrollToBottom() {
        SwingUtilities.invokeLater {
            val bar = messagesScroll.verticalScrollBar
            bar.value = bar.maximum
        }
    }

    // ===================== 内部组件 =====================

    /** 连接状态圆点指示器 */
    private class StatusDot : JPanel() {
        private var dotColor: Color = JBColor.GRAY

        fun setState(state: AiChatConnectionService.State) {
            dotColor = when (state) {
                AiChatConnectionService.State.CONNECTED -> JBColor(0x2E7D32, 0x81C784)
                AiChatConnectionService.State.CONNECTING -> JBColor.ORANGE
                AiChatConnectionService.State.ERROR -> JBColor.RED
                AiChatConnectionService.State.DISCONNECTED -> JBColor.GRAY
            }
            repaint()
        }

        override fun getPreferredSize(): Dimension = Dimension(10, 10)

        override fun paintComponent(g: java.awt.Graphics) {
            super.paintComponent(g)
            g.color = dotColor
            g.fillOval(0, 0, 10, 10)
        }
    }

    /** 单条消息气泡：角色标签在上，内容占满整行宽度 */
    private class MessageBubble(role: String, initial: String) : JPanel(BorderLayout()) {
        private val pane = JEditorPane().apply {
            editorKit = HTMLEditorKit()
            isEditable = false
            contentType = "text/html"
            minimumSize = Dimension(200, 30)
        }
        private val roleLabelComp = JBLabel().apply {
            border = JBUI.Borders.empty(2, 10, 2, 10)
            font = font.deriveFont(font.size2D - 2f)
        }

        private val bgHex: String
        private val textHex: String
        private val roleLabel: String
        private val roleColor: String

        init {
            val dark = isDark()
            when (role) {
                "user" -> {
                    val bg = if (dark) Color(0x1A3A5C) else Color(0xE3F2FD)
                    bgHex = toHex(bg)
                    textHex = if (dark) "#B3D4F0" else "#0D47A1"
                    roleLabel = "USER"
                    roleColor = if (dark) "#5B9BD5" else "#1976D2"
                }
                "assistant" -> {
                    val bg = if (dark) Color(0x2D2D30) else Color(0xFFFFFF)
                    bgHex = toHex(bg)
                    textHex = if (dark) "#D4D4D4" else "#333333"
                    roleLabel = "ASSISTANT"
                    roleColor = if (dark) "#888888" else "#757575"
                }
                "tool" -> {
                    val bg = if (dark) Color(0x3D3320) else Color(0xFFF8E1)
                    bgHex = toHex(bg)
                    textHex = if (dark) "#D4B896" else "#795548"
                    roleLabel = "TOOL"
                    roleColor = if (dark) "#A08750" else "#9E7B3F"
                }
                "error" -> {
                    val bg = if (dark) Color(0x4A1A1A) else Color(0xFFEBEE)
                    bgHex = toHex(bg)
                    textHex = if (dark) "#EF9A9A" else "#C62828"
                    roleLabel = "ERROR"
                    roleColor = if (dark) "#EF5350" else "#D32F2F"
                }
                else -> {
                    bgHex = toHex(JBColor.PanelBackground)
                    textHex = if (dark) "#D4D4D4" else "#333333"
                    roleLabel = ""
                    roleColor = "#888888"
                }
            }
            isOpaque = false
            border = JBUI.Borders.empty(2, 4)
            if (roleLabel.isNotBlank()) {
                roleLabelComp.text =
                    "<html><font color='$roleColor'><b>$roleLabel</b></font></html>"
                add(roleLabelComp, BorderLayout.NORTH)
            }
            add(pane, BorderLayout.CENTER)
            setContent(initial)
        }

        fun setContent(markdown: String) {
            val body = if (markdown.isBlank()) {
                "<font color='#888888'><i>正在思考...</i></font>"
            } else if (roleLabel == "TOOL" || roleLabel == "ERROR") {
                MarkdownRenderer.escape(markdown).replace("\n", "<br>")
            } else {
                MarkdownRenderer.toHtml(markdown)
            }
            pane.text =
                "<html><body bgcolor='$bgHex' text='$textHex'><font color='$textHex'>$body</font></body></html>"
            SwingUtilities.invokeLater {
                revalidate()
                repaint()
            }
        }

        private fun isDark(): Boolean {
            return javax.swing.UIManager.getLookAndFeel()?.name?.lowercase()?.contains("dark") == true
        }

        private fun toHex(color: Color): String =
            "#%02x%02x%02x".format(color.red, color.green, color.blue)
    }
}
