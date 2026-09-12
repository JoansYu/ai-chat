package com.aichat.core.enums;

import lombok.Getter;

@Getter
public enum IntentType {

    /**
     * 闲聊、寒暄、简单问答（不需要调用工具和规划）
     */
    CHAT,

    /**
     * 全新的复杂任务（需要重新做Planner 规划并调用工具）
     */
    TASK_NEW,

    /**
     * 延续上一个任务进行追问或修改（不需要重新规划，直接进 AgentLoop)
     */
    TASK_CONTINUE,

    /**
     * 兜底类型（如果大模型抽风没按照格式输出，默认走新任务兜底
     */
    UNKNOWN;

}
