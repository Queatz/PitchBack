#!/usr/bin/env bash
# Idempotent install of nginx + Traefik (no Docker). Safe to re-run.
set -euo pipefail

if [[ "$(id -u)" -ne 0 ]]; then
  exec sudo -E bash "$0" "$@"
fi

STAGING="${1:-/tmp/pitchback-deploy}"
TRAEFIK_VERSION="${TRAEFIK_VERSION:-v3.5.3}"

[[ -d "$STAGING" ]] || { echo "missing staging dir $STAGING" >&2; exit 1; }
[[ -f /etc/debian_version ]] || { echo "init expects Debian/Ubuntu" >&2; exit 1; }

# A previous API init may have added ArangoDB's apt repo. Their 3.12 signing
# key expired 2026-03-24 (EXPKEYSIG C04AD0FD86FEC04D) and makes apt-get update
# fail on that host. Removing the leftover source is a no-op on the web box.
rm -f /etc/apt/sources.list.d/arangodb.list \
  /etc/apt/sources.list.d/arangodb.sources \
  /usr/share/keyrings/arangodb.gpg

export DEBIAN_FRONTEND=noninteractive
apt-get update -y
apt-get install -y --no-install-recommends ca-certificates curl tar nginx rsync ufw

# SSE and WebSocket use the same HTTP(S) ports as the site. Open them on
# the host firewall without exposing Ktor/nginx loopback ports.
SSH_PORT="${SSH_PORT:-22}"
ufw allow "${SSH_PORT}/tcp"
ufw allow 80/tcp
ufw allow 443/tcp
ufw --force enable

rm -f /etc/nginx/sites-enabled/default
mkdir -p /etc/nginx/sites-available /etc/nginx/sites-enabled

install_traefik() {
  if [[ -x /usr/local/bin/traefik ]]; then
    return 0
  fi
  # Prefer the binary uploaded by deploy/web.sh or deploy/server.sh. GitHub's
  # release CDN often resets HTTP/2 from VPS hosts (curl 35 + truncated gzip).
  if [[ -f "$STAGING/traefik" ]]; then
    install -m 0755 "$STAGING/traefik" /usr/local/bin/traefik
    return 0
  fi
  local arch
  case "$(uname -m)" in
    x86_64|amd64) arch=amd64 ;;
    aarch64|arm64) arch=arm64 ;;
    *) echo "unsupported architecture: $(uname -m)" >&2; exit 1 ;;
  esac
  local tmp archive url attempt
  tmp="$(mktemp -d)"
  archive="traefik_${TRAEFIK_VERSION}_linux_${arch}.tar.gz"
  url="https://github.com/traefik/traefik/releases/download/${TRAEFIK_VERSION}/${archive}"
  echo "downloading Traefik ${TRAEFIK_VERSION} ($arch)"
  for attempt in 1 2 3 4 5; do
    if curl -fL --http1.1 --connect-timeout 20 --max-time 180 \
      -o "$tmp/${archive}" "$url" && [[ -s "$tmp/${archive}" ]]; then
      break
    fi
    rm -f "$tmp/${archive}"
    if [[ "$attempt" -eq 5 ]]; then
      echo "error: failed to download Traefik from GitHub (${url})" >&2
      rm -rf "$tmp"
      exit 1
    fi
    echo "retrying Traefik download (${attempt}/5)..." >&2
    sleep $((attempt * 2))
  done
  tar -xz -C "$tmp" -f "$tmp/${archive}" traefik
  install -m 0755 "$tmp/traefik" /usr/local/bin/traefik
  rm -rf "$tmp"
}

restart_traefik=0
if [[ ! -x /usr/local/bin/traefik ]]; then
  restart_traefik=1
fi
install_traefik

mkdir -p /etc/traefik/dynamic /var/lib/traefik
if [[ ! -f /etc/traefik/traefik.yml ]] || ! cmp -s "$STAGING/traefik.yml" /etc/traefik/traefik.yml; then
  install -m 0644 "$STAGING/traefik.yml" /etc/traefik/traefik.yml
  restart_traefik=1
fi
install -m 0644 "$STAGING/traefik.service" /etc/systemd/system/traefik.service

if [[ ! -f /var/lib/traefik/acme.json ]]; then
  umask 077
  : >/var/lib/traefik/acme.json
  chmod 600 /var/lib/traefik/acme.json
fi

systemctl daemon-reload
systemctl enable nginx traefik
if [[ "$restart_traefik" -eq 1 ]] || ! systemctl is-active --quiet traefik; then
  systemctl restart traefik
fi
nginx -t
if systemctl is-active --quiet nginx; then
  systemctl reload nginx
else
  systemctl start nginx
fi
