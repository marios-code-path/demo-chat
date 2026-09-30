#!/usr/bin/env python3
"""Mint one ES256 agent token from a JWK file.

Usage: agent-token.py <jwk-path> <client-id> <scope> [ttl-seconds]
"""

import base64
import json
import sys
import time
from pathlib import Path

from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives.asymmetric.utils import decode_dss_signature


def encoded(value: bytes) -> str:
    return base64.urlsafe_b64encode(value).rstrip(b"=").decode("ascii")


def main() -> int:
    if len(sys.argv) not in (4, 5):
        print(__doc__, file=sys.stderr)
        return 2

    jwk_path, client_id, scope = sys.argv[1:4]
    ttl = int(sys.argv[4]) if len(sys.argv) == 5 else 300
    key_data = json.loads(Path(jwk_path).read_text())
    if key_data.get("kty") != "EC" or key_data.get("crv") != "P-256":
        raise ValueError("the JWK must be an EC P-256 private key")
    private_value = int.from_bytes(
        base64.urlsafe_b64decode(key_data["d"] + "=="), "big"
    )
    key = ec.derive_private_key(private_value, ec.SECP256R1())

    now = int(time.time())
    header = {"alg": "ES256", "typ": "JWT"}
    if key_data.get("kid"):
        header["kid"] = key_data["kid"]
    claims = {
        "iss": "https://authserv",
        "sub": client_id,
        "client_id": client_id,
        "scope": scope,
        "iat": now,
        "exp": now + ttl,
    }
    encoded_header = encoded(json.dumps(header, separators=(",", ":")).encode())
    encoded_claims = encoded(json.dumps(claims, separators=(",", ":")).encode())
    signing_input = f"{encoded_header}.{encoded_claims}".encode("ascii")
    der_signature = key.sign(signing_input, ec.ECDSA(hashes.SHA256()))
    r, s = decode_dss_signature(der_signature)
    signature = r.to_bytes(32, "big") + s.to_bytes(32, "big")
    print(f"{encoded_header}.{encoded_claims}.{encoded(signature)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
