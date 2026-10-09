package com.github.joansyu.aiagentplugin.startup

import com.github.joansyu.aiagentplugin.service.AiChatConnectionService
import com.github.joansyu.aiagentplugin.settings.AiChatSettings
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/**
 * 启动 Activity：若用户在设置中开启了「自动连接」，则启动后自动登录并建立 WebSocket。
 */
class AiChatStartupActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        val settings = AiChatSettings.getInstance()
        if (settings.autoConnect &&
            settings.serverUrl.isNotBlank() &&
            settings.username.isNotBlank()) {
            thisLogger().info("AI Chat 自动连接已启用，开始连接...")
            AiChatConnectionService.connect()
        }
    }
}
