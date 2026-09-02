#!/usr/bin/env python3
"""DolphinScheduler UI 静态服务 + API 反向代理（无 nginx 环境的轻量替代）。

- /IndustrialManagementPlatform/ui/**  -> 本地 ui 目录静态文件
- /IndustrialManagementPlatform/**     -> 转发到后端 API (127.0.0.1:12345)
"""
import os
import sys
import urllib.request
import urllib.error
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

UI_DIR = "/opt/dolphinscheduler/api-server/ui"
BACKEND = "http://127.0.0.1:12345"
LISTEN_PORT = 8888

MIME = {
    ".html": "text/html; charset=utf-8",
    ".js": "application/javascript; charset=utf-8",
    ".css": "text/css; charset=utf-8",
    ".json": "application/json; charset=utf-8",
    ".png": "image/png",
    ".jpg": "image/jpeg",
    ".jpeg": "image/jpeg",
    ".gif": "image/gif",
    ".svg": "image/svg+xml",
    ".ico": "image/x-icon",
    ".woff": "font/woff",
    ".woff2": "font/woff2",
    ".ttf": "font/ttf",
    ".map": "application/json",
}

HOP_BY_HOP = {
    "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
    "te", "trailers", "transfer-encoding", "upgrade", "host", "content-length",
}


def proxy_request(method, path, headers, body):
    req = urllib.request.Request(BACKEND + path, data=body, method=method)
    for k, v in headers.items():
        if k.lower() not in HOP_BY_HOP:
            req.add_header(k, v)
    try:
        resp = urllib.request.urlopen(req, timeout=120)
        return resp.status, dict(resp.headers), resp.read()
    except urllib.error.HTTPError as e:
        return e.code, dict(e.headers), e.read()
    except Exception as e:
        return 502, {"Content-Type": "text/plain; charset=utf-8"}, str(e).encode()


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):
        sys.stderr.write("%s - %s\n" % (self.address_string(), fmt % args))

    def _send(self, status, ctype, data):
        self.send_response(status)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def _serve_ui(self):
        prefix = "/IndustrialManagementPlatform/ui/"
        rel = self.path[len(prefix):].split("?", 1)[0].split("#", 1)[0]
        if rel in ("", "/"):
            rel = "index.html"
        file_path = os.path.normpath(os.path.join(UI_DIR, rel))
        if not file_path.startswith(os.path.normpath(UI_DIR)):
            self.send_error(403)
            return
        if not os.path.isfile(file_path):
            file_path = os.path.join(UI_DIR, "index.html")
        ext = os.path.splitext(file_path)[1].lower()
        with open(file_path, "rb") as f:
            data = f.read()
        self._send(200, MIME.get(ext, "application/octet-stream"), data)

    def _proxy(self):
        length = int(self.headers.get("Content-Length", 0) or 0)
        body = self.rfile.read(length) if length else None
        path = self.path
        if path.startswith("/IndustrialManagementPlatform"):
            path = "/dolphinscheduler" + path[len("/IndustrialManagementPlatform"):]
        status, headers, data = proxy_request(
            self.command, path, self.headers, body
        )
        self.send_response(status)
        for k, v in headers.items():
            if k.lower() not in HOP_BY_HOP:
                self.send_header(k, v)
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def _dispatch(self):
        if self.path.startswith("/IndustrialManagementPlatform/ui/"):
            self._serve_ui()
        else:
            self._proxy()

    def do_GET(self):
        self._dispatch()

    def do_HEAD(self):
        self._dispatch()

    def do_POST(self):
        self._dispatch()

    def do_PUT(self):
        self._dispatch()

    def do_DELETE(self):
        self._dispatch()

    def do_PATCH(self):
        self._dispatch()

    def do_OPTIONS(self):
        self._proxy()


if __name__ == "__main__":
    server = ThreadingHTTPServer(("0.0.0.0", LISTEN_PORT), Handler)
    print("UI proxy listening on http://localhost:%d/IndustrialManagementPlatform/ui/" % LISTEN_PORT, flush=True)
    server.serve_forever()
