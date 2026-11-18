# Project status

**Implemented with external deployment pending.** The local repository contains the working application, test suites, Docker configuration, GitHub Actions workflow, Render Blueprint and interview documentation.

## Completed milestones

- Built a Java 21 and Spring Boot API, a responsive React application, and three Flyway migrations for PostgreSQL.
- Verified sessions, role and assignment boundaries, lead CRUD, retained activity records, user management, AI analysis, draft review, follow-ups and database-backed dashboard metrics.
- Ran 35 backend tests and 11 frontend tests successfully. Built the frontend production bundle and verified two Chromium workflows against the native development stack and again against Docker Compose.
- Verified that a lead, mock analysis, reply draft and follow-up survived a restart of Spring Boot and PostgreSQL. Verified all three migrations and the fictional seed on a fresh database.
- Checked OpenAPI, Swagger UI and the health endpoint. Captured desktop and mobile screenshots from the running demo.
- Ran the mock intent evaluation against a balanced 36-enquiry fixture set with 33 matches. Exercised the OpenAI adapter with an HTTP test server.
- Checked runtime dependencies with npm audit and OSV. The test report records the remaining scanner findings and the features they concern.

## Local deployment

The frontend, backend and PostgreSQL services started with `docker compose up --no-build --wait`. Both real-browser scenarios passed on the Compose stack. The workspace Compose app uses port 3000.

A fresh network-enabled `docker compose up --build` was limited here by dependency downloads inside Docker and VFS storage. Container builds used local dependency caches, and the running backend used the tested patched Spring Boot JAR assembled into its JRE image. The repository Dockerfiles use the normal package registries for clean builds. The [setup guide](docs/SETUP_GUIDE.md) also explains native startup.

## External blockers and next actions

1. **GitHub publishing.** The supplied GitHub CLI credential received HTTP 401. With that credential unset, the CLI had no saved authentication. Authenticate with an account that can create the repository, then run `gh repo create leadpilot-ai --private --source=. --remote=origin --push` from the project root. If the shell still injects the expired token, prefix GitHub CLI commands with `env -u GH_TOKEN`.
2. **Public demo.** Connect the approved GitHub repository to a Render account, review and approve the web and database plan costs, and apply `render.yaml`. Supply the initial admin values through the hosting dashboard. The [deployment guide](docs/DEPLOYMENT_GUIDE.md) specifies live checks. Public URLs and remote CI results are pending.
3. **Live OpenAI.** The mock provider works locally without a paid account. A live OpenAI smoke test requires an API key in the backend environment and approval of API usage. The adapter's request, validation, timeout and error paths were tested with a loopback server.
