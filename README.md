# TeamForge
Production-oriented hackathon team formation for 10,000 participants and 2,000 teams.

Spring Boot/PostgreSQL backend and React/TypeScript/Vite frontend. Authentication/profiles, team lifecycle, persisted idempotency, rotating invitations and leader-approved join requests are implemented. SSE and teammate suggestions remain future work.
Start with [progress](docs/progress.md), [requirements](docs/requirements.md), and [decisions](docs/decisions.md).
M4 automated verification passed on Java21/local PostgreSQL: 133 tests, zero failures/errors/skips, frontend typecheck/build and executable packaging. The public 50-contender invitation race produced exactly one successful admission. Runtime browser visual/keyboard review remains unexecuted. Stop for review before M5.

See [Windows startup commands](docs/local-development.md) and [M4 verification](docs/verification-m4.md). Use the explicit local profile for loopback HTTP; deployed cookies stay Secure. No CI run, load benchmark or deployment is claimed.

