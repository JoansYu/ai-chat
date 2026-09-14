import os
import json
from typing import List, Dict, Any, Generator
from openai import OpenAI
from tools import TOOLS_SCHEMA, TOOLS_ROUTER

# 初始化OpenAI客户端

client = OpenAI(
    api_key=os.getenv("OPENAI_API_KEY", "sk-MdlbuEkY2zK3Zz7bY0D81fSjCLQjBus16n88WDVt1edhXyMo"),
    base_url=os.getenv("OPENAI_BASE_URL", "http://models.ascend.huawei.com/v1")
)


def run_agent_loop(input_messages: List[Dict[str, Any]], max_steps: int = 5) -> Dict[str, Any]:
    """
    Agent核心循环（非流式，保留原有逻辑）
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
            response = {
                "status": "success",
                "final_answer": assistant_message.content or "",
                "messages": messages,
                "usage": {
                    "prompt_tokens": total_prompt_tokens,
                    "completion_tokens": total_completion_tokens,
                    "total_tokens": total_prompt_tokens + total_completion_tokens
                }
            }
            print(f"当前结果为：{response}" )
            return response

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


def run_agent_loop_stream(input_messages: List[Dict[str, Any]], max_steps: int = 5) -> Generator[Dict[str, Any], None, None]:
    """
    Agent核心循环（流式版）
    yield 事件格式：
      {"type": "token", "content": "..."}
      {"type": "tool_call", "name": "...", "arguments": "..."}
      {"type": "tool_result", "name": "...", "content": "..."}
      {"type": "done", "content": "完整回复", "messages": [...], "usage": {...}}
      {"type": "error", "message": "..."}
    """
    messages = list(input_messages)
    total_prompt_tokens = 0
    total_completion_tokens = 0

    # 注入代码探索策略系统提示：引导 Agent 用最小代价定位目标文件，避免全量盲搜浪费步数
    messages.insert(0, {
        "role": "system",
        "content": (
            "【代码探索纪律】当任务涉及查看/分析/定位代码时，必须遵循以下高效策略：\n"
            "1. 第一步优先用 glob_files 按文件名精确定位（如 '**/*AgentController*'），或用 list_directory 查看目录结构；\n"
            "2. 定位到目标文件后，立即用 read_file 精读，不要反复搜索；\n"
            "3. 严禁用 search_code 在整个工作区做无差别全量搜索（慢且结果噪声大）；必要时只在已定位的子目录内搜；\n"
            "4. 一次任务中 search_code 调用不得超过 2 次；\n"
            "5. 读到了足够信息就立刻给出最终结论，不要无意义地继续探索。"
        )
    })

    try:
        for step in range(max_steps):
            print(f"🤖 [AgentEngine-Stream] Step {step + 1} 思考中...")
            response = client.chat.completions.create(
                model=os.getenv("OPENAI_MODEL", "deepseek-v4-flash"),
                messages=messages,
                tools=TOOLS_SCHEMA,
                temperature=0.2,
                stream=True
            )

            content_buf = ""
            tool_calls_buf = {}

            for chunk in response:
                if not chunk.choices:
                    continue
                delta = chunk.choices[0].delta

                if delta.content:
                    content_buf += delta.content
                    yield {"type": "token", "content": delta.content}

                if delta.tool_calls:
                    for tc in delta.tool_calls:
                        idx = tc.index if tc.index is not None else 0
                        buf = tool_calls_buf.setdefault(idx, {"id": "", "name": "", "arguments": ""})
                        if tc.id:
                            buf["id"] = tc.id
                        if tc.function:
                            if tc.function.name:
                                buf["name"] += tc.function.name
                            if tc.function.arguments:
                                buf["arguments"] += tc.function.arguments

            assistant_msg = {"role": "assistant", "content": content_buf}
            if tool_calls_buf:
                assistant_msg["tool_calls"] = [
                    {"id": v["id"], "type": "function",
                     "function": {"name": v["name"], "arguments": v["arguments"]}}
                    for v in tool_calls_buf.values()
                ]
            messages.append(assistant_msg)

            if not tool_calls_buf:
                print("🎯 [AgentEngine-Stream] 得出最终结论！")
                yield {
                    "type": "done",
                    "content": content_buf,
                    "messages": messages,
                    "usage": {
                        "prompt_tokens": total_prompt_tokens,
                        "completion_tokens": total_completion_tokens,
                        "total_tokens": total_prompt_tokens + total_completion_tokens
                    }
                }
                return

            for idx in sorted(tool_calls_buf.keys()):
                tc_info = tool_calls_buf[idx]
                tool_name = tc_info["name"]
                args_str = tc_info["arguments"]

                yield {"type": "tool_call", "name": tool_name, "arguments": args_str}

                try:
                    args = json.loads(args_str) if args_str else {}
                except json.JSONDecodeError:
                    args = {}

                print(f"🧰 [AgentEngine-Stream] 触发工具调用: {tool_name}, 参数: {args}")

                if tool_name in TOOLS_ROUTER:
                    tool_result = TOOLS_ROUTER[tool_name](**args)
                else:
                    tool_result = f"Error: 工具 '{tool_name}' 未找到."

                yield {"type": "tool_result", "name": tool_name, "content": str(tool_result)}

                messages.append({
                    "role": "tool",
                    "tool_callId": tc_info["id"],
                    "name": tool_name,
                    "content": str(tool_result)
                })

        yield {
            "type": "done",
            "content": "Agent 达到了最大执行步数限制，无法完成任务。",
            "messages": messages,
            "usage": {
                "prompt_tokens": total_prompt_tokens,
                "completion_tokens": total_completion_tokens,
                "total_tokens": total_prompt_tokens + total_completion_tokens
            }
        }

    except Exception as e:
        print(f"❌ [AgentEngine-Stream] 异常: {str(e)}")
        yield {"type": "error", "message": f"Agent 执行异常: {str(e)}"}
