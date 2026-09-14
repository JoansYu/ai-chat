package com.aichat.core;

import ch.qos.logback.core.util.StringUtil;
import com.aichat.core.model.AgentModelClient;
import com.aichat.model.AgentContext;
import com.aichat.model.AgentMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class Planner {

    private final AgentModelClient client;


    /**
     * 第一阶段：全局规划(plan)
     * 利用大模型强大的逻辑推理能力，将复杂问题拆解为可执行的步骤
     *
     * @param userInput 用户输入
     * @return 规划方案
     */
    public String generatePlan(String userInput){

        String prompt = """
                你是一个任务规划专家。请将用户的复杂请求拆解为明确的、可执行的步骤（例如：1. 先查xx,2. 再算xx)。
                注意：你不需要实际执行，只需输出精简的步骤计划即可。
                【重点】：如果用户的问题涉及“当前时间”、“今天日期”、“某地天气”、“实时新闻”等动态信息，请务必在计划中明确写出“调用相关工具查询xxx”。
                用户请求：
                """ + userInput;
        List<AgentMessage> msgs = List.of(AgentMessage.user(prompt));
        try{
            AgentMessage planMsg = client.chat(msgs, null);
            return planMsg.content();
        }catch (Exception e) {
            return "1. 按需调用工具解决用户问题。"; // 兜底容错
        }
    }

    public List<AgentMessage> buildExecutionMessage(String userInput, String plan, AgentContext ctx, String memoryContext) {
        List<AgentMessage> msgs = new ArrayList<>();
        StringBuilder sys = new StringBuilder();

        sys.append("你是一个严谨的执行智能体 (Agent)。请严格遵循以下纪律来执行任务：\n");

        // 👇 优化 2：给执行者定下铁律，彻底杜绝幻觉！
        sys.append("【纪律红线 1】：遇到需要获取当前时间、日期、实时天气等需要获取实时信息时，你【必须】优先调用提供的工具，【绝对禁止】自己编造实时变化的信息，如日期和天气数据！\n");
        sys.append("【纪律红线 2】：在调用任何工具前，请务必先输出你的思考过程(Thought)。\n\n");

        sys.append("【执行计划参考】\n").append(plan).append("\n\n");

        sys.append("当前用户ID=").append(ctx.userId()).append("。");

        if (StringUtil.notNullNorEmpty(memoryContext)) {
            sys.append("\n用户长期上下文：").append(memoryContext);
        }
        msgs.add(AgentMessage.system(sys.toString()));
        msgs.add(AgentMessage.user(userInput));
        return msgs;
    }



    public List<AgentMessage> plan(String userInput, AgentContext ctx, String memoryContext) {
        List<AgentMessage> msgs = new ArrayList<>();
        StringBuilder sys = new StringBuilder();
        sys.append("你是一个严谨的智能助手，可调用工具完成任务。未知的信息先调用工具，不要编造。");
        sys.append("当前用户ID=").append(ctx.userId()).append("。");
        if (StringUtil.notNullNorEmpty(memoryContext)) {
            sys.append("\n用户长期上下文：" ).append(memoryContext);
        }
        msgs.add(AgentMessage.system(sys.toString()));
        msgs.add(AgentMessage.user(userInput));
        return msgs;
    }
}
