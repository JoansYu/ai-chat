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
        when (AiChatConnectionService.getState()) {
            AiChatConnectionService.State.CONNECTED -> AiChatConnectionService.disconnect()
            AiChatConnectionService.State.CONNECTING -> {
                Messages.showInfoMessage("正在连接中，请稍候...", "AI Chat")
            }
            else -> AiChatConnectionService.connect(
                onSuccess = {},
                onError = { msg -> Messages.showErrorDialog(e.project, msg, "AI Chat 连接失败") }
            )
        }
    }

    override fun update(e: AnActionEvent) {
        val connected = AiChatConnectionService.getState() == AiChatConnectionService.State.CONNECTED
        e.presentation.text = if (connected) "AI Chat: Disconnect" else "AI Chat: Connect"
    }
}
