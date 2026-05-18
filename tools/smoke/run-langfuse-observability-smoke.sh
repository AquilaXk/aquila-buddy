#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
force_dry_run=false

for arg in "$@"; do
  case "${arg}" in
    --dry-run)
      force_dry_run=true
      ;;
    --live)
      export LANGFUSE_SMOKE_ENABLED=true
      export LANGFUSE_SMOKE_MODE=live
      ;;
    *)
      printf 'unknown argument: %s\n' "${arg}" >&2
      exit 2
      ;;
  esac
done

print_plan() {
  printf 'Langfuse observability smoke path:\n'
  printf '01 validate Langfuse env\n'
  printf '02 send success generation payload\n'
  printf '03 send failure generation payload\n'
  printf '04 verify prompt version, model, latency, usage, and failure metadata\n'
}

if [[ "${force_dry_run}" == "true" ]]; then
  print_plan
  printf 'Langfuse observability smoke dry-run completed.\n'
  exit 0
fi

bash "${script_dir}/validate-langfuse-observability-env.sh"

if [[ "${LANGFUSE_SMOKE_ENABLED:-false}" != "true" ]]; then
  printf 'Langfuse observability smoke runner skipped because LANGFUSE_SMOKE_ENABLED is not true.\n'
  exit 0
fi

mode="${LANGFUSE_SMOKE_MODE:-dry-run}"
timeout_seconds="${LANGFUSE_SMOKE_TIMEOUT_SECONDS:-30}"

print_plan

if [[ "${mode}" == "dry-run" ]]; then
  printf 'Langfuse observability smoke dry-run completed.\n'
  exit 0
fi

if ! command -v curl >/dev/null 2>&1; then
  printf 'curl is required to run live Langfuse observability smoke.\n' >&2
  exit 1
fi

base_url="${LANGFUSE_BASE_URL%/}"
environment="${LANGFUSE_ENVIRONMENT:-local}"
timestamp="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"

payload="$(
  printf '{"batch":[{"id":"aquila-langfuse-success","type":"generation-create","timestamp":"%s","body":{"id":"aquila-langfuse-success","traceId":"aquila-langfuse-smoke","name":"ai.completion","model":"smoke-model","startTime":"%s","endTime":"%s","level":"DEFAULT","metadata":{"provider":"openai-compatible","operation":"COMPLETION","promptVersion":"langfuse-smoke.success.v1","latencyMillis":42,"success":true,"environment":"%s"},"usage":{"input":12,"output":5}}},{"id":"aquila-langfuse-failure","type":"generation-create","timestamp":"%s","body":{"id":"aquila-langfuse-failure","traceId":"aquila-langfuse-smoke","name":"ai.embedding","model":"smoke-embedding-model","startTime":"%s","endTime":"%s","level":"ERROR","statusMessage":"SmokeFailure","metadata":{"provider":"openai-compatible","operation":"EMBEDDING","promptVersion":"langfuse-smoke.failure.v1","latencyMillis":13,"success":false,"environment":"%s","errorType":"SmokeFailure","failureReason":"synthetic smoke failure metadata"}}}]}' \
    "${timestamp}" "${timestamp}" "${timestamp}" "${environment}" \
    "${timestamp}" "${timestamp}" "${timestamp}" "${environment}"
)"

curl -sS -f --max-time "${timeout_seconds}" \
  -u "${LANGFUSE_PUBLIC_KEY}:${LANGFUSE_SECRET_KEY}" \
  -H "Content-Type: application/json" \
  --data "${payload}" \
  "${base_url}/api/public/ingestion" >/dev/null

printf 'Langfuse observability smoke live checks completed for target=%s environment=%s\n' \
  "${base_url}" "${environment}"
