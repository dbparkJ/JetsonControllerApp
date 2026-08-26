#!/usr/bin/env bash
set -euo pipefail

if [[ "${EUID}" -ne 0 ]]; then
    exec sudo -- "$0" "$@"
fi

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/../.." && pwd)"
backend_source="${repo_root}/backend"
backend_target="/opt/jetson-control"
systemd_unit_root="/etc/systemd/system"
backup_root="/var/backups/jetson-control"
controller_units=(
    "jetson-control.service"
    "jetson-control-api.service"
    "jetson-wifi-direct.service"
)

required_sources=(
    "${backend_source}/jetson_control/network.py"
    "${backend_source}/jetson_control/wifi_direct.py"
    "${backend_source}/jetson_control/api.py"
    "${backend_source}/jetson_control/ble.py"
)
for unit_name in "${controller_units[@]}"; do
    required_sources+=("${backend_source}/systemd/${unit_name}")
done

for source_path in "${required_sources[@]}"; do
    if [[ ! -f "${source_path}" ]]; then
        echo "Missing hotfix source: ${source_path}" >&2
        exit 1
    fi
done

PYTHONPATH="${backend_source}" /usr/bin/python3 -m py_compile \
    "${backend_source}/jetson_control/network.py" \
    "${backend_source}/jetson_control/wifi_direct.py" \
    "${backend_source}/jetson_control/api.py" \
    "${backend_source}/jetson_control/ble.py"

install -d -m 0700 -o root -g root -- "${backup_root}"
backup_dir="$(mktemp -d "${backup_root}/wifi-hotfix.XXXXXXXX")"

cp -a -- "${backend_target}/jetson_control/network.py" "${backup_dir}/network.py"
cp -a -- "${backend_target}/jetson_control/wifi_direct.py" "${backup_dir}/wifi_direct.py"
cp -a -- "${backend_target}/jetson_control/api.py" "${backup_dir}/api.py"
cp -a -- "${backend_target}/jetson_control/ble.py" "${backup_dir}/ble.py"
for unit_name in "${controller_units[@]}"; do
    unit_target="${systemd_unit_root}/${unit_name}"
    if [[ -e "${unit_target}" || -L "${unit_target}" ]]; then
        cp -a -- "${unit_target}" "${backup_dir}/${unit_name}"
    fi
done

install -m 0644 -- \
    "${backend_source}/jetson_control/network.py" \
    "${backend_target}/jetson_control/network.py.new"
install -m 0644 -- \
    "${backend_source}/jetson_control/wifi_direct.py" \
    "${backend_target}/jetson_control/wifi_direct.py.new"
install -m 0644 -- \
    "${backend_source}/jetson_control/api.py" \
    "${backend_target}/jetson_control/api.py.new"
install -m 0644 -- \
    "${backend_source}/jetson_control/ble.py" \
    "${backend_target}/jetson_control/ble.py.new"
for unit_name in "${controller_units[@]}"; do
    install -m 0644 -o root -g root -- \
        "${backend_source}/systemd/${unit_name}" \
        "${systemd_unit_root}/${unit_name}.new"
done

mv -- "${backend_target}/jetson_control/network.py.new" \
    "${backend_target}/jetson_control/network.py"
mv -- "${backend_target}/jetson_control/wifi_direct.py.new" \
    "${backend_target}/jetson_control/wifi_direct.py"
mv -- "${backend_target}/jetson_control/api.py.new" \
    "${backend_target}/jetson_control/api.py"
mv -- "${backend_target}/jetson_control/ble.py.new" \
    "${backend_target}/jetson_control/ble.py"
for unit_name in "${controller_units[@]}"; do
    mv -- "${systemd_unit_root}/${unit_name}.new" \
        "${systemd_unit_root}/${unit_name}"
done

systemctl daemon-reload
systemctl stop jetson-wifi-direct.service

# A discovery overlap from an older build can leave wpa_supplicant's P2P scan
# state wedged until that process is restarted. Reset it only when doing so
# cannot interrupt an active managed Wi-Fi connection. Ethernet and
# NetworkManager remain untouched.
wifi_device_state="$(/usr/bin/nmcli -t -f DEVICE,TYPE,STATE device status | \
    /usr/bin/awk -F: '$1 == "wlan0" && $2 == "wifi" { print $3; exit }')"
if [[ "${wifi_device_state}" != "connected" ]]; then
    systemctl restart wpa_supplicant.service
    echo "Reset stale wpa_supplicant P2P scan state (wlan0 was not connected)."
else
    echo "Kept wpa_supplicant running because wlan0 has an active connection."
fi

# systemd reports wpa_supplicant active before NetworkManager has recreated its
# per-interface P2P device. Starting the controller during that gap causes a
# needless failure/restart cycle and can remove a non-preserved shared runtime
# directory on older deployed units. Wait for the usable D-Bus-facing device.
wifi_control_ready=false
for _attempt in {1..60}; do
    if systemctl is-active --quiet wpa_supplicant.service && \
        /usr/bin/nmcli -t -f DEVICE,TYPE,STATE device status | \
            /usr/bin/awk -F: '
                $2 == "wifi-p2p" &&
                $3 != "unavailable" &&
                $3 != "unmanaged" &&
                $3 != "unknown" { found = 1 }
                END { exit(found ? 0 : 1) }
            '; then
        wifi_control_ready=true
        break
    fi
    sleep 0.5
done
if [[ "${wifi_control_ready}" != "true" ]]; then
    echo "NetworkManager did not recreate the Wi-Fi P2P control device." >&2
    exit 1
fi

systemctl reset-failed jetson-wifi-direct.service
systemctl restart jetson-control-api.service jetson-control.service
systemctl start jetson-wifi-direct.service

systemctl is-active --quiet jetson-control-api.service
systemctl is-active --quiet jetson-control.service
systemctl is-active --quiet jetson-wifi-direct.service

echo "Wi-Fi hotfix deployed successfully."
echo "Backup: ${backup_dir}"
