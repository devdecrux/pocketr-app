# pocketr-app

Simple on the surface. Solid underneath.

## Local External Services

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
- Traefik reverse proxy: `http://localhost` (`/api` -> backend on `localhost:8081`, `/frontend` -> frontend on `localhost:5173`)
