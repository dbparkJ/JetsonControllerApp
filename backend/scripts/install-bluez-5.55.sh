#!/usr/bin/env bash
set -euo pipefail

if [[ "${EUID}" -ne 0 ]]; then
  echo "Run as root: sudo $0 [--binary <compiled-bluetoothd> | --build]" >&2
  exit 1
fi

source_binary=""
force_build=false
while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --binary)
      [[ "$#" -ge 2 ]] || { echo "--binary requires a path" >&2; exit 2; }
      source_binary="$2"
      shift 2
      ;;
    --build)
      force_build=true
      shift
      ;;
    *)
      echo "Unknown option: $1" >&2
      exit 2
      ;;
  esac
done

version="5.55"
source_url="https://mirrors.edge.kernel.org/pub/linux/bluetooth/bluez-${version}.tar.xz"
source_sha256="8863717113c4897e2ad3271fc808ea245319e6fd95eed2e934fae8e0894e9b88"
destination_dir="/usr/local/libexec/bluetooth"
destination="${destination_dir}/bluetoothd-${version}"
override_dir="/etc/systemd/system/bluetooth.service.d"
override="${override_dir}/override.conf"
build_root=""

cleanup() {
  if [[ -n "${build_root}" && -d "${build_root}" ]]; then
    rm -rf "${build_root}"
  fi
}
trap cleanup EXIT

if [[ -z "${source_binary}" && "${force_build}" == "false" && -x "${destination}" ]]; then
  if [[ "$("${destination}" -v 2>/dev/null || true)" == "${version}" ]]; then
    source_binary="${destination}"
  fi
fi

if [[ -z "${source_binary}" ]]; then
  build_root="$(mktemp -d /tmp/bluez-5.55-build.XXXXXX)"
  archive="${build_root}/bluez-${version}.tar.xz"
  curl --fail --location --proto '=https' --tlsv1.2 "${source_url}" --output "${archive}"
  printf '%s  %s\n' "${source_sha256}" "${archive}" | sha256sum --check --status
  tar --extract --xz --file "${archive}" --directory "${build_root}"
  source_dir="${build_root}/bluez-${version}"
  (
    cd "${source_dir}"
    ./configure \
      --prefix=/usr/local \
      --sysconfdir=/etc \
      --localstatedir=/var \
      --libexecdir=/usr/local/libexec \
      --disable-cups \
      --disable-obex \
      --disable-mesh \
      --disable-manpages
    make -j"$(nproc)"
  )
  source_binary="${source_dir}/src/bluetoothd"
fi

if [[ ! -x "${source_binary}" ]]; then
  echo "BlueZ daemon is not executable: ${source_binary}" >&2
  exit 1
fi
if [[ "$("${source_binary}" -v)" != "${version}" ]]; then
  echo "BlueZ daemon must report version ${version}: ${source_binary}" >&2
  exit 1
fi
if ldd "${source_binary}" | grep -q 'not found'; then
  echo "BlueZ daemon has unresolved shared libraries: ${source_binary}" >&2
  exit 1
fi

install -d -m 0755 -o root -g root "${destination_dir}" "${override_dir}"
if [[ "$(realpath "${source_binary}")" != "$(realpath -m "${destination}")" ]]; then
  temporary_binary="${destination}.tmp"
  install -m 0755 -o root -g root "${source_binary}" "${temporary_binary}"
  mv "${temporary_binary}" "${destination}"
fi

temporary_override="${override}.tmp"
printf '[Service]\nExecStart=\nExecStart=%s\n' "${destination}" >"${temporary_override}"
chmod 0644 "${temporary_override}"
chown root:root "${temporary_override}"
mv "${temporary_override}" "${override}"

systemctl daemon-reload
systemctl restart bluetooth.service
if ! systemctl is-active --quiet bluetooth.service; then
  systemctl --no-pager --full status bluetooth.service >&2 || true
  exit 1
fi

main_pid="$(systemctl show bluetooth.service --property=MainPID --value)"
running_binary="$(readlink -f "/proc/${main_pid}/exe" 2>/dev/null || true)"
if [[ "${running_binary}" != "${destination}" ]]; then
  echo "bluetooth.service is not running ${destination}: ${running_binary}" >&2
  exit 1
fi
echo "BlueZ ${version} is active from ${destination}."
