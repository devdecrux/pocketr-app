# pocketr-app

Simple on the surface. Solud underneath.

## Purpose

Pocketr is a self-hosted personal finance and household budgeting application.
It is designed to help you track money movements clearly, keep account balances accurate, and support both individual and shared household workflows.

## Current Capabilities

- User registration, login, logout, and session-based authentication.
- Account management for personal and household contexts.
- Category management for transaction organization.
- Ledger transaction creation and listing.
- Reporting endpoints for budget and spending visibility.
- Household membership and sharing flows.
- User avatar upload and retrieval.

## Roadmap

_Planned ahead:_

## Development Setup (IntelliJ + Docker)

### Requirements

- Docker must be installed and running locally.
- IntelliJ IDEA must be installed.

### Run Locally

1. Clone this repository to your machine.
2. Open the project root in IntelliJ IDEA.
3. IntelliJ will automatically load the `.run` configurations from the repository.
4. Run the `Pocketr Local Dev` run configuration.

## Docker Compose Files

- `docker-compose.yaml`: production Docker Compose file for the released app image, bundled PostgreSQL, and persistent avatar storage.
- `docker-compose.dev.yaml`: local development infrastructure with PostgreSQL, pgAdmin, and Traefik.

## Production With Docker

The production Compose file runs `ghcr.io/devdecrux/pocketr-app:${POCKETR_VERSION:-latest}` and a bundled PostgreSQL database by default. Set a strong database password before starting it:

```bash
POSTGRES_PASSWORD=change-me docker compose up -d
```

The app is available on `http://<HOST>:8081` by default. Use `POCKETR_PORT` to change the host port, or `POCKETR_BIND_ADDRESS` to restrict the bind address.

For an external PostgreSQL server, provide the Spring datasource variables and start only the app service:

```bash
DB_URL=jdbc:postgresql://postgres.example.com:5432/pocketr_db \
DB_USERNAME=pocketr_user \
DB_PASSWORD=change-me \
docker compose up -d --no-deps pocketr-app
```

When using an external database, do not run bare `docker compose up`; that command starts the bundled PostgreSQL service too.

### Useful Commands

- Start production app with bundled PostgreSQL: `POSTGRES_PASSWORD=change-me docker compose up -d`
- Start development infrastructure: `docker compose -f docker-compose.dev.yaml up -d db traefik-reverse-proxy`
- Stop development infrastructure: `docker compose -f docker-compose.dev.yaml down`

## Runtime Contract (LXC/Production)

- Frontend URL: `http://<HOST>:8081`
- API base URL: `http://<HOST>:8081/api/v1`
- App port: `SERVER_PORT` (defaults to `8081`)
