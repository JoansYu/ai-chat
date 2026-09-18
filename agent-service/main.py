import json
import logging
from fastapi import FastAPI
from fastapi.responses import StreamingResponse
from pydantic import BaseModel
from typing import List, Dict, Any, Optional
from contextlib import asynccontextmanager

from config import logger, DEFAULT_WORKSPACE, HOST, PORT, DEFAULT_MAX_STEPS
from mcp_manager import mcp_manager
from agent_graph import run_langgraph_agent, run_langgraph_agent_stream


# 生命周期管理：启动时自动连接 MCP，停止时优雅断开
@asynccontextmanager
async def lifespan(app: FastAPI):
    await mcp_manager.start(DEFAULT_WORKSPACE)
    yield
    await mcp_manager.stop()


app = FastAPI(lifespan=lifespan)


class ChatRequest(BaseModel):
    user_input: str
    history: List[Dict[str, Any]] = []
    workspace_root: Optional[str] = ""
    java_callback_url: Optional[str] = ""
    user_token: Optional[str] = ""
    max_steps: Optional[int] = DEFAULT_MAX_STEPS


@app.get("/health")
async def health():
    return {"status": "UP", "service": "FastAPI + LangGraph + MCP Agent Engine"}


@app.post("/api/v1/agent/chat")
async def agent_chat(req: ChatRequest):
    if not req.user_input:
        return {"status": "error", "message": "user_input 不能为空"}

    ctx = {
        "workspace_root": req.workspace_root,
        "java_callback_url": req.java_callback_url,
        "user_token": req.user_token
    }

    try:
        result = await run_langgraph_agent(req.user_input, req.history, ctx, req.max_steps)
        return result
    except Exception as e:
        logger.error(f"❌ [Chat] 异常: {e}")
        return {"status": "error", "message": f"Agent 执行异常: {str(e)}"}


@app.post("/api/v1/agent/stream")
async def agent_stream(req: ChatRequest):
    ctx = {
        "workspace_root": req.workspace_root,
        "java_callback_url": req.java_callback_url,
        "user_token": req.user_token
    }
    logger.info(f"📥 [Stream] user_input={req.user_input!r}")

    async def event_generator():
        try:
            event_count = 0
            async for event in run_langgraph_agent_stream(req.user_input, req.history, ctx, req.max_steps):
                event_count += 1
                yield f"data: {json.dumps(event, ensure_ascii=False)}\n\n"

            logger.info(f"✅ [Stream] 完成, 共推送 {event_count} 个事件")
        except Exception as e:
            logger.error(f"❌ [Stream] 异常: {e}")
            err = {"type": "error", "message": f"Agent 执行异常: {str(e)}"}
            yield f"data: {json.dumps(err, ensure_ascii=False)}\n\n"

    return StreamingResponse(event_generator(), media_type="text/event-stream", headers={
        'Cache-Control': 'no-cache',
        'X-Accel-Buffering': 'no'
    })


if __name__ == '__main__':
    import uvicorn
    uvicorn.run("main:app", host=HOST, port=PORT, reload=True)
