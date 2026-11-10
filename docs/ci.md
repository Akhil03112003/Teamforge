# Continuous integration

M7 configures `.github/workflows/ci.yml` for pushes and pull requests targeting `main`, plus manual dispatch. Remote execution is **unverified**: the local GitHub CLI cannot connect to `api.github.com` from this sandbox. Local passing commands are not a GitHub-hosted result.

## Runner contract

- Explicit Ubuntu 24.04, Temurin Java 21, Maven dependency cache keyed by the POM; backend job timeout 20 minutes.
- An owned PostgreSQL 17.7 container listens on loopback only. A generated, masked password exists only for that job. Readiness is checked before tests, and the container is removed on failure/success. No development database or repository secrets are required.
- `TEAMFORGE_DATABASE_TESTS=true` enables all real PostgreSQL suites. Startup applies/validates Flyway; `mvn --batch-mode --no-transfer-progress -f backend/pom.xml clean verify` compiles, tests and packages. Failures propagate to the job.
- Node 24, npm cache keyed by the unchanged lockfile, `npm ci`, `npm run typecheck`, `npm test`, `npm run build` in `frontend/`.
- Chrome stable and Linux dependencies are provisioned explicitly; `BROWSER_BIN` is passed to the existing portable Node/CDP browser harness. No Windows browser path is needed. Chrome stable is intentionally updated by the installer; package dependencies are lockfile-pinned.
- Action code is pinned to full commit SHAs, checkout does not retain Git credentials, token permission is `contents: read`, overlapping branch runs are cancelled. No `pull_request_target` execution.
- Always-uploaded artifacts contain only Surefire reports and frontend synthetic-fixture screenshots, retained seven days. Never upload `.env`, whole workspaces, browser profiles or runtime logs containing user traffic. Test reports may contain synthetic identifiers; use the isolated CI database only.

Action pins were checked against official release pages: [checkout v4.2.2](https://github.com/actions/checkout/releases/tag/v4.2.2), [setup-java v4.7.1](https://github.com/actions/setup-java/releases/tag/v4.7.1), [setup-node v4.4.0](https://github.com/actions/setup-node/releases/tag/v4.4.0), [upload-artifact v4.6.2](https://github.com/actions/upload-artifact/releases/tag/v4.6.2), [setup-chrome v2.2.0](https://github.com/browser-actions/setup-chrome/releases/tag/v2.2.0). Runner inventory: [Ubuntu 24.04](https://github.com/actions/runner-images/blob/main/images/ubuntu/Ubuntu2404-Readme.md). Container tags are not digest-pinned; verify platform image digests before release.

## Trigger and inspect after review

No commit, push, dispatch or repository-settings change was performed. After the reviewed workflow is committed and pushed with approval to `main`, a push triggers CI. A PR into `main` also triggers it. From a normal authenticated terminal:

```powershell
gh workflow run ci.yml --ref main --repo Akhil03112003/Teamforge
gh run list --workflow ci.yml --branch main --limit 5 --repo Akhil03112003/Teamforge
# Replace RUN_ID with the run returned above.
gh run watch RUN_ID --exit-status --repo Akhil03112003/Teamforge
gh run view RUN_ID --log-failed --repo Akhil03112003/Teamforge
gh run download RUN_ID --dir .tmp/ci-RUN_ID --repo Akhil03112003/Teamforge
```

Workflow dispatch requires the workflow on the default branch and appropriate GitHub permission. Require both jobs in branch protection after observing the first hosted run; protection has not been configured here. Release packaging is described in [deployment](deployment.md); CI does not deploy.
