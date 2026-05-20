#!/usr/bin/env bash
set -euo pipefail

env_file="${1:-/opt/aquila-buddy/aquila-buddy.env}"

if [[ ! -f "$env_file" ]]; then
  printf 'env file not found: %s\n' "$env_file" >&2
  exit 1
fi

set -a
# shellcheck disable=SC1090
source "$env_file"
set +a

: "${AQUILA_BUDDY_DOMAIN:?missing AQUILA_BUDDY_DOMAIN}"
AQUILA_BUDDY_FRONTEND_PORT="${AQUILA_BUDDY_FRONTEND_PORT:-13011}"
AQUILA_BUDDY_BACKEND_PORT="${AQUILA_BUDDY_BACKEND_PORT:-18091}"
AQUILA_BUDDY_SSL_CERTIFICATE="${AQUILA_BUDDY_SSL_CERTIFICATE:-/etc/letsencrypt/live/$AQUILA_BUDDY_DOMAIN/fullchain.pem}"
AQUILA_BUDDY_SSL_CERTIFICATE_KEY="${AQUILA_BUDDY_SSL_CERTIFICATE_KEY:-/etc/letsencrypt/live/$AQUILA_BUDDY_DOMAIN/privkey.pem}"

cat <<NGINX
server {
    listen 80;
    server_name ${AQUILA_BUDDY_DOMAIN};
    server_tokens off;

    location ^~ /.well-known/acme-challenge/ {
        root /var/www/certbot;
        default_type "text/plain";
    }

    location / {
        return 301 https://\$host\$request_uri;
    }
}

server {
    listen 443 ssl;
    server_name ${AQUILA_BUDDY_DOMAIN};
    server_tokens off;

    ssl_certificate ${AQUILA_BUDDY_SSL_CERTIFICATE};
    ssl_certificate_key ${AQUILA_BUDDY_SSL_CERTIFICATE_KEY};
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_prefer_server_ciphers off;

    location = /healthz {
        add_header Content-Type text/plain;
        return 200 "ok\n";
    }

    location /api/ {
        proxy_pass http://127.0.0.1:${AQUILA_BUDDY_BACKEND_PORT};
        proxy_http_version 1.1;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto https;
        proxy_read_timeout 60s;
    }

    location /oauth2/ {
        proxy_pass http://127.0.0.1:${AQUILA_BUDDY_BACKEND_PORT};
        proxy_set_header Host \$host;
        proxy_set_header X-Forwarded-Proto https;
    }

    location /login/ {
        proxy_pass http://127.0.0.1:${AQUILA_BUDDY_BACKEND_PORT};
        proxy_set_header Host \$host;
        proxy_set_header X-Forwarded-Proto https;
    }

    location /actuator/health {
        proxy_pass http://127.0.0.1:${AQUILA_BUDDY_BACKEND_PORT};
        proxy_set_header Host \$host;
        proxy_set_header X-Forwarded-Proto https;
    }

    location /v3/api-docs {
        proxy_pass http://127.0.0.1:${AQUILA_BUDDY_BACKEND_PORT};
        proxy_set_header Host \$host;
        proxy_set_header X-Forwarded-Proto https;
    }

    location /swagger-ui {
        proxy_pass http://127.0.0.1:${AQUILA_BUDDY_BACKEND_PORT};
        proxy_set_header Host \$host;
        proxy_set_header X-Forwarded-Proto https;
    }

    location / {
        proxy_pass http://127.0.0.1:${AQUILA_BUDDY_FRONTEND_PORT};
        proxy_http_version 1.1;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto https;
        proxy_read_timeout 60s;
    }
}
NGINX
