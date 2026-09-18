import os
import logging

# ==================== 大模型配置 ====================
OPENAI_API_KEY = os.getenv("OPENAI_API_KEY", "sk-MdlbuEkY2zK3Zz7bY0D81fSjCLQjBus16n88WDVt1edhXyMo")
OPENAI_BASE_URL = os.getenv("OPENAI_BASE_URL", "http://models.ascend.huawei.com/v1")
OPENAI_MODEL = os.getenv("OPENAI_MODEL", "deepseek-v4-flash")
TEMPERATURE = float(os.getenv("TEMPERATURE", "0.2"))

# ==================== 服务配置 ====================
HOST = os.getenv("HOST", "0.0.0.0")
PORT = int(os.getenv("PORT", "8000"))
DEFAULT_WORKSPACE = os.getenv("AGENT_WORKSPACE", "/tmp/agent_workspace")
DEFAULT_MAX_STEPS = int(os.getenv("MAX_STEPS", "10"))

# ==================== 日志配置 ====================
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S"
)
logger = logging.getLogger("agent-service")
