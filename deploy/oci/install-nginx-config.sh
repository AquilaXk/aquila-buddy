#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
env_file="${1:-/opt/aquila-buddy/aquila-buddy.env}"

if [[ ! -f "$env_file" ]]; then
  printf 'env file not found: %s\n' "$env_file" >&2
  exit 1
fi

set -a
# shellcheck disable=SC1090
source "$env_file"
set +a

config_path="${AQUILA_BUDDY_NGINX_CONFIG_PATH:-/etc/nginx/conf.d/aquila-buddy.conf}"
tmp_file="$(mktemp)"
trap 'rm -f "$tmp_file"' EXIT

"$script_dir/render-nginx-config.sh" "$env_file" > "$tmp_file"

if [[ "$(id -u)" -eq 0 ]]; then
  install -m 0644 "$tmp_file" "$config_path"
  nginx -t
  systemctl reload nginx
else
  sudo install -m 0644 "$tmp_file" "$config_path"
  sudo nginx -t
  sudo systemctl reload nginx
fi

printf 'installed nginx config: %s\n' "$config_path"
