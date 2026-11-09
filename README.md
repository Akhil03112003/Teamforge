# TeamForge
Production-oriented hackathon team formation for 10,000 participants and 2,000 teams.

Spring Boot/PostgreSQL backend and React/TypeScript/Vite frontend. Local authentication/profiles and M2 team creation, details, roster management, departure/succession/deletion and retained audit history are implemented. Joining/invites/requests/SSE remain future work.
Start with [progress](docs/progress.md), [requirements](docs/requirements.md), and [decisions](docs/decisions.md).
M0/M1 are complete. M2 automated verification passed on Java21: 61 tests, frontend typecheck/build and packaged local PostgreSQL/proxy flows. Browser visual/keyboard runtime review is unavailable; M3 has not started. The 50-contender race and persisted idempotency replay are not yet verified/implemented.

See [Windows startup commands](docs/local-development.md) and [M2 verification](docs/verification-m2.md). Use the explicit local profile for loopback HTTP; deployed cookies stay Secure. No CI run, load benchmark or deployment is claimed.

