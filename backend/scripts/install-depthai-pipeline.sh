#!/usr/bin/env bash
set -euo pipefail

if [[ "${EUID}" -ne 0 ]]; then
  echo "Run as root: sudo $0 [--repo <path>] [--venv <path>] [--output-root <path>] [--start-now]" >&2
  exit 1
fi

target_user="${SUDO_USER:-jm}"
target_home="$(getent passwd "${target_user}" | cut -d: -f6)"
repo="${target_home}/26_camera_record/depthai_refactored_ver2"
venv="${target_home}/26_camera_record/.venv"
output_root="/data/collections"
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
    --output-root)
      [[ "$#" -ge 2 ]] || { echo "--output-root requires a path" >&2; exit 2; }
      output_root="$2"
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

if [[ "${output_root}" != /* ]]; then
  echo "--output-root must be an absolute path" >&2
  exit 2
fi
target_group="$(id -gn "${target_user}")"
install -d -m 0755 -o "${target_user}" -g "${target_group}" "${output_root}"
output_root="$(realpath -e "${output_root}")"

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
  --working-dir "${output_root}"
  --write-path "${output_root}"
  --user "${target_user}"
  --autostart
)
if [[ "${start_now}" == "true" ]]; then
  command+=(--start-now)
fi
"${command[@]}"
