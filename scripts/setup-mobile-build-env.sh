#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repository_root="$(cd "${script_dir}/.." && pwd)"
environment_root="${repository_root}/.mobile-build"
android_source="${ANDROID_SDK_SOURCE:-${HOME}/.local/lib/android-sdk}"
gradle_source="${GRADLE_HOME_SOURCE:-${HOME}/.gradle}"
qemu_source="${QEMU_X86_64_SOURCE:-${HOME}/.local/lib/qemu-user/usr/bin/qemu-x86_64}"
sysroot_source="${AAPT2_SYSROOT_SOURCE:-${HOME}/.local/lib/aapt2-x86-sysroot}"

install_tree_once() {
  local source_path="$1"
  local destination_path="$2"
  local marker_path="$3"
  if [[ -e "${destination_path}/${marker_path}" ]]; then
    return
  fi
  [[ -d "${source_path}" ]] || { echo "Missing build environment source: ${source_path}" >&2; exit 1; }
  mkdir -p "${destination_path}"
  if [[ "$(stat -c %d "${source_path}")" == "$(stat -c %d "${destination_path}")" ]]; then
    cp -al "${source_path}/." "${destination_path}/"
  else
    cp -a "${source_path}/." "${destination_path}/"
  fi
}

install_tree_once "${android_source}" "${environment_root}/android-sdk" "platforms/android-37.0/android.jar"
install_tree_once "${gradle_source}" "${environment_root}/gradle-home" "wrapper/dists/gradle-9.5.0-bin"
install_tree_once "${sysroot_source}" "${environment_root}/aapt2-x86-sysroot" "lib/x86_64-linux-gnu/libc.so.6"

gradle_environment_marker="${environment_root}/gradle-home/.repository-environment-v1"
if [[ ! -e "${gradle_environment_marker}" ]]; then
  # Imported daemon registries point at processes and caches outside this checkout.
  # User-level Gradle properties may also contain absolute host tool paths.
  rm -rf -- \
    "${environment_root}/gradle-home/daemon" \
    "${environment_root}/gradle-home/workers"
  rm -f -- "${environment_root}/gradle-home/gradle.properties"
  touch "${gradle_environment_marker}"
fi

printf 'android.aapt2FromMavenOverride=%s/scripts/aapt2\n' \
  "${repository_root}" >"${environment_root}/gradle-home/gradle.properties"

if [[ ! -x "${environment_root}/qemu/qemu-x86_64" ]]; then
  [[ -x "${qemu_source}" ]] || { echo "Missing ARM64 qemu-x86_64: ${qemu_source}" >&2; exit 1; }
  install -D -m 0755 "${qemu_source}" "${environment_root}/qemu/qemu-x86_64"
fi

local_properties="${repository_root}/local.properties"
if [[ -f "${local_properties}" ]]; then
  if grep -q '^sdk.dir=' "${local_properties}"; then
    sed -i "s|^sdk.dir=.*$|sdk.dir=${environment_root}/android-sdk|" "${local_properties}"
  else
    printf '\nsdk.dir=%s/android-sdk\n' "${environment_root}" >>"${local_properties}"
  fi
else
  printf 'sdk.dir=%s/android-sdk\n' "${environment_root}" >"${local_properties}"
fi

echo "Mobile build environment is ready at ${environment_root}"
