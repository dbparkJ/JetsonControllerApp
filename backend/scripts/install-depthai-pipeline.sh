#!/usr/bin/env bash
set -euo pipefail

target_user="${SUDO_USER:-jm}"
target_home="$(getent passwd "${target_user}" | cut -d: -f6)"
if [[ -z "${target_home}" ]]; then
  echo "Pipeline user does not exist: ${target_user}" >&2
  exit 2
fi
repo="${target_home}/26_camera_record"
venv="${target_home}/26_camera_record/.venv"
output_root="/data/collections"
sensor_bridge_dir="/var/lib/jetson-sensors"
pipeline_id_fallback="depthai-capture"
start_now=false
dry_run=false

while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --repo)
      [[ "$#" -ge 2 ]] || { echo "--repo requires a path" >&2; exit 2; }
      repo="$2"
      shift 2
      ;;
    --venv)
      [[ "$#" -ge 2 ]] || { echo "--venv requires a path" >&2; exit 2; }
      venv="$2"
      shift 2
      ;;
    --output-root)
      [[ "$#" -ge 2 ]] || { echo "--output-root requires a path" >&2; exit 2; }
      output_root="$2"
      shift 2
      ;;
    --start-now)
      start_now=true
      shift
      ;;
    --dry-run)
      dry_run=true
      shift
      ;;
    *)
      echo "Unknown option: $1" >&2
      exit 2
      ;;
  esac
done

if [[ "${EUID}" -ne 0 && "${dry_run}" != "true" ]]; then
  echo "Run as root: sudo $0 [--repo <path>] [--venv <path>] [--output-root <path>] [--start-now]" >&2
  exit 1
fi
if [[ "${output_root}" != /* ]]; then
  echo "--output-root must be an absolute path" >&2
  exit 2
fi
target_group="$(id -gn "${target_user}")"
if [[ "${dry_run}" != "true" ]]; then
  install -d -m 0755 -o "${target_user}" -g "${target_group}" "${output_root}"
  install -d -m 0750 -o "${target_user}" -g "${target_group}" "${sensor_bridge_dir}"
  output_root="$(realpath -e "${output_root}")"
  sensor_bridge_dir="$(realpath -e "${sensor_bridge_dir}")"
fi

registrar="/opt/jetson-control/register-pipeline.sh"
if [[ ! -x "${registrar}" ]]; then
  script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
  registrar="${script_dir}/register-pipeline.sh"
fi
resolver="/opt/jetson-control/resolve-depthai-pipeline-id.py"
if [[ ! -f "${resolver}" ]]; then
  script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
  resolver="${script_dir}/resolve-depthai-pipeline-id.py"
fi
pipeline_id="$(
  /usr/bin/python3 "${resolver}" \
    --repo "${repo}" \
    --registry /opt/jetson-pipelines \
    --fallback "${pipeline_id_fallback}"
)"
if [[ "${dry_run}" != "true" ]] && \
  systemctl is-active --quiet "jetson-pipeline@${pipeline_id}.service"; then
  echo "Pipeline ${pipeline_id} is collecting data. Stop it cleanly before installing the sensor monitor preset." >&2
  exit 1
fi

command=(
  "${registrar}"
  --id "${pipeline_id}"
  --label "DepthAI Capture"
  --description "RGB-D, GPS, and IMU capture pipeline"
  --repo "${repo}"
  --venv "${venv}"
  --entry main.py
  --config config.yaml
  --working-dir "${repo}"
  --write-path "${output_root}"
  --write-path "${sensor_bridge_dir}"
  --argument=--output-dir
  --argument "${output_root}"
  --argument=--controller-bridge-dir
  --argument "${sensor_bridge_dir}"
  --user "${target_user}"
  --no-autostart
)
if [[ "${start_now}" == "true" ]]; then
  command+=(--start-now)
fi
if [[ "${dry_run}" == "true" ]]; then
  printf '%q' "${command[0]}"
  printf ' %q' "${command[@]:1}"
  printf '\n'
  exit 0
fi

monitor_config="/etc/jetson-sensor-monitor.json"
monitor_config_backup="${monitor_config}.backup.$$"
monitor_config_existed=false
monitor_config_committed=false
if [[ -f "${monitor_config}" ]]; then
  cp -a "${monitor_config}" "${monitor_config_backup}"
  monitor_config_existed=true
fi
restore_monitor_config() {
  if [[ "${monitor_config_committed}" == "true" ]]; then
    rm -f "${monitor_config_backup}"
    return
  fi
  if [[ "${monitor_config_existed}" == "true" ]]; then
    mv "${monitor_config_backup}" "${monitor_config}"
  else
    rm -f "${monitor_config}" "${monitor_config_backup}"
  fi
}
trap restore_monitor_config EXIT

PIPELINE_ID="${pipeline_id}" SENSOR_BRIDGE_DIR="${sensor_bridge_dir}" \
python3 - "${monitor_config}" <<'PY'
import json
import os
import sys

path = sys.argv[1]
value = {
    "schema_version": 1,
    "pipeline_id": os.environ["PIPELINE_ID"],
    "bridge_dir": os.environ["SENSOR_BRIDGE_DIR"],
    "registry_root": "/opt/jetson-pipelines",
    "capture_pipeline_ids": [os.environ["PIPELINE_ID"]],
    "monitor_arguments": [
        "--monitor-only",
        "--allow-usb2",
        "--fps",
        "15",
        "--depth-fps",
        "0",
        "--controller-preview-fps",
        "15",
        "--controller-preview-max-width",
        "1920",
        "--controller-bridge-dir",
        os.environ["SENSOR_BRIDGE_DIR"],
    ],
}
temporary = path + ".tmp"
with open(temporary, "w", encoding="utf-8") as output:
    json.dump(value, output, indent=2)
    output.write("\n")
    output.flush()
    os.fsync(output.fileno())
os.chmod(temporary, 0o644)
os.replace(temporary, path)
PY
"${command[@]}"
monitor_config_committed=true
restore_monitor_config
trap - EXIT
if ! systemctl cat jetson-sensor-monitor.service >/dev/null 2>&1; then
  echo "jetson-sensor-monitor.service is not installed; rerun backend/scripts/install.sh first" >&2
  exit 1
fi
systemctl daemon-reload
systemctl enable jetson-sensor-monitor.service
systemctl restart jetson-sensor-monitor.service
