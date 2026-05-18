#!/usr/bin/env bash
set -euo pipefail

VALIDATE_ONLY=false
USE_COMPOSE=false
DRY_RUN=false
COMPOSE_FILE="tools/test/compose.pgvector-integration.yaml"

usage() {
  echo "Usage: bash tools/test/run-pgvector-integration.sh [--validate-only] [--compose] [--dry-run]" >&2
}

for arg in "$@"; do
  case "$arg" in
    --validate-only)
      VALIDATE_ONLY=true
      ;;
    --compose)
      USE_COMPOSE=true
      ;;
    --dry-run)
      DRY_RUN=true
      ;;
    *)
      echo "Unknown argument: $arg" >&2
      usage
      exit 2
      ;;
  esac
done

compose_up() {
  docker compose -f "$COMPOSE_FILE" up -d --wait postgres
}

compose_down() {
  docker compose -f "$COMPOSE_FILE" down --volumes --remove-orphans
}

node tools/db/validate-pgvector-init.mjs
bash tools/db/init-postgres-pgvector.sh --dry-run

if [[ "$VALIDATE_ONLY" == "true" ]]; then
  exit 0
fi

if [[ "$USE_COMPOSE" == "true" ]]; then
  export PGVECTOR_TEST_DB_URL="${PGVECTOR_TEST_DB_URL:-jdbc:postgresql://localhost:${PGVECTOR_TEST_DB_PORT:-54329}/aquila_buddy}"
  export PGVECTOR_TEST_DB_USER="${PGVECTOR_TEST_DB_USER:-aquila}"
  export PGVECTOR_TEST_DB_PASSWORD="${PGVECTOR_TEST_DB_PASSWORD:-aquila}"
else
  export PGVECTOR_TEST_DB_URL="${PGVECTOR_TEST_DB_URL:-jdbc:postgresql://localhost:5432/aquila_buddy}"
  export PGVECTOR_TEST_DB_USER="${PGVECTOR_TEST_DB_USER:-aquila}"
  export PGVECTOR_TEST_DB_PASSWORD="${PGVECTOR_TEST_DB_PASSWORD:-aquila}"
fi

if [[ "$DRY_RUN" == "true" ]]; then
  echo "pgvector integration test plan:"
  echo "compose=$USE_COMPOSE"
  echo "compose_file=$COMPOSE_FILE"
  echo "db_url=$PGVECTOR_TEST_DB_URL"
  echo "gradle_test=PgvectorRecommendedIssueSimilarityIntegrationTest"
  exit 0
fi

if [[ "$USE_COMPOSE" == "true" ]]; then
  trap 'compose_down' EXIT
  compose_up
fi

export PGVECTOR_INTEGRATION_TESTS="${PGVECTOR_INTEGRATION_TESTS:-true}"

./back/gradlew -p back test \
  --tests com.aquilabuddy.adapter.out.persistence.issue.PgvectorRecommendedIssueSimilarityIntegrationTest \
  --no-daemon
