package com.aichat.service.llm;

import com.aichat.model.ChatMessage;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * 内置模拟大模型客户端
 * <p>未配置真实大模型时使用，保证项目开箱即用，且同样模拟流式逐字输出效果。</p>
 */
public class MockLLMClient implements LLMClient {

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.CHINA);

    @Override
    public boolean isConfigured() {
        return false;
    }

    @Override
    public String chat(List<ChatMessage> messages) throws Exception {
        StringBuilder sb = new StringBuilder();
        streamChat(messages, sb::append);
        return sb.toString();
    }

    @Override
    public void streamChat(List<ChatMessage> messages, Consumer<String> onToken) {
        String reply = buildReply(messages);
        // 模拟逐字流式输出
        for (int i = 0; i < reply.length(); i++) {
            onToken.accept(String.valueOf(reply.charAt(i)));
            try {
                Thread.sleep(15);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private String buildReply(List<ChatMessage> messages) {
        if (messages.isEmpty()) {
            return "你好！我是 AI 助手，请问有什么可以帮你的吗？";
        }
        String last = messages.get(messages.size() - 1).content();

        String text = last.toLowerCase(Locale.ROOT);
        if (text.contains("你好") || text.contains("您好") || text.contains("hello") || text.contains("hi")) {
            return "你好！很高兴见到你。我是一个支持多轮对话的 AI 助手，你可以随时向我提问，我会结合上下文连续回答。";
        }
        if (text.contains("时间") || text.contains("日期") || text.contains("几点")) {
            return "现在是 " + LocalDateTime.now().format(TIME_FORMATTER) + "。";
        }
        if (text.contains("能做什么") || text.contains("功能") || text.contains("帮助") || text.contains("help")) {
            return "我可以帮助你：\n"
                    + "1. 解答技术问题、写代码、做代码审查\n"
                    + "2. 撰写文案、翻译、总结文档\n"
                    + "3. 多轮对话：我会记住上下文，围绕同一个话题持续深入\n\n"
                    + "提示：当前使用的是内置模拟引擎。如需接入真实大模型，请在 application.yml 中配置 ai.chat.llm.api-key。";
        }
        if (text.contains("谢谢") || text.contains("感谢")) {
            return "不客气！有任何问题随时找我，我很乐意继续为你服务。";
        }
        return "我收到你的消息了：" + last + "\n\n"
                + "（这是内置模拟引擎的回复。要获得真正的 AI 智能回答，请在 `application.yml` 中配置真实大模型：将 `ai.chat.llm.enabled` 设为 `true` 并填入 `api-key`。）";
    }
}
