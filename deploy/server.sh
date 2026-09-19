#!/usr/bin/env bash
# Build the API jar locally and publish it behind nginx + Traefik.
set -euo pipefail

DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib.sh
source "$DEPLOY_DIR/lib.sh"

usage() {
  cat <<'EOF'
Usage: deploy/server.sh [--skip-init]

Build the server fat jar locally, upload it with .env.prod as .env, and
restart the systemd service. Unless --skip-init, also install nginx, Traefik,
Java, ArangoDB, and create the Arango user/database from .env.prod.

Init is idempotent: re-running does not drop the database or wipe uploads.
EOF
}

parse_cli "$@"
load_deploy_env
HOST="${API_SSH_HOST:-$SSH_HOST}"
require_ssh_host "$HOST"
need_cmd ssh scp curl
command -v python3 >/dev/null 2>&1 || command -v python >/dev/null 2>&1 || die "python3 is required"

[[ -x "$ROOT/gradlew" ]] || die "missing $ROOT/gradlew"
[[ -n "$ARANGO_USER" && -n "$ARANGO_DATABASE" ]] || die ".env.prod must set ARANGO_USER and ARANGO_DATABASE"

log "Building server jar"
(
  cd "$ROOT"
  ./gradlew :server:buildFatJar --no-daemon
)

JAR=""
if compgen -G "$ROOT/server/build/libs/*-all.jar" >/dev/null; then
  JAR="$(ls -1 "$ROOT/server/build/libs/"*-all.jar | head -n 1)"
else
  JAR="$(ls -1 "$ROOT/server/build/libs/"*.jar 2>/dev/null | grep -v -- '-plain.jar$' | head -n 1 || true)"
fi
[[ -n "$JAR" && -f "$JAR" ]] || die "fat jar not found under server/build/libs"

STAGING="$(mktemp -d)"
trap 'rm -rf "$STAGING"' EXIT
mkdir -p "$STAGING"

cp "$JAR" "$STAGING/server.jar"
cp "$ENV_PROD" "$STAGING/env"
if [[ "$WEB_DOMAIN" != "$API_DOMAIN" ]] && ! grep -q '^CORS_HOSTS=' "$STAGING/env"; then
  printf '\nCORS_HOSTS=https://%s\n' "$WEB_DOMAIN" >>"$STAGING/env"
fi

if [[ "$SKIP_INIT" -eq 0 ]]; then
  log "Initializing nginx + Traefik + ArangoDB (api)"
  prepare_edge_staging "$STAGING"
  render_placeholders "$DEPLOY_DIR/nginx-api.conf" "$STAGING/nginx-api.conf"
  cp "$DEPLOY_DIR/nginx-upgrade.conf" "$STAGING/nginx-upgrade.conf"
  write_traefik_api_dynamic "$STAGING/traefik-api.yml"
  cp "$DEPLOY_DIR/pitchback.service" "$STAGING/pitchback.service"
  cp "$DEPLOY_DIR/remote-init-api.sh" "$STAGING/remote-init-api.sh"
  chmod +x "$STAGING/remote-init-edge.sh" "$STAGING/remote-init-api.sh"
  stage_traefik_for_host "$HOST" "$STAGING"

  cat >"$STAGING/arango-setup.js" <<EOF
const users = require("@arangodb/users");
const user = $(js_string "$ARANGO_USER");
const pass = $(js_string "$ARANGO_PASSWORD");
const database = $(js_string "$ARANGO_DATABASE");

if (users.exists(user)) {
  users.update(user, pass);
} else {
  users.save(user, pass);
}
if (db._databases().indexOf(database) === -1) {
  db._createDatabase(database);
}
users.grantDatabase(user, database, "rw");
try { users.grantCollection(user, database, "*", "rw"); } catch (e) {}
EOF

  scp_dir "$HOST" "$STAGING" "/tmp/pitchback-deploy"
  ssh_root "$HOST" "env TRAEFIK_VERSION=$(printf '%q' "$TRAEFIK_VERSION") SSH_PORT=$(printf '%q' "$SSH_PORT") bash /tmp/pitchback-deploy/remote-init-edge.sh /tmp/pitchback-deploy"
  ssh_root "$HOST" "env ARANGO_ROOT_PASSWORD=$(printf '%q' "$ARANGO_ROOT_PASSWORD") bash /tmp/pitchback-deploy/remote-init-api.sh /tmp/pitchback-deploy"
fi

log "Uploading jar and .env"
ssh_root "$HOST" "mkdir -p /opt/pitchback/data/uploads"
scp -o BatchMode=yes -o StrictHostKeyChecking=accept-new -P "$SSH_PORT" \
  "$STAGING/server.jar" "${SSH_USER}@${HOST}:/tmp/pitchback-server.jar"
scp -o BatchMode=yes -o StrictHostKeyChecking=accept-new -P "$SSH_PORT" \
  "$STAGING/env" "${SSH_USER}@${HOST}:/tmp/pitchback.env"
ssh_root "$HOST" "bash -s" <<'REMOTE'
set -euo pipefail
install -m 0644 /tmp/pitchback-server.jar /opt/pitchback/server.jar
install -m 0600 /tmp/pitchback.env /opt/pitchback/.env
rm -f /tmp/pitchback-server.jar /tmp/pitchback.env
if id pitchback >/dev/null 2>&1; then
  chown pitchback:pitchback /opt/pitchback/server.jar /opt/pitchback/.env
  chown -R pitchback:pitchback /opt/pitchback
fi
systemctl restart pitchback
REMOTE

report_health "api" "http://127.0.0.1:${NGINX_API_PORT}/api/health" "https://${API_DOMAIN}/api/health" "$HOST"
log "Server deploy complete"
