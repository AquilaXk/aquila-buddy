#!/usr/bin/env bash
set -u

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
  *)
    printf 'AI_PROVIDER must be vertex-ai for runtime smoke.\n' >&2
    missing=1
    ;;
esac

require_var GITHUB_TOKEN

similarity_provider="${RECOMMENDATION_SIMILARITY_PROVIDER:-score}"
case "${similarity_provider}" in
  score)
    ;;
  pgvector)
    require_var DB_URL
    require_var DB_USER
    require_var DB_PASSWORD
    require_integer RECOMMENDATION_SIMILARITY_LIMIT
    ;;
  *)
    printf 'RECOMMENDATION_SIMILARITY_PROVIDER must be score or pgvector.\n' >&2
    missing=1
    ;;
esac

if [[ "${missing}" -ne 0 ]]; then
  printf 'AI runtime smoke env validation failed.\n' >&2
  exit 1
fi

printf 'AI runtime smoke env validation passed for provider=%s similarity=%s\n' "${provider}" "${similarity_provider}"
