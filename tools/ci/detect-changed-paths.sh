#!/usr/bin/env bash
set -euo pipefail

changed_files_path="${1:?changed files path is required}"

backend=false
frontend=false
extension=false
pgvector=false
operations=false
docs_only=true
ci=false
saw_file=false

is_docs_file() {
  case "$1" in
    README.md|LICENSE|LICENSE.*|*.md|docs/**|.github/ISSUE_TEMPLATE/*|.github/release.yml)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

while IFS= read -r file; do
  [[ -z "${file}" ]] && continue
  saw_file=true

  if ! is_docs_file "${file}"; then
    docs_only=false
  fi

  case "${file}" in
    .github/workflows/ci.yml|tools/ci/**|tools/guards/**|tools/readme/validate-readme.mjs)
      ci=true
      ;;
  esac

  case "${file}" in
    back/**|.env.example|back/.env.example|tools/smoke/*.sh)
      backend=true
      ;;
  esac

  case "${file}" in
    front/**|tools/frontend/**)
      frontend=true
      ;;
  esac

  case "${file}" in
    extension/**|tools/extension/**)
      extension=true
      ;;
  esac

  case "${file}" in
    back/**|tools/test/**|tools/db/**|compose.yaml)
      pgvector=true
      ;;
  esac

  case "${file}" in
    deploy/**|infra/**|tools/smoke/**|tools/test/check-oci-*|.env.example|back/.env.example|.github/workflows/**|.github/actions/**|tools/release/**|tools/readme/**)
      operations=true
      ;;
  esac
done < "${changed_files_path}"

if [[ "${saw_file}" == "false" ]]; then
  backend=true
  frontend=true
  extension=true
  pgvector=true
  operations=true
  docs_only=false
fi

if [[ "${ci}" == "true" ]]; then
  backend=true
  frontend=true
  extension=true
  pgvector=true
  operations=true
  docs_only=false
fi

write_outputs() {
  {
    echo "backend=${backend}"
    echo "frontend=${frontend}"
    echo "extension=${extension}"
    echo "pgvector=${pgvector}"
    echo "operations=${operations}"
    echo "docs_only=${docs_only}"
    echo "ci=${ci}"
  } >> "${GITHUB_OUTPUT}"
}

write_summary() {
  {
    echo "### Changed files"
    sed 's/^/- `/' "${changed_files_path}" | sed 's/$/`/'
    echo
    echo "### CI gates"
    echo "- backend: ${backend}"
    echo "- frontend: ${frontend}"
    echo "- extension: ${extension}"
    echo "- pgvector: ${pgvector}"
    echo "- operations: ${operations}"
    echo "- docs_only: ${docs_only}"
    echo "- ci: ${ci}"
  } >> "${GITHUB_STEP_SUMMARY}"
}

if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
  write_outputs
else
  GITHUB_OUTPUT="/dev/stdout"
  write_outputs
fi

if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
  write_summary
fi
