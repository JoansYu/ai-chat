package com.aichat.core.model;

import com.aichat.config.LLMProperties;
import com.aichat.model.AgentMessage;
import com.aichat.model.ToolCall;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 专用模型客户端：非流式 + 支持 Function Calling。
 * 为什么不复用 OpenAIClient？OpenAIClient 只解析 SSE 文本增量、不支持 tool_calls；
 * Agent 循环需要结构化的 tool_calls，且需要一次性拿到完整响应，故新建此类。
 * 复用点：与 OpenAIClient 同样使用 LLMProperties 与 java.net.http，风格一致。
 */
@Component
public class AgentModelClient {

    private final LLMProperties props;

    private final HttpClient http;

    private final ObjectMapper om = new ObjectMapper();

    public AgentModelClient(LLMProperties props) {
        this.props = props;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
    }

    public boolean idConfigured() {
        return props.isEnabled() && props.getApiKey() != null
                && !props.getApiKey().isEmpty();
    }

    public AgentMessage chat(List<AgentMessage> messages, List<Map<String, Object>> tools)
            throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("model", props.getModel());
        body.put("stream", false);
        body.put("temperature", props.getTemperature());
        body.put("max_tokens", props.getMaxTokens());
        body.put("enable_thinking", props.isEnableThinking());
        body.put("tool_choice", "auto");
        body.put("messages", toApi(messages));
        if (!CollectionUtils.isEmpty(tools)) {
            body.put("tools", tools);
        }

        HttpRequest rep = HttpRequest.newBuilder()
                .uri(URI.create(props.getBaseUrl() + "/chat/completions"))
                .header("Authorization", "Bearer" + props.getApiKey())
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(om.writeValueAsString(body)))
                .build();

        HttpResponse<String> resp = http.send(rep, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new RuntimeException("大模型带敖勇失败 HTTP " + resp.statusCode());
        }
        return fromApi(om.readTree(resp.body()));

    }

    private AgentMessage fromApi(JsonNode root){
        JsonNode msg = root.path("choices").path(0).path("message");
        String role = msg.path("role").asText("assistant");
        String content = msg.get("content").asText("");
        List<ToolCall> tcs = new ArrayList<>();
        JsonNode calls = msg.path("tool_calls");
        if (calls.isArray()) {
            for (JsonNode c: calls) {
                tcs.add(new ToolCall(
                        c.get("id").asText(),
                        c.path("function").path("name").asText(),
                        c.path("function").path("arguments").asText("{}")
                ));
            }
        }
        return new AgentMessage(role, content, tcs, null, null);

    }

    private List<Map<String, Object>> toApi(List<AgentMessage> messages){
        List<Map<String, Object>> out = new ArrayList<>();
        for (AgentMessage message: messages) {
            Map<String, Object> objectMap = new HashMap<>();
            objectMap.put("role", message.role());
            if (message.toolCallId() != null) {
                objectMap.put("tool_call_id", message.toolCallId());
            }
            if (!CollectionUtils.isEmpty(message.toolCalls())) {
                List<Map<String, Object>> tcs = new ArrayList<>();
                for (ToolCall tc: message.toolCalls()) {
                    Map<String, Object> tcMap = new HashMap<>();
                    tcMap.put("id", tc.id());
                    tcMap.put("type", "function");
                    Map<String, Object> fn = new HashMap<>();
                    fn.put("name", tc.name());
                    fn.put("arguments", tc.arguments());
                    tcMap.put("function", fn);
                    tcs.add(tcMap);
                }
                objectMap.put("tool_calls",tcs);
            }else {
                objectMap.put("content", message.content() == null?"":message.content());
            }
            out.add(objectMap);
        }
        return out;
    }
}
