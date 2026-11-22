#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
evidence_file="${PROVIDER_E2E_EVIDENCE_FILE:-}"
current_step="startup"

write_evidence() {
  if [[ -z "${evidence_file}" ]]; then
    return
  fi

  local result="$1"
  local failure_reason="${2:-none}"
  local draft_id="${3:-}"
  local step_status="pass"
  if [[ "${result}" == "dry-run" ]]; then
    step_status="planned"
  elif [[ "${result}" == "skipped" ]]; then
    step_status="skipped"
  elif [[ "${result}" == "failed" ]]; then
    step_status="failed"
  fi

  mkdir -p "$(dirname "${evidence_file}")"
  {
    printf '# Provider E2E Smoke Evidence\n\n'
    printf -- '- executed_at: %s\n' "$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
    printf -- '- commit: %s\n' "${GITHUB_SHA:-unknown}"
    printf -- '- github_run_id: %s\n' "${GITHUB_RUN_ID:-local}"
    printf -- '- provider: %s\n' "${AI_PROVIDER:-vertex-ai}"
    printf -- '- langfuse_enabled: %s\n' "${LANGFUSE_ENABLED:-false}"
    printf -- '- backend_base_url: %s\n' "${BACKEND_BASE_URL:-unset}"
    printf -- '- repo_full_name: %s\n' "${PROVIDER_E2E_REPO_FULL_NAME:-unset}"
    printf -- '- issue_number: %s\n' "${PROVIDER_E2E_ISSUE_NUMBER:-unset}"
    printf -- '- result: %s\n' "${result}"
    printf -- '- failure_reason: %s\n' "${failure_reason}"
    if [[ -n "${draft_id}" ]]; then
      printf -- '- draft_id: %s\n' "${draft_id}"
    fi
    printf '\n## smoke 결과\n\n'
    printf '| area | result | evidence | note |\n'
    printf '| --- | --- | --- | --- |\n'
    printf '| provider-e2e | %s | tools/smoke/run-provider-e2e-smoke.sh | %s |\n' "${step_status}" "${failure_reason}"
    printf '| profile-vector | %s | POST /api/v1/users/me/vector | profile embedding 저장 확인 |\n' "${step_status}"
    printf '| issue-crawl | %s | POST /api/v1/issues/crawl/search | GitHub issue 수집 확인 |\n' "${step_status}"
    printf '| embedding-backfill | %s | POST /api/v1/embeddings/backfill | profile/issue embedding backfill 확인 |\n' "${step_status}"
    printf '| pgvector-recommendation | %s | GET /api/v1/issues/recommend | pgvector 추천 흐름 확인 |\n' "${step_status}"
    printf '| issue-guide | %s | GET /api/v1/issues/{issueId}/guide | provider 기반 guide 생성 확인 |\n' "${step_status}"
    printf '| pr-draft | %s | POST /api/v1/pr | issue/diff 기반 PR 초안 생성 확인 |\n' "${step_status}"
    printf '| translation | %s | POST /api/v1/pr/{draftId}/translate | PR 초안 번역 확인 |\n' "${step_status}"
    printf '| observability-trace | %s | GET /api/v1/ai/observability | prompt/model/latency/token/failure metadata 확인 |\n' "${step_status}"
  } > "${evidence_file}"
  printf 'Provider E2E smoke evidence written to %s\n' "${evidence_file}"
}

on_error() {
  local status=$?
  write_evidence "failed" "command failed at ${current_step}"
  exit "${status}"
}

skip_file="${TMPDIR:-/tmp}/provider-e2e-smoke-skip.$$"
rm -f "${skip_file}"
PROVIDER_E2E_SKIP_FILE="${skip_file}" bash "${script_dir}/validate-provider-e2e-env.sh"
if [[ -s "${skip_file}" ]]; then
  skip_reason="$(cat "${skip_file}")"
  rm -f "${skip_file}"
  write_evidence "skipped" "${skip_reason}"
  exit 0
fi
rm -f "${skip_file}"

if [[ "${PROVIDER_E2E_SMOKE_ENABLED:-false}" != "true" ]]; then
  printf 'Provider E2E smoke runner skipped because PROVIDER_E2E_SMOKE_ENABLED is not true.\n'
  write_evidence "skipped" "PROVIDER_E2E_SMOKE_ENABLED is not true"
  exit 0
fi

print_plan() {
  printf 'Provider E2E smoke path:\n'
  printf '01 GET /api/v1/health\n'
  printf '02 POST /api/v1/users/me/vector\n'
  printf '03 POST /api/v1/issues/crawl/search\n'
  printf '03a POST /api/v1/embeddings/backfill\n'
  printf '04 GET /api/v1/issues/recommend\n'
  printf '05 GET /api/v1/issues/lookup\n'
  printf '06 GET /api/v1/issues/{issueId}/guide\n'
  printf '07 POST /api/v1/pr\n'
  printf '08 POST /api/v1/pr/{draftId}/translate\n'
  printf '09 GET /api/v1/ai/observability\n'
  printf 'Evidence rows: profile-vector, issue-crawl, embedding-backfill, pgvector-recommendation, issue-guide, pr-draft, translation, observability-trace\n'
  printf 'Evidence policy: record result, command or run URL, and retry note without secret values.\n'
}

if [[ "${PROVIDER_E2E_DRY_RUN:-false}" == "true" ]]; then
  print_plan
  write_evidence "dry-run" "dry-run request path only"
  exit 0
fi

trap on_error ERR

if ! command -v curl >/dev/null 2>&1; then
  printf 'curl is required to run provider E2E smoke.\n' >&2
  exit 1
fi

auth_header="${PROVIDER_E2E_AUTH_HEADER:-}"
if [[ -z "${auth_header}" ]]; then
  printf 'missing required env: PROVIDER_E2E_AUTH_HEADER for live provider E2E smoke\n' >&2
  printf 'run OAuth first and pass the resulting Authorization or Cookie header as PROVIDER_E2E_AUTH_HEADER.\n' >&2
  exit 1
fi

base_url="${BACKEND_BASE_URL%/}"
repo_full_name="${PROVIDER_E2E_REPO_FULL_NAME}"
issue_number="${PROVIDER_E2E_ISSUE_NUMBER}"
base_branch="${PROVIDER_E2E_BASE_BRANCH}"
head_branch="${PROVIDER_E2E_HEAD_BRANCH}"
search_query="${PROVIDER_E2E_SEARCH_QUERY:-repo:${repo_full_name} is:issue is:open ${issue_number}}"
backfill_limit="${PROVIDER_E2E_BACKFILL_LIMIT:-${RECOMMENDATION_SIMILARITY_LIMIT}}"
diff_content="${PROVIDER_E2E_DIFF_CONTENT:-Provider E2E smoke diff placeholder.}"

json_escape() {
  local value="$1"
  value="${value//\\/\\\\}"
  value="${value//\"/\\\"}"
  value="${value//$'\n'/\\n}"
  printf '%s' "${value}"
}

extract_string_field() {
  local response="$1"
  local field="$2"
  printf '%s\n' "${response}" \
    | sed -nE 's/.*"'"${field}"'"[[:space:]]*:[[:space:]]*"([^"]+)".*/\1/p' \
    | head -n 1
}

curl_base() {
  curl -sS -f \
    -H "${auth_header}" \
    -H "Accept: application/json" \
    "$@"
}

step() {
  printf '[provider-e2e] %s\n' "$1"
}

print_plan

current_step="01 GET /api/v1/health"
step "01 GET /api/v1/health"
curl -sS -f -H "Accept: application/json" "${base_url}/api/v1/health" >/dev/null

current_step="02 GET /api/v1/users/me"
step "02 GET /api/v1/users/me"
curl_base "${base_url}/api/v1/users/me" >/dev/null

current_step="02 POST /api/v1/users/me/vector"
step "02 POST /api/v1/users/me/vector"
curl_base -X POST "${base_url}/api/v1/users/me/vector" >/dev/null

current_step="03 POST /api/v1/issues/crawl/search"
step "03 POST /api/v1/issues/crawl/search"
curl_base -X POST --get --data-urlencode "q=${search_query}" "${base_url}/api/v1/issues/crawl/search" >/dev/null

current_step="03a POST /api/v1/embeddings/backfill"
step "03a POST /api/v1/embeddings/backfill"
curl_base \
  -X POST \
  -H "X-Aquila-Operation-Token: ${EMBEDDING_BACKFILL_OPERATION_TOKEN}" \
  --get \
  --data-urlencode "limit=${backfill_limit}" \
  "${base_url}/api/v1/embeddings/backfill" >/dev/null

current_step="04 GET /api/v1/issues/recommend"
step "04 GET /api/v1/issues/recommend"
curl_base "${base_url}/api/v1/issues/recommend" >/dev/null

current_step="05 GET /api/v1/issues/lookup"
step "05 GET /api/v1/issues/lookup"
issue_response="$(
  curl_base \
    --get \
    --data-urlencode "repo=${repo_full_name}" \
    --data-urlencode "number=${issue_number}" \
    "${base_url}/api/v1/issues/lookup"
)"
issue_id="$(extract_string_field "${issue_response}" "id")"
if [[ -z "${issue_id}" ]]; then
  printf 'failed to extract issue id from /api/v1/issues/lookup response.\n' >&2
  exit 1
fi

current_step="06 GET /api/v1/issues/{issueId}/guide"
step "06 GET /api/v1/issues/{issueId}/guide"
curl_base "${base_url}/api/v1/issues/${issue_id}/guide" >/dev/null

draft_body="$(
  printf '{"upstreamRepo":"%s","githubIssueNumber":%s,"baseBranch":"%s","headBranch":"%s","diffContent":"%s","sourceIssueId":"%s"}' \
    "$(json_escape "${repo_full_name}")" \
    "${issue_number}" \
    "$(json_escape "${base_branch}")" \
    "$(json_escape "${head_branch}")" \
    "$(json_escape "${diff_content}")" \
    "$(json_escape "${issue_id}")"
)"

current_step="07 POST /api/v1/pr"
step "07 POST /api/v1/pr"
draft_response="$(
  curl_base \
    -X POST \
    -H "Content-Type: application/json" \
    --data "${draft_body}" \
    "${base_url}/api/v1/pr"
)"
draft_id="$(extract_string_field "${draft_response}" "id")"
if [[ -z "${draft_id}" ]]; then
  printf 'failed to extract draft id from /api/v1/pr response.\n' >&2
  exit 1
fi

current_step="08 POST /api/v1/pr/{draftId}/translate"
step "08 POST /api/v1/pr/{draftId}/translate"
curl_base -X POST "${base_url}/api/v1/pr/${draft_id}/translate" >/dev/null

current_step="09 GET /api/v1/ai/observability"
step "09 GET /api/v1/ai/observability"
curl_base --get --data-urlencode "limit=20" "${base_url}/api/v1/ai/observability" >/dev/null

write_evidence "pass" "none" "${draft_id}"
printf 'Provider E2E smoke completed for provider=%s repo=%s issue=%s draft=%s\n' \
  "${AI_PROVIDER:-vertex-ai}" "${repo_full_name}" "${issue_number}" "${draft_id}"
