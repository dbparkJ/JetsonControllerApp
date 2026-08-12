#!/usr/bin/env bash
set -euo pipefail

if [[ "${EUID}" -ne 0 ]]; then
  echo "Run as root: sudo $0 [--repo <path>] [--venv <path>] [--start-now]" >&2
  exit 1
fi

target_user="${SUDO_USER:-jm}"
target_home="$(getent passwd "${target_user}" | cut -d: -f6)"
repo="${target_home}/26_camera_record/depthai_refactored_ver2"
venv="${target_home}/26_camera_record/.venv"
start_now=false

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
    --start-now)
      start_now=true
      shift
      ;;
    *)
      echo "Unknown option: $1" >&2
      exit 2
      ;;
  esac
done

registrar="/opt/jetson-control/register-pipeline.sh"
if [[ ! -x "${registrar}" ]]; then
  script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
  registrar="${script_dir}/register-pipeline.sh"
fi

command=(
  "${registrar}"
  --id depthai-capture
  --label "DepthAI Capture"
  --description "RGB-D, GPS, and IMU capture pipeline"
  --repo "${repo}"
  --venv "${venv}"
  --entry synced_image_recorder.py
  --config configs/capture.yaml
  --working-dir "${repo}"
  --write-path "${repo}/image_records"
  --user "${target_user}"
  --autostart
)
if [[ "${start_now}" == "true" ]]; then
  command+=(--start-now)
fi
"${command[@]}"
