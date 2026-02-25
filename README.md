# pocketr-app

Simple on the surface. Solid underneath.

## Dev Runtime Model (Debug)

In local development, only infrastructure runs in Docker:

- `db` (PostgreSQL) and `traefik-reverse-proxy` run in Compose.
- `pocketr-api` and `pocketr-ui` run on the host (IntelliJ/terminal).

### 1) Prepare environment variables (optional)

```bash
cp .env.example .env
```

- If `.env` is missing, Docker Compose falls back to `pocketr_user` / `pocketr_password`.
- `.env` is used when you want custom local DB credentials.
- Backend `dev` profile keeps fallback credentials (`pocketr_user` / `pocketr_password`) for local IntelliJ startup.
- If you change DB credentials in `.env`, set matching `DB_USER` and `DB_PASSWORD` in backend Run/Debug env vars.

### 2) Start infra services (Docker)

```bash
docker compose up -d db traefik-reverse-proxy
```

Stop/remove infra services:

```bash
docker compose down
```

### 3) Start backend on host (`localhost:8081`)

```bash
cd pocketr-api
./gradlew bootRun
```

### 4) Start frontend on host (`localhost:5173`)

```bash
cd pocketr-ui
npm run dev
```

### Access points through Traefik

- `http://localhost/frontend` -> frontend dev server
- `http://localhost/api/...` -> backend API

### Traefik dev hardening notes

- Traefik is development/debug only in this repo.
- Published HTTP is localhost-bound only (`127.0.0.1:80:80`).
- Dashboard is not exposed (`api.dashboard=false`, `api.insecure=false`).
- Routing is restricted to `localhost`, `127.0.0.1`, and `host.docker.internal`.
- HTTP remains the default dev path; HTTPS is not required for local startup.
