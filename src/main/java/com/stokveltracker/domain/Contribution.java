package com.stokveltracker.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One member's obligation for one month. Records are never deleted:
 * mistakes are fixed with reverse(), and every change is written to the audit log.
 */
@Entity
@Table(name = "contribution",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "period"}))
public class Contribution {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id")
    private AppUser user;

    /** Format YYYY-MM, e.g. 2026-11 */
    @Column(nullable = false, length = 7)
    private String period;

    @Column(nullable = false)
    private LocalDate dueDate;

    @Column(nullable = false)
    private long amountDueCents;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ContributionStatus status = ContributionStatus.DUE;

    @Column(length = 100)
    private String reference;

    private Instant claimedAt;

    @Column(length = 50)
    private String decidedBy;

    private Instant decidedAt;

    @Column(length = 200)
    private String rejectReason;

    protected Contribution() {}

    public Contribution(AppUser user, String period, LocalDate dueDate, long amountDueCents) {
        this.user = user;
        this.period = period;
        this.dueDate = dueDate;
        this.amountDueCents = amountDueCents;
    }

    // ----- state changes (the rules live here, services decide who may call them) -----
    public void claim(String reference, Instant at) {
        this.status = ContributionStatus.PENDING;
        this.reference = reference;
        this.claimedAt = at;
        this.rejectReason = null;
        this.decidedBy = null;
        this.decidedAt = null;
    }

    public void confirm(String by, Instant at) {
        this.status = ContributionStatus.CONFIRMED;
        this.decidedBy = by;
        this.decidedAt = at;
    }

    public void reject(String by, String reason, Instant at) {
        this.status = ContributionStatus.REJECTED;
        this.rejectReason = reason;
        this.decidedBy = by;
        this.decidedAt = at;
    }

    public void reverse() {
        this.status = ContributionStatus.DUE;
        this.reference = null;
        this.claimedAt = null;
        this.decidedBy = null;
        this.decidedAt = null;
        this.rejectReason = null;
    }

    public Long getId() { return id; }
    public AppUser getUser() { return user; }
    public String getPeriod() { return period; }
    public LocalDate getDueDate() { return dueDate; }
    public long getAmountDueCents() { return amountDueCents; }
    public ContributionStatus getStatus() { return status; }
    public String getReference() { return reference; }
    public Instant getClaimedAt() { return claimedAt; }
    public String getDecidedBy() { return decidedBy; }
    public Instant getDecidedAt() { return decidedAt; }
    public String getRejectReason() { return rejectReason; }
}
