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
    ctx = ctx or {}
    callback_url = ctx.get("java_callback_url", "")
    token = ctx.get("user_token", "")

    if not callback_url:
        return "错误：未配置 Java 回调地址"

    try:
        async with httpx.AsyncClient() as client:
            resp = await client.post(
                f"{callback_url}/api/agent/tools/execute",
                json={"tool": tool_name, "args": args, "clientId": ctx.get("client_id")},
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
            "description": "查看当前项目的代码和配置文件结构；用 get_symbol_info 进一步查询 Java 类和方法。",
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


# These tools always execute through the authenticated Java callback.
# In IDE mode client_id routes the request to exactly one project window.
async def get_project_context(ctx: dict = None) -> str:
    return await _call_java_tool_async("get_project_context", {}, ctx)


async def read_file(path: str, ctx: dict = None) -> str:
    return await _call_java_tool_async("read_file", {"path": path}, ctx)


async def list_directory(path: str = ".", ctx: dict = None) -> str:
    return await _call_java_tool_async("list_directory", {"path": path}, ctx)


async def write_file(path: str, content: str, ctx: dict = None) -> str:
    return await _call_java_tool_async("write_file", {"path": path, "content": content}, ctx)


async def edit_file(path: str, old_string: str, new_string: str, ctx: dict = None) -> str:
    return await _call_java_tool_async("edit_file", {"path": path, "old_string": old_string, "new_string": new_string}, ctx)


async def rename_symbol(path: str, offset: int, new_name: str, ctx: dict = None) -> str:
    return await _call_java_tool_async("rename_symbol", {"path": path, "offset": offset, "new_name": new_name}, ctx)


def _schema(name, description, properties, required=()):
    return {"type": "function", "function": {"name": name, "description": description,
            "parameters": {"type": "object", "properties": properties, "required": list(required)}}}


LOCAL_TOOLS_SCHEMA.extend([
    _schema("get_project_context", "读取当前 IDEA 项目名称、根目录、活动文件、光标 UTF-16 offset 和选区；项目任务首先调用。", {}),
    _schema("read_file", "读取当前项目文件（含未保存编辑器内容），最多 400 行；后续使用 read_file_region。", {"path": {"type": "string"}}, ["path"]),
    _schema("list_directory", "列出相对当前项目的代码和配置文件，自动排除构建产物。", {"path": {"type": "string"}}),
    _schema("write_file", "生成一个新文件并创建父目录；已存在文件必须先读取后用 edit_file；IDE 支持撤销。", {"path": {"type": "string"}, "content": {"type": "string"}}, ["path", "content"]),
    _schema("edit_file", "在当前编辑器内容中唯一匹配 old_string 并替换；修改前必须读取，保留无关代码。", {"path": {"type": "string"}, "old_string": {"type": "string"}, "new_string": {"type": "string"}}, ["path", "old_string", "new_string"]),
    _schema("rename_symbol", "对 Java 声明启动 IDEA PSI 重命名预览，同步处理引用；用户需在预览窗口确认，工具返回不代表已应用。offset 为声明名称的零基 UTF-16 偏移，使用 get_symbol_info 获取。", {"path": {"type": "string"}, "offset": {"type": "integer", "minimum": 0}, "new_name": {"type": "string"}}, ["path", "offset", "new_name"]),
])
LOCAL_TOOLS_ROUTER.update({fn.__name__: fn for fn in [get_project_context, read_file, list_directory, write_file, edit_file, rename_symbol]})
