#!/usr/bin/env bash
set -euo pipefail

if [[ "${EUID}" -ne 0 ]]; then
  echo "Run as root: sudo $0 [device-name]" >&2
  exit 1
fi

config_dir="/etc/jetson-control"
state_dir="/var/lib/jetson-control"
config_file="${config_dir}/device.json"
uri_file="${state_dir}/jetson-pairing-uri.txt"
qr_file="${state_dir}/jetson-pairing-qr.png"

install -d -m 0700 -o root -g root "${config_dir}" "${state_dir}"

if [[ -e "${config_file}" ]]; then
  echo "Refusing to replace existing device identity: ${config_file}" >&2
  exit 1
fi

device_id="$(cat /proc/sys/kernel/random/uuid)"
secret_hex="$(openssl rand -hex 32)"
short_id="$(printf '%s' "${device_id}" | tr -d '-' | tail -c 4 | tr '[:lower:]' '[:upper:]')"
device_name="${1:-MMS-${short_id}}"
if [[ -z "${device_name}" || "${#device_name}" -gt 64 || "${device_name}" == *$'\n'* || "${device_name}" == *$'\r'* ]]; then
  echo "Device name must contain 1 to 64 characters without newlines." >&2
  exit 2
fi

DEVICE_ID="${device_id}" DEVICE_NAME="${device_name}" SECRET_HEX="${secret_hex}" \
  python3 - "${config_file}" <<'PY'
import json
import os
import sys

value = {
    "version": 1,
    "device_id": os.environ["DEVICE_ID"],
    "device_name": os.environ["DEVICE_NAME"],
    "bootstrap_secret_hex": os.environ["SECRET_HEX"],
    "controlled_services": [],
    "service_flags": {"camera": "", "lidar": "", "gnss": "", "mms": ""},
    "allow_power_commands": False,
    "wifi_interface": "wlan0",
}
with open(sys.argv[1], "x", encoding="utf-8") as output:
    json.dump(value, output, indent=2)
    output.write("\n")
PY
chmod 0600 "${config_file}"

pairing_uri="jetsonctl://pair?v=1&id=${device_id}&key=${secret_hex}"
printf '%s\n' "${pairing_uri}" >"${uri_file}"
chmod 0600 "${uri_file}"

if command -v qrencode >/dev/null 2>&1; then
  qrencode -o "${qr_file}" -s 8 -m 2 "${pairing_uri}"
  chmod 0600 "${qr_file}"
fi

echo "Provisioned ${device_name} (${device_id})."
echo "The pairing URI and QR image are credentials; keep them private."
