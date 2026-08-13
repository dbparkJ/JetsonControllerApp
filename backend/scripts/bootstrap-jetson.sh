#!/usr/bin/env bash
set -euo pipefail

if [[ "${EUID}" -ne 0 ]]; then
  echo "Run as root: sudo $0 [options]" >&2
  exit 1
fi

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
device_name=""
storage_root=""
pipeline_user="${SUDO_USER:-root}"
enable_power=false
skip_packages=false
skip_bluez=false
bluez_binary=""
depthai_repo=""
depthai_venv=""
start_depthai=false
disable_wifi_direct=false
wifi_direct_frequency=""

while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --device-name)
      [[ "$#" -ge 2 ]] || { echo "--device-name requires a value" >&2; exit 2; }
      device_name="$2"
      shift 2
      ;;
    --storage-root)
      [[ "$#" -ge 2 ]] || { echo "--storage-root requires a path" >&2; exit 2; }
      storage_root="$2"
      shift 2
      ;;
    --pipeline-user)
      [[ "$#" -ge 2 ]] || { echo "--pipeline-user requires a user" >&2; exit 2; }
      pipeline_user="$2"
      shift 2
      ;;
    --enable-power)
      enable_power=true
      shift
      ;;
    --skip-packages)
      skip_packages=true
      shift
      ;;
    --skip-bluez)
      skip_bluez=true
      shift
      ;;
    --bluez-binary)
      [[ "$#" -ge 2 ]] || { echo "--bluez-binary requires a path" >&2; exit 2; }
      bluez_binary="$2"
      shift 2
      ;;
    --depthai-repo)
      [[ "$#" -ge 2 ]] || { echo "--depthai-repo requires a path" >&2; exit 2; }
      depthai_repo="$2"
      shift 2
      ;;
    --depthai-venv)
      [[ "$#" -ge 2 ]] || { echo "--depthai-venv requires a path" >&2; exit 2; }
      depthai_venv="$2"
      shift 2
      ;;
    --start-depthai-now)
      start_depthai=true
      shift
      ;;
    --disable-wifi-direct)
      disable_wifi_direct=true
      shift
      ;;
    --wifi-direct-frequency)
      [[ "$#" -ge 2 ]] || { echo "--wifi-direct-frequency requires a MHz value" >&2; exit 2; }
      wifi_direct_frequency="$2"
      shift 2
      ;;
    *)
      echo "Unknown option: $1" >&2
      exit 2
      ;;
  esac
done

if [[ "${skip_packages}" == "false" ]]; then
  export DEBIAN_FRONTEND=noninteractive
  apt-get update
  apt-get install -y --no-install-recommends \
    avahi-daemon \
    build-essential \
    ca-certificates \
    curl \
    dnsmasq-base \
    git \
    iproute2 \
    iw \
    libdbus-1-dev \
    libglib2.0-dev \
    libical-dev \
    libreadline-dev \
    libudev-dev \
    network-manager \
    openssl \
    python3 \
    python3-cryptography \
    python3-dbus \
    python3-gi \
    python3-pip \
    python3-venv \
    qrencode \
    wpasupplicant \
    xz-utils
fi

if [[ "${skip_bluez}" == "false" ]]; then
  if [[ -n "${bluez_binary}" ]]; then
    "${script_dir}/install-bluez-5.55.sh" --binary "${bluez_binary}"
  else
    "${script_dir}/install-bluez-5.55.sh"
  fi
fi

install_args=(--pipeline-user "${pipeline_user}")
[[ -z "${device_name}" ]] || install_args+=(--device-name "${device_name}")
[[ -z "${storage_root}" ]] || install_args+=(--storage-root "${storage_root}")
[[ "${enable_power}" == "false" ]] || install_args+=(--enable-power)
[[ "${disable_wifi_direct}" == "false" ]] || install_args+=(--disable-wifi-direct)
[[ -z "${wifi_direct_frequency}" ]] || install_args+=(--wifi-direct-frequency "${wifi_direct_frequency}")
"${script_dir}/install.sh" "${install_args[@]}"

if [[ -n "${depthai_repo}" || -n "${depthai_venv}" ]]; then
  if [[ -z "${depthai_repo}" || -z "${depthai_venv}" ]]; then
    echo "--depthai-repo and --depthai-venv must be supplied together" >&2
    exit 2
  fi
  pipeline_args=(
    --repo "${depthai_repo}"
    --venv "${depthai_venv}"
    --output-root "${storage_root:-/data/collections}"
  )
  [[ "${start_depthai}" == "false" ]] || pipeline_args+=(--start-now)
  SUDO_USER="${pipeline_user}" "${script_dir}/install-depthai-pipeline.sh" "${pipeline_args[@]}"
fi

"/opt/jetson-control/doctor.sh"
echo "Jetson Controller bootstrap completed."
