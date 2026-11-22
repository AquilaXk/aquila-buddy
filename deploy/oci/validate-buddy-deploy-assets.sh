#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

fail() {
  printf 'aquila-buddy deploy asset validation failed: %s\n' "$1" >&2
  exit 1
}

require_file() {
  local path="$1"
  [[ -f "$repo_root/$path" ]] || fail "missing file: $path"
}

require_executable() {
  local path="$1"
  [[ -x "$repo_root/$path" ]] || fail "not executable: $path"
}

require_contains() {
  local path="$1"
  local expected="$2"
  grep -F -- "$expected" "$repo_root/$path" >/dev/null || fail "$path must contain: $expected"
}

for file in \
  back/Dockerfile \
  .dockerignore \
  front/Dockerfile \
  .github/workflows/oci-buddy-cd.yml \
  deploy/oci/README.md \
  deploy/oci/aquila-buddy.env.example \
  deploy/oci/bootstrap-github-runner.sh \
  deploy/oci/build-aquila-buddy-images.sh \
  deploy/oci/prepare-buddy-runner-storage.sh \
  deploy/oci/run-aquila-buddy.sh \
  deploy/oci/render-nginx-config.sh \
  deploy/oci/install-nginx-config.sh \
  deploy/oci/aquila-buddy.nginx.conf.template \
  back/src/main/resources/schema-postgres-operational.sql \
  back/src/main/resources/schema-postgres-rls.sql
do
  require_file "$file"
done

for file in \
  deploy/oci/build-aquila-buddy-images.sh \
  deploy/oci/bootstrap-github-runner.sh \
  deploy/oci/prepare-buddy-runner-storage.sh \
  deploy/oci/run-aquila-buddy.sh \
  deploy/oci/render-nginx-config.sh \
  deploy/oci/install-nginx-config.sh
do
  require_executable "$file"
done

require_contains back/Dockerfile 'eclipse-temurin:21-jdk'
require_contains back/Dockerfile './back/gradlew -p back --no-daemon bootJar'
require_contains back/Dockerfile 'EXPOSE 8080'
require_contains .dockerignore '.worktrees'
require_contains .dockerignore 'vertex-key.json'
require_contains front/Dockerfile 'node:22-alpine'
require_contains front/Dockerfile 'NEXT_PUBLIC_API_URL'
require_contains front/Dockerfile 'EXPOSE 3000'

require_contains deploy/oci/aquila-buddy.env.example 'AQUILA_BUDDY_DOMAIN='
require_contains deploy/oci/aquila-buddy.env.example 'AQUILA_BUDDY_DOMAIN=buddy.aquilaxk.site'
require_contains deploy/oci/aquila-buddy.env.example 'AQUILA_BUDDY_FRONTEND_PORT=13011'
require_contains deploy/oci/aquila-buddy.env.example 'AQUILA_BUDDY_BACKEND_PORT=18091'
require_contains deploy/oci/aquila-buddy.env.example 'POSTGRES_IMAGE=pgvector/pgvector:pg16'
require_contains deploy/oci/aquila-buddy.env.example 'POSTGRES_USER=aquila_admin'
require_contains deploy/oci/aquila-buddy.env.example 'DB_USER=aquila_app'
require_contains deploy/oci/aquila-buddy.env.example 'DB_URL=jdbc:postgresql://aquila-buddy-postgres:5432/aquila_buddy'
require_contains deploy/oci/aquila-buddy.env.example 'SPRING_PROFILES_ACTIVE=prod'
require_contains deploy/oci/aquila-buddy.env.example 'SERVER_FORWARD_HEADERS_STRATEGY=framework'
require_contains deploy/oci/aquila-buddy.env.example 'JWT_SECRET='
require_contains deploy/oci/aquila-buddy.env.example 'GITHUB_CLIENT_SECRET='
require_contains deploy/oci/aquila-buddy.env.example 'CSRF_ALLOWED_ORIGINS=https://buddy.aquilaxk.site'
require_contains deploy/oci/aquila-buddy.env.example 'GITHUB_USER_CONTEXT_ENABLED=true'
require_contains deploy/oci/aquila-buddy.env.example 'GITHUB_ISSUE_SEARCH_ENABLED=true'
require_contains deploy/oci/aquila-buddy.env.example 'VERTEX_AI_CREDENTIALS_PATH=/run/secrets/vertex-key.json'
require_contains deploy/oci/README.md 'https://<AQUILA_BUDDY_DOMAIN>/login/oauth2/code/github'
require_contains deploy/oci/README.md 'AQUILA_BUDDY_FORCE_RESET_SCHEMA=true'

require_contains .github/workflows/oci-buddy-cd.yml 'runs-on: [self-hosted, oci-a1-staging, aquila-buddy]'
require_contains .github/workflows/oci-buddy-cd.yml 'OCI_A1_BUDDY_ENV_B64'
require_contains .github/workflows/oci-buddy-cd.yml 'OCI_A1_VERTEX_KEY_JSON_B64'
require_contains .github/workflows/oci-buddy-cd.yml 'managed_runtime_keys_regex'
require_contains .github/workflows/oci-buddy-cd.yml 'aquila-buddy.private.env'
require_contains .github/workflows/oci-buddy-cd.yml 'deploy/oci/prepare-buddy-runner-storage.sh'
require_contains .github/workflows/oci-buddy-cd.yml 'deploy/oci/build-aquila-buddy-images.sh'
require_contains .github/workflows/oci-buddy-cd.yml 'deploy/oci/run-aquila-buddy.sh'
require_contains .github/workflows/oci-buddy-cd.yml 'buddy.aquilaxk.site'
require_contains .github/workflows/oci-buddy-cd.yml 'DB_URL=jdbc:postgresql://aquila-buddy-postgres:5432/aquila_buddy'
require_contains .github/workflows/oci-buddy-cd.yml 'DB_USER=aquila_app'
require_contains .github/workflows/oci-buddy-cd.yml 'CSRF_ALLOWED_ORIGINS=https://$AQUILA_BUDDY_DOMAIN'
require_contains .github/workflows/oci-buddy-cd.yml 'SPRING_PROFILES_ACTIVE=prod'
require_contains .github/workflows/oci-buddy-cd.yml 'SERVER_FORWARD_HEADERS_STRATEGY=framework'
require_contains .github/workflows/oci-buddy-cd.yml 'VERTEX_AI_CREDENTIALS_PATH=/run/secrets/vertex-key.json'
require_contains .github/workflows/oci-buddy-cd.yml 'GOOGLE_APPLICATION_CREDENTIALS=/run/secrets/vertex-key.json'
require_contains deploy/oci/bootstrap-github-runner.sh 'GITHUB_RUNNER_TOKEN'
require_contains deploy/oci/bootstrap-github-runner.sh 'AquilaXk/aquila-buddy'
require_contains deploy/oci/bootstrap-github-runner.sh 'oci-a1-buddy'
require_contains deploy/oci/bootstrap-github-runner.sh 'oci-a1-staging,aquila-buddy'
require_contains deploy/oci/bootstrap-github-runner.sh 'CONTAINERD_ROOT'
require_contains deploy/oci/bootstrap-github-runner.sh 'root = "$CONTAINERD_ROOT/root"'
require_contains deploy/oci/bootstrap-github-runner.sh 'state = "$CONTAINERD_ROOT/state"'
require_contains deploy/oci/bootstrap-github-runner.sh 'aquila-buddy-install-nginx-config'
require_contains deploy/oci/install-nginx-config.sh 'aquila-buddy-install-nginx-config'

require_contains deploy/oci/prepare-buddy-runner-storage.sh 'AQUILA_BUDDY_MIN_ROOT_FREE_GIB'
require_contains deploy/oci/prepare-buddy-runner-storage.sh 'system prune -af'
require_contains deploy/oci/prepare-buddy-runner-storage.sh 'assert_min_free_gib /var/lib/aquila-data'
require_contains deploy/oci/build-aquila-buddy-images.sh '"${DOCKER[@]}" build'
require_contains deploy/oci/build-aquila-buddy-images.sh 'NEXT_PUBLIC_BACKEND_OAUTH_URL'
require_contains deploy/oci/run-aquila-buddy.sh 'pgvector/pgvector:pg16'
require_contains deploy/oci/run-aquila-buddy.sh 'replace-with-git-sha'
require_contains deploy/oci/run-aquila-buddy.sh 'back/src/main/resources/schema-postgres-operational.sql'
require_contains deploy/oci/run-aquila-buddy.sh 'back/src/main/resources/schema-postgres-pgvector.sql'
require_contains deploy/oci/run-aquila-buddy.sh 'back/src/main/resources/schema-postgres-rls.sql'
require_contains back/src/main/resources/schema-postgres-operational.sql 'ALTER TABLE refresh_token_sessions'
require_contains back/src/main/resources/schema-postgres-operational.sql 'ADD COLUMN IF NOT EXISTS family_id VARCHAR(64)'
require_contains back/src/main/resources/schema-postgres-operational.sql 'ADD COLUMN IF NOT EXISTS replaced_by_token_hash VARCHAR(64)'
require_contains back/src/main/resources/schema-postgres-operational.sql 'fk_refresh_token_sessions_profile'
require_contains deploy/oci/run-aquila-buddy.sh 'reject_duplicate_env_keys'
require_contains deploy/oci/run-aquila-buddy.sh 'require_var GITHUB_CLIENT_ID'
require_contains deploy/oci/run-aquila-buddy.sh 'require_var GITHUB_CLIENT_SECRET'
require_contains deploy/oci/run-aquila-buddy.sh 'NEXT_PUBLIC_BACKEND_OAUTH_URL=$NEXT_PUBLIC_BACKEND_OAUTH_URL'
require_contains deploy/oci/run-aquila-buddy.sh 'DB_USER must be a non-superuser app role distinct from POSTGRES_USER'
require_contains deploy/oci/run-aquila-buddy.sh 'NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS'
require_contains deploy/oci/run-aquila-buddy.sh '127.0.0.1:${AQUILA_BUDDY_BACKEND_PORT}'
require_contains deploy/oci/run-aquila-buddy.sh '127.0.0.1:${AQUILA_BUDDY_FRONTEND_PORT}'
require_contains deploy/oci/render-nginx-config.sh 'proxy_pass http://127.0.0.1:${AQUILA_BUDDY_BACKEND_PORT}'
require_contains deploy/oci/render-nginx-config.sh 'proxy_pass http://127.0.0.1:${AQUILA_BUDDY_FRONTEND_PORT}'
require_contains deploy/oci/aquila-buddy.nginx.conf.template 'server_name ${AQUILA_BUDDY_DOMAIN};'
require_contains deploy/oci/aquila-buddy.nginx.conf.template 'location /api/'
require_contains deploy/oci/aquila-buddy.nginx.conf.template 'location /oauth2/'
require_contains deploy/oci/aquila-buddy.nginx.conf.template 'location /login/oauth2/'

if grep -R --line-number -E 'location[[:space:]]+/login/[[:space:]]*\{' "$repo_root/deploy/oci/aquila-buddy.nginx.conf.template" "$repo_root/deploy/oci/render-nginx-config.sh" >/dev/null; then
  fail "login page path must stay on frontend; only /login/oauth2/ may proxy to backend"
fi

frontend_run_block="$(sed -n '/remove_container "\$AQUILA_BUDDY_FRONTEND_CONTAINER"/,/"\$AQUILA_BUDDY_FRONTEND_IMAGE"/p' "$repo_root/deploy/oci/run-aquila-buddy.sh")"
if grep -F -- '--env-file "$env_file"' <<< "$frontend_run_block" >/dev/null; then
  fail "frontend container must not receive the full private env file"
fi

if grep -R --line-number --exclude 'validate-buddy-deploy-assets.sh' -E '(ghp_|github_pat_|BEGIN PRIVATE KEY|AIza|sk-[A-Za-z0-9])' "$repo_root/deploy/oci" >/dev/null; then
  fail "deploy/oci must not contain secret-looking values"
fi

printf 'aquila-buddy deploy asset validation passed\n'
