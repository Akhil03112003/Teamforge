# TeamForge

M7 adds CI, security and release hardening. Start with [local development](docs/local-development.md); review [CI](docs/ci.md), [security](docs/security.md), [deployment prerequisites](docs/deployment.md) and [M7 verification](docs/verification-m7.md). Hosting/remote CI remain unverified; no M8 load result or live deployment is claimed. The newest entry in [progress](docs/progress.md) supersedes historical milestone notes.
Production-oriented hackathon team formation for 10,000 participants and 2,000 teams.

Spring Boot/PostgreSQL backend and React/TypeScript/Vite frontend. Authentication/profiles, team lifecycle, persisted idempotency, invitations/requests, filtered live discovery, SSE invalidation and explainable teammate suggestions are implemented.
Start with [progress](docs/progress.md), [requirements](docs/requirements.md), and [decisions](docs/decisions.md).
M6 verification passed: 171 Java21/PostgreSQL tests, 15 Chrome interaction/accessibility tests, frontend typecheck/build and real three-participant browser journeys through the production bundle/Vite proxy. Responsive desktop/tablet/mobile checks and keyboard/contrast checks ran; manual screen-reader and cross-browser reviews remain open. The public 50-contender admission race remains green. Stop for review before M7.

See [Windows startup and test commands](docs/local-development.md) and [M6 verification](docs/verification-m6.md). SSE is best-effort notification; REST reconciliation is authoritative. Use the explicit local profile for loopback HTTP; deployed cookies stay Secure. No remote CI run, load benchmark or deployment is claimed.

