import os
from contextlib import AsyncExitStack
from mcp import ClientSession, StdioServerParameters
from mcp.client.stdio import stdio_client


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
        read_stream, write_stream = await self._exit_stack.enter_async_context(stdio_client(server_params))
        self.session = await self._exit_stack.enter_async_context(ClientSession(read_stream, write_stream))
        await self.session.initialize()

        # 缓存 MCP 提供的工具 Schema
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
        print(f"✅ [MCP] Filesystem Server 已挂载至: {work_dir}")

    async def stop(self):
        await self._exit_stack.aclose()
        print("🛑 [MCP] 服务已安全关闭")


# 全局单例
mcp_manager = GlobalMCPManager()