package com.github.joansyu.aiagentplugin.settings

import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * 设置页：Settings | Tools | AI Chat Agent
 */
class AiChatConfigurable : Configurable {

    private val serverUrlField = JBTextField()
    private val usernameField = JBTextField()
    private val passwordField = JBPasswordField()
    private val autoConnectBox = JBCheckBox("启动 IDE 时自动连接后端")

    override fun getDisplayName(): String = "AI Chat Agent"

    override fun createComponent(): JComponent {
        return com.intellij.util.ui.FormBuilder.createFormBuilder()
            .addLabeledComponent("后端地址", serverUrlField, 1, false)
            .addComponent(JBLabel("例如 http://localhost:8080（Spring Boot 后端）").apply {
                font = font.deriveFont(font.size2D - 2f)
            })
            .addLabeledComponent("用户名", usernameField, 1, false)
            .addLabeledComponent("密码", passwordField, 1, false)
            .addComponent(JBLabel("代码工作区自动绑定当前 IDEA 项目；多项目独立连接，无需填写路径").apply {
                font = font.deriveFont(font.size2D - 2f)
            })
            .addComponent(autoConnectBox)
            .addComponentFillVertically(JPanel(), 0)
            .panel
    }

    override fun isModified(): Boolean {
        val s = AiChatSettings.getInstance()
        return s.serverUrl != serverUrlField.text ||
                s.username != usernameField.text ||
                String(passwordField.password) != s.password ||
                s.autoConnect != autoConnectBox.isSelected
    }

    override fun apply() {
        val s = AiChatSettings.getInstance()
        s.serverUrl = serverUrlField.text.trim()
        s.username = usernameField.text.trim()
        s.password = String(passwordField.password)
        s.autoConnect = autoConnectBox.isSelected
    }

    override fun reset() {
        val s = AiChatSettings.getInstance()
        serverUrlField.text = s.serverUrl
        usernameField.text = s.username
        passwordField.text = s.password
        autoConnectBox.isSelected = s.autoConnect
    }
}
