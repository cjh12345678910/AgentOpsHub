import argparse
import json
from dataclasses import asdict
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Any, Dict, Optional

from config_loader import load_env_file
from runner import AgentRunner


class AgentHandler(BaseHTTPRequestHandler):
    runner: Optional[AgentRunner] = None

    def do_POST(self) -> None:
        if self.path != "/agent/run":
            self._json_response(404, {"code": "NOT_FOUND", "message": "Path not found"})
            return

        try:
            payload = self._read_json()
        except ValueError as exc:
            self._json_response(400, {"code": "BAD_REQUEST", "message": str(exc)})
            return

        result = self._get_runner().run(payload)
        self._json_response(200, asdict(result))

    def do_GET(self) -> None:
        if self.path == "/agent/health":
            self._json_response(200, {"status": "ok"})
            return
        self._json_response(404, {"code": "NOT_FOUND", "message": "Path not found"})

    def log_message(self, fmt: str, *args: Any) -> None:
        # 避免默认日志过于噪声，保留服务端输出简洁可读。
        return

    def _read_json(self) -> Dict:
        raw_len = self.headers.get("Content-Length", "0")
        try:
            size = int(raw_len)
        except ValueError as exc:
            raise ValueError("Invalid Content-Length") from exc
        if size <= 0:
            raise ValueError("Empty request body")
        raw = self.rfile.read(size)
        try:
            payload = json.loads(raw.decode("utf-8"))
        except json.JSONDecodeError as exc:
            raise ValueError("Invalid JSON payload") from exc
        if not isinstance(payload, dict):
            raise ValueError("JSON payload must be an object")
        return payload

    def _json_response(self, status: int, payload: Dict) -> None:
        data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    @classmethod
    def _get_runner(cls) -> AgentRunner:
        if cls.runner is None:
            cls.runner = AgentRunner()
        return cls.runner


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=18080)
    parser.add_argument("--env-file", default=".env")
    args = parser.parse_args()

    env_file = load_env_file(args.env_file, override=False)
    server = ThreadingHTTPServer((args.host, args.port), AgentHandler)
    print(f"Agent runtime listening on {args.host}:{args.port} (env: {env_file})")
    server.serve_forever()


if __name__ == "__main__":
    main()
