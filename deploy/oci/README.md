# OCI aquila-buddy Runtime Deploy

이 디렉터리는 OCI A1 인스턴스에서 aquila-buddy를 Docker와 host nginx로 실행하기 위한 배포 자산입니다.

## 서버 전제

- OS: Ubuntu on ARM64
- Docker data-root: `/var/lib/aquila-data/docker`
- Public entrypoint: host nginx `80/tcp`, `443/tcp`
- App bind: loopback only
  - frontend: `127.0.0.1:13011`
  - backend: `127.0.0.1:18091`
- DB: 별도 `pgvector/pgvector:pg16` 컨테이너

## Private Files

서버에는 다음 파일을 private path로 둡니다. 이 파일들은 git에 올리지 않습니다.

- `/opt/aquila-buddy/aquila-buddy.env`
- `/opt/aquila-buddy/secrets/vertex-key.json`

```bash
sudo mkdir -p /opt/aquila-buddy/secrets
sudo cp deploy/oci/aquila-buddy.env.example /opt/aquila-buddy/aquila-buddy.env
sudo editor /opt/aquila-buddy/aquila-buddy.env
```

## Secret Checklist

필수:

- `AQUILA_BUDDY_DOMAIN`
- `AQUILA_BUDDY_BACKEND_IMAGE`
- `AQUILA_BUDDY_FRONTEND_IMAGE`
- `POSTGRES_PASSWORD`
- `DB_PASSWORD`
- `JWT_SECRET`
- `GITHUB_CLIENT_ID`
- `GITHUB_CLIENT_SECRET`

AI provider 사용 시:

- Vertex AI: `GOOGLE_CLOUD_PROJECT_ID`, `VERTEX_AI_*`, `/opt/aquila-buddy/secrets/vertex-key.json`
- OpenAI-compatible: `AI_PROVIDER=openai-compatible`, `AI_API_BASE_URL`, `AI_API_KEY`, `AI_CHAT_MODEL`, `AI_EMBEDDING_MODEL`

GitHub OAuth App callback URL은 Spring Security callback입니다.

```text
https://<AQUILA_BUDDY_DOMAIN>/login/oauth2/code/github
```

`OAUTH2_REDIRECT_URI`는 로그인 성공 후 frontend가 token을 받는 화면입니다.

```text
https://<AQUILA_BUDDY_DOMAIN>/oauth/callback
```

## Build

Frontend image는 public API/OAuth URL을 build time에 포함합니다. domain과 OAuth URL을 먼저 확정한 뒤 빌드합니다.

```bash
bash deploy/oci/build-aquila-buddy-images.sh /opt/aquila-buddy/aquila-buddy.env
```

registry에 push하려면 env에 다음 값을 둡니다.

```bash
AQUILA_BUDDY_PUSH_IMAGES=true
```

## Run Containers

```bash
bash deploy/oci/run-aquila-buddy.sh /opt/aquila-buddy/aquila-buddy.env
```

처음 생성된 빈 DB에는 `schema.sql`과 `schema-postgres-pgvector.sql`을 자동 적용합니다. 기존 schema가 있으면 destructive `schema.sql` 재적용을 건너뜁니다. 의도적으로 초기화하려면 private env에 다음 값을 명시합니다.

```bash
AQUILA_BUDDY_FORCE_RESET_SCHEMA=true
```

## Install nginx

인증서 파일은 먼저 준비되어 있어야 합니다.

```bash
bash deploy/oci/install-nginx-config.sh /opt/aquila-buddy/aquila-buddy.env
```

렌더링 결과만 확인할 때는 다음 명령을 사용합니다.

```bash
bash deploy/oci/render-nginx-config.sh /opt/aquila-buddy/aquila-buddy.env
```

## Validate

```bash
bash deploy/oci/validate-buddy-deploy-assets.sh
```

## GitHub Actions CD

`OCI Buddy CD` workflow는 `self-hosted`, `oci-a1-staging`, `aquila-buddy` labels가 붙은 repo 전용 runner에서 실행됩니다. runner는 OCI A1 host에서 다음 값으로 등록합니다.

```bash
GITHUB_RUNNER_TOKEN=<repo-runner-registration-token> \
bash deploy/oci/bootstrap-github-runner.sh
```

workflow secret은 실제 값을 base64로 저장합니다.

```text
OCI_A1_BUDDY_ENV_B64
OCI_A1_VERTEX_KEY_JSON_B64
```

수동 실행은 기본 `dry_run=true`입니다. 실제 컨테이너 교체는 `dry_run=false`로 실행합니다.
