package com.leadpilot.lead;

import com.jayway.jsonpath.JsonPath;
import com.leadpilot.PostgresIntegrationTest;
import com.leadpilot.auth.UserPrincipal;
import com.leadpilot.user.Role;
import com.leadpilot.user.UserAccount;
import com.leadpilot.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Transactional
class LeadApiIntegrationTest extends PostgresIntegrationTest {
    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserRepository users;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EntityManager entityManager;
    private UserAccount admin;
    private UserAccount salesAgent;
    private UserAccount secondAgent;

    @BeforeEach
    void setUp() {
        admin = users.saveAndFlush(new UserAccount("CRM Integration Admin", "crud-admin@example.test",
                "test-hash", Role.ADMIN));
        salesAgent = users.saveAndFlush(new UserAccount("CRM Integration Sales", "crud-agent@example.test",
                "test-hash", Role.SALES_AGENT));
        secondAgent = users.saveAndFlush(new UserAccount("CRM Integration Second Sales", "crud-agent-2@example.test",
                "test-hash", Role.SALES_AGENT));
    }

    @Test
    void createsValidatesAndDetectsDuplicateSubmissions() throws Exception {
        mvc.perform(post("/api/leads").with(as(admin)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerName":" ","email":"wrong","phone":"letters","enquiryMessage":"","source":"WEBSITE"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.customerName").exists())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.phone").exists());
        mvc.perform(post("/api/leads").with(as(admin)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(request("Typical Buyer", "CRM-VALID@example.test", "MEDIUM")))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.email").value("crm-valid@example.test"));
        mvc.perform(post("/api/leads").with(as(admin)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(request("Different Buyer", "crm-valid@example.test", "LOW")))
                .andExpect(status().isConflict());
    }

    @Test
    void enforcesAgentScopeAndRejectsAssignmentOrDeletion() throws Exception {
        long ownLead = create(salesAgent, "Scope Own", "crud-own@example.test", "HIGH");
        long otherLead = create(secondAgent, "Scope Other", "crud-other@example.test", "LOW");

        mvc.perform(get("/api/leads").param("q", "Scope").with(as(salesAgent)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(ownLead));
        mvc.perform(get("/api/leads/{id}", otherLead).with(as(salesAgent)))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/leads/{id}", ownLead).with(as(salesAgent)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CONTACTED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONTACTED"));
        mvc.perform(patch("/api/leads/{id}", ownLead).with(as(salesAgent)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"assignedUserId\":null}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/leads/{id}", ownLead).with(as(salesAgent)).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanAssignUnassignAndClearPhoneWithAuditHistory() throws Exception {
        long leadId = create(admin, "Assign Me", "crud-assign@example.test", "MEDIUM");
        mvc.perform(patch("/api/leads/{id}", leadId).with(as(admin)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"assignedUserId":%d,"priority":"HIGH","status":"QUALIFIED","phone":"+91 10000 20000"}
                                """.formatted(salesAgent.getId())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.assignedUser.id").value(salesAgent.getId()))
                .andExpect(jsonPath("$.phone").value("+91 10000 20000"));
        mvc.perform(get("/api/leads/{id}", leadId).with(as(salesAgent))).andExpect(status().isOk());
        mvc.perform(patch("/api/leads/{id}", leadId).with(as(admin)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"assignedUserId":null,"phone":null}
                                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.assignedUser").isEmpty())
                .andExpect(jsonPath("$.phone").isEmpty());
        mvc.perform(get("/api/leads/{id}", leadId).with(as(salesAgent))).andExpect(status().isNotFound());
        mvc.perform(get("/api/leads/{id}/activities", leadId).with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].type", hasItem("LEAD_CREATED")))
                .andExpect(jsonPath("$[*].type", hasItem("LEAD_ASSIGNED")))
                .andExpect(jsonPath("$[*].type", hasItem("STATUS_CHANGED")))
                .andExpect(jsonPath("$[*].type", hasItem("PRIORITY_CHANGED")));
    }

    @Test
    void sortsFiltersAndPaginatesDatabaseResults() throws Exception {
        create(admin, "Order Literal% Low", "crud-low@example.test", "LOW");
        long medium = create(admin, "Order Medium", "crud-medium@example.test", "MEDIUM");
        long high = create(admin, "Order High", "crud-high@example.test", "HIGH");
        mvc.perform(get("/api/leads").with(as(admin)).param("q", "ORDER").param("size", "2")
                        .param("sort", "priority,desc").param("source", "WEBSITE").param("status", "NEW"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content[0].id").value(high))
                .andExpect(jsonPath("$.content[1].id").value(medium));
        mvc.perform(get("/api/leads").with(as(admin)).param("q", "Order Literal%"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/leads").with(as(admin)).param("sort", "passwordHash,desc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deletingLeadPreservesAuditRowsInPostgres() throws Exception {
        long leadId = create(admin, "Remove Me", "crm-delete@example.test", "MEDIUM");
        mvc.perform(delete("/api/leads/{id}", leadId).with(as(admin)).with(csrf()))
                .andExpect(status().isNoContent());
        entityManager.flush();

        mvc.perform(get("/api/leads/{id}", leadId).with(as(admin))).andExpect(status().isNotFound());
        var retained = jdbc.queryForList("""
                SELECT type FROM activities
                WHERE subject_lead_id = ? AND lead_id IS NULL
                ORDER BY id
                """, String.class, leadId);
        assertThat(retained).containsExactly("LEAD_CREATED", "LEAD_DELETED");
    }

    @Test
    void userManagementRestrictsDirectoryAndKeepsAnActiveAdmin() throws Exception {
        mvc.perform(get("/api/users").with(as(salesAgent))).andExpect(status().isForbidden());
        mvc.perform(get("/api/users/assignees").with(as(salesAgent)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(salesAgent.getId()));
        mvc.perform(get("/api/users").with(as(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].passwordHash").doesNotExist());
        jdbc.update("UPDATE users SET active = false WHERE role = 'ADMIN' AND id <> ?", admin.getId());
        mvc.perform(patch("/api/users/{id}", admin.getId()).with(as(admin)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isConflict());
    }

    private long create(UserAccount account, String name, String email, String priority) throws Exception {
        String body = mvc.perform(post("/api/leads").with(as(account)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(request(name, email, priority)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private String request(String name, String email, String priority) {
        return """
                {"customerName":"%s","email":"%s","enquiryMessage":"Tour an apartment","source":"WEBSITE","priority":"%s"}
                """.formatted(name, email, priority);
    }

    private RequestPostProcessor as(UserAccount account) {
        return user(UserPrincipal.from(account));
    }
}
