#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
app_root="$(cd "${script_dir}/.." && pwd)"
data_root="${1:-/data/server_storage/jetson-upload-receiver}"
expected_mount="/data/server_storage"

if ! mountpoint --quiet "${expected_mount}"; then
  echo "Required HDD is not mounted: ${expected_mount}" >&2
  exit 1
fi
resolved_data_root="$(realpath -m "${data_root}")"
resolved_expected_mount="$(realpath "${expected_mount}")"
if [[ "${resolved_data_root}" == "${resolved_expected_mount}" ]]; then
  echo "Data root must be a child of ${expected_mount}, not the mount root itself" >&2
  exit 2
fi
case "${resolved_data_root}/" in
  "${resolved_expected_mount}/"*) ;;
  *)
    echo "Data root must be below ${expected_mount}: ${data_root}" >&2
    exit 2
    ;;
esac

python3 -m venv "${app_root}/.venv"
"${app_root}/.venv/bin/pip" install \
  --disable-pip-version-check \
  --requirement "${app_root}/requirements.txt"

export UPLOAD_RECEIVER_DATA_ROOT="$(realpath -m "${data_root}")"
export UPLOAD_RECEIVER_EXPECTED_MOUNT="$(realpath "${expected_mount}")"
export UPLOAD_RECEIVER_REQUIRE_MOUNT=true
PYTHONPATH="${app_root}" "${app_root}/.venv/bin/python" \
  -m upload_receiver.admin init

config_root="${XDG_CONFIG_HOME:-${HOME}/.config}/jetson-upload-receiver"
unit_root="${XDG_CONFIG_HOME:-${HOME}/.config}/systemd/user"
install -d -m 0700 "${config_root}"
install -d -m 0755 "${unit_root}"

environment_file="${config_root}/environment"
temporary_environment="${environment_file}.tmp"
printf '%s\n' \
  "UPLOAD_RECEIVER_DATA_ROOT=${UPLOAD_RECEIVER_DATA_ROOT}" \
  "UPLOAD_RECEIVER_EXPECTED_MOUNT=${UPLOAD_RECEIVER_EXPECTED_MOUNT}" \
  "UPLOAD_RECEIVER_REQUIRE_MOUNT=true" \
  "PYTHONDONTWRITEBYTECODE=1" \
  > "${temporary_environment}"
chmod 0600 "${temporary_environment}"
mv "${temporary_environment}" "${environment_file}"

escape_sed_replacement() {
  printf '%s' "$1" | sed -e 's/[\\&|]/\\&/g'
}

escaped_app_root="$(escape_sed_replacement "${app_root}")"
escaped_environment="$(escape_sed_replacement "${environment_file}")"
escaped_data_root="$(escape_sed_replacement "${UPLOAD_RECEIVER_DATA_ROOT}")"

for name in jetson-upload-receiver.service jetson-upload-receiver-cleanup.service; do
  temporary_unit="${unit_root}/.${name}.tmp"
  sed \
    -e "s|@APP_ROOT@|${escaped_app_root}|g" \
    -e "s|@ENV_FILE@|${escaped_environment}|g" \
    -e "s|@DATA_ROOT@|${escaped_data_root}|g" \
    "${app_root}/systemd/${name}" > "${temporary_unit}"
  chmod 0644 "${temporary_unit}"
  mv "${temporary_unit}" "${unit_root}/${name}"
done
install -m 0644 \
  "${app_root}/systemd/jetson-upload-receiver-cleanup.timer" \
  "${unit_root}/jetson-upload-receiver-cleanup.timer"

systemctl --user daemon-reload
systemctl --user enable --now \
  jetson-upload-receiver.service \
  jetson-upload-receiver-cleanup.timer
systemctl --user restart \
  jetson-upload-receiver.service \
  jetson-upload-receiver-cleanup.timer

for _attempt in $(seq 1 30); do
  if curl --fail --silent \
    http://127.0.0.1:8877/health/ready >/dev/null; then
    break
  fi
  sleep 1
done
curl --fail --silent --show-error \
  http://127.0.0.1:8877/health/ready >/dev/null

echo "Jetson upload receiver installed on http://127.0.0.1:8877"
