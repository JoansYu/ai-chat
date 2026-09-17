import json
from fastapi import FastAPI
from fastapi.responses import StreamingResponse
from pydantic import BaseModel
from typing import List, Dict, Any, Optional
from contextlib import asynccontextmanager

from agent_engine import run_smart_agent_loop, run_smart_agent_loop_stream
from mcp_manager import mcp_manager


# 💡 生命周期管理：启动时自动连接 MCP，停止时优雅断开
@asynccontextmanager
async def lifespan(app: FastAPI):
    # 这里设置默认挂载的公共安全工作区
    await mcp_manager.start("/tmp/agent_workspace")
    yield
    await mcp_manager.stop()


app = FastAPI(lifespan=lifespan)


# 定义强类型的 HTTP 请求体格式
class ChatRequest(BaseModel):
    user_input: str
    history: List[Dict[str, Any]] = []
    workspace_root: Optional[str] = ""
    java_callback_url: Optional[str] = ""
    user_token: Optional[str] = ""
    max_steps: Optional[int] = 10


@app.get("/health")
async def health():
    return {"status": "UP", "service": "FastAPI + MCP Agent Engine"}


@app.post("/api/v1/agent/chat")
async def agent_chat(req: ChatRequest):
    if not req.user_input:
        return {"status": "error", "message": "user_input 不能为空"}

    ctx = {
        "workspace_root": req.workspace_root,
        "java_callback_url": req.java_callback_url,
        "user_token": req.user_token
    }
    print(f"📥 [Chat-非流式] user_input={req.user_input!r}, history={len(req.history)}条, max_steps={req.max_steps}")
    print(f"📥 [Chat-非流式] workspace_root={req.workspace_root!r}, callback={req.java_callback_url!r}, token={'有' if req.user_token else '无'}")

    try:
        result = await run_smart_agent_loop(req.user_input, req.history, req.max_steps, ctx)
        print(f"✅ [Chat-非流式] 完成, status={result.get('status')}, 回复长度={len(result.get('final_answer',''))}")
        return result
    except Exception as e:
        print(f"❌ [Chat-非流式] 异常: {str(e)}")
        return {"status": "error", "message": f"Agent 执行异常: {str(e)}"}


@app.post("/api/v1/agent/stream")
async def agent_stream(req: ChatRequest):
    ctx = {
        "workspace_root": req.workspace_root,
        "java_callback_url": req.java_callback_url,
        "user_token": req.user_token
    }
    print(f"📥 [Stream] user_input={req.user_input!r}, history={len(req.history)}条, max_steps={req.max_steps}")
    print(f"📥 [Stream] workspace_root={req.workspace_root!r}, callback={req.java_callback_url!r}, token={'有' if req.user_token else '无'}")

    async def event_generator():
        try:
            event_count = 0
            async for event in run_smart_agent_loop_stream(req.user_input, req.history, req.max_steps, ctx):
                event_count += 1
                etype = event.get("type", "?")
                brief = json.dumps(event, ensure_ascii=False)
                if len(brief) > 120:
                    brief = brief[:120] + "..."
                print(f"📤 [Stream#{event_count}] 事件: {brief}")
                yield f"data: {json.dumps(event, ensure_ascii=False)}\n\n"
            print(f"✅ [Stream] 完成, 共推送 {event_count} 个事件")
        except Exception as e:
            print(f"❌ [Stream] 异常: {str(e)}")
            err = {"type": "error", "message": f"Agent 执行异常: {str(e)}"}
            yield f"data: {json.dumps(err, ensure_ascii=False)}\n\n"

    return StreamingResponse(event_generator(), media_type="text/event-stream", headers={
        'Cache-Control': 'no-cache',
        'X-Accel-Buffering': 'no'
    })


if __name__ == '__main__':
    import uvicorn

    # 通过 uvicorn 启动，支持热重载
    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=True)