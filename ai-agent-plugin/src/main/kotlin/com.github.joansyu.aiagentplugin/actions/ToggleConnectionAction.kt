package com.github.joansyu.aiagentplugin.actions

import com.github.joansyu.aiagentplugin.service.AiChatConnectionService
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages

/**
 * 连接/断开后端的 Action（Tools 菜单）。
 */
class ToggleConnectionAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val connection = AiChatConnectionService.getInstance(e.project ?: return)
        when (connection.getState()) {
            AiChatConnectionService.State.CONNECTED -> connection.disconnect()
            AiChatConnectionService.State.CONNECTING -> {
                Messages.showInfoMessage("正在连接中，请稍候...", "AI Chat")
            }
            else -> connection.connect(
                onSuccess = {},
                onError = { msg -> Messages.showErrorDialog(e.project, msg, "AI Chat 连接失败") }
            )
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
        val connected = e.project?.let { AiChatConnectionService.getInstance(it).getState() } == AiChatConnectionService.State.CONNECTED
        e.presentation.text = if (connected) "AI Chat: Disconnect" else "AI Chat: Connect"
    }
}
