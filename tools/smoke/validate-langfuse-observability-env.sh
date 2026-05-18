#!/usr/bin/env bash
set -u

if [[ "${LANGFUSE_SMOKE_ENABLED:-false}" != "true" ]]; then
  printf 'Langfuse observability smoke is disabled. Set LANGFUSE_SMOKE_ENABLED=true to validate required env.\n'
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

mode="${LANGFUSE_SMOKE_MODE:-dry-run}"
case "${mode}" in
  dry-run|live)
    ;;
  *)
    printf 'LANGFUSE_SMOKE_MODE must be dry-run or live.\n' >&2
    missing=1
    ;;
esac

require_var LANGFUSE_BASE_URL
require_var LANGFUSE_PUBLIC_KEY
require_var LANGFUSE_SECRET_KEY
require_integer LANGFUSE_SMOKE_TIMEOUT_SECONDS

if [[ "${missing}" -ne 0 ]]; then
  printf 'Langfuse observability smoke env validation failed.\n' >&2
  exit 1
fi

target="${LANGFUSE_BASE_URL%/}"
environment="${LANGFUSE_ENVIRONMENT:-local}"
timeout_seconds="${LANGFUSE_SMOKE_TIMEOUT_SECONDS}"

printf 'Langfuse observability smoke env validation passed for target=%s mode=%s environment=%s timeout=%ss\n' \
  "${target}" "${mode}" "${environment}" "${timeout_seconds}"
