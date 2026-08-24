#!/usr/bin/env bash
set -u

failed=0

check() {
  local label="$1"
  shift
  if "$@" >/dev/null 2>&1; then
    printf '[OK] %s\n' "${label}"
  else
    printf '[FAIL] %s\n' "${label}"
    failed=1
  fi
}

bluez_555_active() {
  local daemon="/usr/local/libexec/bluetooth/bluetoothd-5.55"
  local main_pid running_binary
  [[ -x "${daemon}" ]] || return 1
  [[ "$("${daemon}" -v 2>/dev/null)" == "5.55" ]] || return 1
  systemctl is-active --quiet bluetooth.service || return 1
  main_pid="$(systemctl show bluetooth.service --property=MainPID --value)"
  running_binary="$(readlink -f "/proc/${main_pid}/exe" 2>/dev/null)"
  [[ "${running_binary}" == "${daemon}" ]]
}

pipeline_registry_valid() {
  local manifest pipeline_id
  [[ -d /opt/jetson-pipelines ]] || return 1
  shopt -s nullglob
  for manifest in /opt/jetson-pipelines/*/pipeline.json; do
    pipeline_id="$(basename "$(dirname "${manifest}")")"
    /usr/bin/python3 - "${manifest}" "${pipeline_id}" <<'PY' || return 1
import json
import sys

with open(sys.argv[1], "r", encoding="utf-8") as source:
    value = json.load(source)
if value.get("schema_version") != 1 or value.get("id") != sys.argv[2]:
    raise SystemExit(1)
PY
    systemctl show "jetson-pipeline@${pipeline_id}.service" \
      --property=LoadState --value | grep -qx loaded || return 1
  done
}

sensor_monitor_healthy() {
  if [[ ! -f /etc/jetson-sensor-monitor.json ]]; then
    return 0
  fi
  systemctl is-enabled --quiet jetson-sensor-monitor.service || return 1
  systemctl is-active --quiet jetson-sensor-monitor.service || return 1
  local attempt
  for attempt in {1..30}; do
    if /opt/jetson-control/venv/bin/python - <<'PY'
import json
import os
import time

with open("/etc/jetson-sensor-monitor.json", "r", encoding="utf-8") as source:
    config = json.load(source)
bridge_dir = config.get("bridge_dir")
if (
    config.get("schema_version") != 1
    or not isinstance(bridge_dir, str)
    or not os.path.isabs(bridge_dir)
    or os.path.normpath(bridge_dir) == "/"
):
    raise SystemExit(1)
with open(os.path.join(bridge_dir, "status.json"), "r", encoding="utf-8") as source:
    value = json.load(source)
updated_at = value.get("updatedAtEpochMillis")
if (
    value.get("schemaVersion") != 1
    or not isinstance(updated_at, int)
    or abs(int(time.time() * 1000) - updated_at) > 15_000
    or value.get("pipeline", {}).get("active") is not True
):
    raise SystemExit(1)
PY
    then
      return 0
    fi
    sleep 0.5
  done
  return 1
}

wifi_direct_healthy() {
  local enabled state interface address
  enabled="$(python3 -c 'import json; print(str(json.load(open("/etc/jetson-control/device.json")).get("wifi_direct_enabled", True)).lower())')" || return 1
  if [[ "${enabled}" != "true" ]]; then
    ! systemctl is-enabled --quiet jetson-wifi-direct.service
    return
  fi

  systemctl is-enabled --quiet jetson-wifi-direct.service || return 1
  systemctl is-active --quiet jetson-wifi-direct.service || return 1
  read -r state interface address < <(python3 - <<'PY'
import json

with open("/run/jetson-control/wifi-direct.json", "r", encoding="utf-8") as source:
    value = json.load(source)
state = value.get("state")
if state not in {"DISCOVERABLE", "CONNECTING", "READY"}:
    raise SystemExit(1)
print(state, value.get("groupInterface") or "-", value["address"])
PY
  ) || return 1
  if [[ "${state}" != "READY" ]]; then
    return 0
  fi
  [[ -d "/sys/class/net/${interface}" ]] || return 1
  ip -4 address show dev "${interface}" | grep -q " ${address}/" || return 1
  curl --fail --silent --insecure --interface "${interface}" --max-time 3 \
    "https://${address}:8765/v1/hello" >/dev/null
}

check "BlueZ 5.55 daemon" bluez_555_active
check "BLE service" systemctl is-active --quiet jetson-control.service
check "Local API service" systemctl is-active --quiet jetson-control-api.service
check "Local API hello" curl --fail --silent --insecure --max-time 3 https://127.0.0.1:8765/v1/hello
check "Wi-Fi Direct discovery/connection service" wifi_direct_healthy
check "Avahi service" systemctl is-active --quiet avahi-daemon.service
check "TCP 8765 listener" bash -c "ss -lnt | grep -q ':8765 '"
check "Device configuration" test -r /etc/jetson-control/device.json
check "Storage roots configuration" test -r /etc/jetson-control/storage_roots.json
check "Upload targets configuration" test -r /etc/jetson-control/upload_targets.json
check "TLS certificate" openssl x509 -in /etc/jetson-control/tls.crt -noout
check "TLS private key" openssl pkey -in /etc/jetson-control/tls.key -noout
check "Pipeline runner" test -x /opt/jetson-control/run-pipeline.py
check "Pipeline registrar" test -x /opt/jetson-control/register-pipeline.py
check "Pipeline systemd template" systemctl cat jetson-pipeline@.service
check "Pipeline registry" pipeline_registry_valid
check "Boot sensor monitor" sensor_monitor_healthy

exit "${failed}"
