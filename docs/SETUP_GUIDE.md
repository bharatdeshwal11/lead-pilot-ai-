# Setup guide

Use Docker Compose for the complete local demo. Use the native setup when you want frontend hot reload or a debugger attached to Spring Boot.

## Complete local demo

From the repository root:

```bash
cp .env.example .env
docker compose up --build
```

Use the fictional account credentials defined in your local `.env` and open [LeadPilot AI](http://localhost:3000). The example environment enables seeding and mock AI for a fresh development database. Keep those credentials local. On an empty database, the demo seeder creates one administrator, one sales agent, 30 fictional leads and three example follow-ups. A restart reuses the PostgreSQL volume and does not reapply the demo seed.

Check [health](http://localhost:3000/health) or open [Swagger UI](http://localhost:3000/swagger-ui/index.html) once the services are ready. Use `docker compose logs -f` to follow startup and `docker compose down` to stop the services while keeping the data volume.

## Native development

Install JDK 21, Node.js 24 LTS and PostgreSQL 17. Confirm `java -version`, `node --version` and `psql --version`. In `backend/`, run `./mvnw -v` to confirm Maven also uses Java 21. Set `JAVA_HOME` to your JDK 21 installation if it points to an older JDK.

For an existing development PostgreSQL server, create a dedicated database and role. If you administer that server with a different PostgreSQL account, use your usual admin connection.

```sh
createuser --pwprompt leadpilot
createdb --owner=leadpilot leadpilot
```

In your root `.env`, configure the database role password and use `jdbc:postgresql://localhost:5432/leadpilot` as `DB_URL` for native development. For local HTTP, set `APP_SECURE_COOKIES=false`. Keep `APP_DEMO_SEED=true` and use the local demo credentials for a new database. Start the backend from the project root:

```bash
set -a
. ./.env
set +a
cd backend
./mvnw spring-boot:run
```

In a second terminal, from the project root:

```bash
cd frontend
npm ci
npm run dev
```

Open [the Vite development server](http://localhost:5173). By default, Vite proxies API requests to Spring Boot at `http://localhost:8080`. Set `VITE_PROXY_TARGET` before starting Vite if Spring Boot runs elsewhere. On Windows PowerShell, set the corresponding `$env:...` variables and use `.\mvnw.cmd spring-boot:run`.

## Test and build

Run the backend suite with Docker available for Testcontainers. Without Docker, provision a separate test database, set `TEST_DB_URL`, `TEST_DB_USERNAME` and `TEST_DB_PASSWORD`, then run the same command. Use a database reserved for tests.

```bash
cd backend
./mvnw verify
```

From the repository root:

```bash
cd frontend
npm ci
npm test
npm run build
```

Use [the test report](TEST_REPORT.md) for the browser test setup and [API documentation](API_DOCUMENTATION.md) for session-aware curl examples.

## Environment settings

| Name | Use |
| --- | --- |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | JDBC URL and database role credentials. The role must be able to run Flyway migrations for the target schema. |
| `DB_NAME` | Database created and targeted by the local Compose stack. |
| `APP_PORT` | Local Compose frontend port, default `3000`. |
| `PORT` | Spring Boot listening port, default `8080`. |
| `APP_SECURE_COOKIES` | Set to `false` on local HTTP and to `true` in an HTTPS deployment. |
| `APP_FORWARD_HEADERS` | Forwarded-header strategy. Set to `framework` only behind a trusted proxy that sets or sanitizes forwarded headers. |
| `APP_ALLOWED_ORIGINS` | Comma-separated explicit origins for cross-origin access. Leave empty for the normal same-origin setup. |
| `APP_OPENAPI_ENABLED` | Enable Swagger UI and the OpenAPI endpoint. Consider disabling for the hosted demo. |
| `APP_DEMO_SEED` | Opt in to fictional development records on an empty accounts table. |
| `APP_DEMO_ADMIN_EMAIL`, `APP_DEMO_ADMIN_PASSWORD`, `APP_DEMO_AGENT_EMAIL`, `APP_DEMO_AGENT_PASSWORD` | Accounts created by the local demo seed. Use only local values. |
| `APP_BOOTSTRAP_ADMIN_EMAIL`, `APP_BOOTSTRAP_ADMIN_PASSWORD` | Initial administrator for an empty hosted workspace with demo seeding off. Use a unique password of 14 to 72 UTF-8 bytes. |
| `APP_LOGIN_LIMIT`, `APP_AI_LIMIT` | Per-minute application limits for login by request IP and AI action by user ID. Defaults are 12 each. |
| `AI_PROVIDER` | `mock` for local deterministic behavior or `openai` for the real adapter. |
| `OPENAI_API_KEY`, `OPENAI_MODEL` | Backend-only API key and model for real-provider mode. |
| `OPENAI_BASE_URL` | Optional backend base URL for a compatible Responses API, default `https://api.openai.com/v1`. |
| `OPENAI_CONNECT_TIMEOUT`, `OPENAI_REQUEST_TIMEOUT` | Backend provider timeouts, default `5s` and `20s`. |
| `VITE_PROXY_TARGET` | Optional development proxy target, default `http://localhost:8080`. |
| `VITE_API_BASE_URL` | Optional frontend build override for an alternate API origin. Keep empty for the default same-origin setup. |

Use the [deployment guide](DEPLOYMENT_GUIDE.md) for hosted settings and [the example environment](../.env.example) for the checked-in local defaults.
