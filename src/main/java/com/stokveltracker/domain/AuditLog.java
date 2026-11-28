package com.stokveltracker.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "audit_log")
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Nullable only so rows created before audit entries were group-scoped remain readable.
     * New entries always carry the group.
     */
    @ManyToOne
    @JoinColumn(name = "group_id")
    private StokvelGroup group;

    @Column(nullable = false, length = 50)
    private String actor;

    @Column(nullable = false, length = 30)
    private String action;

    @Column(nullable = false, length = 500)
    private String detail;

    @Column(nullable = false)
    private Instant createdAt;

    protected AuditLog() {}

    public AuditLog(StokvelGroup group, String actor, String action, String detail) {
        this.group = group;
        this.actor = actor;
        this.action = action;
        this.detail = detail.length() > 500 ? detail.substring(0, 500) : detail;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public StokvelGroup getGroup() { return group; }
    public String getActor() { return actor; }
    public String getAction() { return action; }
    public String getDetail() { return detail; }
    public Instant getCreatedAt() { return createdAt; }
}
