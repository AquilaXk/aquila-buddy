#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

bash "${script_dir}/validate-provider-e2e-env.sh"

if [[ "${PROVIDER_E2E_SMOKE_ENABLED:-false}" != "true" ]]; then
  printf 'Provider E2E smoke runner skipped because PROVIDER_E2E_SMOKE_ENABLED is not true.\n'
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
}

if [[ "${PROVIDER_E2E_DRY_RUN:-false}" == "true" ]]; then
  print_plan
  exit 0
fi

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

step "01 GET /api/v1/health"
curl -sS -f -H "Accept: application/json" "${base_url}/api/v1/health" >/dev/null

step "02 GET /api/v1/users/me"
curl_base "${base_url}/api/v1/users/me" >/dev/null

step "02 POST /api/v1/users/me/vector"
curl_base -X POST "${base_url}/api/v1/users/me/vector" >/dev/null

step "03 POST /api/v1/issues/crawl/search"
curl_base -X POST --get --data-urlencode "q=${search_query}" "${base_url}/api/v1/issues/crawl/search" >/dev/null

step "03a POST /api/v1/embeddings/backfill"
curl_base \
  -X POST \
  -H "X-Aquila-Operation-Token: ${EMBEDDING_BACKFILL_OPERATION_TOKEN}" \
  --get \
  --data-urlencode "limit=${backfill_limit}" \
  "${base_url}/api/v1/embeddings/backfill" >/dev/null

step "04 GET /api/v1/issues/recommend"
curl_base "${base_url}/api/v1/issues/recommend" >/dev/null

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

step "08 POST /api/v1/pr/{draftId}/translate"
curl_base -X POST "${base_url}/api/v1/pr/${draft_id}/translate" >/dev/null

step "09 GET /api/v1/ai/observability"
curl_base --get --data-urlencode "limit=20" "${base_url}/api/v1/ai/observability" >/dev/null

printf 'Provider E2E smoke completed for provider=%s repo=%s issue=%s draft=%s\n' \
  "${AI_PROVIDER:-vertex-ai}" "${repo_full_name}" "${issue_number}" "${draft_id}"
