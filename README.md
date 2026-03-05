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

## Runtime Contract (LXC/Production)

- Frontend URL: `http://<HOST>:8081/frontend`
- API base URL: `http://<HOST>:8081/api/v1`
- App port: `SERVER_PORT` (defaults to `8081`)
