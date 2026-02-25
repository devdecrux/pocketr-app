# pocketr-app

Simple on the surface. Solid underneath.

## Local External Services (Dev Only)

Start infrastructure dependencies with Docker Compose:

```bash
docker compose up -d db traefik-reverse-proxy
```

Stop and remove them:

```bash
docker compose down
```

Service defaults:

- PostgreSQL: `localhost:5432` (`pocketr_db` / `pocketr_user` / `pocketr_password`)
- Traefik reverse proxy (dev-only): `http://localhost` (`/api` -> backend on `localhost:8081`, `/frontend` -> frontend on `localhost:5173`)

Traefik dev expectations:

- Traefik is intentionally local-development only in this repo.
- The published HTTP port is localhost-bound only (`127.0.0.1:80:80` in `docker-compose.yaml`).
- Traefik dashboard is disabled (`api.dashboard=false` and `api.insecure=false` in `config/traefik/traefik.yml`).
- Routing remains host-based via `host.docker.internal` (`config/traefik/dynamic.yml`), so backend/frontend should continue running on the host machine.

Expected commands and runtime model:

```bash
# Terminal 1: backend on host :8081
cd pocketr-api
./gradlew bootRun

# Terminal 2: frontend dev server on host :5173
cd pocketr-ui
npm run dev

# Terminal 3: infra (Postgres + Traefik)
docker compose up -d db traefik-reverse-proxy
```

Expected access points:

- `http://localhost/frontend` -> frontend dev server through Traefik
- `http://localhost/api/...` -> backend through Traefik
