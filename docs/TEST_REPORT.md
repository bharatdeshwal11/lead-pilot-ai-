# Test report

## Repeatable checks

| Area | Run from | Command |
| --- | --- | --- |
| Backend unit and PostgreSQL integration suite | `backend/` | `./mvnw verify` |
| Frontend component and form suite | `frontend/` | `npm test` |
| Frontend TypeScript and production build | `frontend/` | `npm run build` |
| Browser workflow | `frontend/` with the seeded stack running | `npm run test:e2e` |
| Mock intent evaluation | repository root | `./scripts/evaluate-intent.sh` |
| Complete local stack | repository root | `docker compose up --build` |

Backend integration tests use real PostgreSQL, with Testcontainers by default or a separate server configured via `TEST_DB_URL`, `TEST_DB_USERNAME` and `TEST_DB_PASSWORD`. Browser testing needs the running full stack and a configured demo account. See the [setup guide](SETUP_GUIDE.md).

To run the browser workflow against a seeded Compose stack, run these commands from the repository root:

```bash
set -a
. ./.env
set +a
cd frontend
npm ci
npx playwright install chromium
npm run test:e2e
```

Set `PLAYWRIGHT_CHROMIUM_PATH` if using an existing system Chromium. The default target is `http://127.0.0.1:3000`. Set `PLAYWRIGHT_BASE_URL=http://127.0.0.1:5173` if using the native Vite stack. Use a disposable demo database. The browser workflow creates and removes its own lead.

## Suite results

| Command | Observed result |
| --- | --- |
| `cd backend && ./mvnw verify` | 35 passed, 0 failed, 0 errors, 0 skipped. PostgreSQL 17.12 applied Flyway migrations V1 through V3. |
| `cd frontend && npm test` | 11 passed across 4 component and feature test files. |
| `cd frontend && npm run build` | TypeScript check and Vite production build passed. |
| `cd frontend && npm audit --omit=dev` | Production dependency audit found zero vulnerabilities. |
| `docker compose config --quiet` | Compose configuration parsed successfully. |
| `docker compose up --no-build --wait` | Passed with locally assembled images. PostgreSQL and the API were healthy, and Nginx served the frontend, health and OpenAPI endpoints on port 3000. |

Backend coverage includes session authentication and invalidation, role and record scope, validation and duplicate intake, lead CRUD and retained activities, PostgreSQL follow-ups and dashboard aggregates, the AI draft workflow, and mock and simulated provider failures. Frontend coverage includes sign-in, form validation and API success and failure states.

The full frontend audit reported a build/test dependency advisory for Picomatch. The reported affected version is `4.0.7`. The suggested fix required an incompatible downgrade and a patched release was unavailable at check time. [GHSA-fc5q-39xc-vjpf](https://github.com/advisories/GHSA-fc5q-39xc-vjpf)

The final OSV scan checked 85 Maven dependencies. The report listed two Spring MVC 6.2.19 advisories for SSE fragment rendering and XSLT views. LeadPilot uses JSON controllers and static SPA routes and enables neither feature. Compatible dependency patches were applied for Jackson, Tomcat, Commons Lang, pgJDBC and Log4j. The 35-test Maven run above passed with the patched dependency set. [GHSA-j9f9-w8pj-32f8](https://github.com/advisories/GHSA-j9f9-w8pj-32f8) · [GHSA-pc63-qcmh-9cmg](https://github.com/advisories/GHSA-pc63-qcmh-9cmg)

## Executed browser checks

Playwright passed both Chromium scenarios against the native Vite stack and passed both again against the Compose stack on port 3000. The Compose run took 15.5 seconds, with 0 failed or skipped tests. The desktop workflow signed in, created and viewed an assigned lead, ran mock analysis, applied its priority recommendation, edited and approved a reply, and verified the draft after a reload. It scheduled an overdue follow-up, checked that the dashboard count changed, completed the follow-up and checked its history. It also confirmed lead deletion, logout and redirect from a protected route. The mobile scenario exercised the navigation drawer.

The separate screenshot run passed its one scenario and captured the [sign-in screen](images/login-desktop.png), [desktop dashboard](images/dashboard-desktop.png) and [mobile dashboard](images/dashboard-mobile.png).

Local HTTP verification checked `/health`, Swagger UI and the OpenAPI JSON through the Vite proxy. The OpenAPI response listed 21 paths and 18 DTO schemas, with no authentication principal or password hash in the contract. A CSRF-aware login returned `200` from both `127.0.0.1` and `localhost` Vite origins.

An additional Chromium smoke run opened the administrator team directory, verified that a duplicate account submission displayed the server's conflict, and confirmed that logout returned the session API to `401`.

The restart check created a lead, mock analysis, reply draft and follow-up via HTTP. After restarting both Spring Boot and PostgreSQL, the check signed in again, confirmed the same records through the API, and removed the test lead.

## Executed AI checks

The targeted command `./mvnw -Dtest=MockLeadAiProviderTest,OpenAiLeadAiProviderTest,AiServiceTest test` ran 11 tests successfully. It exercised mock extraction, the real adapter's request and response handling against a local HTTP stub, and failure handling in the AI service.

The `./scripts/evaluate-intent.sh` run reported **33 of 36 correct (91.7%)** on `backend/src/test/resources/ai-intent-evaluation.json`. The dataset has 12 fictional examples per intent label.

| Expected label | Predicted high | Predicted medium | Predicted low |
| --- | ---: | ---: | ---: |
| High | 11 | 1 | 0 |
| Medium | 0 | 11 | 1 |
| Low | 0 | 1 | 11 |

The high-intent booking-form enquiry landed at medium. A possible relocation and transit enquiry landed at low. A market survey containing a budget amount landed at medium. The result describes agreement between the mock rules and the fixture labels. A live OpenAI run and purchase outcomes were outside this evaluation.

## Deployment configuration checks

The Render Blueprint was checked against the official configuration documented for web services and managed PostgreSQL. The entrypoint passed `sh -n` and six smoke cases using a local process stub. These covered Render URL conversion, TLS options, a direct JDBC URL, manual host settings, default configuration and rejection of a malformed Render URL. [Render Blueprint specification](https://render.com/docs/blueprint-spec)

A separate local smoke test put the Vite production bundle in a scratch copy of the executable Spring Boot JAR, matching the hosted image's file layout. The executable served five SPA routes, a JavaScript asset, `/health`, a CSRF-aware login and the PostgreSQL-backed dashboard. This exercised the same-origin application layout directly on the JVM. Hosting-service deployment requires separate verification.

The workspace's Docker builder encountered package registry egress failures and ran out of space in its nested VFS storage during clean source builds. The frontend image was built from source using a temporary local dependency cache. The final backend image used the Maven-verified JAR on the configured Java runtime base after the source build ran out of space. Standard Compose runtime startup succeeded with those images. The clean `docker compose up --build` path remains unverified in this workspace. See [project status](../PROJECT_STATUS.md) for external deployment status.
