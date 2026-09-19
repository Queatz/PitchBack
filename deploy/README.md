# Deploy

The project is configured for a multi-server production architecture:
- **Web app** (`ranh.app`): Nginx serves the compiled Compose HTML SPA with Traefik handling TLS.
- **API server** (`api.ranh.app`): Nginx reverse-proxies Ktor on port 8080 with Traefik handling TLS, and ArangoDB on port 8529.

During `deploy/web.sh`, the build configures `window.__API_BASE__ = "https://api.ranh.app"` in `config.js` so the browser directly sends requests and SSE connections to `https://api.ranh.app`. The API server in `.env.prod` allows CORS requests from `https://ranh.app`.

## One-time local setup

```shell
cp deploy/config.env.example deploy/config.env
```

Configure `deploy/config.env`:
- `WEB_SSH_HOST` / `WEB_DOMAIN` (default: `ranh.app`)
- `API_SSH_HOST` / `API_DOMAIN` (default: `api.ranh.app`)
- `ACME_EMAIL` (for Let's Encrypt certificates)
- `ARANGO_ROOT_PASSWORD` (for first-time server initialization of ArangoDB user/database)

The remote servers should be Debian/Ubuntu. SSH key auth is required (`BatchMode`).

## Commands

Deploy web frontend:
```shell
./deploy/web.sh
./deploy/web.sh --skip-init   # after first run
```

Deploy backend API:
```shell
./deploy/server.sh
./deploy/server.sh --skip-init # after first run
```

Re-deploys are safe. Init (packages, Traefik, nginx, Arango user/db) is idempotent and does not drop data.

ArangoDB is installed from the official 3.12 `.deb` (the Debian repo signing key expired in March 2026). Re-running init removes a leftover apt source so `apt-get update` keeps working.

Traefik is downloaded on the machine running the deploy script (SHA256-checked) and uploaded. That avoids GitHub's release CDN resetting the connection from the VPS (`curl: (35) Recv failure: Connection reset by peer`). Re-runs skip the download when `/usr/local/bin/traefik` is already present.

SSE (`/api/events`) and WebSocket upgrades share ports 80/443 with the rest of the site — they do not use extra public ports. Init opens those on `ufw`, and Traefik/nginx keep the streams open (no 60s read timeout, no gzip, WebSocket `Upgrade` headers). Re-run `./deploy/server.sh` without `--skip-init` so the API host picks this up.

Each script ends with a local-on-server health check (must pass) and a public HTTPS check (warns if DNS/Let's Encrypt is still catching up).
