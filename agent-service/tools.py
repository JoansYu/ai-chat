import json
from datetime import datetime


# =================
# 业务工具实现
# ================
def get_weather(location: str) -> str:
    """查询指定城市的天气"""
    print(f"🛠️ [Tools] 正在执行天气查询: {location}")
    # 模拟真实 API 返回
    return json.dumps({"location": location, "weather": "Sunny", "temperature": "25C"})


def get_current_time() -> str:
    """获取当前真实时间"""
    print("🛠️ [Tools] 正在获取系统时间...")

    # 1. 获取当前系统真实时间
    now = datetime.now()

    # 2. 将时间对象格式化为指定的字符串样式
    return now.strftime("%Y-%m-%d %H:%M:%S")


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
                    "location": {
                        "type": "string",
                        "description": "城市名称，如：成都, 北京"
                    },
                    "required": ["location"]
                }

            }
        }
    },
    {
        "type": "function",
        "function": {
            "name": "get_current_time",
            "description": "当用户询问当前时间、现在几点、今天日期时调用此工具",
            "parameters": {
                "type": "object",
                "properties": {}  # 🔑 必须保留为空对象，规避 500 错误
            }
        }
    }
]

TOOLS_ROUTER = {
    "get_weather": get_weather,
    "get_current_time": get_current_time
}
