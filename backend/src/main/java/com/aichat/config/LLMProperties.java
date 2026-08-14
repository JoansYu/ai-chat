package com.aichat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 大模型接入配置
 */
@Component
@ConfigurationProperties(prefix = "ai.chat.llm")
public class LLMProperties {

    /** 是否启用真实大模型；为 false 时使用内置模拟引擎 */
    private boolean enabled = false;

    /** OpenAI 兼容接口地址，例如 https://api.openai.com/v1 */
    private String baseUrl = "https://api.openai.com/v1";

    /** API Key */
    private String apiKey = "";

    /** 模型名称 */
    private String model = "gpt-4o-mini";

    /** 采样温度 */
    private double temperature = 0.7;

    /** 最大生成 Token 数 */
    private int maxTokens = 2048;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }
}
