#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
PSQL_BIN="${PSQL_BIN:-psql}"
DB_URL="${PGVECTOR_DB_URL:-${DATABASE_URL:-}}"
DRY_RUN=false

for arg in "$@"; do
  case "$arg" in
    --dry-run)
      DRY_RUN=true
      ;;
    *)
      echo "Unknown argument: $arg" >&2
      echo "Usage: PGVECTOR_DB_URL=<postgres-url> bash tools/db/init-postgres-pgvector.sh [--dry-run]" >&2
      exit 2
      ;;
  esac
done

SCHEMA_FILES=(
  "$REPO_ROOT/back/src/main/resources/schema.sql"
  "$REPO_ROOT/back/src/main/resources/schema-postgres-pgvector.sql"
)

if [[ "$DRY_RUN" == "true" ]]; then
  echo "pgvector init plan:"
  for schema_file in "${SCHEMA_FILES[@]}"; do
    echo "- ${schema_file#"$REPO_ROOT/"}"
  done
  exit 0
fi

if [[ -z "$DB_URL" ]]; then
  echo "PGVECTOR_DB_URL or DATABASE_URL is required." >&2
  exit 2
fi

for schema_file in "${SCHEMA_FILES[@]}"; do
  "$PSQL_BIN" "$DB_URL" -v ON_ERROR_STOP=1 -f "$schema_file"
done
