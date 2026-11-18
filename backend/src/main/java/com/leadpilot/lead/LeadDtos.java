package com.leadpilot.lead;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.leadpilot.user.UserDtos;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class LeadDtos {
    private static final String PHONE_PATTERN = "(?:[+0-9() -]{7,20})?";
    private static final String NON_BLANK_PATTERN = "(?s).*\\S.*";

    private LeadDtos() {
    }

    public record Create(
            @NotBlank @Size(max = 120) String customerName,
            @NotBlank @Email @Size(max = 254) String email,
            @Pattern(regexp = PHONE_PATTERN, message = "Use 7 to 20 characters: digits, spaces, +, -, or parentheses.") String phone,
            @NotBlank @Size(max = 4000) String enquiryMessage,
            @NotNull LeadSource source,
            LeadStatus status,
            Priority priority,
            @Positive Long assignedUserId
    ) {
    }

    public static final class Update {
        @JsonProperty @Pattern(regexp = NON_BLANK_PATTERN) @Size(max = 120)
        String customerName;

        @JsonProperty @Pattern(regexp = NON_BLANK_PATTERN) @Email @Size(max = 254)
        String email;

        @JsonProperty @Pattern(regexp = PHONE_PATTERN, message = "Use 7 to 20 characters: digits, spaces, +, -, or parentheses.")
        String phone;

        @JsonProperty @Pattern(regexp = NON_BLANK_PATTERN) @Size(max = 4000)
        String enquiryMessage;

        @JsonProperty LeadSource source;
        @JsonProperty LeadStatus status;
        @JsonProperty Priority priority;

        @Positive
        Long assignedUserId;

        boolean assignmentProvided;
        boolean phoneProvided;

        @JsonSetter("assignedUserId")
        public void setAssignedUserId(Long assignedUserId) {
            this.assignedUserId = assignedUserId;
            this.assignmentProvided = true;
        }

        @JsonSetter("phone")
        public void setPhone(String phone) {
            this.phone = phone;
            this.phoneProvided = true;
        }
    }

    public record Filter(String q, LeadStatus status, Priority priority, LeadSource source, Long assignedUserId,
                         int page, int size, String sort) {
    }

    public record Summary(Long id, String customerName, String email, String phone, String enquiryMessage,
                          LeadSource source, LeadStatus status, Priority priority, UserDtos.Assignee assignedUser,
                          Instant createdAt, Instant updatedAt) {
        static Summary from(Lead lead) {
            return new Summary(lead.getId(), lead.getCustomerName(), lead.getEmail(), lead.getPhone(),
                    lead.getEnquiryMessage(), lead.getSource(), lead.getStatus(), lead.getPriority(),
                    UserDtos.Assignee.from(lead.getAssignedUser()), lead.getCreatedAt(), lead.getUpdatedAt());
        }
    }

    public record Page(List<Summary> content, long totalElements, int totalPages, int page, int size) {
    }
}
