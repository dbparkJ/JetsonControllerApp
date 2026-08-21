#!/usr/bin/env bash
set -euo pipefail

modprobe_config="/etc/modprobe.d/jetson-control-realtek-bluetooth.conf"
sysfs_root="/sys"
firmware_root="/lib/firmware"
kernel_release="$(uname -r)"
rebind_driver=true
custom_paths=false

usage() {
  echo "Usage: sudo $0 [--config <modprobe.conf>] [--sysfs-root <path>] [--firmware-root <path>] [--kernel-release <release>] [--no-rebind]" >&2
}

while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --config)
      if [[ "$#" -lt 2 ]]; then
        usage
        exit 2
      fi
      modprobe_config="$2"
      custom_paths=true
      shift 2
      ;;
    --sysfs-root)
      if [[ "$#" -lt 2 ]]; then
        usage
        exit 2
      fi
      sysfs_root="$2"
      custom_paths=true
      shift 2
      ;;
    --firmware-root)
      if [[ "$#" -lt 2 ]]; then
        usage
        exit 2
      fi
      firmware_root="$2"
      custom_paths=true
      shift 2
      ;;
    --kernel-release)
      if [[ "$#" -lt 2 ]]; then
        usage
        exit 2
      fi
      kernel_release="$2"
      custom_paths=true
      shift 2
      ;;
    --no-rebind)
      rebind_driver=false
      shift
      ;;
    *)
      usage
      exit 2
      ;;
  esac
done

if [[ "${EUID}" -ne 0 && ("${custom_paths}" != "true" || "${rebind_driver}" == "true") ]]; then
  echo "Run as root when changing the system driver configuration or rebinding hardware." >&2
  exit 1
fi

if [[ "${kernel_release}" != 5.10.*-tegra ]]; then
  echo "Kernel ${kernel_release} does not need the Jetson 5.10 Realtek driver override."
  exit 0
fi

usb_devices_root="${sysfs_root%/}/bus/usb/devices"
matching_interfaces=()
shopt -s nullglob
for interface_path in "${usb_devices_root}"/*:1.0; do
  usb_device_path="${interface_path%:1.0}"
  if [[ ! -r "${usb_device_path}/idVendor" || ! -r "${usb_device_path}/idProduct" ]]; then
    continue
  fi
  read -r vendor_id <"${usb_device_path}/idVendor"
  read -r product_id <"${usb_device_path}/idProduct"
  [[ "${vendor_id,,}" == "1358" && "${product_id,,}" == "c123" ]] || continue

  interface_class=""
  interface_subclass=""
  interface_protocol=""
  interface_number=""
  [[ -r "${interface_path}/bInterfaceClass" ]] && read -r interface_class <"${interface_path}/bInterfaceClass"
  [[ -r "${interface_path}/bInterfaceSubClass" ]] && read -r interface_subclass <"${interface_path}/bInterfaceSubClass"
  [[ -r "${interface_path}/bInterfaceProtocol" ]] && read -r interface_protocol <"${interface_path}/bInterfaceProtocol"
  [[ -r "${interface_path}/bInterfaceNumber" ]] && read -r interface_number <"${interface_path}/bInterfaceNumber"
  if [[ "${interface_class,,}" == "e0" && "${interface_subclass,,}" == "01" \
      && "${interface_protocol,,}" == "01" && "${interface_number,,}" == "00" ]]; then
    matching_interfaces+=("$(basename "${interface_path}")")
  fi
done
shopt -u nullglob

if [[ "${#matching_interfaces[@]}" -eq 0 ]]; then
  echo "Realtek 1358:c123 Bluetooth adapter is not present; no driver override is needed."
  exit 0
fi

if [[ "${rebind_driver}" == "true" && "${#matching_interfaces[@]}" -ne 1 ]]; then
  echo "Refusing live driver rebinding for ${#matching_interfaces[@]} matching adapters." >&2
  exit 1
fi

if ! command -v modinfo >/dev/null 2>&1 || ! command -v modprobe >/dev/null 2>&1 \
    || ! modinfo rtk_btusb >/dev/null 2>&1; then
  echo "The Realtek adapter is present, but the rtk_btusb kernel module is unavailable." >&2
  exit 1
fi

module_vermagic="$(modinfo -F vermagic rtk_btusb 2>/dev/null || true)"
if [[ "${module_vermagic}" != "${kernel_release}"* ]]; then
  echo "rtk_btusb was built for a different kernel: ${module_vermagic:-unknown}." >&2
  exit 1
fi

primary_interface="${matching_interfaces[0]}"
modalias_path="${usb_devices_root}/${primary_interface}/modalias"
if [[ ! -r "${modalias_path}" ]] \
    || ! modprobe --resolve-alias "$(<"${modalias_path}")" 2>/dev/null | grep -Fxq rtk_btusb; then
  echo "rtk_btusb does not advertise support for this USB interface." >&2
  exit 1
fi

for firmware_name in rtl8822cu_fw rtl8822cu_config; do
  if [[ ! -s "${firmware_root%/}/${firmware_name}" ]]; then
    echo "Required Realtek firmware is missing: ${firmware_root%/}/${firmware_name}" >&2
    exit 1
  fi
done

config_state="$(python3 - "${modprobe_config}" <<'PY'
import os
import stat
import sys
import tempfile

path = os.path.abspath(sys.argv[1])
directory = os.path.dirname(path)
os.makedirs(directory, mode=0o755, exist_ok=True)
if os.path.islink(path):
    raise SystemExit("Refusing to replace a symlinked modprobe configuration")
desired = (
    "# Managed by Jetson Controller for the Realtek 1358:c123 adapter.\n"
    "softdep btusb pre: rtk_btusb\n"
)

try:
    with open(path, "r", encoding="utf-8") as source:
        original = source.read()
    metadata = os.stat(path, follow_symlinks=False)
except FileNotFoundError:
    original = ""
    metadata = None

if original == desired:
    print("unchanged")
    raise SystemExit(0)

if metadata is not None:
    backup = path + ".jetson-control.bak"
    if not os.path.exists(backup):
        backup_fd, backup_temp = tempfile.mkstemp(prefix=".realtek-bt.backup-", dir=directory)
        try:
            with os.fdopen(backup_fd, "w", encoding="utf-8", newline="") as target:
                target.write(original)
                target.flush()
                os.fsync(target.fileno())
            os.chmod(backup_temp, stat.S_IMODE(metadata.st_mode))
            os.chown(backup_temp, metadata.st_uid, metadata.st_gid)
            os.replace(backup_temp, backup)
        finally:
            if os.path.exists(backup_temp):
                os.unlink(backup_temp)

file_descriptor, temporary = tempfile.mkstemp(prefix=".realtek-bt-", dir=directory)
try:
    with os.fdopen(file_descriptor, "w", encoding="utf-8", newline="") as target:
        target.write(desired)
        target.flush()
        os.fsync(target.fileno())
    os.chmod(temporary, 0o644)
    if metadata is not None:
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

if [[ "${config_state}" == "changed" ]]; then
  echo "Configured rtk_btusb to load before generic btusb at boot."
else
  echo "Realtek Bluetooth driver boot order is already configured."
fi

if [[ "${rebind_driver}" != "true" ]]; then
  exit 0
fi

driver_root="${sysfs_root%/}/bus/usb/drivers"
needs_rebind=false
for interface_id in "${matching_interfaces[@]}"; do
  current_driver="$(basename "$(readlink -f "${usb_devices_root}/${interface_id}/driver" 2>/dev/null || true)")"
  case "${current_driver}" in
    rtk_btusb)
      ;;
    btusb)
      needs_rebind=true
      ;;
    *)
      echo "Refusing to replace unexpected driver '${current_driver:-none}' for ${interface_id}." >&2
      exit 1
      ;;
  esac
done

if [[ "${needs_rebind}" != "true" ]]; then
  echo "Realtek Bluetooth adapter is already using rtk_btusb."
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
  systemctl stop bluetooth.service
fi

rebound_interfaces=()
rollback() {
  set +e
  for interface_id in "${rebound_interfaces[@]}"; do
    if [[ -L "${driver_root}/rtk_btusb/${interface_id}" ]]; then
      printf '%s' "${interface_id}" >"${driver_root}/rtk_btusb/unbind"
    fi
    if [[ ! -L "${driver_root}/btusb/${interface_id}" ]]; then
      printf '%s' "${interface_id}" >"${driver_root}/btusb/bind"
    fi
  done
  if [[ "${bluetooth_was_active}" == "true" ]]; then
    systemctl start bluetooth.service
  fi
  if [[ "${jetson_control_was_active}" == "true" ]]; then
    systemctl start jetson-control.service
  fi
}
trap rollback ERR

modprobe rtk_btusb
for interface_id in "${matching_interfaces[@]}"; do
  current_driver="$(basename "$(readlink -f "${usb_devices_root}/${interface_id}/driver" 2>/dev/null || true)")"
  if [[ "${current_driver}" == "rtk_btusb" ]]; then
    continue
  fi

  companion_id="${interface_id%:1.0}:1.1"
  rebound_interfaces+=("${interface_id}")
  printf '%s' "${interface_id}" >"${driver_root}/btusb/unbind"
  if [[ -L "${driver_root}/btusb/${companion_id}" ]]; then
    printf '%s' "${companion_id}" >"${driver_root}/btusb/unbind"
  fi
  printf '%s' "${interface_id}" >"${driver_root}/rtk_btusb/bind"
  test -L "${driver_root}/rtk_btusb/${interface_id}"
  test -L "${driver_root}/rtk_btusb/${companion_id}"
done

if [[ "${bluetooth_was_active}" == "true" ]]; then
  systemctl start bluetooth.service
fi
if [[ "${jetson_control_was_active}" == "true" ]]; then
  systemctl start jetson-control.service
fi
trap - ERR

echo "Realtek Bluetooth firmware driver is active."
