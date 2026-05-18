#!/usr/bin/env bash
set -u

if [[ "${AI_PROVIDER_SMOKE_ENABLED:-false}" != "true" ]]; then
  printf 'AI provider smoke is disabled. Set AI_PROVIDER_SMOKE_ENABLED=true to validate required env.\n'
  exit 0
fi

missing=0

require_var() {
  local name="$1"
  local value="${!name:-}"
  if [[ -z "${value}" ]]; then
    printf 'missing required env: %s\n' "${name}" >&2
    missing=1
  fi
}

require_integer() {
  local name="$1"
  local value="${!name:-}"
  if [[ -z "${value}" || ! "${value}" =~ ^[0-9]+$ ]]; then
    printf 'env must be a positive integer: %s\n' "${name}" >&2
    missing=1
  fi
}

require_any_var() {
  local first_name="$1"
  local second_name="$2"
  local first_value="${!first_name:-}"
  local second_value="${!second_name:-}"
  if [[ -z "${first_value}" && -z "${second_value}" ]]; then
    printf 'missing required env: %s or %s\n' "${first_name}" "${second_name}" >&2
    missing=1
  fi
}

target="${AI_PROVIDER_SMOKE_TARGET:-${AI_PROVIDER:-vertex-ai}}"
mode="${AI_PROVIDER_SMOKE_MODE:-dry-run}"
timeout_seconds="${AI_PROVIDER_SMOKE_TIMEOUT_SECONDS:-30}"

case "${mode}" in
  dry-run|live)
    ;;
  *)
    printf 'AI_PROVIDER_SMOKE_MODE must be dry-run or live.\n' >&2
    missing=1
    ;;
esac

require_integer AI_PROVIDER_SMOKE_TIMEOUT_SECONDS

case "${target}" in
  openai-compatible)
    require_var AI_API_BASE_URL
    require_any_var AI_API_KEY OPENAI_API_KEY
    require_var AI_CHAT_MODEL
    require_var AI_EMBEDDING_MODEL
    require_integer AI_EMBEDDING_DIMENSIONS
    ;;
  vertex-ai)
    require_var GOOGLE_CLOUD_PROJECT_ID
    require_var VERTEX_AI_LOCATION
    require_var VERTEX_AI_MODEL
    require_var VERTEX_AI_EMBEDDING_MODEL
    require_integer VERTEX_AI_EMBEDDING_DIMENSIONS
    credential_path="${VERTEX_AI_CREDENTIALS_PATH:-${GOOGLE_APPLICATION_CREDENTIALS:-}}"
    if [[ -z "${credential_path}" ]]; then
      printf 'missing required env: VERTEX_AI_CREDENTIALS_PATH or GOOGLE_APPLICATION_CREDENTIALS\n' >&2
      missing=1
    elif [[ ! -e "${credential_path}" ]]; then
      printf 'credential path does not exist yet: %s\n' "${credential_path}" >&2
      printf 'create it locally, but do not commit the credential file.\n' >&2
    fi
    ;;
  *)
    printf 'AI_PROVIDER_SMOKE_TARGET must be vertex-ai or openai-compatible.\n' >&2
    missing=1
    ;;
esac

if [[ "${missing}" -ne 0 ]]; then
  printf 'AI provider smoke env validation failed.\n' >&2
  exit 1
fi

printf 'AI provider smoke env validation passed for target=%s mode=%s timeout=%ss\n' \
  "${target}" "${mode}" "${timeout_seconds}"
