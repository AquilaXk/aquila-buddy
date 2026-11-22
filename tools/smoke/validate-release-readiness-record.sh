#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
RECORD_DIR="$ROOT_DIR/tools/smoke/release-readiness"

fail() {
  echo "[release-readiness] $*" >&2
  exit 1
}

collect_records() {
  if [ "$#" -gt 0 ]; then
    printf '%s\n' "$@"
    return
  fi

  find "$RECORD_DIR" -maxdepth 1 -type f -name '*.md' \
    ! -name 'README.md' \
    ! -name 'TEMPLATE.md' \
    ! -name 'PARITY_COMPLETION_MATRIX.md' \
    | sort
}

validate_record() {
  local file="$1"

  [ -f "$file" ] || fail "record not found: $file"

  local required_text=(
    "# Release Readiness Record"
    "## 대상"
    "## smoke 결과"
    "## secret 점검"
    "## rollback 기준"
    "## 재실행 체크리스트"
    "commit:"
    "ci_run:"
    "secret_values_recorded: \`false\`"
  )

  for text in "${required_text[@]}"; do
    grep -Fq "$text" "$file" || fail "$file is missing required text: $text"
  done

  local areas=(
    backend
    frontend
    extension
    pgvector
    operations
    ai-provider
    ai-prompt-evaluation
    langfuse
    extension-github-live
    provider-e2e
    profile-vector
    issue-crawl
    embedding-backfill
    pgvector-recommendation
    issue-guide
    pr-draft
    translation
    observability-trace
  )

  for area in "${areas[@]}"; do
    grep -Eq "^\| ${area} \| (pass|fail|pending-manual|blocked|not-run) \|" "$file" \
      || fail "$file is missing smoke result row for: $area"
  done

  if grep -En '(TBD|TODO|<[^>]+>)' "$file"; then
    fail "$file contains placeholder text"
  fi

  if grep -En '(ghp_[A-Za-z0-9_]+|github_pat_[A-Za-z0-9_]+|sk-[A-Za-z0-9]{10,}|Bearer[[:space:]][A-Za-z0-9._-]{10,}|(AI_API_KEY|OPENAI_API_KEY|LANGFUSE_SECRET_KEY|GITHUB_TOKEN|DB_PASSWORD|PASSWORD|SECRET|TOKEN)=[^[:space:]`<]+)' "$file"; then
    fail "$file appears to contain a secret-like value"
  fi

  echo "[release-readiness] ok: ${file#$ROOT_DIR/}"
}

records=()
while IFS= read -r record; do
  [ -n "$record" ] && records+=("$record")
done < <(collect_records "$@")

if [ "${#records[@]}" -eq 0 ]; then
  echo "[release-readiness] no release readiness records found"
  exit 0
fi

for record in "${records[@]}"; do
  validate_record "$record"
done
