package com.aichat.service.llm;

import com.aichat.config.LLMProperties;
import com.aichat.dto.ChatCompletionChunk;
import com.aichat.model.ChatMessage;
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
                .connectTimeout(Duration.ofSeconds(10))
                .version(HttpClient.Version.HTTP_1_1) // 明确指定 HTTP 1.1，大多数本地大模型兼容性更好
                // 不要在这里设置全局读取超时，否则流式吐字慢了会被掐断
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

        // 解析 SSE 流
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("【LLM原始返回】：" + line);
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
                ChatCompletionChunk chatCompletionChunk = objectMapper.readValue(data, ChatCompletionChunk.class);
                if (chatCompletionChunk != null) {
                    ChatCompletionChunk.Choice.Delta delta = chatCompletionChunk.choices().getFirst().delta();
                    String content = delta.content();
                    if (!content.isEmpty()) {
                        onToken.accept(content);
                    }
                }
            }
        } catch (RuntimeException e) {
            if ("CLIENT_ABORT".equals(e.getMessage())) {
                System.out.println("检测到前端终止了对话，立刻停止读取大模型，释放连接。");
                // 【核心修复】：必须将异常继续抛出，否则外层会误以为执行成功，继续发送后续信息导致二次崩溃
                throw e;
            } else {
                throw e; // 继续往外抛
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // 【修正部分】：原生 HttpClient 只需要关闭 body(InputStream)
            if (response != null && response.body() != null) {
                try {
                    response.body().close();
                } catch (IOException e) {
                    // 忽略关闭流时可能抛出的异常
                }
            }
        }
    }

    private Map<String, Object> buildRequestBody(List<ChatMessage> messages, boolean stream) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", props.getModel());
        body.put("stream", stream);
        body.put("temperature", props.getTemperature());
        body.put("max_tokens", props.getMaxTokens());
        body.put("messages", messages.stream()
                .map(m -> Map.of("role", m.role(), "content", m.content()))
                .collect(Collectors.toList()));
        // 👇 核心改造：重新构建要发送给大模型的消息列表
        List<Map<String, String>> apiMessages = new java.util.ArrayList<>();

        // 1. 如果配置了系统提示词，并且不为空，强制把它作为第一条消息 (role = system)
        if (props.getSystemPrompt() != null && !props.getSystemPrompt().trim().isEmpty()) {
            apiMessages.add(Map.of(
                    "role", "system",
                    "content", props.getSystemPrompt()
            ));
        }

        // 2. 追加用户和助手的历史上下文
        for (ChatMessage m : messages) {
            apiMessages.add(Map.of(
                    "role", m.role(),
                    "content", m.content()
            ));
        }

        // 把拼装好的消息放进请求体中
        body.put("messages", apiMessages);
        return body;
    }

    // 👇 增加判空和安全的 try-catch，绝不让这里抛出异常打断主流程
    private String readAll(InputStream in) {
        if (in == null) {
            return "【响应体为空 (可能被代理拦截或未返回任何内容)】";
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        } catch (Exception e) {
            return "【读取响应体异常: " + e.getMessage() + "】";
        }
    }
}