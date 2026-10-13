"""skAPI 인게임 테스트용 로컬 HTTP 서버 (127.0.0.1:18080)."""
import json
import sys
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):
        sys.stdout.write("[api] " + (fmt % args) + "\n")
        sys.stdout.flush()

    def _send(self, status, body, content_type="application/json; charset=utf-8", extra=None):
        data = body.encode("utf-8") if isinstance(body, str) else body
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(data)))
        self.send_header("X-Test", "yes")
        for k, v in (extra or {}).items():
            self.send_header(k, v)
        self.end_headers()
        self.wfile.write(data)

    def _handle(self):
        url = urlparse(self.path)
        length = int(self.headers.get("Content-Length") or 0)
        body = self.rfile.read(length).decode("utf-8") if length else ""
        args = {k: v[0] for k, v in parse_qs(url.query).items()}

        if url.path == "/echo":
            self._send(200, json.dumps({
                "method": self.command,
                "path": url.path,
                "query": url.query,
                "args": args,
                "body": body,
                "headers": {k.lower(): v for k, v in self.headers.items()},
            }, ensure_ascii=False))
        elif url.path == "/slow":
            time.sleep(int(args.get("ms", "1000")) / 1000)
            self._send(200, json.dumps({"slow": True, "ms": int(args.get("ms", "1000"))}))
        elif url.path.startswith("/status/"):
            code = int(url.path.rsplit("/", 1)[1])
            self._send(code, json.dumps({"error": {"message": "status %d" % code}}))
        elif url.path == "/ai":
            self._send(200, json.dumps({
                "content": [{"type": "thinking", "thinking": ""}, {"type": "text", "text": "안녕하세요 플레이어!"}],
                "stop_reason": "end_turn",
                "usage": {"input_tokens": 10, "output_tokens": 5},
            }, ensure_ascii=False))
        elif url.path == "/users":
            self._send(200, json.dumps({
                "user": {"name": "Steve", "level": 7, "ratio": 0.5},
                "tags": ["a", "b", "c"],
                "items": [{"id": 1, "name": "sword"}, {"id": 2, "name": "shield"}],
            }))
        elif url.path == "/text":
            self._send(200, "hello text", "text/plain; charset=utf-8")
        elif url.path == "/redirect":
            self._send(302, "", extra={"Location": "/echo?from=redirect"})
        else:
            self._send(404, json.dumps({"error": "unknown path"}))

    do_GET = do_POST = do_PUT = do_PATCH = do_DELETE = _handle


if __name__ == "__main__":
    server = ThreadingHTTPServer(("127.0.0.1", 18080), Handler)
    print("[api] listening on 127.0.0.1:18080", flush=True)
    server.serve_forever()
