package com.aichat.service.llm;

import com.aichat.config.LLMProperties;
import com.aichat.model.ChatMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * OpenAI 兼容接口客户端（支持 /v1/chat/completions 流式输出）
 */
public class OpenAIClient implements LLMClient {

    private final LLMProperties props;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OpenAIClient(LLMProperties props) {
        this.props = props;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    @Override
    public boolean isConfigured() {
        return props.isEnabled()
                && props.getApiKey() != null
                && !props.getApiKey().isBlank();
    }

    @Override
    public String chat(List<ChatMessage> messages) throws Exception {
        StringBuilder sb = new StringBuilder();
        streamChat(messages, sb::append);
        return sb.toString();
    }

    @Override
    public void streamChat(List<ChatMessage> messages, Consumer<String> onToken) throws Exception {
        Map<String, Object> body = buildRequestBody(messages, true);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(props.getBaseUrl() + "/chat/completions"))
                .header("Authorization", "Bearer " + props.getApiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();

        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() != 200) {
            String errorBody = readAll(response.body());
            throw new IOException("大模型接口调用失败，HTTP " + response.statusCode() + "：" + errorBody);
        }

        // 解析 SSE 流：data: {...} \n data: [DONE]
        try (InputStream responseBody = response.body();
             BufferedReader reader = new BufferedReader(new InputStreamReader(responseBody, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (!trimmed.startsWith("data:")) {
                    continue;
                }
                String data = trimmed.substring(5).trim();
                if (data.isEmpty()) {
                    continue;
                }
                if ("[DONE]".equals(data)) {
                    break;
                }
                JsonNode node = objectMapper.readTree(data);
                JsonNode delta = node.path("choices").path(0).path("delta").path("content");
                if (delta != null && !delta.isMissingNode() && !delta.isNull()) {
                    String text = delta.asText();
                    if (!text.isEmpty()) {
                        onToken.accept(text);
                    }
                }
            }
        }
    }

    private Map<String, Object> buildRequestBody(List<ChatMessage> messages, boolean stream) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", props.getModel());
        body.put("stream", stream);
        body.put("temperature", props.getTemperature());
        body.put("frequency_penalty", props.getFrequencyPenalty());
        body.put("presence_penalty", props.getPresencePenalty());
        body.put("max_tokens", props.getMaxTokens());
        body.put("messages", messages.stream()
                .map(m -> Map.of("role", m.role(), "content", m.content()))
                .collect(Collectors.toList()));
        return body;
    }

    private String readAll(InputStream in) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }
}
