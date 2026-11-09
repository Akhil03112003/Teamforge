# TeamForge
Production-oriented hackathon team formation for 10,000 participants and 2,000 teams.

Spring Boot/PostgreSQL backend and React/TypeScript/Vite frontend. M1-B adds local registration/login/logout, session restoration and private participant profiles. Team workflows are not implemented.
Start with [progress](docs/progress.md), [requirements](docs/requirements.md), and [decisions](docs/decisions.md).
M0/M1-A are complete. M1-B automated verification passed on Java 21: 34 backend tests, frontend typecheck/build, and real local PostgreSQL/session flow through Vite's proxy. Browser visual/keyboard runtime review is unavailable; M2 has not started.

See [Windows startup commands](docs/local-development.md) and [M1-B verification](docs/verification-m1-b.md). Use the explicit local profile for loopback HTTP; deployed cookies stay Secure. No CI run, load benchmark or deployment is claimed.

