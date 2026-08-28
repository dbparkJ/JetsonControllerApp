#!/usr/bin/env bash
set -euo pipefail

if [[ "${EUID}" -ne 0 ]]; then
  echo "Run as root: sudo $0 [--device-name <name>] [--pipeline-user <user>] [--enable-power] [--storage-root <directory>] [--disable-wifi-direct]" >&2
  exit 1
fi

enable_power=false
storage_root=""
storage_root_explicit=false
storage_root_needs_update=false
device_name=""
pipeline_user=""
wifi_direct_enabled_override=""
wifi_direct_frequency=""
while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --device-name)
      if [[ "$#" -lt 2 ]]; then
        echo "--device-name requires a value" >&2
        exit 2
      fi
      device_name="$2"
      shift 2
      ;;
    --pipeline-user)
      if [[ "$#" -lt 2 ]]; then
        echo "--pipeline-user requires a user" >&2
        exit 2
      fi
      pipeline_user="$2"
      shift 2
      ;;
    --enable-power)
      enable_power=true
      shift
      ;;
    --storage-root)
      if [[ "$#" -lt 2 ]]; then
        echo "--storage-root requires a directory" >&2
        exit 2
      fi
      storage_root="$2"
      storage_root_explicit=true
      shift 2
      ;;
    --disable-wifi-direct)
      wifi_direct_enabled_override="false"
      shift
      ;;
    --enable-wifi-direct)
      wifi_direct_enabled_override="true"
      shift
      ;;
    --wifi-direct-frequency)
      if [[ "$#" -lt 2 ]]; then
        echo "--wifi-direct-frequency requires a MHz value" >&2
        exit 2
      fi
      wifi_direct_frequency="$2"
      shift 2
      ;;
    *)
      echo "Unknown option: $1" >&2
      exit 2
      ;;
  esac
done

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source_root="$(cd "${script_dir}/.." && pwd)"
install_root="/opt/jetson-control"
config_dir="/etc/jetson-control"
state_dir="/var/lib/jetson-control"
sensor_bridge_dir="/var/lib/jetson-sensors"
pipeline_root="/opt/jetson-pipelines"

invoking_user="${SUDO_USER:-root}"
invoking_home="$(getent passwd "${invoking_user}" | cut -d: -f6)"
if [[ -z "${invoking_home}" ]]; then
  invoking_home="/root"
fi
if [[ -z "${pipeline_user}" ]]; then
  pipeline_user="${invoking_user}"
fi
if [[ ! "${pipeline_user}" =~ ^[a-z_][a-z0-9_-]{0,31}$ ]]; then
  echo "Invalid pipeline user: ${pipeline_user}" >&2
  exit 2
fi
if ! getent passwd "${pipeline_user}" >/dev/null; then
  echo "Pipeline user does not exist: ${pipeline_user}" >&2
  exit 2
fi

if ! /usr/bin/python3 -c 'from cryptography.hazmat.primitives.ciphers.aead import AESGCM' 2>/dev/null; then
  echo "Missing python3-cryptography (AESGCM is required for encrypted BLE Wi-Fi setup)." >&2
  exit 1
fi

install -d -m 0755 -o root -g root "${install_root}"
install -d -m 0700 -o root -g root "${config_dir}" "${state_dir}"
install -d -m 0755 -o root -g root "${state_dir}/upload-jobs" "${state_dir}/uploads"
pipeline_group="$(id -gn "${pipeline_user}")"
install -d -m 0750 -o "${pipeline_user}" -g "${pipeline_group}" "${sensor_bridge_dir}"
install -d -m 0755 -o root -g root "${pipeline_root}"

rm -rf "${install_root}/jetson_control.new"
cp -a "${source_root}/jetson_control" "${install_root}/jetson_control.new"
find "${install_root}/jetson_control.new" -type d -name __pycache__ -prune -exec rm -rf {} +
find "${install_root}/jetson_control.new" -type f \( -name '*.pyc' -o -name '*.pyo' \) -delete
rm -rf "${install_root}/jetson_control"
mv "${install_root}/jetson_control.new" "${install_root}/jetson_control"
chown -R root:root "${install_root}/jetson_control"
find "${install_root}/jetson_control" -type d -exec chmod 0755 {} +
find "${install_root}/jetson_control" -type f -exec chmod 0644 {} +
install -m 0644 -o root -g root "${source_root}/requirements.txt" "${install_root}/requirements.txt"
install -m 0755 -o root -g root "${source_root}/scripts/provision-device.sh" "${install_root}/provision-device.sh"
install -m 0755 -o root -g root "${source_root}/scripts/configure-upload-target.sh" "${install_root}/configure-upload-target.sh"
install -m 0755 -o root -g root "${source_root}/scripts/configure-bluez-advertising.sh" "${install_root}/configure-bluez-advertising.sh"
install -m 0755 -o root -g root "${source_root}/scripts/configure-realtek-bluetooth-driver.sh" "${install_root}/configure-realtek-bluetooth-driver.sh"
install -m 0755 -o root -g root "${source_root}/scripts/doctor.sh" "${install_root}/doctor.sh"
install -m 0755 -o root -g root "${source_root}/scripts/configure-api-storage-access.py" "${install_root}/configure-api-storage-access.py"
install -m 0755 -o root -g root "${source_root}/scripts/register-pipeline.py" "${install_root}/register-pipeline.py"
install -m 0755 -o root -g root "${source_root}/scripts/register-pipeline.sh" "${install_root}/register-pipeline.sh"
install -m 0755 -o root -g root "${source_root}/scripts/run-pipeline.py" "${install_root}/run-pipeline.py"
install -m 0755 -o root -g root "${source_root}/scripts/install-depthai-pipeline.sh" "${install_root}/install-depthai-pipeline.sh"
install -m 0755 -o root -g root "${source_root}/scripts/resolve-depthai-pipeline-id.py" "${install_root}/resolve-depthai-pipeline-id.py"

if [[ ! -x "${install_root}/venv/bin/python" ]]; then
  python3 -m venv "${install_root}/venv"
fi
"${install_root}/venv/bin/pip" install --disable-pip-version-check -r "${install_root}/requirements.txt"

if [[ ! -f "${config_dir}/device.json" ]]; then
  "${install_root}/provision-device.sh" "${device_name}"
fi

if [[ -z "${storage_root}" ]]; then
  storage_root="/data/collections"
  install -d -m 0755 -o "${pipeline_user}" -g "${pipeline_group}" "${storage_root}"
elif [[ ! -d "${storage_root}" ]]; then
  echo "Storage root is not a directory: ${storage_root}" >&2
  exit 2
fi
storage_root="$(realpath -e "${storage_root}")"

if [[ ! -f "${config_dir}/storage_roots.json" || "${storage_root_explicit}" == "true" ]]; then
  storage_root_needs_update=true
elif python3 - "${config_dir}/storage_roots.json" \
    "${invoking_home}/26_camera_record" "${state_dir}/data" <<'PY'
import json
import os
import sys

try:
    with open(sys.argv[1], "r", encoding="utf-8") as source:
        value = json.load(source)
    recordings = value.get("recordings", {})
    current = os.path.realpath(str(recordings.get("path", "")))
except (OSError, ValueError, AttributeError):
    raise SystemExit(1)

legacy_defaults = {os.path.realpath(path) for path in sys.argv[2:]}
raise SystemExit(0 if current in legacy_defaults else 1)
PY
then
  storage_root_needs_update=true
fi

if [[ "${storage_root_needs_update}" == "true" ]]; then
  TARGET_PATH="${storage_root}" TARGET_HOME="${invoking_home}" \
  LEGACY_RECORDINGS="${invoking_home}/26_camera_record" \
  python3 - "${config_dir}/storage_roots.json" <<'PY'
import json
import os
import sys

path = sys.argv[1]
try:
    with open(path, "r", encoding="utf-8") as source:
        value = json.load(source)
except FileNotFoundError:
    value = {}
if not isinstance(value, dict):
    raise SystemExit("storage_roots.json must contain an object")

target_home = os.path.realpath(os.environ["TARGET_HOME"])
legacy_recordings = os.path.realpath(os.environ["LEGACY_RECORDINGS"])
existing_recordings = value.get("recordings", {})
existing_recordings_path = (
    os.path.realpath(str(existing_recordings.get("path", "")))
    if isinstance(existing_recordings, dict)
    else ""
)
value = {
    key: entry
    for key, entry in value.items()
    if not isinstance(entry, dict)
    or os.path.realpath(str(entry.get("path", ""))) != target_home
}
target_path = os.environ["TARGET_PATH"]
if (
    existing_recordings_path == legacy_recordings
    and legacy_recordings != target_path
    and os.path.isdir(legacy_recordings)
):
    value.setdefault(
        "legacy-recordings",
        {
            "label": "Previous collected data",
            "path": legacy_recordings,
            "path_hint": legacy_recordings,
        },
    )
value["recordings"] = {
    "label": "Collected data",
    "path": target_path,
    "path_hint": target_path,
}
temporary = path + ".tmp"
with open(temporary, "w", encoding="utf-8") as output:
    json.dump(value, output, indent=2)
    output.write("\n")
os.chmod(temporary, 0o600)
os.replace(temporary, path)
PY
fi

if [[ ! -f "${config_dir}/upload_targets.json" ]]; then
  python3 - "${config_dir}/upload_targets.json" <<'PY'
import json
import sys

value = {}
with open(sys.argv[1], "x", encoding="utf-8") as output:
    json.dump(value, output, indent=2)
    output.write("\n")
PY
fi

ENABLE_POWER="${enable_power}" DEVICE_NAME="${device_name}" PIPELINE_USER="${pipeline_user}" \
WIFI_DIRECT_ENABLED_OVERRIDE="${wifi_direct_enabled_override}" \
WIFI_DIRECT_FREQUENCY="${wifi_direct_frequency}" \
python3 - "${config_dir}/device.json" <<'PY'
import json
import os
import sys

path = sys.argv[1]
with open(path, "r", encoding="utf-8") as source:
    value = json.load(source)
value.setdefault("controlled_services", [])
value.setdefault("service_flags", {"camera": "", "lidar": "", "gnss": "", "mms": ""})
value.setdefault("wifi_interface", "wlan0")
value.setdefault("wifi_direct_enabled", True)
value.setdefault("wifi_direct_frequency", 2412)
value.setdefault("wifi_direct_address", "192.168.49.1/24")
value["pipeline_user"] = os.environ["PIPELINE_USER"]
if os.environ["DEVICE_NAME"]:
    value["device_name"] = os.environ["DEVICE_NAME"]
else:
    compact_device_id = str(value["device_id"]).replace("-", "").upper()
    legacy_default_name = f"MMS-{compact_device_id[-5:]}"
    if value.get("device_name") == legacy_default_name:
        value["device_name"] = f"MMS-{compact_device_id[-4:]}"
if os.environ["ENABLE_POWER"] == "true":
    value["allow_power_commands"] = True
else:
    value.setdefault("allow_power_commands", False)
if os.environ["WIFI_DIRECT_ENABLED_OVERRIDE"]:
    value["wifi_direct_enabled"] = os.environ["WIFI_DIRECT_ENABLED_OVERRIDE"] == "true"
if os.environ["WIFI_DIRECT_FREQUENCY"]:
    frequency = int(os.environ["WIFI_DIRECT_FREQUENCY"])
    valid_frequencies = (
        set(range(2412, 2473, 5))
        | {2484}
        | set(range(5180, 5241, 20))
        | set(range(5260, 5321, 20))
        | set(range(5500, 5721, 20))
        | set(range(5745, 5806, 20))
    )
    if frequency not in valid_frequencies:
        raise SystemExit("Wi-Fi Direct frequency must be a standard 2.4 or 5 GHz channel")
    value["wifi_direct_frequency"] = frequency
temporary = path + ".tmp"
with open(temporary, "w", encoding="utf-8") as output:
    json.dump(value, output, indent=2)
    output.write("\n")
os.chmod(temporary, 0o600)
os.replace(temporary, path)
PY
chmod 0600 "${config_dir}"/*.json

tls_certificate="${config_dir}/tls.crt"
tls_private_key="${config_dir}/tls.key"
renew_tls=false
if [[ ! -s "${tls_certificate}" || ! -s "${tls_private_key}" ]]; then
  renew_tls=true
elif ! openssl x509 -in "${tls_certificate}" -noout -checkend 2592000 >/dev/null 2>&1; then
  renew_tls=true
elif ! openssl pkey -in "${tls_private_key}" -noout >/dev/null 2>&1; then
  renew_tls=true
else
  certificate_public_key="${config_dir}/.tls.crt.pub.tmp"
  private_public_key="${config_dir}/.tls.key.pub.tmp"
  if ! openssl x509 -in "${tls_certificate}" -pubkey -noout >"${certificate_public_key}" 2>/dev/null \
    || ! openssl pkey -in "${tls_private_key}" -pubout >"${private_public_key}" 2>/dev/null \
    || ! cmp -s "${certificate_public_key}" "${private_public_key}"; then
    renew_tls=true
  fi
  rm -f "${certificate_public_key}" "${private_public_key}"
fi

if [[ "${renew_tls}" == "true" ]]; then
  certificate_tmp="${config_dir}/.tls.crt.tmp"
  private_key_tmp="${config_dir}/.tls.key.tmp"
  rm -f "${certificate_tmp}" "${private_key_tmp}"
  openssl req -x509 -newkey rsa:3072 -sha256 -nodes -days 825 \
    -subj "/CN=jetson-controller" \
    -addext "subjectAltName=DNS:localhost,IP:127.0.0.1" \
    -keyout "${private_key_tmp}" \
    -out "${certificate_tmp}"
  chmod 0600 "${certificate_tmp}" "${private_key_tmp}"
  mv "${certificate_tmp}" "${tls_certificate}"
  mv "${private_key_tmp}" "${tls_private_key}"
fi
openssl x509 -in "${tls_certificate}" -noout >/dev/null
openssl pkey -in "${tls_private_key}" -noout >/dev/null
chmod 0600 "${tls_certificate}" "${tls_private_key}"

install -m 0644 -o root -g root "${source_root}/systemd/jetson-control.service" "/etc/systemd/system/jetson-control.service"
install -m 0644 -o root -g root "${source_root}/systemd/jetson-control-api.service" "/etc/systemd/system/jetson-control-api.service"
install -m 0644 -o root -g root "${source_root}/systemd/jetson-wifi-direct.service" "/etc/systemd/system/jetson-wifi-direct.service"
install -m 0644 -o root -g root "${source_root}/systemd/jetson-pipeline@.service" "/etc/systemd/system/jetson-pipeline@.service"
install -m 0644 -o root -g root "${source_root}/systemd/jetson-sensor-monitor.service" "/etc/systemd/system/jetson-sensor-monitor.service"
install -d -m 0700 -o root -g root "${config_dir}/pipelines"
install -d -m 0755 -o root -g root "/etc/udev/rules.d"
install -m 0644 -o root -g root "${source_root}/udev/99-jetson-controller-sensors.rules" "/etc/udev/rules.d/99-jetson-controller-sensors.rules"
udevadm control --reload-rules
udevadm trigger --subsystem-match=tty --action=change
udevadm trigger --subsystem-match=usb --attr-match=idVendor=03e7 --action=change
"${install_root}/configure-api-storage-access.py" \
  --storage-roots "${config_dir}/storage_roots.json" \
  --output "/etc/systemd/system/jetson-control-api.service.d/storage-roots.conf" \
  --pipeline-output "/etc/systemd/system/jetson-pipeline@.service.d/10-storage-defaults.conf" \
  --pipeline-user "${pipeline_user}"

python3 - "${config_dir}/device.json" "/etc/avahi/services/jetson-control.service" <<'PY'
import html
import json
import os
import sys

with open(sys.argv[1], "r", encoding="utf-8") as source:
    config = json.load(source)
name = html.escape(str(config["device_name"]))
device_id = html.escape(str(config["device_id"]))
xml = f'''<?xml version="1.0" standalone="no"?>
<!DOCTYPE service-group SYSTEM "avahi-service.dtd">
<service-group>
  <name replace-wildcards="yes">{name}</name>
  <service>
    <type>_jetsonctl._tcp</type>
    <port>8765</port>
    <txt-record>id={device_id}</txt-record>
    <txt-record>api=1</txt-record>
    <txt-record>tls=1</txt-record>
  </service>
</service-group>
'''
temporary = sys.argv[2] + ".tmp"
with open(temporary, "w", encoding="utf-8") as output:
    output.write(xml)
os.chmod(temporary, 0o644)
os.replace(temporary, sys.argv[2])
PY

legacy_pids="$(fuser 8765/tcp 2>/dev/null || true)"
systemctl stop jetson-control.service 2>/dev/null || true
systemctl stop jetson-control-api.service 2>/dev/null || true
systemctl stop jetson-wifi-direct.service 2>/dev/null || true
"${install_root}/configure-realtek-bluetooth-driver.sh"
"${install_root}/configure-bluez-advertising.sh"
for pid in ${legacy_pids}; do
  if [[ ! -r "/proc/${pid}/cmdline" ]]; then
    continue
  fi
  command_line="$(tr '\0' ' ' <"/proc/${pid}/cmdline")"
  if [[ "${command_line}" == *"/opt/jetson-control/venv/bin/uvicorn app:app"* ]]; then
    kill "${pid}"
  elif [[ -n "${command_line}" ]]; then
    echo "Port 8765 is owned by an unrelated process: ${command_line}" >&2
    exit 1
  fi
done

systemctl daemon-reload
systemctl enable --now jetson-control.service jetson-control-api.service
if [[ -f "/etc/jetson-sensor-monitor.json" ]] && \
  PYTHONPATH="${install_root}" \
    "${install_root}/venv/bin/python" -m jetson_control.sensor_monitor --check-config; then
  systemctl enable jetson-sensor-monitor.service
  systemctl restart jetson-sensor-monitor.service
elif [[ -f "/etc/jetson-sensor-monitor.json" ]]; then
  # A configuration can outlive its registered capture snapshot.  Do not leave
  # an enabled service in a restart loop; the DepthAI installer enables it again
  # immediately after successfully registering a matching snapshot.
  systemctl disable --now jetson-sensor-monitor.service 2>/dev/null || true
  echo "Sensor monitor configuration is stale; service left disabled until the capture pipeline is registered." >&2
fi
wifi_direct_enabled="$(python3 -c 'import json; print(str(json.load(open("/etc/jetson-control/device.json"))["wifi_direct_enabled"]).lower())')"
if [[ "${wifi_direct_enabled}" == "true" ]]; then
  for command in /usr/sbin/wpa_cli /usr/sbin/iw /usr/sbin/ip /usr/sbin/dnsmasq /usr/bin/nmcli; do
    if [[ ! -x "${command}" ]]; then
      echo "Wi-Fi Direct dependency is missing: ${command}" >&2
      exit 1
    fi
  done
  systemctl enable --now jetson-wifi-direct.service
else
  systemctl disable --now jetson-wifi-direct.service 2>/dev/null || true
fi
systemctl restart avahi-daemon.service

for attempt in {1..40}; do
  api_ready=false
  wifi_direct_ready=false
  if curl --fail --silent --insecure --max-time 2 https://127.0.0.1:8765/v1/hello >/dev/null; then
    api_ready=true
  fi
  if [[ "${wifi_direct_enabled}" != "true" ]] || \
      grep -Eq '"state":"(DISCOVERABLE|CONNECTING|READY)"' \
        /run/jetson-control/wifi-direct.json 2>/dev/null; then
    wifi_direct_ready=true
  fi
  if [[ "${api_ready}" == "true" && "${wifi_direct_ready}" == "true" ]]; then
    echo "Jetson Controller backend is running."
    exit 0
  fi
  sleep 0.5
done

systemctl --no-pager --full status jetson-control-api.service >&2 || true
if [[ "${wifi_direct_enabled}" == "true" ]]; then
  systemctl --no-pager --full status jetson-wifi-direct.service >&2 || true
fi
exit 1
