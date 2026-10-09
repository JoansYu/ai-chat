package com.github.joansyu.aiagentplugin.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * 插件持久化配置：后端地址、账号、工作区路径、运行时 token。
 * 对应原 local-agent-client 的 application.yml 配置项。
 */
@Service(Service.Level.APP)
@State(name = "com.github.joansyu.aiagentplugin.settings.AiChatSettings", storages = [Storage("ai-chat-agent.xml")])
class AiChatSettings : PersistentStateComponent<AiChatSettings> {

    var serverUrl: String = "http://localhost:8080"
    var username: String = ""
    var password: String = ""
    var workspacePath: String = ""
    var token: String = ""

    /** 是否自动连接（IDE 启动时） */
    var autoConnect: Boolean = false

    override fun getState(): AiChatSettings = this

    override fun loadState(state: AiChatSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        @JvmStatic
        fun getInstance(): AiChatSettings =
            ApplicationManager.getApplication().getService(AiChatSettings::class.java)
    }
}
