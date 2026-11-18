package com.leadpilot.lead;

import com.leadpilot.activity.ActivityService;
import com.leadpilot.common.ApiException;
import com.leadpilot.user.Role;
import com.leadpilot.user.UserAccount;
import com.leadpilot.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeadServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-01T08:00:00Z");
    private static final String ENQUIRY = "Looking for a 3BHK in Noida.";
    @Mock
    private LeadRepository repository;
    @Mock
    private UserService users;
    @Mock
    private ActivityService activities;
    private LeadService service;
    private UserAccount agent;
    private UserAccount admin;

    @BeforeEach
    void setUp() {
        service = new LeadService(repository, users, activities, Clock.fixed(NOW, ZoneOffset.UTC));
        admin = account(1, Role.ADMIN);
        agent = account(2, Role.SALES_AGENT);
    }

    @Test
    void agentCreationNormalizesFieldsAndAssignsOwnership() {
        when(repository.saveAndFlush(any(Lead.class))).thenAnswer(invocation -> {
            Lead lead = invocation.getArgument(0);
            ReflectionTestUtils.setField(lead, "id", 20L);
            return lead;
        });

        LeadDtos.Summary created = service.create(new LeadDtos.Create(" Customer ", "CUSTOMER@example.test",
                "", " " + ENQUIRY + " ", LeadSource.MANUAL, null, null, null), agent);

        assertThat(created.customerName()).isEqualTo("Customer");
        assertThat(created.email()).isEqualTo("customer@example.test");
        assertThat(created.enquiryMessage()).isEqualTo(ENQUIRY);
        assertThat(created.phone()).isNull();
        assertThat(created.status()).isEqualTo(LeadStatus.NEW);
        assertThat(created.priority()).isEqualTo(Priority.MEDIUM);
        assertThat(created.assignedUser().id()).isEqualTo(2);
        assertThat(created.createdAt()).isEqualTo(NOW);
        verify(activities).record(any(Lead.class), eq(agent), eq("LEAD_CREATED"), any());
        verify(activities).record(any(Lead.class), eq(agent), eq("LEAD_ASSIGNED"), any());
    }

    @Test
    void normalizedDuplicateLeavesRecordsUntouched() {
        when(repository.existsByFingerprint(LeadService.fingerprint("customer@example.test", ENQUIRY)))
                .thenReturn(true);

        assertThatThrownBy(() -> service.create(new LeadDtos.Create("Customer", "CUSTOMER@example.test",
                null, " " + ENQUIRY, LeadSource.EMAIL, null, null, null), admin))
                .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.status()).isEqualTo(HttpStatus.CONFLICT));
        verify(repository, never()).saveAndFlush(any());
        verify(activities, never()).record(any(), any(), any(), any());
    }

    @Test
    void agentCannotAccessAnotherOwnersLead() {
        UserAccount anotherAgent = account(3, Role.SALES_AGENT);
        Lead lead = new Lead("Customer", "customer@example.test", null, ENQUIRY, LeadSource.MANUAL,
                LeadStatus.NEW, Priority.MEDIUM, anotherAgent, "unused-test-hash", NOW);
        when(repository.findById(20L)).thenReturn(Optional.of(lead));

        assertThatThrownBy(() -> service.requireAccessible(20, agent))
                .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.status()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThat(service.requireAccessible(20, anotherAgent)).isSameAs(lead);
        assertThat(service.requireAccessible(20, admin)).isSameAs(lead);
    }

    @Test
    void agentCannotCreateForAnotherAgent() {
        assertThatThrownBy(() -> service.create(new LeadDtos.Create("Customer", "customer@example.test",
                null, ENQUIRY, LeadSource.MANUAL, null, null, 3L), agent))
                .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.status()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(repository, never()).saveAndFlush(any());
    }

    private UserAccount account(long id, Role role) {
        UserAccount account = new UserAccount("Employee " + id, "employee-" + id + "@example.test", "test-hash", role);
        ReflectionTestUtils.setField(account, "id", id);
        return account;
    }
}
