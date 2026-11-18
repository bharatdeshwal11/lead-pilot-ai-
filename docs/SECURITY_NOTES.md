# Security notes

## Browser authentication

Spring Security verifies the account password using BCrypt with cost 12. After successful login, the server rotates the session identifier and uses the `LEADPILOT_SESSION` cookie with `HttpOnly` and `SameSite=Lax`. Enable the secure-cookie setting for an HTTPS deployment. The browser sends its cookie automatically to the application origin. The frontend fetches a separate CSRF token and includes the returned header on requests that change data, including login and logout.

The session expires after 30 minutes of inactivity. Logout invalidates the session. The client fetches a fresh CSRF token after authentication changes. The server also checks that the session still matches the active account version. Changing a name, password, role or active flag invalidates older sessions. A missing or expired session returns an authentication error. An authenticated request can still be denied by role, CSRF or record scope.

## Authorization

Administrator role checks apply to user management and administrator-only lead operations. The lead service applies a record-level scope to sales-agent access, and related AI and follow-up operations check that scope server-side. A frontend menu or hidden button improves navigation. Server authorization protects the operation when a request is sent directly.

The application uses DTOs and validates user input before executing business logic. Repository queries bind supplied values as parameters. API errors contain a safe message and validation details where applicable. Account responses omit password hashes, and provider secrets are held in backend environment configuration.

Login attempts are limited by client IP and AI generation requests by signed-in account ID. Each limit defaults to 12 requests per minute. The hosted proxy must supply or sanitize forwarding headers for IP-based limits to reflect the client.

## AI boundary

The provider receives enquiry text as data. The real adapter omits structured contact fields and redacts matching email addresses and Indian mobile numbers from the text. Server instructions constrain the task and the response contract. The real adapter has no action tools, and the application validates model output before storing it. Model output cannot send a message, approve a draft, or make a CRM change beyond the explicit analysis or draft operation requested by the staff member.

A response schema checks shape and types. Verifying a natural-language claim requires evidence outside that schema. A draft can be edited and reviewed before approval. The application stores the review decision and presents the approved text in the CRM. The real adapter sets `store:false` on Responses requests. Other identifying text can still reach the configured provider, so its terms and the business data-handling policy still apply. The fictional demo can run entirely with the local mock provider.

## Operational settings

- Keep local secrets in an ignored `.env` file and use the host's secret manager in a deployment.
- Enable HTTPS and secure cookies for public access. Allow only explicit, owned frontend origins if using a cross-origin development or hosted setup.
- Keep the database on a private network and restrict the Spring origin if a public proxy fronts it.
- Configure public demo credentials separately from any production credentials. For a privately shared live demo, provision credentials on the host and share them through an authorized channel.
- Review account roles and provider spend controls before giving multiple users access.

## MVP boundaries

Sessions and request limiting are per application instance where maintained in memory. A multi-replica deployment needs a shared session design and a shared or edge-enforced rate policy. This version uses password login and one business workspace. MFA, self-service password recovery, SSO, organization-level isolation and a formal security review are future work.

See [deployment settings](DEPLOYMENT_GUIDE.md) for the hosted configuration and [test evidence](TEST_REPORT.md) for the security cases actually exercised.
