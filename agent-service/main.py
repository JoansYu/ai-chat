from flask import Flask, request, jsonify, Response
from agent_engine import run_agent_loop, run_agent_loop_stream
import json
import os

app = Flask(__name__)


def _setup_workspace(data):
    """从请求体中提取 workspace_root、java_callback_url、user_token 并设为环境变量"""
    workspace = data.get("workspace_root", "")
    callback_url = data.get("java_callback_url", "")
    user_token = data.get("user_token", "")

    os.environ["WORKSPACE_ROOT"] = workspace
    os.environ["JAVA_CALLBACK_URL"] = callback_url
    os.environ["USER_TOKEN"] = user_token

    if workspace:
        print(f"📁 [Workspace] 已设置授权工作区: {workspace}")
    if callback_url:
        print(f"🔗 [Callback] Java 回调地址: {callback_url}")
    if user_token:
        print(f"🔑 [Auth] 用户 Token 已注入")


@app.route('/health', methods=['GET'])
def health():
    return jsonify({"status": "UP", "service": "Python Agent Engine"})


@app.route('/api/v1/agent/chat', methods=['POST'])
def agent_chat():
    """供Java调度的核心 API 接口（非流式）"""
    data = request.get_json() or {}
    _setup_workspace(data)
    messages = data.get("messages", [])
    max_steps = data.get("max_steps", 5)

    if not messages:
        return jsonify({"status": "error", "message": "message 不能为空"}), 200
    try:
        result = run_agent_loop(input_messages=messages, max_steps=max_steps)
        return jsonify(result), 200
    except Exception as e:
        print(f"❌ [Error] 执行过程抛出异常: {str(e)}")
        return jsonify({
            "status": "error",
            "message": f"Agent 执行异常: {str(e)}"
        }), 500


@app.route('/api/v1/agent/stream', methods=['POST'])
def agent_stream():
    """流式 Agent 接口（SSE）"""
    data = request.get_json() or {}
    _setup_workspace(data)
    messages = data.get("messages", [])
    max_steps = data.get("max_steps", 5)

    if not messages:
        return jsonify({"status": "error", "message": "message 不能为空"}), 200

    def generate():
        try:
            for event in run_agent_loop_stream(input_messages=messages, max_steps=max_steps):
                yield f"data: {json.dumps(event, ensure_ascii=False)}\n\n"
        except Exception as e:
            error_event = {"type": "error", "message": f"Agent 执行异常: {str(e)}"}
            yield f"data: {json.dumps(error_event, ensure_ascii=False)}\n\n"

    return Response(generate(), mimetype='text/event-stream',
                    headers={'Cache-Control': 'no-cache', 'X-Accel-Buffering': 'no'})


if __name__ == '__main__':
    print("🚀 Agent Engine Started on http://0.0.0.0:8000")
    app.run(host='0.0.0.0', port=8000, debug=True, threaded=True)

