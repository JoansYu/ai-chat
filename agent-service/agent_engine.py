import os
import json
from typing import List, Dict, Any
from openai import OpenAI
from tools import TOOLS_SCHEMA, TOOLS_ROUTER

# 初始化OpenAI客户端

client = OpenAI(
    api_key=os.getenv("OPENAI_API_KEY", "sk-MdlbuEkY2zK3Zz7bY0D81fSjCLQjBus16n88WDVt1edhXyMo"),
    base_url=os.getenv("OPENAI_BASE_URL", "http://models.ascend.huawei.com/v1")
)


def run_agent_loop(input_messages: List[Dict[str, Any]], max_steps: int = 5) -> Dict[str, Any]:
    """
    Agent核心循环
    :param input_messages java端上下文列表
    :param max_steps 最大思考轮数，防止死循环
    :rtype: object
    """
    messages = list(input_messages)

    total_prompt_tokens = 0
    total_completion_tokens = 0

    for step in range(max_steps):
        print(f"🤖 [AgentEngine] Step {step + 1} 思考中...")
        response = client.chat.completions.create(
            model=os.getenv("OPENAI_MODEL", "deepseek-v4-flash"),
            messages=messages,
            tools=TOOLS_SCHEMA,
            temperature=0.2
        )

        if response.usage:
            total_prompt_tokens += response.usage.prompt_tokens
            total_completion_tokens += response.usage.completion_tokens

        choice = response.choices[0]
        assistant_message = choice.message

        messages.append(assistant_message.model_dump())

        if not assistant_message.tool_calls:
            print("🎯 [AgentEngine] 得出最终结论！")
            return {
                "status": "success",
                "final_answer": assistant_message.content or "",
                "messages": messages,
                "usage": {
                    "prompt_tokens": total_prompt_tokens,
                    "completion_tokens": total_completion_tokens,
                    "total_tokens": total_prompt_tokens + total_completion_tokens
                }
            }

        for tool_call in assistant_message.tool_calls:
            tool_name = tool_call.function.name
            args_str = tool_call.function.arguments

            try:
                args = json.loads(args_str) if args_str else {}
            except json.JSONDecodeError:
                args = {}

            print(f"🧰 [AgentEngine] 触发工具调用: {tool_name}, 参数: {args}")

            if tool_name in TOOLS_ROUTER:
                tool_func = TOOLS_ROUTER[tool_name]
                tool_result = tool_func(**args)
            else:
                tool_result = f"Error: 工具 '{tool_name}' 未找到."

            messages.append({
                "role": "tool",
                "tool_callId": tool_call.id,
                "name": tool_name,
                "content": str(tool_result)
            })

    return {
        "status": "failed",
        "final_answer": "Agent 达到了最大执行步数限制，无法完成任务。",
        "messages": messages,
        "usage": {
            "prompt_tokens": total_prompt_tokens,
            "completion_tokens": total_completion_tokens,
            "total_tokens": total_prompt_tokens + total_completion_tokens
        }
    }
