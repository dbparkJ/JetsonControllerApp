#!/usr/bin/env bash
set -euo pipefail

if [[ "${EUID}" -ne 0 ]]; then
  echo "Run as root: sudo $0 <https-base-url> <token-file> [label]" >&2
  exit 1
fi

if [[ "$#" -lt 2 || "$#" -gt 3 ]]; then
  echo "Usage: sudo $0 <https-base-url> <token-file> [label]" >&2
  exit 2
fi

base_url="$1"
token_file="$2"
label="${3:-External upload}"
config_path="/etc/jetson-control/upload_targets.json"

if [[ ! -f "${token_file}" ]]; then
  echo "Token file does not exist: ${token_file}" >&2
  exit 2
fi

python3 - "${base_url}" "${token_file}" "${label}" "${config_path}" <<'PY'
import json
import ipaddress
import os
import sys
from urllib.parse import urlsplit

base_url, token_file, label, config_path = sys.argv[1:]
base_url = base_url.rstrip("/")
parsed = urlsplit(base_url)
if (
    parsed.scheme != "https"
    or not parsed.hostname
    or parsed.username is not None
    or parsed.password is not None
    or parsed.query
    or parsed.fragment
):
    raise SystemExit("The upload receiver must be a valid HTTPS base URL")
if parsed.hostname.lower() == "localhost" or parsed.hostname.lower().endswith(".local"):
    raise SystemExit("The upload receiver must be reachable through the public internet")
try:
    address = ipaddress.ip_address(parsed.hostname)
except ValueError:
    address = None
if address is not None and not address.is_global:
    raise SystemExit("The upload receiver IP address must be public")
if not label.strip():
    raise SystemExit("The upload target label cannot be empty")

token_file = os.path.realpath(token_file)
with open(token_file, "r", encoding="utf-8") as source:
    token = source.read().strip()
if not token or len(token) > 4096 or "\n" in token:
    raise SystemExit("The token file does not contain a valid single-line token")

token_destination = os.path.join(os.path.dirname(config_path), "upload-receiver.token")
token_temporary = token_destination + ".tmp"
with open(token_temporary, "w", encoding="utf-8") as output:
    output.write(token)
    output.write("\n")
    output.flush()
    os.fsync(output.fileno())
os.chmod(token_temporary, 0o600)
os.replace(token_temporary, token_destination)

value = {
    "external": {
        "label": label.strip(),
        "type": "http",
        "base_url": base_url,
        "token_file": token_destination,
        "verify_tls": True,
    }
}
temporary = config_path + ".tmp"
with open(temporary, "w", encoding="utf-8") as output:
    json.dump(value, output, indent=2)
    output.write("\n")
    output.flush()
    os.fsync(output.fileno())
os.chmod(temporary, 0o600)
os.replace(temporary, config_path)
PY

systemctl restart jetson-control-api.service
echo "External upload target configured."
