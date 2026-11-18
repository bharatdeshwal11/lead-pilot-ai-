# Deployment guide

The local stack is described by `docker-compose.yml`. For a hosted demo, use an HTTPS web service for the application and managed PostgreSQL in the same region. A single public origin lets the browser use the same session and CSRF flow as local development.

## Hosted configuration

A straightforward option is one Render Docker web service and one Render PostgreSQL instance. `Dockerfile.deploy` puts the built frontend in the Spring Boot image, so one public service can serve both. Render can terminate TLS and run the `/health` check. The checked-in `render.yaml` connects to managed PostgreSQL over the internal network.

On 18 November 2026, Render listed `0.5c-512mb` web compute at US$7/month and `0.1c-256mb` PostgreSQL compute at US$6/month. The Blueprint requests 1 GB of database storage, listed at US$0.30/GB per month. The listed base estimate is US$13.30/month before taxes, bandwidth, workspace fees or usage changes. The mock provider adds no external AI usage. Confirm the quote, database retention and backup rules in the host dashboard before provisioning. [Render pricing](https://render.com/pricing)

## Deployment steps

1. Push the project to an authorized GitHub repository. Grant Render access to that repository and obtain approval for any billable resources.
2. In Render, choose **New > Blueprint**, connect the repository and select the branch that contains `render.yaml`. Inspect both proposed services, region and prices before creating them. [Render Blueprints](https://render.com/docs/infrastructure-as-code)
3. Enter `APP_BOOTSTRAP_ADMIN_EMAIL` and `APP_BOOTSTRAP_ADMIN_PASSWORD` as service secrets. Use a unique password of 14 to 72 UTF-8 bytes. After this administrator exists, remove the bootstrap credentials from the service environment.
4. Review the service settings. The Blueprint selects `Dockerfile.deploy`, reads the platform's listening `PORT`, enables secure cookies, sets the forwarded header strategy, disables demo seeding and OpenAPI, and uses mock AI. It gets PostgreSQL's internal connection string, username and password through database references. Keep `APP_ALLOWED_ORIGINS` empty when the app and API share a browser origin.
5. Deploy and check the logs for successful Flyway migrations. Verify the HTTPS URL, `/health` and a real browser sign-in. Create an enquiry and run mock analysis to confirm database persistence and application routing.

To configure the image without a Blueprint, set the health check and runtime settings above on the Docker web service. Pass `DB_URL` as `jdbc:postgresql://<internal-host>:5432/<database>?sslmode=require`, or set `DB_HOST`, `DB_PORT` and `DB_NAME` so `deployment/start.sh` builds the JDBC URL. The checked-in Blueprint uses `RENDER_DATABASE_URL` and the script extracts its internal database address. Set `DB_USERNAME` and `DB_PASSWORD` separately. Render's internal database certificates are self-signed and support `sslmode=require`. Supply `OPENAI_API_KEY` and `OPENAI_MODEL` through backend secrets if using the real provider. [Render Docker deployments](https://render.com/docs/docker) · [Render PostgreSQL](https://render.com/docs/databases)

## Publishing the repository

For a new private repository under your own GitHub account, run this from the committed project root. Confirm `gh auth status` shows the intended account before creating the repository.

```sh
gh auth login --hostname github.com --web
gh auth status --hostname github.com
gh repo create leadpilot-ai --private --source=. --remote=origin --push
gh repo view --json url
```

If an invalid `GH_TOKEN` or `GITHUB_TOKEN` in the current shell overrides interactive authentication, unset that variable before running `gh auth login`. Keep account secrets in the CLI's credential store.

On a designated existing repository, configure its approved URL as the remote and use the required branch or pull request workflow. Review the GitHub Actions run after the push.

Before updating a live database, follow the host's snapshot or backup process. A code rollback does not reverse a migration that has already run. Where practical, design later migrations to remain compatible with the previous deployment while the new version rolls out.

Hosted repository and demo URLs are available only after authorized publication and verification. See [project status](../PROJECT_STATUS.md) for the current state.
