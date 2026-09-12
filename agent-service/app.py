from flask import Flask, request, jsonify
from agent_engine import run_agent_loop

app = Flask(__name__)


@app.route('/health', methods=['GET'])
def health():
    return jsonify({"status": "UP", "service": "Python Agent Engine"})


@app.route('/api/v1/agent/chat', methods=['POST'])
def agent_chat():
    """
    供Java调度的核心 API 接口
    接收 Payload 格式：
    {
        "messages": [
            {"role": "system", "content": "..."},
            {"role": "user", "content": "..."}
        ],
        "max_steps": 5
    }
    :return:
    """
    data = request.get_json() or {}
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


if __name__ == '__main__':
    # 监听在 8000 端口（避开特权端口，方便内部微服务互调）
    print("🚀 Agent Engine Started on http://0.0.0.0:8000")
    app.run(host='0.0.0.0', port=8000, debug=True)

