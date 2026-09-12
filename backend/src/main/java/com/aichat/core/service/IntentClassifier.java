package com.aichat.core.service;

import com.aichat.core.enums.IntentType;
import com.aichat.core.model.AgentModelClient;
import com.aichat.model.AgentMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Locale;

/**
 * 意图分类器
 * 新建一个组件，专门用大模型来做“短平快”的意图判断。
 * 架构师秘诀：分类任务不需要长篇大论，必须要求大模型只输出英文标识，并且建议在底层调用时把 temperature（随机性）调到极低（比如 0.1）。
 */

@Component
@RequiredArgsConstructor
public class IntentClassifier {

    private final AgentModelClient client;

    public IntentType analyze(String userInput, List<AgentMessage> history) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一个意图识别引擎。根据用户的输出，以及可选的历史对话记录，判断用户的当前意图类别。\n");
        prompt.append("【类别定义】：\n");
        prompt.append("1. CHAT：简单的日常闲聊、寒暄、或者纯文本逻辑问答（如“你好”、“谢谢”，“什么是JAVA”）。\n");
        prompt.append("2. TASK_NEW：用户提出了一个新的、需要调用外部工具或需要多步规划的复杂请求（如“查天气”、“订机票”、“帮我规划...”）。\n");
        prompt.append("3. TASK_CONTINUE：用户在顺着刚才的话题继续追问、或者修改刚才的条件（如“那就把时间改到明天”、“继续”）。\n");
        prompt.append("【严格纪律】：你【只能】输出CHAT、TASK_NEW、TASK_CONTINUE 这三个词中的一个，绝对不能输出任何其他多余的字符或标点符号！ \n\n");

        if (!CollectionUtils.isEmpty(history)) {
            prompt.append("前文回顾（摘要）：...[历史记录已省略，请重点关注最新输入] ...\n");
        }
        prompt.append("用户最新输入：").append(userInput).append("\n");
        prompt.append("你的输出类别是：");

        try{
            AgentMessage resultMsg = client.chat(List.of(AgentMessage.user(prompt.toString())), null);
            String rawResult = resultMsg.content().trim().toUpperCase(Locale.ROOT);
            if (rawResult.contains("CHAT")) {
                System.out.println("当前的任务类型：Chat");
                return IntentType.CHAT;
            }
            if (rawResult.contains("TASK_NEW")) {
                System.out.println("当前的任务类型：TASK_NEW");
                return IntentType.TASK_NEW;
            }
            if (rawResult.contains("TASK_CONTINUE")) {
                System.out.println("当前的任务类型：TASK_CONTINUE");
                return IntentType.TASK_CONTINUE;
            }
        }catch (Exception e){
            System.err.println("意图识别异常，降级走兜底逻辑: " + e.getMessage());
        }
        return IntentType.UNKNOWN;
    }


}
