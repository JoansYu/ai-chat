import json
import logging
import httpx
import datetime

logger = logging.getLogger("agent-service.tools")


# ====================================================
# 异步基础工具
# ====================================================
async def get_weather(location: str, ctx: dict) -> str:
    logger.info(f"查询天气: {location}")
    return json.dumps({"location": location, "weather": "Sunny", "temperature": "25C"})


async def get_current_time(ctx: dict) -> str:
    logger.info("获取系统时间")
    utc_now = datetime.datetime.now(datetime.timezone.utc)
    beijing_now = utc_now + datetime.timedelta(hours=8)
    return f"{beijing_now.strftime('%Y-%m-%d %H:%M:%S')} (UTC+8)"


# ====================================================
# Java 回调工具
# ====================================================
async def _call_java_tool_async(tool_name: str, args: dict, ctx: dict) -> str:
    callback_url = ctx.get("java_callback_url", "")
    token = ctx.get("user_token", "")

    if not callback_url:
        return "错误：未配置 Java 回调地址"

    try:
        async with httpx.AsyncClient() as client:
            resp = await client.post(
                f"{callback_url}/api/agent/tools/execute",
                json={"tool": tool_name, "args": args},
                headers={"satoken": token},
                timeout=35.0
            )
            if resp.status_code != 200:
                return f"Java 回调失败 HTTP {resp.status_code}"

            data = resp.json()
            if data.get("code") == 200:
                return data.get("data", {}).get("result", "（空结果）")
            return f"工具执行失败: {data.get('message')}"
    except Exception as e:
        return f"回调 Java 异常: {str(e)}"


async def search_code(pattern: str, path: str = ".", ctx: dict = None) -> str:
    return await _call_java_tool_async("search_code", {"pattern": pattern, "path": path}, ctx)


async def glob_files(pattern: str, path: str = ".", ctx: dict = None) -> str:
    return await _call_java_tool_async("glob_files", {"pattern": pattern, "path": path}, ctx)


# ====================================================
# 项目阅读工具（由 IDE 侧 PSI 实现）
# ====================================================
async def read_project_structure(path: str = ".", ctx: dict = None) -> str:
    return await _call_java_tool_async("read_project_structure", {"path": path}, ctx)


async def get_symbol_info(symbol: str, ctx: dict = None) -> str:
    return await _call_java_tool_async("get_symbol_info", {"symbol": symbol}, ctx)


async def find_usages(symbol: str, ctx: dict = None) -> str:
    return await _call_java_tool_async("find_usages", {"symbol": symbol}, ctx)


async def read_file_region(path: str, startLine: int = 1, endLine: int = None, ctx: dict = None) -> str:
    args = {"path": path, "startLine": startLine}
    if endLine is not None:
        args["endLine"] = endLine
    return await _call_java_tool_async("read_file_region", args, ctx)


# ====================================================
# 本地工具 Schema 声明
# ====================================================
LOCAL_TOOLS_SCHEMA = [
    {
        "type": "function",
        "function": {
            "name": "get_weather",
            "description": "获取指定城市的天气信息",
            "parameters": {"type": "object", "properties": {"location": {"type": "string"}}, "required": ["location"]}
        }
    },
    {
        "type": "function",
        "function": {
            "name": "get_current_time",
            "description": "获取当前系统时间",
            "parameters": {"type": "object", "properties": {}}
        }
    },
    {
        "type": "function",
        "function": {
            "name": "search_code",
            "description": "正则搜索代码内容",
            "parameters": {"type": "object", "properties": {"pattern": {"type": "string"}, "path": {"type": "string"}},
                           "required": ["pattern"]}
        }
    },
    {
        "type": "function",
        "function": {
            "name": "glob_files",
            "description": "按文件名模式匹配查找文件",
            "parameters": {"type": "object", "properties": {"pattern": {"type": "string"}, "path": {"type": "string"}},
                           "required": ["pattern"]}
        }
    },
    {
        "type": "function",
        "function": {
            "name": "read_project_structure",
            "description": "查看项目目录结构，并展示代码文件内的类与方法概览。适合定位项目组织、快速了解有哪些模块。",
            "parameters": {"type": "object", "properties": {"path": {"type": "string", "description": "相对工作区的目录路径，默认 '.'"}}}
        }
    },
    {
        "type": "function",
        "function": {
            "name": "get_symbol_info",
            "description": "查询符号（类名或方法名/字段名）的定义位置、签名、方法列表与继承关系。适合了解某个类或方法在哪里定义。",
            "parameters": {"type": "object", "properties": {"symbol": {"type": "string", "description": "类名或方法名/字段名"}},
                           "required": ["symbol"]}
        }
    },
    {
        "type": "function",
        "function": {
            "name": "find_usages",
            "description": "查找某个符号（方法/类/字段）在项目中的所有引用位置。重构或修改前先调用此工具评估影响范围。",
            "parameters": {"type": "object", "properties": {"symbol": {"type": "string", "description": "方法名/类名/字段名"}},
                           "required": ["symbol"]}
        }
    },
    {
        "type": "function",
        "function": {
            "name": "read_file_region",
            "description": "按行号区间读取大文件的一部分，避免整文件读取占用过多上下文。",
            "parameters": {"type": "object", "properties": {
                "path": {"type": "string", "description": "相对工作区的文件路径"},
                "startLine": {"type": "integer", "description": "起始行号（含），默认 1"},
                "endLine": {"type": "integer", "description": "结束行号（含），默认到文件末尾"}
            }, "required": ["path"]}
        }
    }
]

LOCAL_TOOLS_ROUTER = {
    "get_weather": get_weather,
    "get_current_time": get_current_time,
    "search_code": search_code,
    "glob_files": glob_files,
    "read_project_structure": read_project_structure,
    "get_symbol_info": get_symbol_info,
    "find_usages": find_usages,
    "read_file_region": read_file_region
}
