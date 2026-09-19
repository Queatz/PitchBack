#!/usr/bin/env bash
# Shared helpers for deploy/web.sh and deploy/server.sh.
# shellcheck disable=SC2034

die() {
  echo "error: $*" >&2
  exit 1
}

log() {
  echo "==> $*"
}

need_cmd() {
  command -v "$1" >/dev/null 2>&1 || die "missing required command: $1"
}

# GitHub release assets sit on an Azure CDN that often resets HTTP/2
# (curl 35) from VPS hosts. Download to a file with HTTP/1.1 and retries.
download_with_retry() {
  local url="$1" dest="$2"
  local attempt
  for attempt in 1 2 3 4 5; do
    if curl -fL --http1.1 --connect-timeout 20 --max-time 180 -o "$dest" "$url" \
      && [[ -s "$dest" ]]; then
      return 0
    fi
    rm -f "$dest"
    sleep $((attempt * 2))
  done
  return 1
}

traefik_linux_arch() {
  case "$1" in
    x86_64|amd64) printf 'amd64' ;;
    aarch64|arm64) printf 'arm64' ;;
    *) die "unsupported architecture for Traefik: $1" ;;
  esac
}

# Download Traefik on this machine and put the binary in dest_dir/traefik.
stage_traefik_binary() {
  local dest_dir="$1" uname_m="$2"
  local arch archive tmp sums_url
  arch="$(traefik_linux_arch "$uname_m")"
  archive="traefik_${TRAEFIK_VERSION}_linux_${arch}.tar.gz"
  tmp="$(mktemp -d)"
  sums_url="https://github.com/traefik/traefik/releases/download/${TRAEFIK_VERSION}/traefik_${TRAEFIK_VERSION}_checksums.txt"
  log "Downloading Traefik ${TRAEFIK_VERSION} (${arch})"
  download_with_retry \
    "https://github.com/traefik/traefik/releases/download/${TRAEFIK_VERSION}/${archive}" \
    "$tmp/${archive}" \
    || die "failed to download Traefik ${archive} from GitHub"
  download_with_retry "$sums_url" "$tmp/checksums.txt" \
    || die "failed to download Traefik checksums"
  (
    cd "$tmp"
    grep -E "[[:space:]]${archive}\$" checksums.txt | sha256sum -c --strict -
  ) || die "Traefik checksum mismatch for ${archive}"
  tar -xz -C "$tmp" -f "$tmp/${archive}" traefik
  [[ -f "$tmp/traefik" ]] || die "Traefik archive did not contain a traefik binary"
  install -m 0755 "$tmp/traefik" "$dest_dir/traefik"
  rm -rf "$tmp"
}

stage_traefik_for_host() {
  local host="$1" dest_dir="$2"
  if ssh_root "$host" "test -x /usr/local/bin/traefik"; then
    log "Traefik already installed on ${host}"
    return 0
  fi
  stage_traefik_binary "$dest_dir" "$(ssh_run "$host" "uname -m")"
}

env_file_get() {
  local key="$1" file="$2" line value
  [[ -f "$file" ]] || return 0
  line="$(grep -E "^${key}=" "$file" | tail -n 1 || true)"
  [[ -n "$line" ]] || return 0
  value="${line#*=}"
  value="${value%$'\r'}"
  if [[ "$value" == \"*\" && "$value" == *\" ]]; then
    value="${value:1:${#value}-2}"
  fi
  printf '%s' "$value"
}

url_host() {
  local url="$1"
  url="${url#http://}"
  url="${url#https://}"
  url="${url%%/*}"
  printf '%s' "$url"
}

js_string() {
  if command -v python3 >/dev/null 2>&1; then
    python3 -c 'import json,sys; print(json.dumps(sys.argv[1]))' "$1"
  else
    python -c 'import json,sys; print(json.dumps(sys.argv[1]))' "$1"
  fi
}

parse_cli() {
  SKIP_INIT=0
  local arg
  for arg in "$@"; do
    case "$arg" in
      --skip-init) SKIP_INIT=1 ;;
      -h|--help) usage; exit 0 ;;
      *) die "unknown argument: $arg (try --help)" ;;
    esac
  done
}

load_deploy_env() {
  ROOT="$(cd "$DEPLOY_DIR/.." && pwd)"
  ENV_PROD="$ROOT/.env.prod"
  CONFIG_FILE="$DEPLOY_DIR/config.env"

  [[ -f "$ENV_PROD" ]] || die "missing $ENV_PROD"
  if [[ ! -f "$CONFIG_FILE" ]]; then
    die "missing $CONFIG_FILE — copy deploy/config.env.example to deploy/config.env and fill it in"
  fi

  # shellcheck disable=SC1090
  set -a
  # shellcheck disable=SC1091
  source "$CONFIG_FILE"
  set +a

  SSH_USER="${SSH_USER:-root}"
  SSH_PORT="${SSH_PORT:-22}"
  TRAEFIK_VERSION="${TRAEFIK_VERSION:-v3.5.3}"
  NGINX_WEB_PORT="${NGINX_WEB_PORT:-8081}"
  NGINX_API_PORT="${NGINX_API_PORT:-8082}"

  PUBLIC_BASE_URL="$(env_file_get PUBLIC_BASE_URL "$ENV_PROD")"
  [[ -n "$PUBLIC_BASE_URL" ]] || die "PUBLIC_BASE_URL is not set in .env.prod"
  PUBLIC_BASE_URL="${PUBLIC_BASE_URL%/}"
  PUBLIC_HOST="$(url_host "$PUBLIC_BASE_URL")"
  [[ -n "$PUBLIC_HOST" ]] || die "could not parse host from PUBLIC_BASE_URL"

  DOMAIN="${DOMAIN:-$PUBLIC_HOST}"
  WEB_DOMAIN="$(url_host "${WEB_DOMAIN:-ranh.app}")"
  API_DOMAIN="$(url_host "${API_DOMAIN:-$DOMAIN}")"
  KTOR_PORT="$(env_file_get PORT "$ENV_PROD")"
  KTOR_PORT="${KTOR_PORT:-8080}"
  ARANGO_USER="$(env_file_get ARANGO_USER "$ENV_PROD")"
  ARANGO_PASSWORD="$(env_file_get ARANGO_PASSWORD "$ENV_PROD")"
  ARANGO_DATABASE="$(env_file_get ARANGO_DATABASE "$ENV_PROD")"

  SSH_OPTS=(-o BatchMode=yes -o StrictHostKeyChecking=accept-new -p "$SSH_PORT")
  RSYNC_SSH="ssh -o BatchMode=yes -o StrictHostKeyChecking=accept-new -p ${SSH_PORT}"
}

require_ssh_host() {
  local host="${1:-}"
  [[ -n "$host" ]] || die "SSH host is not set (set WEB_SSH_HOST, API_SSH_HOST, or SSH_HOST in deploy/config.env)"
}

ssh_run() {
  local host="$1"
  shift
  ssh "${SSH_OPTS[@]}" "${SSH_USER}@${host}" "$@"
}

ssh_root() {
  local host="$1"
  shift
  if [[ "${SSH_USER}" == "root" ]]; then
    ssh_run "$host" "$@"
  else
    ssh_run "$host" sudo -n "$@"
  fi
}

rsync_to() {
  local host="$1" src="$2" dest="$3"
  shift 3
  rsync -az --rsync-path='rsync' -e "$RSYNC_SSH" "$@" "$src" "${SSH_USER}@${host}:${dest}"
}

scp_dir() {
  local host="$1" src="$2" dest="$3"
  ssh_run "$host" "mkdir -p $(printf '%q' "$dest")"
  scp -o BatchMode=yes -o StrictHostKeyChecking=accept-new -P "$SSH_PORT" -r "$src/." "${SSH_USER}@${host}:${dest}/"
}

upload_dir() {
  local host="$1" src="$2" dest="$3"
  shift 3
  if ssh_run "$host" "command -v rsync >/dev/null 2>&1"; then
    ssh_run "$host" "mkdir -p $(printf '%q' "$dest")"
    rsync_to "$host" "$src" "$dest" "$@"
  else
    scp_dir "$host" "${src%/}" "$dest"
  fi
}

wait_local_http() {
  local host="$1" url="$2"
  local i
  for i in $(seq 1 20); do
    if ssh_run "$host" "curl -fsS --max-time 5 $(printf '%q' "$url") >/dev/null"; then
      return 0
    fi
    sleep 2
  done
  return 1
}

render_placeholders() {
  local src="$1" dest="$2"
  local content
  content="$(cat "$src")"
  content="${content//__ACME_EMAIL__/${ACME_EMAIL}}"
  content="${content//__NGINX_WEB_PORT__/${NGINX_WEB_PORT}}"
  content="${content//__NGINX_API_PORT__/${NGINX_API_PORT}}"
  content="${content//__KTOR_PORT__/${KTOR_PORT}}"
  content="${content//__WEB_DOMAIN__/${WEB_DOMAIN}}"
  content="${content//__API_DOMAIN__/${API_DOMAIN}}"
  printf '%s\n' "$content" >"$dest"
}

write_traefik_web_dynamic() {
  local dest="$1"
  cat >"$dest" <<EOF
http:
  routers:
    pitchback-web:
      rule: "Host(\`${WEB_DOMAIN}\`)"
      entryPoints:
        - websecure
      service: pitchback-web
      tls:
        certResolver: le
      priority: 1
  services:
    pitchback-web:
      loadBalancer:
        servers:
          - url: "http://127.0.0.1:${NGINX_WEB_PORT}"
EOF
}

write_traefik_api_dynamic() {
  local dest="$1" rule
  if [[ "$WEB_DOMAIN" == "$API_DOMAIN" ]]; then
    rule="Host(\`${API_DOMAIN}\`) && (PathPrefix(\`/api\`) || PathPrefix(\`/uploads\`))"
  else
    rule="Host(\`${API_DOMAIN}\`)"
  fi
  cat >"$dest" <<EOF
http:
  serversTransports:
    pitchback-longlived:
      forwardingTimeouts:
        dialTimeout: 30s
        responseHeaderTimeout: 0s
        idleConnTimeout: 1800s
  routers:
    pitchback-api:
      rule: "${rule}"
      entryPoints:
        - websecure
      service: pitchback-api
      tls:
        certResolver: le
      priority: 20
  services:
    pitchback-api:
      loadBalancer:
        servers:
          - url: "http://127.0.0.1:${NGINX_API_PORT}"
        serversTransport: pitchback-longlived
        responseForwarding:
          flushInterval: 1ms
EOF
}

write_api_config_js() {
  local dest="$1" api_base=""
  if [[ "$WEB_DOMAIN" != "$API_DOMAIN" ]]; then
    api_base="https://${API_DOMAIN}"
  fi
  cat >"$dest" <<EOF
// Generated by deploy/web.sh
window.__API_BASE__ = $(js_string "$api_base");
EOF
}

prepare_edge_staging() {
  local dir="$1"
  mkdir -p "$dir"
  [[ -n "${ACME_EMAIL:-}" ]] || die "ACME_EMAIL is required in deploy/config.env for Traefik / Let's Encrypt"
  render_placeholders "$DEPLOY_DIR/traefik.yml" "$dir/traefik.yml"
  cp "$DEPLOY_DIR/traefik.service" "$dir/traefik.service"
  cp "$DEPLOY_DIR/remote-init-edge.sh" "$dir/remote-init-edge.sh"
}

health_curl() {
  local url="$1"
  curl -fsS --max-time 8 "$url" >/dev/null 2>&1
}

report_health() {
  local name="$1" local_url="$2" public_url="$3" host="$4"
  local local_ok=0 public_ok=0
  log "Health check (${name})"
  if wait_local_http "$host" "$local_url"; then
    echo "  local  ${local_url}  UP"
    local_ok=1
  else
    echo "  local  ${local_url}  DOWN"
  fi
  if health_curl "$public_url"; then
    echo "  public ${public_url}  UP"
    public_ok=1
  else
    echo "  public ${public_url}  DOWN (DNS or Let's Encrypt may still be settling)"
  fi
  if [[ "$local_ok" -ne 1 ]]; then
    die "${name} is not up on the server (${local_url})"
  fi
  if [[ "$public_ok" -ne 1 ]]; then
    echo "warning: ${name} local health passed but public HTTPS is not ready yet" >&2
  fi
}
