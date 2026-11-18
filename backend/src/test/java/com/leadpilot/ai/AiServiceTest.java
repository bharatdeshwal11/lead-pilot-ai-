package com.leadpilot.ai;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.leadpilot.activity.ActivityService;
import com.leadpilot.common.ApiException;
import com.leadpilot.lead.Lead;
import com.leadpilot.lead.LeadService;
import com.leadpilot.user.Role;
import com.leadpilot.user.UserAccount;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.time.Clock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

class AiServiceTest {
    private final ValidatorFactory validation = Validation.buildDefaultValidatorFactory();
    private final LeadService leads = mock(LeadService.class);
    private final LeadAnalysisRepository analyses = mock(LeadAnalysisRepository.class);
    private final ReplyDraftRepository drafts = mock(ReplyDraftRepository.class);
    private final ActivityService activities = mock(ActivityService.class);
    private final LeadAiProvider provider = mock(LeadAiProvider.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private final AiService service = new AiService(
            leads, analyses, drafts, activities, provider, validation.getValidator(), Clock.systemUTC(), transactions);
    private final UserAccount actor = new UserAccount("Tester", "tester@example.test", "unused", Role.ADMIN);

    @AfterEach
    void closeValidator() {
        validation.close();
    }

    @Test
    void providerTimeoutLeavesLeadAndActivityUntouched() {
        stubEnquiry("May I arrange a site visit?");
        when(provider.analyze("May I arrange a site visit?")).thenThrow(new AiProviderException(true));

        assertThatThrownBy(() -> service.analyze(1, actor)).isInstanceOf(ApiException.class)
                .hasMessageContaining("could not complete").hasMessageContaining("lead is unchanged");
        verifyNoInteractions(analyses, drafts, activities, transactions);
    }

    @Test
    void rejectsMalformedProviderResultBeforeOpeningWriteTransaction() {
        stubEnquiry("Interested in a place near work");
        when(provider.analyze("Interested in a place near work")).thenReturn(
                new AnalysisResult(Intent.MEDIUM, null, "9".repeat(181), null, "Summary", "", "Notes"));

        assertThatThrownBy(() -> service.analyze(1, actor)).isInstanceOf(ApiException.class)
                .hasMessageContaining("could not complete");
        verifyNoInteractions(analyses, drafts, activities, transactions);
    }

    @Test
    void limitsEnquirySizeBeforeCallingProvider() {
        stubEnquiry("x".repeat(4001));

        assertThatThrownBy(() -> service.createDraft(1, actor)).isInstanceOf(ApiException.class)
                .hasMessageContaining("4,000");
        verifyNoInteractions(provider, analyses, drafts, activities, transactions);
    }

    @Test
    void agentCannotReviewEvenWithLeadAccess() {
        UserAccount agent = new UserAccount("Agent", "agent@example.test", "unused", Role.SALES_AGENT);
        assertThatThrownBy(() -> service.reviewDraft(1, agent, false)).isInstanceOf(ApiException.class)
                .hasMessageContaining("Only administrators");
        verifyNoInteractions(provider, analyses, drafts, activities, leads, transactions);
    }

    private void stubEnquiry(String enquiry) {
        Lead lead = mock(Lead.class);
        when(leads.requireAccessible(1, actor)).thenReturn(lead);
        when(lead.getEnquiryMessage()).thenReturn(enquiry);
    }
}
