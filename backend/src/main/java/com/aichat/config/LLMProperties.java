package com.aichat.config;

import com.fasterxml.jackson.annotation.JsonProperty;
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


    @JsonProperty(value = "frequency_penalty")
    private double frequencyPenalty = 1.2;

    @JsonProperty(value = "top_p")
    private double topP = 0.8;

    @JsonProperty(value = "enable_thinking")
    private boolean enableThinking = true;

    public boolean isEnableThinking() {
        return enableThinking;
    }

    public void setEnableThinking(boolean enableThinking) {
        this.enableThinking = enableThinking;
    }


    public double getFrequencyPenalty() {
        return frequencyPenalty;
    }

    public void setFrequencyPenalty(double frequencyPenalty) {
        this.frequencyPenalty = frequencyPenalty;
    }

    public double getTopP() {
        return topP;
    }

    public void setTopP(double topP) {
        this.topP = topP;
    }


    private String systemPrompt = "你是一个专业而优秀的AI助手，对于用户的问题，你如果不知道，就告诉用户你不知道，请用户提供详细信息，不可以胡乱编造答案。";

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

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }


}
