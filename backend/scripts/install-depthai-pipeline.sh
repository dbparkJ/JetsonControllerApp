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

command=(
  "${registrar}"
  --id depthai-capture
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
  --autostart
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
"${command[@]}"
