#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
env_file="${1:-}"

if [[ -n "$env_file" ]]; then
  if [[ ! -f "$env_file" ]]; then
    printf 'env file not found: %s\n' "$env_file" >&2
    exit 1
  fi
  set -a
  # shellcheck disable=SC1090
  source "$env_file"
  set +a
fi

if docker info >/dev/null 2>&1; then
  DOCKER=(docker)
elif sudo -n docker info >/dev/null 2>&1; then
  DOCKER=(sudo -n docker)
else
  printf 'docker is not available for the current user\n' >&2
  exit 1
fi

if [[ -z "${AQUILA_BUDDY_IMAGE_TAG:-}" || "${AQUILA_BUDDY_IMAGE_TAG:-}" == "replace-with-git-sha" ]]; then
  image_tag="$(git -C "$repo_root" rev-parse --short=12 HEAD)"
else
  image_tag="$AQUILA_BUDDY_IMAGE_TAG"
fi

image_prefix="${AQUILA_BUDDY_IMAGE_PREFIX:-ghcr.io/aquilaxk}"
backend_image="${AQUILA_BUDDY_BACKEND_IMAGE:-$image_prefix/aquila-buddy-back:$image_tag}"
frontend_image="${AQUILA_BUDDY_FRONTEND_IMAGE:-$image_prefix/aquila-buddy-front:$image_tag}"

if [[ "$backend_image" == *replace-with-git-sha* ]]; then
  backend_image="$image_prefix/aquila-buddy-back:$image_tag"
fi

if [[ "$frontend_image" == *replace-with-git-sha* ]]; then
  frontend_image="$image_prefix/aquila-buddy-front:$image_tag"
fi
api_url="${NEXT_PUBLIC_API_URL:-https://${AQUILA_BUDDY_DOMAIN:-buddy.example.com}}"
oauth_url="${NEXT_PUBLIC_BACKEND_OAUTH_URL:-$api_url/oauth2/authorization/github}"
platform="${AQUILA_BUDDY_BUILD_PLATFORM:-linux/arm64}"

platform_args=()
if [[ -n "$platform" ]]; then
  platform_args=(--platform "$platform")
fi

"${DOCKER[@]}" build "${platform_args[@]}" \
  -f "$repo_root/back/Dockerfile" \
  -t "$backend_image" \
  "$repo_root"

"${DOCKER[@]}" build "${platform_args[@]}" \
  --build-arg "NEXT_PUBLIC_API_URL=$api_url" \
  --build-arg "NEXT_PUBLIC_BACKEND_OAUTH_URL=$oauth_url" \
  -f "$repo_root/front/Dockerfile" \
  -t "$frontend_image" \
  "$repo_root"

if [[ "${AQUILA_BUDDY_PUSH_IMAGES:-false}" == "true" ]]; then
  "${DOCKER[@]}" push "$backend_image"
  "${DOCKER[@]}" push "$frontend_image"
fi

printf 'backend_image=%s\n' "$backend_image"
printf 'frontend_image=%s\n' "$frontend_image"
