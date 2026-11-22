#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

bash "${script_dir}/validate-ai-provider-smoke-env.sh"

if [[ "${AI_PROVIDER_SMOKE_ENABLED:-false}" != "true" ]]; then
  printf 'AI provider smoke runner skipped because AI_PROVIDER_SMOKE_ENABLED is not true.\n'
  exit 0
fi

target="${AI_PROVIDER_SMOKE_TARGET:-${AI_PROVIDER:-vertex-ai}}"
mode="${AI_PROVIDER_SMOKE_MODE:-dry-run}"
timeout_seconds="${AI_PROVIDER_SMOKE_TIMEOUT_SECONDS:-30}"

print_plan() {
  printf 'AI provider smoke path:\n'
  printf '01 OpenAI-compatible chat completion\n'
  printf '02 OpenAI-compatible embedding\n'
  printf '03 Vertex AI text generation\n'
  printf '04 Vertex AI embedding\n'
  printf '05 provider timeout/error observation\n'
  printf '06 provider fallback path\n'
}

print_plan

if [[ "${mode}" == "dry-run" ]]; then
  printf 'AI provider smoke dry-run completed for target=%s timeout=%ss\n' "${target}" "${timeout_seconds}"
  exit 0
fi

if ! command -v curl >/dev/null 2>&1; then
  printf 'curl is required to run live AI provider smoke.\n' >&2
  exit 1
fi

case "${target}" in
  openai-compatible)
    api_key="${AI_API_KEY:-${OPENAI_API_KEY:-}}"
    base_url="${AI_API_BASE_URL%/}"
    printf '[ai-provider-smoke] OpenAI-compatible chat completion\n'
    curl -sS -f --max-time "${timeout_seconds}" \
      -H "Authorization: Bearer ${api_key}" \
      -H "Content-Type: application/json" \
      --data "{\"model\":\"${AI_CHAT_MODEL}\",\"messages\":[{\"role\":\"user\",\"content\":\"Return the word ok.\"}],\"temperature\":0}" \
      "${base_url}/chat/completions" >/dev/null

    printf '[ai-provider-smoke] OpenAI-compatible embedding\n'
    curl -sS -f --max-time "${timeout_seconds}" \
      -H "Authorization: Bearer ${api_key}" \
      -H "Content-Type: application/json" \
      --data "{\"model\":\"${AI_EMBEDDING_MODEL}\",\"input\":\"Java Spring smoke\",\"encoding_format\":\"float\",\"dimensions\":${AI_EMBEDDING_DIMENSIONS}}" \
      "${base_url}/embeddings" >/dev/null
    ;;
  vertex-ai)
    printf 'Vertex AI live smoke requires application-authenticated backend or Google SDK smoke and is documented as an operator step.\n' >&2
    printf 'Use dry-run here, then run backend/provider E2E with Vertex credentials in the target environment.\n' >&2
    exit 2
    ;;
  *)
    printf 'Unsupported AI provider smoke target: %s\n' "${target}" >&2
    exit 2
    ;;
esac

printf 'AI provider smoke live checks completed for target=%s\n' "${target}"
