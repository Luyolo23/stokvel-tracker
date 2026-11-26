package com.stokveltracker.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "stokvel_group")
public class StokvelGroup {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    /** Money is always stored as whole cents, never floating point. */
    @Column(nullable = false)
    private long monthlyAmountCents;

    @Column(nullable = false)
    private int dueDay;

    protected StokvelGroup() {}

    public StokvelGroup(String name, long monthlyAmountCents, int dueDay) {
        this.name = name;
        this.monthlyAmountCents = monthlyAmountCents;
        this.dueDay = dueDay;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public long getMonthlyAmountCents() { return monthlyAmountCents; }
    public int getDueDay() { return dueDay; }
}
