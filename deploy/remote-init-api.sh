#!/usr/bin/env bash
# Idempotent API host setup: Java, ArangoDB user/db, nginx reverse proxy, Traefik, systemd.
# Does not drop existing databases or wipe uploads.
set -euo pipefail

if [[ "$(id -u)" -ne 0 ]]; then
  exec sudo -E bash "$0" "$@"
fi

STAGING="${1:-/tmp/pitchback-deploy}"
ARANGO_ROOT_PASSWORD="${ARANGO_ROOT_PASSWORD:-}"

[[ -f /etc/debian_version ]] || { echo "init expects Debian/Ubuntu" >&2; exit 1; }
[[ -f "$STAGING/nginx-api.conf" ]] || { echo "missing $STAGING/nginx-api.conf" >&2; exit 1; }
[[ -f "$STAGING/nginx-upgrade.conf" ]] || { echo "missing $STAGING/nginx-upgrade.conf" >&2; exit 1; }
[[ -f "$STAGING/traefik-api.yml" ]] || { echo "missing $STAGING/traefik-api.yml" >&2; exit 1; }
[[ -f "$STAGING/pitchback.service" ]] || { echo "missing $STAGING/pitchback.service" >&2; exit 1; }
[[ -f "$STAGING/arango-setup.js" ]] || { echo "missing $STAGING/arango-setup.js" >&2; exit 1; }

# Official ArangoDB 3.12 apt key expired 2026-03-24 (EXPKEYSIG C04AD0FD86FEC04D).
# Drop a leftover repo so apt-get update can run; we install the .deb directly.
rm -f /etc/apt/sources.list.d/arangodb.list \
  /etc/apt/sources.list.d/arangodb.sources \
  /usr/share/keyrings/arangodb.gpg

export DEBIAN_FRONTEND=noninteractive
apt-get update -y
apt-get install -y --no-install-recommends ca-certificates curl gnupg

if ! command -v java >/dev/null 2>&1; then
  if ! apt-get install -y --no-install-recommends openjdk-21-jre-headless; then
    echo "error: Java 21 is required (openjdk-21-jre-headless)" >&2
    exit 1
  fi
fi

if ! id pitchback >/dev/null 2>&1; then
  useradd --system --home /opt/pitchback --create-home --shell /usr/sbin/nologin pitchback
fi
mkdir -p /opt/pitchback/data/uploads
chown -R pitchback:pitchback /opt/pitchback

install_arangodb() {
  if command -v arangosh >/dev/null 2>&1; then
    systemctl enable --now arangodb3 2>/dev/null || true
    return 0
  fi
  if [[ -z "$ARANGO_ROOT_PASSWORD" ]]; then
    echo "error: ARANGO_ROOT_PASSWORD is required in deploy/config.env to install ArangoDB" >&2
    exit 1
  fi

  local arch
  arch="$(dpkg --print-architecture)"
  case "$arch" in
    amd64|arm64) ;;
    *)
      echo "error: unsupported architecture for ArangoDB: $arch" >&2
      exit 1
      ;;
  esac

  # Apt repo signatures are unusable (expired key). Download the latest stable
  # 3.12 .deb over HTTPS and verify SHA256 from the Packages index.
  local tmp_dir version filename sha256
  tmp_dir="$(mktemp -d)"
  trap 'rm -rf "$tmp_dir"' RETURN
  curl -fsSL https://download.arangodb.com/arangodb312/DEBIAN/Packages -o "$tmp_dir/Packages"
  read -r version filename sha256 < <(
    awk -v arch="$arch" '
      BEGIN { RS=""; FS="\n" }
      {
        pkg = ver = fn = sha = thisarch = ""
        for (i = 1; i <= NF; i++) {
          split($i, a, ": ")
          if (a[1] == "Package") pkg = a[2]
          else if (a[1] == "Version") ver = a[2]
          else if (a[1] == "Architecture") thisarch = a[2]
          else if (a[1] == "Filename") fn = a[2]
          else if (a[1] == "SHA256") sha = a[2]
        }
        if (pkg == "arangodb3" && thisarch == arch && ver !~ /~/) {
          print ver, fn, sha
        }
      }
    ' "$tmp_dir/Packages" | sort -V | tail -n 1
  )
  if [[ -z "${filename:-}" || -z "${sha256:-}" ]]; then
    echo "error: could not find a stable arangodb3 package for $arch" >&2
    exit 1
  fi

  echo "downloading ArangoDB ${version} ($arch)"
  curl -fsSL "https://download.arangodb.com/arangodb312/DEBIAN/${filename}" -o "$tmp_dir/arangodb3.deb"
  echo "${sha256}  ${tmp_dir}/arangodb3.deb" | sha256sum -c --strict -

  echo "arangodb3 arangodb3/password password ${ARANGO_ROOT_PASSWORD}" | debconf-set-selections
  echo "arangodb3 arangodb3/password_again password ${ARANGO_ROOT_PASSWORD}" | debconf-set-selections
  echo "arangodb3 arangodb3/upgrade boolean true" | debconf-set-selections
  echo "arangodb3 arangodb3/storage_engine select auto" | debconf-set-selections
  apt-get install -y "$tmp_dir/arangodb3.deb"
  systemctl enable --now arangodb3
}

install_arangodb

echo "waiting for ArangoDB..."
ok=0
for _ in $(seq 1 30); do
  if arangosh \
    --server.endpoint tcp://127.0.0.1:8529 \
    --server.username root \
    --server.password "$ARANGO_ROOT_PASSWORD" \
    --javascript.execute-string 'db._version();' >/dev/null 2>&1; then
    ok=1
    break
  fi
  sleep 2
done
if [[ "$ok" -ne 1 ]]; then
  echo "error: ArangoDB did not become ready on 127.0.0.1:8529" >&2
  exit 1
fi

arangosh \
  --server.endpoint tcp://127.0.0.1:8529 \
  --server.username root \
  --server.password "$ARANGO_ROOT_PASSWORD" \
  --javascript.execute "$STAGING/arango-setup.js"

mkdir -p /etc/nginx/sites-available /etc/nginx/sites-enabled /etc/nginx/conf.d /etc/traefik/dynamic
install -m 0644 "$STAGING/nginx-upgrade.conf" /etc/nginx/conf.d/pitchback-upgrade.conf
install -m 0644 "$STAGING/nginx-api.conf" /etc/nginx/sites-available/pitchback-api
ln -sfn /etc/nginx/sites-available/pitchback-api /etc/nginx/sites-enabled/pitchback-api
install -m 0644 "$STAGING/traefik-api.yml" /etc/traefik/dynamic/pitchback-api.yml
install -m 0644 "$STAGING/pitchback.service" /etc/systemd/system/pitchback.service

nginx -t
if systemctl is-active --quiet nginx; then
  systemctl reload nginx
else
  systemctl enable --now nginx
fi

systemctl daemon-reload
systemctl enable pitchback
