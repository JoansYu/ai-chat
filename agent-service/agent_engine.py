import json
import os
from typing import List, Dict, Any, AsyncGenerator
from openai import AsyncOpenAI
from tools import LOCAL_TOOLS_SCHEMA, LOCAL_TOOLS_ROUTER
from mcp_manager import mcp_manager

client = AsyncOpenAI(
    api_key=os.getenv("OPENAI_API_KEY", "sk-MdlbuEkY2zK3Zz7bY0D81fSjCLQjBus16n88WDVt1edhXyMo"),
    base_url=os.getenv("OPENAI_BASE_URL", "http://models.ascend.huawei.com/v1")
)


# ====================================================
# 意图识别 + 任务规划（合并为一次模型调用，减少延迟）
# ====================================================
# ====================================================
# 意图识别 + 任务规划（合并为一次模型调用，减少延迟）
# ====================================================
def _parse_intent_plan(raw: str) -> dict:
    """容错解析模型的 JSON 输出：{intent, plan}"""
    import re
    match = re.search(r'\{.*\}', raw, re.DOTALL)
    if match:
        try:
            data = json.loads(match.group(0))
            intent = str(data.get("intent", "")).upper()
            if intent in ("CHAT", "TASK_NEW", "TASK_CONTINUE"):
                return {"intent": intent, "plan": str(data.get("plan", "") or "")}
        except Exception:
            pass
    # 兜底：关键词判断
    if "TASK_NEW" in raw:
        return {"intent": "TASK_NEW", "plan": ""}
    if "TASK_CONTINUE" in raw:
        return {"intent": "TASK_CONTINUE", "plan": ""}
    return {"intent": "CHAT", "plan": ""}


async def classify_and_plan(user_input: str, history: List[Dict[str, Any]]) -> dict:
    """一次模型调用完成意图识别 + 任务规划，返回 {"intent": "...", "plan": "..."}"""
    prompt = (
        "你是一个智能决策引擎。根据用户的输入和历史对话，一次性完成两件事：\n"
        "【1. 意图识别】判断用户意图，只能是以下三类之一：\n"
        '  - CHAT：日常闲聊、寒暄、纯文本问答（如"你好"、"什么是JAVA"）。\n'
        '  - TASK_NEW：新的复杂请求，需要调用工具或多步规划（如"查天气"、"看代码"、"查看目录"）。\n'
        '  - TASK_CONTINUE：顺着刚才话题继续追问或修改条件（如"继续"、"看下一个文件"）。\n'
        "【2. 任务规划】仅当意图为 TASK_NEW 时，输出精简的可执行步骤计划（如：1. 先查xx, 2. 再算xx）。\n"
        '若问题涉及"当前时间"、"日期"、"天气"、"实时信息"，计划中必须写"调用工具查询xxx"。\n'
        "若意图为 CHAT 或 TASK_CONTINUE，plan 字段输出空字符串即可。\n"
        '【严格纪律】：必须输出合法 JSON，格式为 {"intent": "CHAT", "plan": ""} '
        '或 {"intent": "TASK_NEW", "plan": "1. xxx\\n2. xxx"}，禁止输出任何其他内容。\n\n'
    )
    if history:
        prompt += "前文回顾（摘要）：...[历史已省略，请结合最新输入]...\n"
    prompt += f"用户最新输入：{user_input}\n"

    try:
        response = await client.chat.completions.create(
            model=os.getenv("OPENAI_MODEL", "deepseek-v4-flash"),
            messages=[{"role": "user", "content": prompt}],
            temperature=0.1,
            max_tokens=800
        )
        raw = response.choices[0].message.content or ""
        print(f"🎯 [Intent+Plan] 模型输出: {raw[:200]!r}")
        result = _parse_intent_plan(raw)
        print(f"🎯 [Intent+Plan] 判定: intent={result['intent']}, plan长度={len(result['plan'])}")
        return result
    except Exception as e:
        print(f"⚠️ [Intent+Plan] 异常，降级 CHAT: {e}")
        return {"intent": "CHAT", "plan": ""}


def build_system_prompt(plan: str, ctx: dict) -> str:
    """构建执行智能体的系统提示"""
    sys = (
        "你是一个严谨的执行智能体 (Agent)。请严格遵循以下纪律来执行任务：\n"
        "【纪律红线 1】：遇到需要获取当前时间、日期、实时天气等需要获取实时信息时，"
        "你【必须】优先调用提供的工具，【绝对禁止】自己编造实时变化的信息，如日期和天气数据！\n"
        "【纪律红线 2】：在调用任何工具前，请务必先输出你的思考过程(Thought)。\n\n"
        "【代码探索纪律】当任务涉及查看/分析/定位代码时，必须遵循以下高效策略：\n"
        "1. 第一步优先用 glob_files 按文件名精确定位，或用 list_directory 查看目录结构；\n"
        "2. 定位到目标文件后，立即用 read_file 精读，不要反复搜索；\n"
        "3. 严禁用 search_code 在整个工作区做无差别全量搜索；必要时只在已定位的子目录内搜；\n"
        "4. 一次任务中 search_code 调用不得超过 2 次；\n"
        "5. 读到了足够信息就立刻给出最终结论，不要无意义地继续探索。\n\n"
    )
    sys += f"【执行计划参考】\n{plan}\n\n"
    user_id = ctx.get("user_id", "")
    if user_id:
        sys += f"当前用户ID={user_id}。"
    return sys


def _sanitize_history(history: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    """清洗 Java 传来的历史消息，只保留 OpenAI 需要的字段（去掉 toolCalls/toolCallId/usage 等多余字段）"""
    cleaned = []
    for msg in history:
        if not isinstance(msg, dict):
            continue
        role = msg.get("role", "")
        if role == "tool":
            cleaned.append({
                "role": "tool",
                "tool_call_id": msg.get("tool_call_id") or msg.get("toolCallId") or "",
                "name": msg.get("name", ""),
                "content": msg.get("content", "")
            })
        elif role:
            cleaned.append({"role": role, "content": msg.get("content", "")})
    return cleaned


# ====================================================
# 智能入口：意图识别 + 规划 + Agent 循环（流式）
# ====================================================
async def run_smart_agent_loop_stream(
    user_input: str,
    history: List[Dict[str, Any]],
    max_steps: int,
    ctx: dict
) -> AsyncGenerator[Dict[str, Any], None]:
    """
    智能流式 Agent：内部完成意图识别 → 规划 → 消息构建 → Agent 循环
    Java 端只需传入 user_input + history，无需再做意图识别和规划
    """
    # 1. 一次调用完成意图识别 + 规划（减少一次模型调用）
    decision = await classify_and_plan(user_input, history)
    intent = decision["intent"]
    plan = decision.get("plan", "")
    yield {"type": "intent", "intent": intent}

    # 2. 构建消息列表（清洗历史消息格式）
    messages = _sanitize_history(history)
    print(f"🚀 [SmartStream] 意图={intent}, 清洗后消息数={len(messages)}")

    if intent == "TASK_NEW" or not messages:
        if not plan:
            plan = "1. 按需调用工具解决用户问题。"
        yield {"type": "plan", "plan": plan}
        sys_prompt = build_system_prompt(plan, ctx)
        messages = [{"role": "system", "content": sys_prompt}] + messages
        print(f"🚀 [SmartStream] 已注入系统提示(计划), 最终消息数={len(messages)}")
    else:
        print(f"🚀 [SmartStream] 意图={intent} 或已有历史, 不重新规划")

    messages.append({"role": "user", "content": user_input})
    print(f"🚀 [SmartStream] 消息结构:")
    for i, m in enumerate(messages):
        content = (m.get("content") or "")[:60].replace("\n", " ")
        print(f"🚀 [SmartStream]   [{i}] {m.get('role')}: {content!r}")

    # 3. 复用已有的 Agent 循环
    async for event in run_agent_loop_stream(messages, max_steps, ctx):
        yield event


# ====================================================
# 智能入口：意图识别 + 规划 + Agent 循环（非流式）
# ====================================================
async def run_smart_agent_loop(
    user_input: str,
    history: List[Dict[str, Any]],
    max_steps: int,
    ctx: dict
) -> Dict[str, Any]:
    """智能非流式 Agent"""
    decision = await classify_and_plan(user_input, history)
    intent = decision["intent"]
    plan = decision.get("plan", "")
    print(f"🎯 [SmartAgent] 意图: {intent}")

    messages = _sanitize_history(history)
    if intent == "TASK_NEW" or not messages:
        if not plan:
            plan = "1. 按需调用工具解决用户问题。"
        sys_prompt = build_system_prompt(plan, ctx)
        messages = [{"role": "system", "content": sys_prompt}] + messages
    messages.append({"role": "user", "content": user_input})

    return await run_agent_loop(messages, max_steps, ctx)


async def run_agent_loop(input_messages: List[Dict[str, Any]], max_steps: int, ctx: dict) -> Dict[str, Any]:
    messages = list(input_messages)

    # 💡 核心融合：将本地工具与 MCP 动态获取的工具拼接，全部喂给大模型！
    combined_tools = LOCAL_TOOLS_SCHEMA + mcp_manager.mcp_schemas

    for step in range(max_steps):
        print(f"🤖 [AgentEngine] Step {step + 1} 思考中...")
        response = await client.chat.completions.create(
            model=os.getenv("OPENAI_MODEL", "deepseek-v4-flash"),
            messages=messages,
            tools=combined_tools,
            temperature=0.2
        )

        assistant_message = response.choices[0].message
        messages.append(assistant_message.model_dump())

        if not assistant_message.tool_calls:
            print("🎯 [AgentEngine] 得出最终结论！")
            return {"status": "success", "final_answer": assistant_message.content or "", "messages": messages}

        for tool_call in assistant_message.tool_calls:
            tool_name = tool_call.function.name
            args = json.loads(tool_call.function.arguments or "{}")

            print(f"🧰 [AgentEngine] 触发调用: {tool_name}, 参数: {args}")

            # 💡 核心路由：根据工具归属决定在哪执行
            if tool_name in mcp_manager.mcp_tool_names:
                # 走底层的 MCP 子进程执行
                mcp_result = await mcp_manager.session.call_tool(tool_name, args)
                tool_result = mcp_result.content[0].text
            elif tool_name in LOCAL_TOOLS_ROUTER:
                # 走本地 Python 逻辑或 Java 回调，把 ctx 透传进去
                tool_result = await LOCAL_TOOLS_ROUTER[tool_name](**args, ctx=ctx)
            else:
                tool_result = f"Error: 工具 '{tool_name}' 未找到."

            messages.append(
                {"role": "tool", "tool_call_id": tool_call.id, "name": tool_name, "content": str(tool_result)})

    return {"status": "failed", "final_answer": "达到最大步数", "messages": messages}


# --- 异步流式引擎 (SSE) ---
async def run_agent_loop_stream(input_messages: List[Dict[str, Any]], max_steps: int, ctx: dict) -> AsyncGenerator[
    Dict[str, Any], None]:
    messages = list(input_messages)
    combined_tools = LOCAL_TOOLS_SCHEMA + mcp_manager.mcp_schemas

    try:
        for step in range(max_steps):
            print(f"🤖 [Loop] ═══ Step {step + 1}/{max_steps} 开始 ═══")
            print(f"🤖 [Loop] 当前 messages 数={len(messages)}, 工具数={len(combined_tools)}")
            response = await client.chat.completions.create(
                model=os.getenv("OPENAI_MODEL", "deepseek-v4-flash"),
                messages=messages,
                tools=combined_tools,
                temperature=0.2,
                stream=True
            )

            content_buf = ""
            tool_calls_buf = {}

            # 异步流式读取
            async for chunk in response:
                if not chunk.choices: continue
                delta = chunk.choices[0].delta

                if delta.content:
                    content_buf += delta.content
                    yield {"type": "token", "content": delta.content}

                if delta.tool_calls:
                    for tc in delta.tool_calls:
                        idx = tc.index
                        buf = tool_calls_buf.setdefault(idx, {"id": "", "name": "", "arguments": ""})
                        if tc.id: buf["id"] = tc.id
                        if tc.function:
                            if tc.function.name: buf["name"] += tc.function.name
                            if tc.function.arguments: buf["arguments"] += tc.function.arguments

            print(f"🤖 [Loop] Step{step + 1} 模型输出: content_len={len(content_buf)}, tool_calls={len(tool_calls_buf)}")
            if tool_calls_buf:
                for idx, v in tool_calls_buf.items():
                    print(f"🤖 [Loop]   tool_call[{idx}] name={v['name']!r}, args={v['arguments'][:100]!r}")

            # 组合 assistant 消息并 append 回上下文（流式模式下必须手动重建）
            assistant_msg = {"role": "assistant", "content": content_buf}
            if tool_calls_buf:
                assistant_msg["tool_calls"] = [
                    {"id": v["id"], "type": "function",
                     "function": {"name": v["name"], "arguments": v["arguments"]}}
                    for v in tool_calls_buf.values()
                ]
            messages.append(assistant_msg)

            if not tool_calls_buf:
                print(f"✅ [Loop] Step{step + 1} 无工具调用，得出最终结论, 长度={len(content_buf)}")
                yield {"type": "done", "content": content_buf, "messages": messages}
                return

            for idx in sorted(tool_calls_buf.keys()):
                tc_info = tool_calls_buf[idx]
                tool_name, args_str = tc_info["name"], tc_info["arguments"]
                args = json.loads(args_str or "{}")
                print(f"🔧 [Tool] 调用开始: {tool_name}, args={json.dumps(args, ensure_ascii=False)[:150]}")

                # 💡 在流式循环中做异步 MCP 路由
                if tool_name in mcp_manager.mcp_tool_names:
                    print(f"🔧 [Tool]   → 走 MCP 执行")
                    mcp_result = await mcp_manager.session.call_tool(tool_name, args)
                    tool_result = mcp_result.content[0].text
                elif tool_name in LOCAL_TOOLS_ROUTER:
                    print(f"🔧 [Tool]   → 走本地/Java回调执行")
                    tool_result = await LOCAL_TOOLS_ROUTER[tool_name](**args, ctx=ctx)
                else:
                    print(f"🔧 [Tool]   → 未知工具!")
                    tool_result = "工具未找到"

                brief_result = (str(tool_result))[:150].replace("\n", " ")
                print(f"🔧 [Tool] {tool_name} 结果: {brief_result!r}")
                yield {"type": "tool_result", "name": tool_name, "content": str(tool_result)}
                # 关键：把工具结果 append 回上下文，下一轮模型才能看到
                messages.append({
                    "role": "tool",
                    "tool_call_id": tc_info["id"],
                    "name": tool_name,
                    "content": str(tool_result)
                })

        # max_steps 耗尽后的兜底 done 事件，避免前端长时间无信号
        print(f"⚠️ [Loop] max_steps={max_steps} 已耗尽，兜底返回")
        yield {"type": "done", "content": "已达到最大执行步数限制，无法完成任务。", "messages": messages}

    except Exception as e:
        print(f"❌ [Loop] 异常: {str(e)}")
        yield {"type": "error", "message": f"异常: {str(e)}"}