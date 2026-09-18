import json
import logging
from typing import TypedDict, Annotated, Sequence, List, Dict, Any, AsyncGenerator

from langchain_core.messages import (
    BaseMessage, ToolMessage, HumanMessage, AIMessage, SystemMessage
)
from langchain_openai import ChatOpenAI
from langgraph.graph import add_messages, StateGraph, END

from config import OPENAI_API_KEY, OPENAI_BASE_URL, OPENAI_MODEL, TEMPERATURE, DEFAULT_MAX_STEPS
from mcp_manager import mcp_manager
from tools import LOCAL_TOOLS_SCHEMA, LOCAL_TOOLS_ROUTER

logger = logging.getLogger("agent-service.graph")


# ====================================================
# 1. 状态定义
# ====================================================
class AgentState(TypedDict):
    messages: Annotated[Sequence[BaseMessage], add_messages]
    ctx: dict


# ====================================================
# 2. 系统提示词
# ====================================================
SYSTEM_PROMPT = """你是一个严谨的执行智能体 (Agent)。请严格遵循以下纪律来执行任务：

【纪律红线 1】遇到需要获取当前时间、日期、实时天气等需要获取实时信息时，你【必须】优先调用提供的工具，【绝对禁止】自己编造实时变化的信息。

【纪律红线 2】在调用任何工具前，请务必先输出你的思考过程 (Thought)。

【代码探索纪律】当任务涉及查看/分析/定位代码时，必须遵循以下高效策略：
1. 第一步优先用 glob_files 按文件名精确定位，或用 list_directory 查看目录结构；
2. 定位到目标文件后，立即用 read_file 精读，不要反复搜索；
3. 严禁用 search_code 在整个工作区做无差别全量搜索；必要时只在已定位的子目录内搜；
4. 一次任务中 search_code 调用不得超过 2 次；
5. 读到了足够信息就立刻给出最终结论，不要无意义地继续探索。
"""


# ====================================================
# 3. 历史消息转换
# ====================================================
def _convert_history(history: List[Dict[str, Any]]) -> List[BaseMessage]:
    """将 dict 格式的历史消息转换为 LangChain Message 对象，支持多轮对话"""
    messages = []
    for msg in history:
        if not isinstance(msg, dict):
            continue
        role = msg.get("role", "")
        content = msg.get("content", "")

        if role == "system":
            messages.append(SystemMessage(content=content))
        elif role == "user":
            messages.append(HumanMessage(content=content))
        elif role == "assistant":
            messages.append(AIMessage(content=content))
        elif role == "tool":
            messages.append(ToolMessage(
                content=content,
                tool_call_id=msg.get("tool_call_id") or msg.get("toolCallId") or "",
                name=msg.get("name", "")
            ))
    return messages


# ====================================================
# 4. 大模型初始化
# ====================================================
llm = ChatOpenAI(
    api_key=OPENAI_API_KEY,
    base_url=OPENAI_BASE_URL,
    model=OPENAI_MODEL,
    temperature=TEMPERATURE,
    streaming=True
)


# ====================================================
# 5. 节点定义 (Nodes)
# ====================================================
async def call_model_node(state: AgentState):
    """节点 1：呼叫大模型进行推理"""
    logger.info("🧠 [LangGraph] 节点运行: 思考中...")

    combined_tools = LOCAL_TOOLS_SCHEMA + mcp_manager.mcp_schemas
    llm_with_tools = llm.bind_tools(combined_tools)

    response = await llm_with_tools.ainvoke(state['messages'])
    return {"messages": [response]}


async def execute_tools_node(state: AgentState):
    """节点 2：执行大模型指定的工具"""
    logger.info("🧰 [LangGraph] 节点运行: 执行工具")
    last_message = state['messages'][-1]
    tool_results = []

    for tool_call in last_message.tool_calls:
        tool_name = tool_call["name"]
        args = tool_call["args"]
        logger.info(f"   -> 准备执行: {tool_name}, 参数: {args}")

        try:
            if tool_name in LOCAL_TOOLS_ROUTER:
                result_str = await LOCAL_TOOLS_ROUTER[tool_name](**args, ctx=state["ctx"])
            elif tool_name in mcp_manager.mcp_tool_names:
                mcp_res = await mcp_manager.session.call_tool(tool_name, args)
                result_str = mcp_res.content[0].text
            else:
                result_str = f"Error：工具 '{tool_name}' 不存在。"
        except Exception as e:
            result_str = f"执行异常：{str(e)}"
            logger.error(f"工具执行异常: {tool_name} - {e}")

        tool_results.append(
            ToolMessage(content=str(result_str), tool_call_id=tool_call["id"], name=tool_name)
        )

    return {"messages": tool_results}


# ====================================================
# 6. 条件路由 (Edges)
# ====================================================
def should_continue(state: AgentState):
    """判断是继续执行工具，还是结束对话"""
    last_message = state["messages"][-1]
    if hasattr(last_message, "tool_calls") and last_message.tool_calls:
        return "continue"
    return "end"


# ====================================================
# 7. 编译图 (Graph Compilation)
# ====================================================
workflow = StateGraph(AgentState)
workflow.add_node("agent", call_model_node)
workflow.add_node("action", execute_tools_node)
workflow.set_entry_point("agent")
workflow.add_conditional_edges(
    "agent",
    should_continue,
    {"continue": "action", "end": END}
)
workflow.add_edge("action", "agent")
smart_agent_app = workflow.compile()


# ====================================================
# 8. 对外接口
# ====================================================
async def run_langgraph_agent(
    user_input: str, history: list, ctx: dict, max_steps: int = DEFAULT_MAX_STEPS
) -> Dict[str, Any]:
    """非流式 Agent 接口"""
    messages = [SystemMessage(content=SYSTEM_PROMPT)]
    messages.extend(_convert_history(history))
    messages.append(HumanMessage(content=user_input))

    initial_state = {"messages": messages, "ctx": ctx}

    node_state = {}
    try:
        async for event in smart_agent_app.astream(
            initial_state, config={"recursion_limit": max_steps * 2 + 2}
        ):
            for node_name, state in event.items():
                logger.info(f"--- 节点 [{node_name}] 执行完毕 ---")
                node_state = state
    except Exception as e:
        logger.error(f"图引擎执行异常: {e}")
        return {"status": "error", "final_answer": f"执行异常: {str(e)}", "error_message": str(e), "messages": []}

    final_messages = node_state.get("messages", [])
    final_answer = final_messages[-1].content if final_messages else "未得出有效结论"

    return {
        "status": "success",
        "final_answer": final_answer,
        "messages": [{"role": "assistant", "content": final_answer}]
    }


async def run_langgraph_agent_stream(
    user_input: str, history: list, ctx: dict, max_steps: int = DEFAULT_MAX_STEPS
) -> AsyncGenerator[Dict[str, Any], None]:
    """流式 Agent 接口，将 LangGraph 事件流转换为前端可读的 SSE 格式"""
    messages = [SystemMessage(content=SYSTEM_PROMPT)]
    messages.extend(_convert_history(history))
    messages.append(HumanMessage(content=user_input))

    initial_state = {"messages": messages, "ctx": ctx}

    try:
        full_answer = ""
        async for event in smart_agent_app.astream_events(
            initial_state, version="v2", config={"recursion_limit": max_steps * 2 + 2}
        ):
            kind = event["event"]

            if kind == "on_chat_model_stream":
                chunk = event["data"]["chunk"]
                if chunk.content:
                    full_answer += chunk.content
                    yield {"type": "token", "content": chunk.content}

            elif kind == "on_tool_start":
                tool_name = event["name"]
                args = event["data"].get("input", {})
                args_str = json.dumps(args, ensure_ascii=False)
                logger.info(f"🧰 [GraphStream] 触发工具: {tool_name}, 参数: {args_str}")
                yield {"type": "tool_call", "name": tool_name, "arguments": args_str}

            elif kind == "on_tool_end":
                tool_name = event["name"]
                result = event["data"].get("output", "")
                logger.info(f"✅ [GraphStream] 工具返回: {tool_name}")
                yield {"type": "tool_result", "name": tool_name, "content": str(result)}

        yield {"type": "done", "content": full_answer}

    except Exception as e:
        logger.error(f"❌ [GraphStream] 异常: {str(e)}")
        yield {"type": "error", "message": f"图引擎执行异常: {str(e)}"}
