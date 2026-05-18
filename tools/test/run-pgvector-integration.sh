#!/usr/bin/env bash
set -euo pipefail

VALIDATE_ONLY=false

for arg in "$@"; do
  case "$arg" in
    --validate-only)
      VALIDATE_ONLY=true
      ;;
    *)
      echo "Unknown argument: $arg" >&2
      echo "Usage: bash tools/test/run-pgvector-integration.sh [--validate-only]" >&2
      exit 2
      ;;
  esac
done

node tools/db/validate-pgvector-init.mjs
bash tools/db/init-postgres-pgvector.sh --dry-run

if [[ "$VALIDATE_ONLY" == "true" ]]; then
  exit 0
fi

export PGVECTOR_INTEGRATION_TESTS="${PGVECTOR_INTEGRATION_TESTS:-true}"
export PGVECTOR_TEST_DB_URL="${PGVECTOR_TEST_DB_URL:-jdbc:postgresql://localhost:5432/aquila_buddy}"
export PGVECTOR_TEST_DB_USER="${PGVECTOR_TEST_DB_USER:-aquila}"
export PGVECTOR_TEST_DB_PASSWORD="${PGVECTOR_TEST_DB_PASSWORD:-aquila}"

./back/gradlew -p back test \
  --tests com.aquilabuddy.adapter.out.persistence.issue.PgvectorRecommendedIssueSimilarityIntegrationTest \
  --no-daemon
