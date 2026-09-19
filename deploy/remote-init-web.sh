#!/usr/bin/env bash
# Idempotent nginx site + Traefik route for the static web app.
set -euo pipefail

if [[ "$(id -u)" -ne 0 ]]; then
  exec sudo -E bash "$0" "$@"
fi

STAGING="${1:-/tmp/pitchback-deploy}"
[[ -f "$STAGING/nginx-web.conf" ]] || { echo "missing $STAGING/nginx-web.conf" >&2; exit 1; }
[[ -f "$STAGING/traefik-web.yml" ]] || { echo "missing $STAGING/traefik-web.yml" >&2; exit 1; }

mkdir -p /var/www/pitchback /etc/nginx/sites-available /etc/nginx/sites-enabled /etc/traefik/dynamic
install -m 0644 "$STAGING/nginx-web.conf" /etc/nginx/sites-available/pitchback-web
ln -sfn /etc/nginx/sites-available/pitchback-web /etc/nginx/sites-enabled/pitchback-web
install -m 0644 "$STAGING/traefik-web.yml" /etc/traefik/dynamic/pitchback-web.yml

nginx -t
if systemctl is-active --quiet nginx; then
  systemctl reload nginx
else
  systemctl enable --now nginx
fi
