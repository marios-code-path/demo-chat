#!/usr/bin/env python3
"""A synthetic OpenAI embeddings endpoint.

The gate runs production client code against this process. OpenAiEmbeddingModel
builds, serializes a request, reads a response, and returns vectors. So the
client path is real, and only the service is synthetic.

The provider exists to serve any OpenAI-compatible endpoint, so a compatible
endpoint is a valid subject.

The vectors are deterministic character bigrams. Two texts that share
substrings score higher than two that do not. That is enough for one search to
return a ranked answer.

The process ignores the Authorization header. The gate needs no secret key.
Project policy requires a key value, so the launch supplies a dummy.
"""

import json
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

DIMENSIONS = 256


def bigram_vector(text):
    vector = [0.0] * DIMENSIONS
    padded = " " + text.lower() + " "
    for i in range(len(padded) - 1):
        slot = (ord(padded[i]) * 128 + ord(padded[i + 1])) % DIMENSIONS
        vector[slot] += 1.0
    norm = sum(v * v for v in vector) ** 0.5
    if norm > 0:
        vector = [v / norm for v in vector]
    return vector


class Handler(BaseHTTPRequestHandler):

    # HTTP/1.1, and a thread for each connection. Reactor Netty keeps a
    # connection pool. A HTTP/1.0 server closes after each answer, and the
    # next request on that pooled connection fails with "Connection
    # prematurely closed BEFORE response". The client then retries, and the
    # default policy needs 19 minutes to give up. Measured on 2026-09-14.
    #
    # Every answer below carries Content-Length, which HTTP/1.1 needs to hold
    # the connection open.
    protocol_version = "HTTP/1.1"

    def read_body(self):
        """Reads one request body, in either framing.

        The client sends a chunked body. RestClient streams the request, and it
        then sets Transfer-Encoding rather than Content-Length. A read of
        Content-Length alone returns zero bytes, and this process answers a
        vector for no input at all. The caller then reads an empty list and
        fails inside dimensions(). Measured on 2026-09-14.
        """
        if "chunked" in self.headers.get("Transfer-Encoding", "").lower():
            chunks = []
            while True:
                size = int(self.rfile.readline().split(b";")[0], 16)
                if size == 0:
                    self.rfile.readline()
                    break
                chunks.append(self.rfile.read(size))
                self.rfile.read(2)
            return b"".join(chunks)

        length = int(self.headers.get("Content-Length", "0"))
        return self.rfile.read(length) if length > 0 else b""

    def do_POST(self):
        body = json.loads(self.read_body() or b"{}")

        given = body.get("input", [])
        texts = [given] if isinstance(given, str) else list(given)

        payload = {
            "object": "list",
            "model": body.get("model", "stub-embedding"),
            "data": [
                {"object": "embedding", "index": i, "embedding": bigram_vector(t)}
                for i, t in enumerate(texts)
            ],
            "usage": {"prompt_tokens": 0, "total_tokens": 0},
        }

        sys.stderr.write(
            "stub: model=%s inputs=%d vectors=%d width=%d\n"
            % (body.get("model"), len(texts), len(payload["data"]), DIMENSIONS)
        )

        encoded = json.dumps(payload).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(encoded)))
        self.end_headers()
        self.wfile.write(encoded)

    def log_message(self, fmt, *args):
        sys.stderr.write("stub: " + (fmt % args) + "\n")


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 9099
    ThreadingHTTPServer(("127.0.0.1", port), Handler).serve_forever()
