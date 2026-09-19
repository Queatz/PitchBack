#!/usr/bin/env bash
# Build the production web app locally and publish it behind nginx + Traefik.
set -euo pipefail

DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib.sh
source "$DEPLOY_DIR/lib.sh"

usage() {
  cat <<'EOF'
Usage: deploy/web.sh [--skip-init]

Build the Compose HTML app locally, upload it to the server, and (unless
--skip-init) install nginx + Traefik on first setup.

Same-origin is the default: Traefik routes /api and /uploads to the API, so
the browser uses relative URLs. If WEB_DOMAIN != API_DOMAIN, deploy writes
window.__API_BASE__ into config.js from the API host.
EOF
}

parse_cli "$@"
load_deploy_env
HOST="${WEB_SSH_HOST:-$SSH_HOST}"
require_ssh_host "$HOST"
need_cmd ssh scp curl
command -v python3 >/dev/null 2>&1 || command -v python >/dev/null 2>&1 || die "python3 is required"

[[ -x "$ROOT/gradlew" ]] || die "missing $ROOT/gradlew"

log "Building web app"
(
  cd "$ROOT"
  # KotlinWebpack cannot store webpackConfigAppliers in the configuration cache.
  ./gradlew :app:webApp:jsBrowserDistribution --no-daemon --no-configuration-cache
)

DIST="$ROOT/app/webApp/build/dist/js/productionExecutable"
[[ -f "$DIST/index.html" ]] || die "web distribution missing index.html at $DIST"

write_api_config_js "$DIST/config.js"
log "API base for the browser: $(grep '__API_BASE__' "$DIST/config.js")"

if [[ "$SKIP_INIT" -eq 0 ]]; then
  log "Initializing nginx + Traefik (web)"
  STAGING="$(mktemp -d)"
  trap 'rm -rf "$STAGING"' EXIT
  prepare_edge_staging "$STAGING"
  render_placeholders "$DEPLOY_DIR/nginx-web.conf" "$STAGING/nginx-web.conf"
  write_traefik_web_dynamic "$STAGING/traefik-web.yml"
  cp "$DEPLOY_DIR/remote-init-web.sh" "$STAGING/remote-init-web.sh"
  chmod +x "$STAGING/remote-init-edge.sh" "$STAGING/remote-init-web.sh"
  stage_traefik_for_host "$HOST" "$STAGING"
  scp_dir "$HOST" "$STAGING" "/tmp/pitchback-deploy"
  ssh_root "$HOST" "env TRAEFIK_VERSION=$(printf '%q' "$TRAEFIK_VERSION") SSH_PORT=$(printf '%q' "$SSH_PORT") bash /tmp/pitchback-deploy/remote-init-edge.sh /tmp/pitchback-deploy"
  ssh_root "$HOST" "bash /tmp/pitchback-deploy/remote-init-web.sh /tmp/pitchback-deploy"
fi

log "Uploading web files"
upload_dir "$HOST" "$DIST/" "/var/www/pitchback/" --delete

report_health "web" "http://127.0.0.1:${NGINX_WEB_PORT}/" "https://${WEB_DOMAIN}/" "$HOST"
log "Web deploy complete"
