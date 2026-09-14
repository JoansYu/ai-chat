import json
import os
import requests

# ====================================================
# 配置：Java 后端回调地址和用户 Token（由 app.py 从请求体中注入环境变量）
# ====================================================

def _get_callback_url():
    return os.getenv("JAVA_CALLBACK_URL", "")


def _get_user_token():
    return os.getenv("USER_TOKEN", "")


def _call_java_tool(tool_name: str, args: dict) -> str:
    """通过 HTTP 回调 Java 后端执行文件工具（Java 转发到本地客户端执行）"""
    callback_url = _get_callback_url()
    token = _get_user_token()

    if not callback_url:
        return "错误：未配置 Java 回调地址，无法执行文件工具"

    try:
        resp = requests.post(
            f"{callback_url}/api/agent/tools/execute",
            json={"tool": tool_name, "args": args},
            headers={"satoken": token},
            timeout=35
        )
        if resp.status_code != 200:
            return f"Java 回调失败 HTTP {resp.status_code}: {resp.text}"

        data = resp.json()
        if data.get("code") == 200:
            result = data.get("data", {}).get("result", "")
            return result if result else "（空结果）"
        else:
            return f"工具执行失败: {data.get('message', '未知错误')}"
    except Exception as e:
        return f"回调 Java 异常: {str(e)}"


# ====================================================
# 业务工具实现（原有）
# ====================================================
def get_weather(location: str) -> str:
    """查询指定城市的天气"""
    print(f"[Tools] 正在执行天气查询: {location}")
    return json.dumps({"location": location, "weather": "Sunny", "temperature": "25C"})


def get_current_time() -> str:
    """获取当前真实时间"""
    print("[Tools] 正在获取系统时间...")
    try:
        import datetime
        utc_now = datetime.datetime.now(datetime.timezone.utc)
        beijing_now = utc_now + datetime.timedelta(hours=8)
        time_str = beijing_now.strftime("%Y-%m-%d %H:%M:%S")
        return f"{time_str} (UTC+8 北京时间)"
    except Exception as e:
        return "系统时间组件暂时不可用"


# ====================================================
# 代码读取工具（通过 HTTP 回调 Java → 本地客户端执行）
# ====================================================
def read_file(path: str) -> str:
    """读取指定文件内容（带行号），通过 Java 回调到本地客户端执行"""
    return _call_java_tool("read_file", {"path": path})


def list_directory(path: str = ".") -> str:
    """列出指定目录的树形结构，通过 Java 回调到本地客户端执行"""
    return _call_java_tool("list_directory", {"path": path})


def search_code(pattern: str, path: str = ".") -> str:
    """正则搜索代码内容，通过 Java 回调到本地客户端执行"""
    return _call_java_tool("search_code", {"pattern": pattern, "path": path})


def glob_files(pattern: str, path: str = ".") -> str:
    """按文件名通配模式匹配文件，通过 Java 回调到本地客户端执行"""
    return _call_java_tool("glob_files", {"pattern": pattern, "path": path})


# ====================================================
# OPENAI Function Calling Schema 声明
# ====================================================
TOOLS_SCHEMA = [
    {
        "type": "function",
        "function": {
            "name": "get_weather",
            "description": "获取指定城市的天气信息",
            "parameters": {
                "type": "object",
                "properties": {
                    "location": {"type": "string", "description": "城市名称，如：成都, 北京"}
                },
                "required": ["location"]
            }
        }
    },
    {
        "type": "function",
        "function": {
            "name": "get_current_time",
            "description": "当用户询问当前时间、现在几点、今天日期时调用此工具",
            "parameters": {"type": "object", "properties": {}}
        }
    },
    {
        "type": "function",
        "function": {
            "name": "read_file",
            "description": "读取用户已授权工作区中的指定文件内容（带行号）。path 为相对于工作区根目录的相对路径，如 'src/main/java/com/aichat/controller/AgentController.java'",
            "parameters": {
                "type": "object",
                "properties": {
                    "path": {"type": "string", "description": "相对于工作区根目录的文件路径"}
                },
                "required": ["path"]
            }
        }
    },
    {
        "type": "function",
        "function": {
            "name": "list_directory",
            "description": "列出用户已授权工作区中指定目录的树形结构。path 默认 '.' 表示工作区根目录",
            "parameters": {
                "type": "object",
                "properties": {
                    "path": {"type": "string", "description": "相对于工作区根目录的目录路径，默认 '.'"}
                }
            }
        }
    },
    {
        "type": "function",
        "function": {
            "name": "search_code",
            "description": "在用户已授权工作区中正则搜索代码内容，返回 文件路径:行号:行内容",
            "parameters": {
                "type": "object",
                "properties": {
                    "pattern": {"type": "string", "description": "正则表达式搜索模式"},
                    "path": {"type": "string", "description": "搜索范围，默认 '.' 搜索整个工作区"}
                },
                "required": ["pattern"]
            }
        }
    },
    {
        "type": "function",
        "function": {
            "name": "glob_files",
            "description": "按文件名通配模式匹配文件列表，如 '**/*.java' 匹配所有 Java 文件",
            "parameters": {
                "type": "object",
                "properties": {
                    "pattern": {"type": "string", "description": "glob 通配模式"},
                    "path": {"type": "string", "description": "搜索起始目录，默认 '.'"}
                },
                "required": ["pattern"]
            }
        }
    }
]

TOOLS_ROUTER = {
    "get_weather": get_weather,
    "get_current_time": get_current_time,
    "read_file": read_file,
    "list_directory": list_directory,
    "search_code": search_code,
    "glob_files": glob_files
}
