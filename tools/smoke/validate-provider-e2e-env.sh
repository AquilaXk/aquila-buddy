#!/usr/bin/env bash
set -u

if [[ "${PROVIDER_E2E_SMOKE_ENABLED:-false}" != "true" ]]; then
  printf 'Provider E2E smoke is disabled. Set PROVIDER_E2E_SMOKE_ENABLED=true to validate required env.\n'
  exit 0
fi

missing=0
dry_run="${PROVIDER_E2E_DRY_RUN:-false}"

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

require_live_var() {
  if [[ "${dry_run}" == "true" ]]; then
    return
  fi
  require_var "$1"
}

require_live_integer() {
  if [[ "${dry_run}" == "true" ]]; then
    return
  fi
  require_integer "$1"
}

require_live_any_var() {
  if [[ "${dry_run}" == "true" ]]; then
    return
  fi
  require_any_var "$1" "$2"
}

require_github_token_permission() {
  if [[ "${dry_run}" == "true" ]]; then
    return
  fi
  local permission="$1"
  local value="${GITHUB_TOKEN_PERMISSIONS:-}"
  local normalized
  normalized=",${value//[[:space:]]/},"
  if [[ "${normalized}" != *",${permission},"* ]]; then
    printf 'missing GitHub token permission: %s\n' "${permission}" >&2
    missing=1
  fi
}

require_var BACKEND_BASE_URL

provider="${AI_PROVIDER:-vertex-ai}"
case "${provider}" in
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
  openai-compatible)
    require_live_any_var AI_API_KEY OPENAI_API_KEY
    require_var AI_CHAT_MODEL
    require_var AI_EMBEDDING_MODEL
    require_integer AI_EMBEDDING_DIMENSIONS
    ;;
  *)
    printf 'AI_PROVIDER must be vertex-ai or openai-compatible for provider E2E smoke.\n' >&2
    missing=1
    ;;
esac

require_live_var GITHUB_TOKEN
require_live_var GITHUB_TOKEN_PERMISSIONS
if [[ -n "${GITHUB_TOKEN_PERMISSIONS:-}" ]]; then
  require_github_token_permission "metadata:read"
  require_github_token_permission "contents:read"
  require_github_token_permission "pull-requests:read"
  require_github_token_permission "issues:read"
fi

if [[ "${RECOMMENDATION_SIMILARITY_PROVIDER:-}" != "pgvector" ]]; then
  printf 'missing required env: RECOMMENDATION_SIMILARITY_PROVIDER=pgvector\n' >&2
  missing=1
fi
require_live_var DB_URL
require_live_var DB_USER
require_live_var DB_PASSWORD
require_integer RECOMMENDATION_SIMILARITY_LIMIT
require_live_var EMBEDDING_BACKFILL_OPERATION_TOKEN

auth_mode="${PROVIDER_E2E_AUTH_MODE:-default-github-id}"
case "${auth_mode}" in
  default-github-id)
    require_live_var DEFAULT_GITHUB_ID
    ;;
  oauth-header)
    require_live_var PROVIDER_E2E_AUTH_HEADER
    ;;
  *)
    printf 'PROVIDER_E2E_AUTH_MODE must be default-github-id or oauth-header.\n' >&2
    missing=1
    ;;
esac

require_var PROVIDER_E2E_REPO_FULL_NAME
require_integer PROVIDER_E2E_ISSUE_NUMBER
require_var PROVIDER_E2E_BASE_BRANCH
require_var PROVIDER_E2E_HEAD_BRANCH

if [[ "${missing}" -ne 0 ]]; then
  printf 'Provider E2E smoke env validation failed.\n' >&2
  exit 1
fi

printf 'Provider E2E smoke env validation passed for provider=%s auth=%s similarity=pgvector\n' \
  "${provider}" "${auth_mode}"
if [[ "${dry_run}" == "true" ]]; then
  printf 'Provider E2E dry-run validation skipped live secret requirements.\n'
fi
