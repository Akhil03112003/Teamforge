# TeamForge
Production-oriented hackathon team formation for 10,000 participants and 2,000 teams.

Spring Boot/PostgreSQL backend and React/TypeScript/Vite frontend. Authentication/profiles, team lifecycle, persisted idempotency, invitations/requests, filtered live discovery, SSE invalidation and explainable teammate suggestions are implemented.
Start with [progress](docs/progress.md), [requirements](docs/requirements.md), and [decisions](docs/decisions.md).
M5 automated verification passed on Java21/local PostgreSQL: 170 tests, zero failures/errors/skips, frontend typecheck/build, executable packaging and HTTP/Vite-proxy board/SSE/reconnect smoke. The public 50-contender admission race remains green. Runtime browser visual/keyboard review remains unexecuted. Stop for review before M6.

See [Windows startup commands](docs/local-development.md) and [M5 verification](docs/verification-m5.md). SSE is best-effort notification; REST reconciliation is authoritative. Use the explicit local profile for loopback HTTP; deployed cookies stay Secure. No CI run, load benchmark or deployment is claimed.

