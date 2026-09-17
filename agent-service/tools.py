import json
import httpx
import datetime


# ====================================================
# 异步基础工具
# ====================================================
async def get_weather(location: str, ctx: dict) -> str:
    print(f"[Tools] 正在查询天气: {location}")
    return json.dumps({"location": location, "weather": "Sunny", "temperature": "25C"})


async def get_current_time(ctx: dict) -> str:
    print("[Tools] 获取系统时间")
    utc_now = datetime.datetime.now(datetime.timezone.utc)
    beijing_now = utc_now + datetime.timedelta(hours=8)
    return f"{beijing_now.strftime('%Y-%m-%d %H:%M:%S')} (UTC+8)"


# ====================================================
# Java 回调工具 (保留兼容，转为异步)
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


# Java 提供的高级搜索能力保留
async def search_code(pattern: str, path: str = ".", ctx: dict = None) -> str:
    return await _call_java_tool_async("search_code", {"pattern": pattern, "path": path}, ctx)


async def glob_files(pattern: str, path: str = ".", ctx: dict = None) -> str:
    return await _call_java_tool_async("glob_files", {"pattern": pattern, "path": path}, ctx)


# ====================================================
# 声明本地 Schema (注意：read_file 等基础文件操作将由 MCP 提供，无需在此声明)
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
            "parameters": {
                "type": "object",
                "properties": {}
            }
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
    }
]

LOCAL_TOOLS_ROUTER = {
    "get_weather": get_weather,
    "get_current_time": get_current_time,
    "search_code": search_code,
    "glob_files": glob_files
}
