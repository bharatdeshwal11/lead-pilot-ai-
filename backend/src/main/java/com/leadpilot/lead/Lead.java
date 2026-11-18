package com.leadpilot.lead;

import com.leadpilot.user.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "leads")
public class Lead {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String customerName;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(nullable = false, columnDefinition = "text")
    private String enquiryMessage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LeadSource source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LeadStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Priority priority;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_user_id")
    private UserAccount assignedUser;

    @Column(nullable = false, length = 64)
    private String fingerprint;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Lead() {
    }

    Lead(String customerName, String email, String phone, String enquiryMessage, LeadSource source,
         LeadStatus status, Priority priority, UserAccount assignedUser, String fingerprint, Instant now) {
        this.customerName = customerName;
        this.email = email;
        this.phone = phone;
        this.enquiryMessage = enquiryMessage;
        this.source = source;
        this.status = status;
        this.priority = priority;
        this.assignedUser = assignedUser;
        this.fingerprint = fingerprint;
        this.createdAt = now;
        this.updatedAt = now;
    }

    boolean updateContact(String customerName, String email, String phone, String enquiryMessage,
                          LeadSource source, String fingerprint) {
        boolean changed = !Objects.equals(this.customerName, customerName)
                || !Objects.equals(this.email, email)
                || !Objects.equals(this.phone, phone)
                || !Objects.equals(this.enquiryMessage, enquiryMessage)
                || this.source != source;
        this.customerName = customerName;
        this.email = email;
        this.phone = phone;
        this.enquiryMessage = enquiryMessage;
        this.source = source;
        this.fingerprint = fingerprint;
        return changed;
    }

    void updateWorkflow(LeadStatus status, Priority priority, UserAccount assignedUser, Instant updatedAt) {
        this.status = status;
        this.priority = priority;
        this.assignedUser = assignedUser;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public String getCustomerName() {
        return customerName;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public String getEnquiryMessage() {
        return enquiryMessage;
    }

    public LeadSource getSource() {
        return source;
    }

    public LeadStatus getStatus() {
        return status;
    }

    public Priority getPriority() {
        return priority;
    }

    public UserAccount getAssignedUser() {
        return assignedUser;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
