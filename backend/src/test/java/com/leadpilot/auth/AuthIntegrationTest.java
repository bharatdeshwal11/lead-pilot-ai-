package com.leadpilot.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leadpilot.PostgresIntegrationTest;
import com.leadpilot.user.Role;
import com.leadpilot.user.UserAccount;
import com.leadpilot.user.UserRepository;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class AuthIntegrationTest extends PostgresIntegrationTest {
    private static final String TEST_PASSWORD = "Disposable-auth-test-123";

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired ObjectMapper mapper;

    @Test
    void unauthenticatedApiReturnsJson401AndDoesNotRedirect() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("Sign in to continue."));
    }

    @Test
    void loginRotatesSessionAndCsrfThenLogoutInvalidatesAccess() throws Exception {
        var account = account(Role.ADMIN);
        assertThat(account.getPasswordHash()).startsWith("$2a$").doesNotContain(TEST_PASSWORD);
        var csrf = csrf(null);
        String anonymousSessionId = csrf.session().getId();

        var result = mvc.perform(post("/api/auth/login").session(csrf.session())
                        .header(csrf.header(), csrf.token()).contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(account.getEmail().toUpperCase(java.util.Locale.ROOT), TEST_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn();
        var authenticated = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(authenticated.getId()).isNotEqualTo(anonymousSessionId);
        mvc.perform(get("/api/auth/me").session(authenticated)).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(account.getId()));
        mvc.perform(post("/api/auth/logout").session(authenticated).header(csrf.header(), csrf.token()))
                .andExpect(status().isForbidden());

        var renewed = csrf(authenticated);
        mvc.perform(post("/api/auth/logout").session(authenticated).header(renewed.header(), renewed.token()))
                .andExpect(status().isNoContent());
        assertThat(authenticated.isInvalid()).isTrue();
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void missingCsrfAndBadCredentialsHaveDistinctStatusCodes() throws Exception {
        var account = account(Role.SALES_AGENT);
        String wrongCredentials = credentials(account.getEmail(), "wrong-password");
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(wrongCredentials))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));

        var csrf = csrf(null);
        mvc.perform(post("/api/auth/login").session(csrf.session()).header(csrf.header(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content(wrongCredentials))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void rejectsMalformedFieldsAndUnapprovedCorsOrigins() throws Exception {
        var csrf = csrf(null);
        mvc.perform(post("/api/auth/login").session(csrf.session()).header(csrf.header(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content(credentials("invalid", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
        mvc.perform(options("/api/leads")
                        .header("Origin", "https://unapproved.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void salesAgentCannotManageUsersAndDeletedAccountLosesExistingSession() throws Exception {
        var account = account(Role.SALES_AGENT);
        var csrf = csrf(null);
        var result = mvc.perform(post("/api/auth/login").session(csrf.session()).header(csrf.header(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content(credentials(account.getEmail(), TEST_PASSWORD)))
                .andExpect(status().isOk()).andReturn();
        var session = (MockHttpSession) result.getRequest().getSession(false);
        mvc.perform(get("/api/users").session(session)).andExpect(status().isForbidden());
        users.deleteById(account.getId());
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
        assertThat(session.isInvalid()).isTrue();
    }

    private UserAccount account(Role role) {
        return users.save(new UserAccount("Fictional account", UUID.randomUUID() + "@example.test",
                passwords.encode(TEST_PASSWORD), role));
    }

    private String credentials(String email, String password) throws Exception {
        return mapper.writeValueAsString(Map.of("email", email, "password", password));
    }

    private Csrf csrf(MockHttpSession session) throws Exception {
        var request = get("/api/auth/csrf");
        if (session != null) {
            request.session(session);
        }
        var result = mvc.perform(request).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store")).andReturn();
        var body = mapper.readTree(result.getResponse().getContentAsString());
        return new Csrf((MockHttpSession) result.getRequest().getSession(false),
                body.get("headerName").asText(), body.get("token").asText());
    }

    private record Csrf(MockHttpSession session, String header, String token) {
    }
}
