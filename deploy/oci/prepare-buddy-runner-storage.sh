#!/usr/bin/env bash
set -euo pipefail

min_root_free_gib="${AQUILA_BUDDY_MIN_ROOT_FREE_GIB:-8}"
min_data_free_gib="${AQUILA_BUDDY_MIN_DATA_FREE_GIB:-20}"

fail() {
  printf 'aquila-buddy runner storage check failed: %s\n' "$1" >&2
  exit 1
}

if docker info >/dev/null 2>&1; then
  DOCKER=(docker)
elif sudo -n docker info >/dev/null 2>&1; then
  DOCKER=(sudo -n docker)
else
  fail "docker is not available for the current user"
fi

assert_min_free_gib() {
  local path="$1"
  local min_gib="$2"
  local free_kib
  local min_kib

  [[ -d "$path" ]] || fail "missing storage path: $path"
  [[ "$min_gib" =~ ^[0-9]+$ ]] || fail "invalid minimum GiB value for $path: $min_gib"

  free_kib="$(df -Pk "$path" | awk 'NR == 2 { print $4 }')"
  min_kib=$((min_gib * 1024 * 1024))

  if (( free_kib < min_kib )); then
    fail "$path has less than ${min_gib}GiB free"
  fi
}

printf '=== storage before cleanup ===\n'
df -h / /var/lib/aquila-data
"${DOCKER[@]}" system df

# 실행 중 컨테이너와 volume은 보존하고, 이전 CD가 남긴 unused image/layer만 제거한다.
"${DOCKER[@]}" system prune -af

printf '=== storage after cleanup ===\n'
df -h / /var/lib/aquila-data
"${DOCKER[@]}" system df

assert_min_free_gib / "$min_root_free_gib"
assert_min_free_gib /var/lib/aquila-data "$min_data_free_gib"
