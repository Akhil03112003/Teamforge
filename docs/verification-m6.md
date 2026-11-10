# M6 verification — 2026-11-10

M6 implemented; automated gates, responsive/keyboard checks and production-bundle end-to-end browser verification passed. Stop before M7. No commit/push, dependency installation, database reset, deployment/load/submission work. Initial Git tree clean. V1–V6 unchanged; no M3 locking/idempotency change.

## Commands and observed results

- Read AGENTS and current progress/product/requirements/API/architecture/decision documents; inspected React components/client/live hook and M1–M5 routes.
- `python .tmp/verify-m5.py`: unchanged170-test Java21 baseline passed, zero failures/errors/skips, executable packaging,4m50s.
- `python .tmp/verify-m6.py`: Java21 clean verify passed171 tests, zero failures/errors/skips, BUILD SUCCESS/executable packaging,4m20s. Includes all170 originals and one search-read test. All six existing Flyway migrations validated; none edited or added.
- The helpers privately read root .env, map PostgreSQL settings, select installed JDK21.0.13.9 and enable TEAMFORGE_DATABASE_TESTS=true. Actual Maven invocation:

```powershell
& 'C:/Maven/apache-maven-3.9.12-bin/apache-maven-3.9.12/bin/mvn.cmd' `
  --offline --batch-mode --no-transfer-progress `
  -Dmaven.repo.local=D:/HackerRank/PS1/.tmp/m2-local `
  -Dteamforge.build.directory=D:/HackerRank/PS1/.tmp/backend-verify `
  -f backend/pom.xml clean verify
```

Ignored cache/output retain the previous sandbox/IDE workaround; no dependency/build-default changes. Native PostgreSQL18.6 retains the Flyway tested-version warning (latest tested17); PostgreSQL17.7/Testcontainers/remote CI not run.

- `npm.cmd --prefix frontend run typecheck`: passed.
- `npm.cmd --prefix frontend run build`: final source passed;25 modules;207ms; JS265.11kB/gzip80.86kB, CSS12.37kB/gzip3.56kB. No new package dependency; package.json only adds the Node test script.
- `npm.cmd --prefix frontend test`:15 passed,0 failed/skipped,16.143s in the final component run. Sanitized output in ignored .tmp/m6-frontend-tests.log. Node24.19.0 uses existing Vite/React and installed Chrome via CDP; no Playwright/test-library/axe package download.
- `python .tmp/m5-smoke.py`: exit0; actual packaged backend/Vite-proxy authenticated SSE200/ready, keyed create201/invalidate, board/suggestions200, edit200/invalidate, reconnect ready/REST reconciliation, normal final leave and logout. Only smoke-owned processes/active fixtures cleaned; immutable audit retained.
- `git -c core.safecrlf=false diff --check`: passed. Migration diff empty. .env remains ignored; no credentials printed/changed or staged.

## Browser availability and verification history

Impeccable's context launcher could not install its engine in the restricted user cache; existing context/CSS/specifications were read directly. CUA inventory reported the browser access host unavailable. Initial sandboxed Chrome exited2147483651 with GPU sandbox/encryption errors and could not expose its debugging port. An isolated headless Chrome using --no-sandbox/--disable-gpu/--disable-software-rasterizer/--password-store=basic successfully rendered; these flags are confined to ignored fresh QA profiles and synthetic users, not application/deployment settings. No normal user browser profile/window was controlled or reset.

Early component runs were4/11,9/11 and10/11 while the driver and new UI were being corrected: nested navigation hint text did not match the initial selector, clicks hit disabled/loading controls, Escape lacked a native virtual-key code, and dialog focus wrapping needed hardening. Native pointer events, enabled/stable/hit-tested targets, actual key codes and explicit dialog focus containment corrected these. The later11-case run passed; expanded13-,14- and final15-case suites passed. Earlier failures are not claimed as successful checks.

Real browser journeys passed in several development runs. Some intermediate attempts timed out on request/confirmation controls while UI/harness changes were still underway; one diagnostic showed a visible enabled request button with no alert. Subsequent work separated background revalidation from disabling initial loads, guarded stale selection and stabilized navigation/pointer waits. The final run uses a frozen production bundle through Vite preview to remove hot-reload interference; the final observed pass is recorded below. An ignored PowerShell/Python quoting attempt to change the harness mode also failed; it was corrected with an exact file patch and did not change application code or credentials.

## What the15 component/browser tests check

1. Mobile registration, CSRF, cleared password and no credential storage.
2. Login failure focus/associated errors and successful workspace restoration.
3. Keyboard navigation, main focus, visible H1 and skip-link preserving the current route.
4. Profile fields, availability row creation and typed CSRF update.
5. Duplicate create submission guard and retained idempotency header.
6. Whole-event search/filter query forwarding and cursor-compatible server behavior.
7. Invitation confirmation, Escape/opener restoration, copy feedback and no persistent token storage.
8. Request creation/pending state and withdrawal.
9. Leave dialog Tab/Shift+Tab containment, Escape, opener restoration and confirmed membership update.
10. SSE invalidation/refetch, one active subscription across views and logout cleanup.
11. Four authenticated views at1440×1000,768×1024,390×844: one visible H1, labeled inputs, valid described-by references,44px button/nav targets and no horizontal overflow; Chrome accessibility-tree navigation role/name.
12. Useful403/409/429 recovery messages without losing profile drafts.
13. Computed rendered-text contrast (4.5:1 normal/3:1 large), keyboard focus outline and emulated reduced-motion styles across all four views.
14. Hackathon URL restoration on reload/history navigation without browser credential storage.
15. Background request revalidation keeps already loaded actions available.

These tests run the actual React components/client in Chrome with explicit fixture API/SSE responses. They are not evidence of real database/session behavior; the separate end-to-end flow provides that evidence. Clipboard writes are stubbed here, so system clipboard permissions are not certified.

## Real browser and responsive review

The opt-in `frontend/tests/real-flow.mjs` uses three fresh isolated browser contexts, synthetic registrations and genuine server sessions/CSRF. The private harness `python .tmp/m6-e2e.py` provisions one unique owned min2/max5 QA hackathon, starts a packaged Java21 backend8081 and Vite frontend5174, runs the flow and removes only exact owned QA event/accounts/active rows/keys. Immutable audit and existing services/user data are retained. The final harness serves the production dist through Vite preview, whose proxy inherits the configured backend forwarding.

Flows cover registration/login, profile/availability save, team creation, suggestions, generate invitation, join request/withdraw/re-request, leader acceptance/decline/removal, valid invite redemption, leader departure/earliest-member succession, mobile removal/final deletion, reload/session/event restoration and logout401. A third actor registers/requests/redeems at390×844; leader operations run1440×1000; successor actions/reload/departure run390×844. Genuine SSE refresh is needed for remote removal/leadership controls to reconcile before subsequent actions.

Saved captures in ignored .tmp were actually inspected: desktop/mobilediscovery, desktop/mobile My team and desktop/tablet/mobile profile. Visual review led to compact advanced filters, a shorter mobile header, better button spacing and collapsible team editing. Automated layout checks include all four views at all three sizes. This is Chrome viewport emulation, not physical Android/iOS testing.

## Remaining limitations

- No manual NVDA/JAWS/VoiceOver, Firefox/Safari, physical touch-device, forced-colors,200% zoom or complete WCAG audit. The custom DOM/AX-tree/contrast rules are not an axe audit or accessibility certification.
- Browser fixtures cover normal/error/loading states but not every network interruption/permutation. Real full-flow checks are separate; historical timing failures are disclosed above.
- Requests for a team never loaded in the current tab may initially show a short-ID inspect label; the view action retrieves the team. This is a minor labeling limitation, not an admission bypass.
- Clipboard permission-denial fallback exists; native OS clipboard access was not asserted. Invite plaintext never enters URLs/logs/localStorage/sessionStorage.
- q search uses a bounded event-scoped substring scan; M8 must measure it alongside existing board aggregation, SSE fan-out and matching. No100-VU/p95<200ms or deployment/CI claim.

## Final production-bundle result

`python .tmp/m6-e2e.py`: exit0 against the final compiled production dist served by Vite preview5174 and packaged Java21 backend8081, using real local PostgreSQL. The complete three-account flow printed:

```text
REAL_BROWSER_PASS registration/login/profile/create/invite/request/withdraw/accept/decline/remove/redeem/succession/delete/session-restoration/logout
REAL_BROWSER_PASS desktop1440/mobile390, genuine PostgreSQL and SSE; screenshots contain no invitation codes
```

The harness removed only its unique QA event, synthetic accounts, active dependent rows/keys and owned processes; immutable audits and existing services/data remain. Final fixtures were provisioned by owned-ID SQL inserts, not a database reset or migration edit. The run checked actual route/hackathon/session restoration after reload. Mobile actor registration/request/redemption and successor removal/departure ran at390×844. The standalone HTTP/proxy/SSE smoke also passed as recorded above. No source edits/hot reload occurred during the final production-bundle run.

## Final backend regression details

| Test class | Tests | Failures/errors/skips | Seconds |
|---|---:|---|---:|
| ParticipantIntegrationTest | 28 | 0/0/0 | 55.153 |
| DatabaseIntegrationTest | 2 | 0/0/0 | 2.754 |
| HealthIntegrationTest | 3 | 0/0/0 | 1.204 |
| AuthRateLimitFilterTest | 1 | 0/0/0 | 0.048 |
| AdmissionWorkflowsIntegrationTest | 44 | 0/0/0 | 59.704 |
| InviteRateLimitIntegrationTest | 1 | 0/0/0 | 4.406 |
| LiveDiscoveryIntegrationTest | 26 | 0/0/0 | 15.837 |
| MembershipConcurrencyIntegrationTest | 27 | 0/0/0 | 33.504 |
| SuggestionScoringTest | 12 | 0/0/0 | 0.031 |
| TeamLifecycleIntegrationTest | 27 | 0/0/0 | 61.636 |
| **Total** | **171** | **0/0/0** | |

Public50-contender regression: one HTTP201/49 TEAM_FULL409, maximum roster2 and49 nonmembers,329ms, zero ordinary deadlocks/timeouts. Mixed public invite/approval race17ms, one admission. Existing M3 independent50-connection/PID races:5542/101/87ms, one winner each; no ordinary deadlocks/timeouts. Deliberate deadlock/timeout recovery cases passed separately. Nothing here is a k6/p95 performance claim.

## Reproduction and CI

`npm.cmd --prefix frontend test` is the dependency-free browser test entry point. It starts only its own fixture Vite5175 and isolated Chrome, and fails rather than silently skipping when the required browser is unavailable. Set BROWSER_BIN if automatic discovery fails; see local-development.md. The optional real flow requires existing application servers and a dedicated QA event, as documented there; the ignored private harness used above loads existing .env settings without displaying them and cleans exact owned IDs.

GitHub Actions now invokes npm test after frontend typecheck/build. Its runner must provide Chrome/Chromium (or BROWSER_BIN); no browser package is downloaded by this test suite. Remote Actions has not run, so no CI result is claimed. Node runner reports15 successful component tests; native OS clipboard permissions, full assistive-technology behavior and cross-engine coverage remain outside those assertions.

## Files changed

- .github/workflows/ci.yml
- README.md
- backend/src/main/java/com/teamforge/teams/api/TeamController.java
- backend/src/main/java/com/teamforge/teams/application/BoardQuery.java
- backend/src/test/java/com/teamforge/teams/LiveDiscoveryIntegrationTest.java
- docs/api-contract.md
- docs/architecture.md
- docs/decisions.md
- docs/implementation-plan.md
- docs/local-development.md
- docs/product-spec.md
- docs/progress.md
- docs/requirements.md
- docs/test-plan.md
- docs/verification-m6.md
- frontend/package.json
- frontend/src/App.tsx
- frontend/src/api/client.ts
- frontend/src/components/AdmissionPanel.tsx
- frontend/src/components/AuthForm.tsx
- frontend/src/components/ConfirmDialog.tsx
- frontend/src/components/ProfileForm.tsx
- frontend/src/components/TeamDashboard.tsx
- frontend/src/components/useMounted.ts
- frontend/src/styles.css
- frontend/tests/browser.mjs
- frontend/tests/fixture.mjs
- frontend/tests/real-flow.mjs
- frontend/tests/ui.test.mjs

Git remains uncommitted: modified tracked files plus the new files above; nothing staged, committed or pushed. Final diff --check passed, .env is ignored and V1–V6 have no diff. No M7 work started.
