# Problem statement
Structured summary of the supplied PS1 requirements, not a verbatim copy of the competition announcement.

Build a production-grade platform for 10,000 participants and 2,000 teams. Mandatory capabilities are configurable minimum/maximum team sizes; one membership per person per hackathon; expiring high-entropy invites; current-leader-only request acceptance/decline and member removal; automatic leader succession; deletion after the final departure; exactly one successful join among 50 final-seat contenders; retry-safe team creation and joining; live board/team pages; authentication, authorization, validation and rate limiting; actor/action/timestamp audit records for every team change; explainable complementary-skill/time-zone ranking; at least 20 API tests in GitHub Actions; a 10,000-user/2,000-team seed script; board p95 below 200 ms with 100 concurrent users; responsive keyboard-accessible React UI; and live deployment.

Submit GitHub repository, live URL, load-test report, one-page design document, and walkthrough video no longer than five minutes.
Evaluation prioritizes concurrency correctness, defensible design decisions, then code quality, tests and UX.

Approved stack: Java 21, Spring Boot/Security, PostgreSQL/Flyway, React/TypeScript/Vite, modular monolith, persisted idempotency, SSE with refetch, JUnit 5/Testcontainers/k6/GitHub Actions. Avoid Redis, Kafka, microservices and distributed locks unless justified.

Approved defaults: multiple hackathons with one active demo; event limits default 2–5; undersized teams incomplete; public join requires approval; valid invite joins directly subject to constraints; invites expire after 24 hours and rotate; multiple pending requests across teams; earliest remaining member succeeds leader; retain audits after deletion; suggestion weights 70% complementary skills and 30% collaboration overlap.
Authentication provider and deployment platform remain unresolved.

