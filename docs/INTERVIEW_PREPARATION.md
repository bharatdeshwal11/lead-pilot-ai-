# Interview preparation

## 60-second explanation

> I built LeadPilot AI to manage real-estate enquiries from intake through follow-up. An administrator can assign an enquiry to a sales agent, and the agent sees their own leads with status, deadlines and activity history. The React client calls a Java 21 Spring Boot API, with PostgreSQL holding the records and Flyway managing schema changes. A configurable AI provider extracts details such as the customer's budget, timeline and next action. The mock provider keeps the demo runnable without paid credentials. Staff can edit and submit an AI reply for review, and an administrator can approve it inside the CRM. The dashboard reports counts from PostgreSQL, including overdue follow-ups and the share of visible leads marked won.

## Two-minute explanation

> I started with an intake problem. A salesperson might see an enquiry in email, create a spreadsheet row and then lose track of the next action. LeadPilot keeps the enquiry, ownership, status, follow-ups and activity history together.
>
> It uses a modular Spring Boot backend and a React TypeScript client. MVC validates the input, a service enforces the rules, and Spring Data JPA writes to PostgreSQL. Flyway owns the schema. A database constraint catches two concurrent submissions of the same email and enquiry. Administrators can assign leads and manage users. A sales agent's access is scoped to their work. I use a server-side session and CSRF tokens because the app is browser-based and the production frontend proxies API calls on the same origin.
>
> AI is an explicit staff action. The provider returns a structured analysis, the server validates it, and a deterministic rule maps intent to a priority recommendation. The system also generates a reply draft for human review. It stores approval and rejection without sending messages. A provider failure leaves the original lead intact.
>
> In a demo, I create and assign a lead, analyze its enquiry, review a reply, and schedule a follow-up. Then I show how the dashboard changes based on stored records. I explain the conversion denominator as all visible leads and describe the intent evaluation as agreement on a labeled fixture set.

## Five-minute technical walkthrough

| Time | What to show |
| --- | --- |
| 0:00–0:45 | State the enquiry and follow-up problem. Sign in with the local administrator and explain what an agent is allowed to see. |
| 0:45–1:45 | Create a fictional lead with a site-visit request and assign it. Open the detail and activity tabs. Trace the controller, service, repository and migration. |
| 1:45–2:45 | Run analysis. Identify budget, timeline and next action. Explain provider substitution, server-side schema validation and deterministic priority mapping. |
| 2:45–3:35 | Generate a reply, make an edit, submit it and approve it. Show the persisted review state. Schedule a follow-up and explain UTC storage. |
| 3:35–4:20 | Open the dashboard and explain role scope, overdue semantics, source/status counts and conversion. |
| 4:20–5:00 | Show the most relevant test and actual run output. Describe a provider failure and one architectural change you would make when measured load warrants it. |

## Java and object-oriented design

1. **Why Java 21?** It is an LTS release and gives the project supported language and runtime features. Records give DTOs named, immutable components and generated accessors and equality.
2. **Where does polymorphism help here?** The AI workflow calls a provider interface. Spring injects mock or OpenAI behavior from configuration, so business operations depend on the contract.
3. **Why constructor injection?** Required dependencies are visible in the constructor and available when the object is created. A unit test can pass collaborators directly.
4. **Why have DTOs and entities?** Entities participate in JPA's persistence lifecycle. DTOs define the JSON contract and expose only the fields clients need.
5. **Why use enums for roles and status?** The legal values are finite. The compiler and JSON binding can catch misspelling and unsupported values, while schema constraints protect direct database writes.
6. **What makes an `Instant` suitable for due dates?** It identifies a specific position on the UTC timeline. The client can render it in the user's timezone without changing the instant.
7. **How would you unit test time-based classification?** Use a fixed clock or a supplied comparison time and check a past instant, the boundary and a future instant. Keep wall-clock timing out of the assertion.

## Spring Boot and persistence

8. **What does Spring Boot auto-configuration do?** It creates application components such as MVC, a data source or JPA infrastructure when matching dependencies and properties are present. The project supplies its own security and application rules.
9. **What happens before a validated controller action runs?** The security filter chain evaluates the request. MVC maps the route, deserializes JSON to the DTO and applies validation.
10. **Why do writes use transactions?** A mutation and its dependent records, such as activity, should succeed or fail together. A transaction also defines when ORM state is flushed.
11. **What is the JPA persistence context?** It tracks managed entities by identity within a unit of work. Hibernate can detect changes to managed fields and flush them as SQL.
12. **What role does Spring Data provide?** It implements repositories from interfaces and defines query operations. The project can use method conventions, explicit queries or specifications according to the query.
13. **How does `LeadService.requireAccessible` fit into the design?** The service verifies record visibility where the operation depends on a lead. Reusing it keeps related endpoints from drifting to different scope rules.
14. **What is the N+1 query problem?** Loading a collection and separately resolving relationships for each row creates a query per row. Check SQL and plans, then choose an appropriate join, entity graph or projection for the view.
15. **Why does Flyway own schema creation?** It gives each change a repeatable SQL version and records applied migrations. The application validates that entities and tables still agree.

## DBMS and SQL

16. **What joins a lead and an assigned agent?** The optional `leads.assigned_user_id` references one account in `users`. An account can be assigned to many leads.
17. **Why keep follow-ups in a separate table?** A lead can have multiple independently scheduled or completed follow-ups. Each has its own due instant, owner and note.
18. **How do you prevent duplicate lead submissions?** The service derives a fingerprint from normalized email and trimmed enquiry text. A unique database constraint makes the decision reliable when requests overlap.
19. **Why keep activity separate from current lead state?** The lead row answers what the state is now. Activities answer what happened, who did it and when.
20. **What happens when a lead is deleted?** Its dependent analyses, drafts and follow-ups are removed according to the schema. Activity retains the original lead identifier for its audit trail.
21. **What types are used for business timestamps?** Java uses UTC instants and PostgreSQL uses timestamps with time zone. The database compares instants regardless of the viewer's display timezone.
22. **How do you count leads by source in SQL?** Select `source, COUNT(*)` from the scoped lead rows and group by `source`. The service includes categories absent from the result as zeros where required.
23. **What is the dashboard conversion formula?** `100 × won leads / visible total leads`, rounded to two decimal places. An empty visible set returns zero.
24. **How would you pick an index?** Start from frequent filters, joins and sort keys, then use `EXPLAIN ANALYZE` with realistic cardinality. An index has a storage and write cost, so measure before adding one.
25. **When would offset paging need revision?** A large offset makes the database skip many rows, and concurrent writes can move rows between pages. Keyset pagination can use a stable sort value and ID as the cursor.

## REST and frontend

26. **Why use PATCH for status and assignment?** The client can send the fields it intends to update. JSON field absence and an explicit null have different meanings for clearable fields.
27. **Why use `POST /api/leads/{id}/analyze`?** An analysis is a deliberate command that can call a provider and create a historical record. A GET only reads saved analyses.
28. **When do you use 400, 401, 403 and 409?** Invalid request data gives 400. Authentication is required for 401. A denied authenticated operation or a rejected CSRF token gives 403. A duplicate or state conflict gives 409.
29. **What is OpenAPI useful for?** It documents routes, methods and DTO contracts in a machine-readable description. Swagger UI reads it to display operations and examples.
30. **Why put data loading in the frontend route or feature?** The route already knows the URL parameters and view state. The shared client handles request mechanics such as cookies, CSRF and error decoding.
31. **How should a form behave on API failure?** Preserve the entered values, expose an actionable message and keep the user in context. Disable a submit action while it is in flight to reduce accidental repeats.

## Authentication and security

32. **What is stored for a password?** A BCrypt hash. A password check compares the supplied password with the encoded hash using the hasher's verification function.
33. **What is in the browser's session cookie?** A session identifier used by the server to find the security context. The cookie is HttpOnly and should be Secure on HTTPS.
34. **Why does a session app need CSRF protection?** A browser can attach cookies to requests automatically. The server requires a separate CSRF header for state-changing requests so a cross-site request cannot rely on the cookie alone.
35. **Why does the client refresh the token after login?** The session/security context changes at authentication. The previous token is cleared or replaced, and a fresh token covers the new session.
36. **What is the difference between role and record checks?** An administrator role permits user management. A sales agent role alone does not establish which lead that agent owns. The service checks assignment against the current identity.
37. **What does server-side logout achieve?** It invalidates the session so the session identifier can no longer authenticate subsequent requests.
38. **How does an environment secret differ from a Vite variable?** Backend environment settings remain server-side. Client build variables exposed by Vite can appear in the public browser bundle.

## AI integration

39. **Why have a mock AI provider?** It makes demos and tests runnable offline and gives repeatable behavior for a known text input.
40. **How is priority derived from analysis?** The application maps the allowed intent values to a priority recommendation using a fixed rule. Staff separately control the lead's saved priority.
41. **How is prompt injection constrained?** Customer text goes in as data, provider output uses a narrow schema, and the model has no tools. The server validates the result and enforces role and state transitions itself.
42. **Is structured output the same as factual accuracy?** Structured output gives parseable field shapes. Field values can still be incomplete or mistaken, so the app validates constraints and retains staff review.
43. **What should happen when the provider times out?** The lead remains usable. The request reports a recoverable failure, and a deliberate user action can retry.
44. **What does the intent evaluation measure?** Agreement between predicted labels and expected labels on the fixture dataset. The labels describe the enquiry language and do not establish future sales outcomes.
45. **Why use a reply review state machine?** It defines legal transitions and review authority. This makes approval separately auditable and limits the effects of generated text.

## Testing, deployment and scaling

46. **What does a service unit test buy you?** It controls dependencies and verifies one business decision quickly. A repository or integration test covers real SQL, transaction and framework behavior.
47. **What must an end-to-end test prove?** A user can complete a real browser workflow with actual API and database state. Successful clicks include assertions on the resulting records or UI state.
48. **What does Docker Compose coordinate?** It builds or pulls images, sets configuration, connects services to a network and manages volumes and startup dependencies.
49. **Where are deployment secrets configured?** A local ignored environment file or the host's secret settings. An image, workflow log and source repository should carry only variable names where possible.
50. **What changes if you add more API replicas?** You need a plan for shared sessions and shared rate policy, and you should size the database connection limits across replicas.
51. **When would you move analysis off the request thread?** When measured latency or traffic calls for a job queue. Persist job states, use bounded concurrency and account for duplicate submissions and provider charges.
52. **What would multi-organization support require?** Add organization ownership, enforce it in record relations and every scoped query, and test cross-organization access explicitly.

## Live coding prompts

- Add a status filter to a lead list and an API test for combined filters.
- Add a follow-up view for the next seven days, then test the boundary with a fixed clock.
- Add an organization field to a test-only data model and describe how to propagate tenant scope.
- Make the priority recommendation expose its rule inputs in a DTO.
- Expose an ETag derived from a row's existing version and require an If-Match header for edits.
- Add a new provider test fixture covering a malformed response and confirm that lead data remains intact.

## Things I must understand before putting this project on my resume.

- [ ] Explain one lead creation from browser input to JDBC and PostgreSQL.
- [ ] Draw the relationships between users, leads, follow-ups, drafts, analyses and activities.
- [ ] Demonstrate a legitimate agent request and an out-of-scope request.
- [ ] Explain password hashing, session IDs, CSRF, cookie flags and logout in my own words.
- [ ] Explain how the duplicate constraint handles two concurrent submissions.
- [ ] Run the mock demo and identify the AI provider configuration.
- [ ] Explain intent, priority recommendation, draft review and their limitations.
- [ ] Show how UTC due dates and the dashboard denominator are handled.
- [ ] Run a representative automated test and explain its assertions.
- [ ] State which environments I actually verified before claiming local, CI or public availability.

See the [project explanation](PROJECT_EXPLANATION.md) for source walkthroughs and the [test report](TEST_REPORT.md) for execution evidence.
