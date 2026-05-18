#!/usr/bin/env bash
set -euo pipefail

export PGVECTOR_INTEGRATION_TESTS="${PGVECTOR_INTEGRATION_TESTS:-true}"
export PGVECTOR_TEST_DB_URL="${PGVECTOR_TEST_DB_URL:-jdbc:postgresql://localhost:5432/aquila_buddy}"
export PGVECTOR_TEST_DB_USER="${PGVECTOR_TEST_DB_USER:-aquila}"
export PGVECTOR_TEST_DB_PASSWORD="${PGVECTOR_TEST_DB_PASSWORD:-aquila}"

./back/gradlew -p back test \
  --tests com.aquilabuddy.adapter.out.persistence.issue.PgvectorRecommendedIssueSimilarityIntegrationTest \
  --no-daemon
