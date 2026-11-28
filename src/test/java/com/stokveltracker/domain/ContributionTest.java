package com.stokveltracker.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ContributionTest {

    private Contribution contribution() {
        StokvelGroup group = new StokvelGroup("Test Stokvel", 50_000, 5);
        AppUser user = new AppUser("member", "Test Member", "hash", Role.MEMBER,
                "27000000000", group);
        return new Contribution(user, "2026-11", LocalDate.of(2026, 11, 5), 50_000);
    }

    @Test
    void claimThenWithdrawReturnsToDue() {
        Contribution c = contribution();
        c.claim("REF-1", Instant.now());

        assertEquals(ContributionStatus.PENDING, c.getStatus());
        assertEquals("REF-1", c.getReference());

        c.withdraw();

        assertEquals(ContributionStatus.DUE, c.getStatus());
        assertNull(c.getReference());
        assertNull(c.getClaimedAt());
        assertNull(c.getDecidedBy());
        assertNull(c.getDecidedAt());
    }

    @Test
    void rejectStoresReasonAndReverseClearsIt() {
        Contribution c = contribution();
        c.claim("REF-1", Instant.now());
        c.reject("treasurer", "Wrong reference", Instant.now());

        assertEquals(ContributionStatus.REJECTED, c.getStatus());
        assertEquals("Wrong reference", c.getRejectReason());

        c.reverse();

        assertEquals(ContributionStatus.DUE, c.getStatus());
        assertNull(c.getReference());
        assertNull(c.getRejectReason());
    }

    @Test
    void confirmRecordsWhoDecided() {
        Contribution c = contribution();
        c.claim("REF-1", Instant.now());
        c.confirm("treasurer", Instant.now());

        assertEquals(ContributionStatus.CONFIRMED, c.getStatus());
        assertEquals("treasurer", c.getDecidedBy());
    }
}
