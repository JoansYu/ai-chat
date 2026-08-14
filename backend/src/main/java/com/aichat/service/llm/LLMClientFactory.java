package com.aichat.service.llm;

import com.aichat.config.LLMProperties;
import org.springframework.stereotype.Component;

/**
 * 大模型客户端工厂：根据配置决定使用真实大模型还是内置模拟引擎
 */
@Component
public class LLMClientFactory {

    private final LLMProperties props;

    public LLMClientFactory(LLMProperties props) {
        this.props = props;
    }

    public LLMClient create() {
        OpenAIClient client = new OpenAIClient(props);
        if (client.isConfigured()) {
            return client;
        }
        return new MockLLMClient();
    }
}
