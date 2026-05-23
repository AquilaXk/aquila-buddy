#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage:
  GITHUB_RUNNER_TOKEN=<registration-token> bash deploy/oci/bootstrap-github-runner.sh

Environment:
  GITHUB_REPOSITORY      Repository URL owner/name. Default: AquilaXk/aquila-buddy
  GITHUB_RUNNER_TOKEN    Short-lived repository runner registration token. Required.
  GITHUB_RUNNER_NAME     Self-hosted runner name. Default: oci-a1-buddy
  GITHUB_RUNNER_LABELS   Comma-separated labels. Default: oci-a1-staging,aquila-buddy
  RUNNER_USER            Linux user for the runner service. Default: actions-runner
  RUNNER_HOME            Runner installation directory. Default: /opt/actions-runner/aquila-buddy
  DEPLOY_ROOT            aquila-buddy runtime private directory. Default: /opt/aquila-buddy
  DOCKER_DATA_ROOT       Docker data-root. Default: /var/lib/aquila-data/docker
  CONTAINERD_ROOT        containerd root/state parent. Default: /var/lib/aquila-data/containerd
USAGE
}

if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  usage
  exit 0
fi

GITHUB_REPOSITORY="${GITHUB_REPOSITORY:-AquilaXk/aquila-buddy}"
GITHUB_RUNNER_NAME="${GITHUB_RUNNER_NAME:-oci-a1-buddy}"
GITHUB_RUNNER_LABELS="${GITHUB_RUNNER_LABELS:-oci-a1-staging,aquila-buddy}"
GITHUB_RUNNER_TOKEN="${GITHUB_RUNNER_TOKEN:-}"
ACTIONS_RUNNER_VERSION="${ACTIONS_RUNNER_VERSION:-2.334.0}"
RUNNER_USER="${RUNNER_USER:-actions-runner}"
RUNNER_HOME="${RUNNER_HOME:-/opt/actions-runner/aquila-buddy}"
DEPLOY_ROOT="${DEPLOY_ROOT:-/opt/aquila-buddy}"
DOCKER_DATA_ROOT="${DOCKER_DATA_ROOT:-/var/lib/aquila-data/docker}"
CONTAINERD_ROOT="${CONTAINERD_ROOT:-/var/lib/aquila-data/containerd}"
NGINX_DEPLOY_WRAPPER="${NGINX_DEPLOY_WRAPPER:-/usr/local/sbin/aquila-buddy-install-nginx-config}"

if [[ -z "$GITHUB_RUNNER_TOKEN" ]]; then
  echo "GITHUB_RUNNER_TOKEN is required" >&2
  exit 2
fi

if [[ "$(uname -m)" != "aarch64" && "$(uname -m)" != "arm64" ]]; then
  echo "this bootstrap expects an ARM64 OCI A1 host" >&2
  exit 1
fi

log() {
  printf '[bootstrap-github-runner] %s\n' "$*"
}

require_sudo() {
  if [[ "$(id -u)" == "0" ]]; then
    return
  fi
  sudo -n true
}

require_sudo

log "installing runtime packages"
sudo apt-get update
sudo DEBIAN_FRONTEND=noninteractive apt-get install -y \
  ca-certificates \
  curl \
  docker.io \
  git \
  jq \
  nginx \
  sudo \
  tar

log "ensuring docker and containerd storage"
sudo install -d "$DOCKER_DATA_ROOT" "$CONTAINERD_ROOT/root" "$CONTAINERD_ROOT/state"
sudo install -d /etc/containerd
sudo tee /etc/containerd/config.toml >/dev/null <<TOML
version = 2
root = "$CONTAINERD_ROOT/root"
state = "$CONTAINERD_ROOT/state"
TOML

sudo install -d /etc/docker
sudo tee /etc/docker/daemon.json >/dev/null <<JSON
{
  "data-root": "$DOCKER_DATA_ROOT",
  "live-restore": true
}
JSON
sudo systemctl enable containerd
sudo systemctl restart containerd
sudo systemctl enable docker
sudo systemctl restart docker

log "creating runtime directories"
sudo install -d "$DEPLOY_ROOT" "$DEPLOY_ROOT/secrets" "$DEPLOY_ROOT/releases"

log "creating runner user"
if ! id "$RUNNER_USER" >/dev/null 2>&1; then
  sudo useradd --system --create-home --shell /bin/bash "$RUNNER_USER"
fi
sudo usermod -aG docker "$RUNNER_USER"
sudo chown -R "$RUNNER_USER:$RUNNER_USER" "$DEPLOY_ROOT"

log "granting runner deploy permissions"
sudo tee "$NGINX_DEPLOY_WRAPPER" >/dev/null <<'WRAPPER'
#!/usr/bin/env bash
set -euo pipefail

source_path="${1:-}"
target_path="${2:-/etc/nginx/conf.d/aquila-buddy.conf}"

if [[ -z "$source_path" || ! -f "$source_path" ]]; then
  echo "nginx config source file is required" >&2
  exit 2
fi

if [[ "$target_path" != "/etc/nginx/conf.d/aquila-buddy.conf" ]]; then
  echo "refusing to write unexpected nginx config path: $target_path" >&2
  exit 2
fi

install -m 0644 "$source_path" "$target_path"
nginx -t
systemctl reload nginx
WRAPPER
sudo chmod 0755 "$NGINX_DEPLOY_WRAPPER"

sudo tee /etc/sudoers.d/aquila-buddy-runner-deploy >/dev/null <<SUDOERS
$RUNNER_USER ALL=(root) NOPASSWD: $NGINX_DEPLOY_WRAPPER
SUDOERS
sudo chmod 440 /etc/sudoers.d/aquila-buddy-runner-deploy
sudo visudo -cf /etc/sudoers.d/aquila-buddy-runner-deploy

log "installing GitHub Actions runner"
sudo install -d "$RUNNER_HOME"
sudo chown "$RUNNER_USER:$RUNNER_USER" "$RUNNER_HOME"

runner_tar="actions-runner-linux-arm64-${ACTIONS_RUNNER_VERSION}.tar.gz"
runner_url="https://github.com/actions/runner/releases/download/v${ACTIONS_RUNNER_VERSION}/${runner_tar}"
if [[ ! -x "$RUNNER_HOME/config.sh" ]]; then
  tmp_dir="$(mktemp -d)"
  trap 'rm -rf "$tmp_dir"' EXIT
  curl -fsSL "$runner_url" -o "$tmp_dir/$runner_tar"
  sudo tar -xzf "$tmp_dir/$runner_tar" -C "$RUNNER_HOME"
  sudo chown -R "$RUNNER_USER:$RUNNER_USER" "$RUNNER_HOME"
fi

if [[ -x "$RUNNER_HOME/bin/installdependencies.sh" ]]; then
  (cd "$RUNNER_HOME" && sudo ./bin/installdependencies.sh)
fi

if [[ -x "$RUNNER_HOME/svc.sh" ]]; then
  (cd "$RUNNER_HOME" && sudo ./svc.sh stop) || true
  (cd "$RUNNER_HOME" && sudo ./svc.sh uninstall) || true
fi

if [[ -f "$RUNNER_HOME/.runner" ]]; then
  log "runner is already configured; replacing registration"
  (cd "$RUNNER_HOME" && sudo -u "$RUNNER_USER" ./config.sh remove --token "$GITHUB_RUNNER_TOKEN") || true
fi

(
  cd "$RUNNER_HOME"
  sudo -u "$RUNNER_USER" ./config.sh \
    --url "https://github.com/${GITHUB_REPOSITORY}" \
    --token "$GITHUB_RUNNER_TOKEN" \
    --name "$GITHUB_RUNNER_NAME" \
    --labels "$GITHUB_RUNNER_LABELS" \
    --work _work \
    --unattended \
    --replace
)

(cd "$RUNNER_HOME" && sudo ./svc.sh install "$RUNNER_USER") || true
(cd "$RUNNER_HOME" && sudo ./svc.sh start)

log "bootstrap finished"
