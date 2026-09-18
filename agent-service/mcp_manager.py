import os
import logging
from contextlib import AsyncExitStack
from mcp import ClientSession, StdioServerParameters
from mcp.client.stdio import stdio_client

logger = logging.getLogger("agent-service.mcp")


class GlobalMCPManager:
    def __init__(self):
        self.session: ClientSession = None
        self._exit_stack = AsyncExitStack()
        self.mcp_schemas = []
        self.mcp_tool_names = set()

    async def start(self, work_dir: str):
        """服务启动时拉起 MCP 进程"""
        os.makedirs(work_dir, exist_ok=True)
        server_params = StdioServerParameters(
            command="npx",
            args=["-y", "@modelcontextprotocol/server-filesystem", work_dir]
        )
        try:
            read_stream, write_stream = await self._exit_stack.enter_async_context(
                stdio_client(server_params)
            )
            self.session = await self._exit_stack.enter_async_context(
                ClientSession(read_stream, write_stream)
            )
            await self.session.initialize()

            tools_resp = await self.session.list_tools()
            self.mcp_schemas = [{
                "type": "function",
                "function": {
                    "name": t.name,
                    "description": t.description,
                    "parameters": t.input_schema
                }
            } for t in tools_resp.tools]
            self.mcp_tool_names = {t.name for t in tools_resp.tools}
            logger.info(f"✅ [MCP] Filesystem Server 已挂载至: {work_dir}")
            logger.info(f"✅ [MCP] 可用工具: {self.mcp_tool_names}")
        except Exception as e:
            logger.error(f"❌ [MCP] 启动失败: {e}")
            raise

    async def stop(self):
        """服务停止时安全关闭 MCP"""
        try:
            await self._exit_stack.aclose()
            logger.info("🛑 [MCP] 服务已安全关闭")
        except Exception as e:
            logger.error(f"❌ [MCP] 关闭异常: {e}")


# 全局单例
mcp_manager = GlobalMCPManager()
