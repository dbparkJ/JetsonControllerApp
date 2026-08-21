#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
properties_file="${repo_root}/local.properties"

if [[ ! -t 0 ]]; then
    echo "Run this script in an interactive terminal so the key is not stored in shell history." >&2
    exit 2
fi

printf 'VWorld API key: ' >&2
IFS= read -r -s api_key
printf '\n' >&2

if [[ ! "${api_key}" =~ ^[A-Za-z0-9._-]{1,256}$ ]]; then
    unset api_key
    echo "The key must contain only letters, digits, dots, underscores, or hyphens." >&2
    exit 2
fi

umask 077
temporary_file="$(mktemp "${properties_file}.tmp.XXXXXX")"
cleanup() {
    rm -f -- "${temporary_file}"
    unset api_key
}
trap cleanup EXIT

if [[ -f "${properties_file}" ]]; then
    awk '!/^[[:space:]]*vworld\.apiKey[[:space:]]*=/' "${properties_file}" \
        > "${temporary_file}"
fi
printf 'vworld.apiKey=%s\n' "${api_key}" >> "${temporary_file}"
chmod 600 "${temporary_file}"
mv -f -- "${temporary_file}" "${properties_file}"
trap - EXIT
unset api_key

echo "VWorld API key saved to ignored local.properties (mode 600)."
