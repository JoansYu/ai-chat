package com.aichat.core.client;

import com.aichat.dto.AgentPythonRequest;
import com.aichat.dto.AgentPythonResponse;
import com.aichat.exception.ClientAbortException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.function.Consumer;

@Component
public class AgentPythonClient {

    private static final Logger log = LoggerFactory.getLogger(AgentPythonClient.class);

    private final RestClient client;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String pythonUrl;
    private final String javaCallbackUrl;

    public AgentPythonClient(@Value("${agent.python.url:http://7.192.34.148:8000}") String pythonUrl,
                             @Value("${agent.callback-url:}") String callbackUrl,
                             @Value("${server.port:8080}") int serverPort) {
        this.pythonUrl = pythonUrl.endsWith("/") ? pythonUrl.substring(0, pythonUrl.length() - 1) : pythonUrl;
        this.client = RestClient.builder()
                .baseUrl(pythonUrl)
                .build();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.javaCallbackUrl = resolveCallbackUrl(callbackUrl, serverPort);
    }

    /**
     * 解析 Java 回调地址（供 Python 回调执行文件工具）
     * 优先使用配置的 agent.callback-url；未配置时用本机 IP + server.port
     */
    private String resolveCallbackUrl(String configured, int port) {
        if (configured != null && !configured.isBlank()) {
            return configured.endsWith("/") ? configured.substring(0, configured.length() - 1) : configured;
        }
        try {
            String host = java.net.InetAddress.getLocalHost().getHostAddress();
            return "http://" + host + ":" + port;
        } catch (Exception e) {
            return "http://localhost:" + port;
        }
    }

    /**
     * 构建请求体 JSON，注入授权工作区路径、回调地址与用户 Token（已在上层 Web 上下文解析，勿在子线程调用 StpUtil）
     */
    private String buildRequestBody(AgentPythonRequest request, String workspacePath, String userToken) throws Exception {
        Map<String, Object> body = objectMapper.convertValue(request, Map.class);
        body.put("workspace_root", workspacePath != null ? workspacePath : "");
        body.put("java_callback_url", javaCallbackUrl);
        body.put("user_token", userToken != null ? userToken : "");
        return objectMapper.writeValueAsString(body);
    }

    public AgentPythonResponse executeAgent(AgentPythonRequest request, String workspacePath, String userToken) {
        try {
            String bodyJson = buildRequestBody(request, workspacePath, userToken);
            return client.post()
                    .uri("/api/v1/agent/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(bodyJson)
                    .retrieve()
                    .body(AgentPythonResponse.class);
        } catch (Exception e) {
            throw new RuntimeException("调用 Python Agent 服务失败: " + e.getMessage(), e);
        }
    }

    /**
     * 流式调用 Python Agent，读取 SSE 流并逐事件回调
     */
    public void streamExecuteAgent(AgentPythonRequest request, String workspacePath, String userToken,
                                   Consumer<Map<String, Object>> onEvent) {
        try {
            String bodyJson = buildRequestBody(request, workspacePath, userToken);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(pythonUrl + "/api/v1/agent/stream"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofHours(1))
                    .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
                    .build();

            HttpResponse<InputStream> response = httpClient.send(httpRequest,
                    HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                throw new RuntimeException("Python Agent 流式接口返回 HTTP " + response.statusCode());
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (!trimmed.startsWith("data:")) continue;
                    String data = trimmed.substring(5).trim();
                    if (data.isEmpty()) continue;

                    JsonNode node = objectMapper.readTree(data);
                    Map<String, Object> event = objectMapper.convertValue(node, Map.class);
                    onEvent.accept(event);

                    String type = (String) event.get("type");
                    if ("done".equals(type) || "error".equals(type)) {
                        break;
                    }
                }
            }
        } catch (ClientAbortException e) {
            // 前端已断开连接，原样透传，禁止包装
            throw e;
        } catch (Exception e) {
            log.error("流式调用 Python Agent 异常", e);
            throw new RuntimeException("调用 Python Agent 流式服务失败: " + e.getMessage(), e);
        }
    }
}
