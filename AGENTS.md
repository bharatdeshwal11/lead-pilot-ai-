# LeadPilot AI repository instructions

Use Java 21 and the Maven Wrapper in `backend/`. Use the Node version documented in the README and install frontend dependencies with `npm ci`. Read `PROJECT_STATUS.md` for the latest verified environment and outstanding work.

Keep API controllers, business services, and repositories separate. Preserve the session and CSRF contract, role and assignment checks, and DTO boundaries. Add a Flyway migration when changing deployed database structure. Use fictional records in fixtures and keep credentials in ignored environment files.

Run `./mvnw verify` from `backend/` with an isolated PostgreSQL database or Testcontainers. Run `npm test` and `npm run build` from `frontend/`. Run `npm run test:e2e` against a disposable Compose instance for workflow changes.

Record observed test and deployment outcomes in `docs/TEST_REPORT.md` and `PROJECT_STATUS.md`.
