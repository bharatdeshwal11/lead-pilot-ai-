# Understanding LeadPilot AI

A small real-estate team gets enquiries from websites, email and conversations. Someone has to read them, work out what each customer needs, assign the enquiry and make sure someone follows up. A spreadsheet can hold the messages, but it makes ownership, activity history and overdue work hard to see in one place.

LeadPilot gives that team a shared workflow. An administrator can assign a lead to an active agent. The agent sees assigned leads, records progress, schedules follow-ups and drafts replies. The dashboard counts the records already in PostgreSQL. The example business and customers are fictional and situated around Noida and Greater Noida.

## Why this stack

| Technology | What it does here |
| --- | --- |
| Java 21 and Spring Boot | Java supplies typed DTOs and a standard language/runtime. Boot wires the HTTP server, MVC, validation, JPA and configuration into one deployable application. |
| Spring Security | Verifies passwords, maintains session identity, applies CSRF protection, and provides the authentication context used by record-level access checks. |
| Spring Data JPA and PostgreSQL | JPA maps application entities to relational rows. PostgreSQL provides foreign keys, uniqueness, transactions and report queries. |
| Flyway | Runs ordered SQL migrations and records the applied versions. Hibernate validates that its entity mappings agree with the schema. |
| React, TypeScript and Vite | React renders screens from current state. TypeScript checks client DTO shapes during a build. Vite serves development assets and creates the production bundle. |
| React Router and charting | Routes map URLs to screens. Dashboard charts turn API counts into status and source breakdowns. |
| Maven Wrapper | Selects one Maven distribution for both contributors and CI. |
| Docker Compose and GitHub Actions | Compose describes how the frontend, API and PostgreSQL run together. The CI workflow records repeatable build and test steps. |
| springdoc | Generates OpenAPI from annotated controllers and DTOs and exposes an interactive reference. |

## How a request moves through the application

1. A React route renders the corresponding form, table or detail view. Its event handler sends JSON to `/api` using the shared client.
2. Spring Security checks the session, CSRF token and relevant request rules. MVC binds JSON to a request DTO and runs Jakarta Bean Validation.
3. The controller calls a service. Services handle rules such as who can access a lead or who can approve a draft.
4. A Spring Data repository uses JPA to read or write PostgreSQL. Hibernate converts between Java entity state and SQL values. The service returns a DTO to the controller and Spring serializes it as JSON.
5. The client displays the result, validation errors or a recoverable failure. A fresh load can read the same stored state.

The production frontend bundle is served by Nginx. Its proxy makes the browser's API calls same-origin. During frontend development, Vite serves the client and forwards the same API paths to Spring Boot. The [architecture diagram](ARCHITECTURE.md) follows these two paths.

## Trace a login

Start with `frontend/src/auth/AuthProvider.tsx` and the shared client `frontend/src/lib/api.ts`. The client requests `GET /api/auth/csrf` before mutations and includes the returned header with `POST /api/auth/login`. In `backend/src/main/java/com/leadpilot/auth/AuthController.java`, `login` delegates authentication to the manager configured by `SecurityConfig.authenticationManager`. Spring Security loads the account using `UserRepository.findByEmailIgnoreCase` and compares the supplied password with the stored BCrypt hash. Active accounts with matching credentials receive a session cookie and a profile DTO. Fetching `GET /api/auth/me` on reload restores the frontend view of the current identity.

The end of `AuthController.login` puts the verified identity into the security context and persists it through the session-backed repository:

```java
sessions.onAuthentication(sessionAuthentication, request, response);
var context = SecurityContextHolder.createEmptyContext();
context.setAuthentication(sessionAuthentication);
SecurityContextHolder.setContext(context);
contexts.saveContext(context, request, response);
```

The session strategy rotates the identifier and clears the pre-login CSRF token. The principal saved to the session has its password field cleared.

Spring Security answers two questions at different points. Authentication establishes the account ID and role. Authorization starts with that identity and checks the requested action and, where appropriate, ownership. For example, `LeadService.requireAccessible` is reused by lead-related operations so an agent cannot expose a different agent's record by changing the URL.

## Trace a lead from form to database

Open `backend/src/main/java/com/leadpilot/lead/LeadController.java`. The create action accepts `POST /api/leads` and hands a validated request to `LeadService.create`. The service normalizes identity fields and determines assignment. A lead created by an agent belongs to that agent. An administrator can choose an eligible agent or leave the lead unassigned.

The service writes through `LeadRepository` and uses `ActivityService.record` for the creation event. PostgreSQL then protects the lead's relationships and fingerprint. Unique enforcement at the database is important because two browser requests can both arrive before either has read the other's new row.

Inside the transaction, `LeadService.create` flushes the lead first. Once the insert has succeeded, it records the activity:

```java
leads.saveAndFlush(lead);
activities.record(lead, actor, "LEAD_CREATED", "Lead created.");
```

The fingerprint constraint is declared in `backend/src/main/resources/db/migration/V1__core.sql`. When an employee updates status, the request uses `PATCH /api/leads/{id}`. PATCH handles supplied fields without requiring the whole row. An omitted assignment field leaves the current value in place. Sending a JSON null for assignment requests that the service clear it, with an administrator authorization check.

## Trace the AI analysis and reply workflow

An employee requests analysis after saving a lead. `AiController.analyze` calls `AiService.analyze`, which checks lead access and takes the enquiry as input. `LeadAiProvider` allows `MockLeadAiProvider` and `OpenAiLeadAiProvider` to share the same application workflow. The mock uses fixed text signals. The OpenAI adapter asks for structured output and the server validates required fields and limits before it saves anything. `Intent.recommendedPriority` maps the label to a priority and `LeadAnalysis` stores it. That value remains advice until staff apply it to the lead.

`AiService.analyze` calls the provider before opening the write transaction, then checks the current lead and enquiry again before storing a result:

```java
AnalysisResult result = provider.analyze(enquiry);
if (result == null || !validator.validate(result).isEmpty()) {
    throw new AiProviderException(false);
}
```

This lets a slow provider call finish without keeping the lead write transaction open. If someone changes the enquiry during generation, the service reports a conflict and lets the employee explicitly rerun the action.

A strong enquiry often names a requirement, states a budget or timeframe, and asks the business to do something concrete. The mock first checks for explicit browsing or limited-readiness language, then looks for a concrete step such as a site visit or signing. Requirements, budget and timeline without a concrete next step can indicate medium intent. These are cues in a message. A lower-intent customer can still become a qualified lead later, and the label carries no calibrated purchase probability.

`AiService.createDraft` uses the enquiry and server-owned business guidance. The provider selects a `ReplyTemplate` topic and the server supplies the vetted text. The application stores the draft, lets staff edit it through `AiService.editDraft` and records the transition to review. In `AiService.reviewDraft`, an administrator can approve or reject a pending draft. Approval changes CRM state. The application has no delivery operation.

A failed AI call leaves the lead available. The client reports the failed action. The application can retry only when an authorized user deliberately requests it again. A timeout prevents a slow provider from holding the request open indefinitely.

## Follow-ups, activities and reports

`FollowUpService.create` validates the lead and assignee and stores a UTC due instant. A follow-up is overdue when it is incomplete and its due instant is past the comparison time. Completing it creates a timestamp. The current browser timezone affects display. For example, `10:00Z` and `15:30` in India describe the same instant.

An activity row gives staff context for lead creation, updates and related changes. The relational design associates many follow-ups, activities, drafts and analyses with a single enquiry. For cardinalities and deletion rules, see the [database design](DATABASE_DESIGN.md).

`DashboardService.stats` combines database counts for visible leads and overdue follow-ups. A hypothetical 12 visible leads with three won leads gives `100 × 3 ÷ 12 = 25%`. Conversion here describes the share of all currently visible leads marked won. The API returns zero for an empty dataset and `frontend/src/dashboard/DashboardPage.tsx` charts the API values.

## Errors and tests

`backend/src/main/java/com/leadpilot/common/ApiErrors.java` turns validation and application exceptions into Problem Details responses with safe messages and optional field errors. A missing session and an inadmissible role are distinct conditions. A lead hidden by record scope can respond as missing, limiting disclosure of which IDs exist. An AI provider error is translated locally and leaves the enquiry available.

Read the backend integration tests with the controller open. Follow a successful request and compare it with a duplicate, a malformed payload or an agent trying to access an unauthorized record. Then run the browser test and follow its sign-in, lead, analysis, draft and follow-up steps. The [test report](TEST_REPORT.md) records which suites ran and what remains unverified.

## What comes next

This application is sized for one demo business. Sessions use the application instance, and AI calls run in the HTTP request. To grow responsibly, measure query plans, connection use and provider latency before changing the architecture. If a deployment needs several API replicas, introduce a shared session service or an explicit new authentication design. If provider latency becomes a problem, persist AI job state and use a bounded worker with idempotent completion.

A multi-business product would add organization ownership to users and records and require organization-aware foreign keys, query policies and authorization tests. For staff usage, consider notification integration with explicit review and consent, stronger account recovery, and operational monitoring. For AI quality, collect more representative and independently labeled enquiries, examine disagreements by class, and track changes by model or rule version.
