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
  deploy/oci/README.md \
  deploy/oci/aquila-buddy.env.example \
  deploy/oci/build-aquila-buddy-images.sh \
  deploy/oci/run-aquila-buddy.sh \
  deploy/oci/render-nginx-config.sh \
  deploy/oci/install-nginx-config.sh \
  deploy/oci/aquila-buddy.nginx.conf.template
do
  require_file "$file"
done

for file in \
  deploy/oci/build-aquila-buddy-images.sh \
  deploy/oci/run-aquila-buddy.sh \
  deploy/oci/render-nginx-config.sh \
  deploy/oci/install-nginx-config.sh
do
  require_executable "$file"
done

require_contains back/Dockerfile 'eclipse-temurin:21-jdk'
require_contains back/Dockerfile './back/gradlew -p back --no-daemon bootJar'
require_contains back/Dockerfile 'EXPOSE 8080'
require_contains .dockerignore '.codex'
require_contains .dockerignore 'vertex-key.json'
require_contains front/Dockerfile 'node:22-alpine'
require_contains front/Dockerfile 'NEXT_PUBLIC_API_URL'
require_contains front/Dockerfile 'EXPOSE 3000'

require_contains deploy/oci/aquila-buddy.env.example 'AQUILA_BUDDY_DOMAIN='
require_contains deploy/oci/aquila-buddy.env.example 'AQUILA_BUDDY_FRONTEND_PORT=13011'
require_contains deploy/oci/aquila-buddy.env.example 'AQUILA_BUDDY_BACKEND_PORT=18091'
require_contains deploy/oci/aquila-buddy.env.example 'POSTGRES_IMAGE=pgvector/pgvector:pg16'
require_contains deploy/oci/aquila-buddy.env.example 'DB_URL=jdbc:postgresql://aquila-buddy-postgres:5432/aquila_buddy'
require_contains deploy/oci/aquila-buddy.env.example 'JWT_SECRET='
require_contains deploy/oci/aquila-buddy.env.example 'GITHUB_CLIENT_SECRET='
require_contains deploy/oci/aquila-buddy.env.example 'VERTEX_AI_CREDENTIALS_PATH=/run/secrets/vertex-key.json'
require_contains deploy/oci/README.md 'https://<AQUILA_BUDDY_DOMAIN>/login/oauth2/code/github'
require_contains deploy/oci/README.md 'AQUILA_BUDDY_FORCE_RESET_SCHEMA=true'

require_contains deploy/oci/build-aquila-buddy-images.sh '"${DOCKER[@]}" build'
require_contains deploy/oci/build-aquila-buddy-images.sh 'NEXT_PUBLIC_BACKEND_OAUTH_URL'
require_contains deploy/oci/run-aquila-buddy.sh 'pgvector/pgvector:pg16'
require_contains deploy/oci/run-aquila-buddy.sh 'replace-with-git-sha'
require_contains deploy/oci/run-aquila-buddy.sh 'back/src/main/resources/schema-postgres-pgvector.sql'
require_contains deploy/oci/run-aquila-buddy.sh '127.0.0.1:${AQUILA_BUDDY_BACKEND_PORT}'
require_contains deploy/oci/run-aquila-buddy.sh '127.0.0.1:${AQUILA_BUDDY_FRONTEND_PORT}'
require_contains deploy/oci/render-nginx-config.sh 'proxy_pass http://127.0.0.1:${AQUILA_BUDDY_BACKEND_PORT}'
require_contains deploy/oci/render-nginx-config.sh 'proxy_pass http://127.0.0.1:${AQUILA_BUDDY_FRONTEND_PORT}'
require_contains deploy/oci/aquila-buddy.nginx.conf.template 'server_name ${AQUILA_BUDDY_DOMAIN};'
require_contains deploy/oci/aquila-buddy.nginx.conf.template 'location /api/'
require_contains deploy/oci/aquila-buddy.nginx.conf.template 'location /oauth2/'

if grep -R --line-number --exclude 'validate-buddy-deploy-assets.sh' -E '(ghp_|github_pat_|BEGIN PRIVATE KEY|AIza|sk-[A-Za-z0-9])' "$repo_root/deploy/oci" >/dev/null; then
  fail "deploy/oci must not contain secret-looking values"
fi

printf 'aquila-buddy deploy asset validation passed\n'
