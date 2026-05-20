#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
env_file="${1:-/opt/aquila-buddy/aquila-buddy.env}"

fail() {
  printf 'aquila-buddy deploy failed: %s\n' "$1" >&2
  exit 1
}

require_var() {
  local name="$1"
  [[ -n "${!name:-}" ]] || fail "missing required env: $name"
}

if [[ ! -f "$env_file" ]]; then
  fail "env file not found: $env_file"
fi

set -a
# shellcheck disable=SC1090
source "$env_file"
set +a

if docker info >/dev/null 2>&1; then
  DOCKER=(docker)
elif sudo -n docker info >/dev/null 2>&1; then
  DOCKER=(sudo -n docker)
else
  fail "docker is not available for the current user"
fi

AQUILA_BUDDY_NETWORK="${AQUILA_BUDDY_NETWORK:-aquila-buddy}"
AQUILA_BUDDY_FRONTEND_CONTAINER="${AQUILA_BUDDY_FRONTEND_CONTAINER:-aquila-buddy-front}"
AQUILA_BUDDY_BACKEND_CONTAINER="${AQUILA_BUDDY_BACKEND_CONTAINER:-aquila-buddy-back}"
AQUILA_BUDDY_DB_CONTAINER="${AQUILA_BUDDY_DB_CONTAINER:-aquila-buddy-postgres}"
AQUILA_BUDDY_DB_VOLUME="${AQUILA_BUDDY_DB_VOLUME:-aquila-buddy-postgres-data}"
AQUILA_BUDDY_BIND_ADDRESS="${AQUILA_BUDDY_BIND_ADDRESS:-127.0.0.1}"
AQUILA_BUDDY_FRONTEND_PORT="${AQUILA_BUDDY_FRONTEND_PORT:-13011}"
AQUILA_BUDDY_BACKEND_PORT="${AQUILA_BUDDY_BACKEND_PORT:-18091}"
POSTGRES_IMAGE="${POSTGRES_IMAGE:-pgvector/pgvector:pg16}"
POSTGRES_DB="${POSTGRES_DB:-aquila_buddy}"
POSTGRES_USER="${POSTGRES_USER:-aquila_admin}"
DB_USER="${DB_USER:-aquila_app}"
DB_URL="${DB_URL:-jdbc:postgresql://$AQUILA_BUDDY_DB_CONTAINER:5432/$POSTGRES_DB}"
SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-dev}"

if [[ -z "${AQUILA_BUDDY_IMAGE_TAG:-}" || "${AQUILA_BUDDY_IMAGE_TAG:-}" == "replace-with-git-sha" ]]; then
  AQUILA_BUDDY_IMAGE_TAG="$(git -C "$repo_root" rev-parse --short=12 HEAD)"
fi

AQUILA_BUDDY_IMAGE_PREFIX="${AQUILA_BUDDY_IMAGE_PREFIX:-ghcr.io/aquilaxk}"
AQUILA_BUDDY_BACKEND_IMAGE="${AQUILA_BUDDY_BACKEND_IMAGE:-$AQUILA_BUDDY_IMAGE_PREFIX/aquila-buddy-back:$AQUILA_BUDDY_IMAGE_TAG}"
AQUILA_BUDDY_FRONTEND_IMAGE="${AQUILA_BUDDY_FRONTEND_IMAGE:-$AQUILA_BUDDY_IMAGE_PREFIX/aquila-buddy-front:$AQUILA_BUDDY_IMAGE_TAG}"

if [[ "$AQUILA_BUDDY_BACKEND_IMAGE" == *replace-with-git-sha* ]]; then
  AQUILA_BUDDY_BACKEND_IMAGE="$AQUILA_BUDDY_IMAGE_PREFIX/aquila-buddy-back:$AQUILA_BUDDY_IMAGE_TAG"
fi

if [[ "$AQUILA_BUDDY_FRONTEND_IMAGE" == *replace-with-git-sha* ]]; then
  AQUILA_BUDDY_FRONTEND_IMAGE="$AQUILA_BUDDY_IMAGE_PREFIX/aquila-buddy-front:$AQUILA_BUDDY_IMAGE_TAG"
fi

require_var POSTGRES_PASSWORD
require_var DB_PASSWORD
require_var JWT_SECRET

if [[ "$DB_USER" == "$POSTGRES_USER" ]]; then
  fail "DB_USER must be a non-superuser app role distinct from POSTGRES_USER"
fi

schema_sql="${AQUILA_BUDDY_SCHEMA_SQL:-$repo_root/back/src/main/resources/schema.sql}"
operational_sql="${AQUILA_BUDDY_OPERATIONAL_SQL:-$repo_root/back/src/main/resources/schema-postgres-operational.sql}"
pgvector_sql="${AQUILA_BUDDY_PGVECTOR_SQL:-$repo_root/back/src/main/resources/schema-postgres-pgvector.sql}"

[[ -f "$schema_sql" ]] || fail "missing schema file: $schema_sql"
[[ -f "$operational_sql" ]] || fail "missing operational schema file: $operational_sql"
[[ -f "$pgvector_sql" ]] || fail "missing pgvector schema file: $pgvector_sql"

if ! "${DOCKER[@]}" network inspect "$AQUILA_BUDDY_NETWORK" >/dev/null 2>&1; then
  "${DOCKER[@]}" network create "$AQUILA_BUDDY_NETWORK" >/dev/null
fi

if "${DOCKER[@]}" container inspect "$AQUILA_BUDDY_DB_CONTAINER" >/dev/null 2>&1; then
  "${DOCKER[@]}" start "$AQUILA_BUDDY_DB_CONTAINER" >/dev/null
else
  "${DOCKER[@]}" run -d \
    --name "$AQUILA_BUDDY_DB_CONTAINER" \
    --network "$AQUILA_BUDDY_NETWORK" \
    --restart unless-stopped \
    -e "POSTGRES_DB=$POSTGRES_DB" \
    -e "POSTGRES_USER=$POSTGRES_USER" \
    -e "POSTGRES_PASSWORD=$POSTGRES_PASSWORD" \
    -v "$AQUILA_BUDDY_DB_VOLUME:/var/lib/postgresql/data" \
    "$POSTGRES_IMAGE" >/dev/null
fi

for _ in $(seq 1 60); do
  if "${DOCKER[@]}" exec "$AQUILA_BUDDY_DB_CONTAINER" pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB" >/dev/null 2>&1; then
    break
  fi
  sleep 2
done

if ! "${DOCKER[@]}" exec "$AQUILA_BUDDY_DB_CONTAINER" pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB" >/dev/null 2>&1; then
  fail "postgres did not become ready"
fi

psql_as_admin() {
  "${DOCKER[@]}" exec -i \
    -e "PGPASSWORD=$POSTGRES_PASSWORD" \
    "$AQUILA_BUDDY_DB_CONTAINER" \
    psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" "$@"
}

ensure_app_db_role() {
  psql_as_admin -v app_user="$DB_USER" -v app_password="$DB_PASSWORD" <<'SQL'
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'app_user', :'app_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'app_user')
\gexec
SELECT format(
    'ALTER ROLE %I WITH LOGIN PASSWORD %L NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS',
    :'app_user',
    :'app_password'
)
\gexec
SQL
}

grant_app_db_privileges() {
  psql_as_admin -v app_user="$DB_USER" -v db_name="$POSTGRES_DB" <<'SQL'
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
GRANT CONNECT ON DATABASE :"db_name" TO :"app_user";
GRANT USAGE ON SCHEMA public TO :"app_user";
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO :"app_user";
GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO :"app_user";
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO :"app_user";
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO :"app_user";
SQL
}

ensure_app_db_role

schema_exists="$(psql_as_admin -tAc "select to_regclass('public.user_profiles') is not null;" | tr -d '[:space:]')"

if [[ "${AQUILA_BUDDY_FORCE_RESET_SCHEMA:-false}" == "true" || "$schema_exists" != "t" ]]; then
  psql_as_admin < "$schema_sql"
else
  printf 'schema already exists; set AQUILA_BUDDY_FORCE_RESET_SCHEMA=true to reapply destructive schema.sql\n'
fi

psql_as_admin < "$operational_sql"
psql_as_admin < "$pgvector_sql"
grant_app_db_privileges

remove_container() {
  local name="$1"
  if "${DOCKER[@]}" container inspect "$name" >/dev/null 2>&1; then
    "${DOCKER[@]}" rm -f "$name" >/dev/null
  fi
}

secret_mount_args=()
if [[ -n "${AQUILA_BUDDY_VERTEX_KEY_HOST_PATH:-}" && -f "${AQUILA_BUDDY_VERTEX_KEY_HOST_PATH:-}" ]]; then
  secret_mount_args=(--mount "type=bind,source=$AQUILA_BUDDY_VERTEX_KEY_HOST_PATH,target=/run/secrets/vertex-key.json,readonly")
fi

remove_container "$AQUILA_BUDDY_BACKEND_CONTAINER"
"${DOCKER[@]}" run -d \
  --name "$AQUILA_BUDDY_BACKEND_CONTAINER" \
  --network "$AQUILA_BUDDY_NETWORK" \
  --restart unless-stopped \
  --env-file "$env_file" \
  -e "SPRING_PROFILES_ACTIVE=$SPRING_PROFILES_ACTIVE" \
  -e "DB_URL=$DB_URL" \
  -e "DB_USER=$DB_USER" \
  -e "DB_PASSWORD=$DB_PASSWORD" \
  -p "$AQUILA_BUDDY_BIND_ADDRESS:${AQUILA_BUDDY_BACKEND_PORT}:8080" \
  "${secret_mount_args[@]}" \
  "$AQUILA_BUDDY_BACKEND_IMAGE" >/dev/null

for _ in $(seq 1 60); do
  if curl -fsS "http://127.0.0.1:${AQUILA_BUDDY_BACKEND_PORT}/actuator/health" >/dev/null 2>&1; then
    break
  fi
  sleep 2
done

if ! curl -fsS "http://127.0.0.1:${AQUILA_BUDDY_BACKEND_PORT}/actuator/health" >/dev/null 2>&1; then
  "${DOCKER[@]}" logs --tail 80 "$AQUILA_BUDDY_BACKEND_CONTAINER" >&2 || true
  fail "backend health check failed"
fi

remove_container "$AQUILA_BUDDY_FRONTEND_CONTAINER"
"${DOCKER[@]}" run -d \
  --name "$AQUILA_BUDDY_FRONTEND_CONTAINER" \
  --network "$AQUILA_BUDDY_NETWORK" \
  --restart unless-stopped \
  --env-file "$env_file" \
  -p "$AQUILA_BUDDY_BIND_ADDRESS:${AQUILA_BUDDY_FRONTEND_PORT}:3000" \
  "$AQUILA_BUDDY_FRONTEND_IMAGE" >/dev/null

for _ in $(seq 1 60); do
  if curl -fsS "http://127.0.0.1:${AQUILA_BUDDY_FRONTEND_PORT}/" >/dev/null 2>&1; then
    break
  fi
  sleep 2
done

if ! curl -fsS "http://127.0.0.1:${AQUILA_BUDDY_FRONTEND_PORT}/" >/dev/null 2>&1; then
  "${DOCKER[@]}" logs --tail 80 "$AQUILA_BUDDY_FRONTEND_CONTAINER" >&2 || true
  fail "frontend health check failed"
fi

printf 'aquila-buddy containers are running\n'
printf 'frontend=http://127.0.0.1:%s\n' "$AQUILA_BUDDY_FRONTEND_PORT"
printf 'backend=http://127.0.0.1:%s\n' "$AQUILA_BUDDY_BACKEND_PORT"
