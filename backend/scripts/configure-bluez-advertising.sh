#!/usr/bin/env bash
set -euo pipefail

config_path="/etc/bluetooth/main.conf"
restart_services=true
custom_config=false

usage() {
  echo "Usage: sudo $0 [--config <main.conf>] [--no-restart]" >&2
}

while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --config)
      if [[ "$#" -lt 2 ]]; then
        usage
        exit 2
      fi
      config_path="$2"
      custom_config=true
      shift 2
      ;;
    --no-restart)
      restart_services=false
      shift
      ;;
    *)
      usage
      exit 2
      ;;
  esac
done

if [[ "${EUID}" -ne 0 && ("${custom_config}" != "true" || "${restart_services}" == "true") ]]; then
  echo "Run as root when changing the system BlueZ configuration or restarting services." >&2
  exit 1
fi

change_state="$(python3 - "${config_path}" <<'PY'
import os
import re
import shutil
import stat
import sys
import tempfile

path = os.path.abspath(sys.argv[1])
directory = os.path.dirname(path)
os.makedirs(directory, mode=0o755, exist_ok=True)

try:
    with open(path, "r", encoding="utf-8") as source:
        original = source.read()
    metadata = os.stat(path, follow_symlinks=False)
except FileNotFoundError:
    original = ""
    metadata = None

newline = "\r\n" if "\r\n" in original else "\n"
lines = original.splitlines(keepends=True)
section_pattern = re.compile(r"^\s*\[([^]]+)]\s*(?:[#;].*)?(?:\r?\n)?$")
key_pattern = re.compile(
    r"^\s*(LEMinAdvertisementInterval|LEMaxAdvertisementInterval)\s*=",
    re.IGNORECASE,
)
desired = {
    "leminadvertisementinterval": "LEMinAdvertisementInterval=160" + newline,
    "lemaxadvertisementinterval": "LEMaxAdvertisementInterval=240" + newline,
}

output = []
in_controller = False
found_controller = False
written = set()

def finish_controller() -> None:
    for key in ("leminadvertisementinterval", "lemaxadvertisementinterval"):
        if key not in written:
            output.append(desired[key])
            written.add(key)

for line in lines:
    section = section_pattern.match(line)
    if section:
        if in_controller:
            finish_controller()
        in_controller = section.group(1).strip().lower() == "controller"
        if in_controller:
            found_controller = True
        output.append(line)
        continue

    match = key_pattern.match(line) if in_controller else None
    if match:
        key = match.group(1).lower()
        if key not in written:
            output.append(desired[key])
            written.add(key)
        continue

    output.append(line)

if in_controller:
    finish_controller()
elif not found_controller:
    if output and not output[-1].endswith(("\n", "\r")):
        output[-1] += newline
    if output and output[-1].strip():
        output.append(newline)
    output.extend(
        [
            "[Controller]" + newline,
            desired["leminadvertisementinterval"],
            desired["lemaxadvertisementinterval"],
        ]
    )

updated = "".join(output)
if updated == original:
    print("unchanged")
    raise SystemExit(0)

if metadata is not None:
    backup = path + ".jetson-control.bak"
    if not os.path.exists(backup):
        backup_fd, backup_temp = tempfile.mkstemp(prefix=".main.conf.backup-", dir=directory)
        try:
            with os.fdopen(backup_fd, "wb") as target, open(path, "rb") as source:
                shutil.copyfileobj(source, target)
                target.flush()
                os.fsync(target.fileno())
            os.chmod(backup_temp, stat.S_IMODE(metadata.st_mode))
            os.chown(backup_temp, metadata.st_uid, metadata.st_gid)
            os.replace(backup_temp, backup)
        finally:
            if os.path.exists(backup_temp):
                os.unlink(backup_temp)

file_descriptor, temporary = tempfile.mkstemp(prefix=".main.conf-", dir=directory)
try:
    with os.fdopen(file_descriptor, "w", encoding="utf-8", newline="") as target:
        target.write(updated)
        target.flush()
        os.fsync(target.fileno())
    if metadata is None:
        os.chmod(temporary, 0o644)
    else:
        os.chmod(temporary, stat.S_IMODE(metadata.st_mode))
        os.chown(temporary, metadata.st_uid, metadata.st_gid)
    os.replace(temporary, path)
    directory_fd = os.open(directory, os.O_RDONLY | getattr(os, "O_DIRECTORY", 0))
    try:
        os.fsync(directory_fd)
    finally:
        os.close(directory_fd)
finally:
    if os.path.exists(temporary):
        os.unlink(temporary)

print("changed")
PY
)"

if [[ "${change_state}" == "unchanged" ]]; then
  echo "BlueZ LE advertising intervals are already configured."
  exit 0
fi

echo "Configured BlueZ LE advertising interval to 100-150 ms."
if [[ "${restart_services}" != "true" ]]; then
  exit 0
fi

bluetooth_was_active=false
jetson_control_was_active=false
if systemctl is-active --quiet bluetooth.service; then
  bluetooth_was_active=true
fi
if systemctl is-active --quiet jetson-control.service; then
  jetson_control_was_active=true
  systemctl stop jetson-control.service
fi

if [[ "${bluetooth_was_active}" == "true" ]]; then
  if ! systemctl restart bluetooth.service; then
    if [[ "${jetson_control_was_active}" == "true" ]]; then
      systemctl start jetson-control.service || true
    fi
    exit 1
  fi
fi

if [[ "${jetson_control_was_active}" == "true" ]]; then
  systemctl start jetson-control.service
fi
