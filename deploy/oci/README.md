# OCI aquila-buddy Runtime Deploy

이 디렉터리는 OCI A1 인스턴스에서 aquila-buddy를 Docker와 host nginx로 실행하기 위한 배포 자산입니다.

## 서버 전제

- OS: Ubuntu on ARM64
- Docker data-root: `/var/lib/aquila-data/docker`
- containerd root/state: `/var/lib/aquila-data/containerd/root`, `/var/lib/aquila-data/containerd/state`
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
- `DB_USER`
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

`POSTGRES_USER`는 schema owner/admin role로만 사용하고 backend는 `DB_USER`로 지정한 runtime role로 접속합니다. `DB_USER`는 `POSTGRES_USER`와 달라야 하며, 배포 스크립트가 role을 `NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS`로 생성한 뒤 필요한 table/sequence 권한만 부여합니다. 기존 volume을 쓰는 서버는 `POSTGRES_USER`를 현재 superuser/owner role로 유지하고, `DB_USER`/`DB_PASSWORD`를 새 runtime role 값으로 바꿔 첫 배포에서 role을 만들게 합니다.

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

CD workflow는 private env에서 production topology로 고정해야 하는 값을 제거한 뒤 다시 씁니다. `DB_URL`은 `aquila-buddy-postgres` 컨테이너 네트워크 주소로, `DB_USER`는 `aquila_app` runtime role로, Vertex credential path는 컨테이너 내부 `/run/secrets/vertex-key.json`로 materialize됩니다. 기존 DB volume과 연결된 `POSTGRES_USER`/`POSTGRES_PASSWORD`는 private env 값을 유지합니다.

이미지 빌드 전에는 `deploy/oci/prepare-buddy-runner-storage.sh`가 실행됩니다. 이 단계는 실행 중인 컨테이너와 Docker volume을 보존하고 unused image/layer만 정리한 뒤 `/`와 `/var/lib/aquila-data`의 최소 여유 공간을 검사합니다. 기본 gate는 루트 디스크 8GiB, data volume 20GiB입니다.

수동 실행은 기본 `dry_run=true`입니다. 실제 컨테이너 교체는 `dry_run=false`로 실행합니다.
