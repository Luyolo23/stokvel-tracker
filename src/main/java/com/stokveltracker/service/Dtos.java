package com.stokveltracker.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Shapes returned by the API. Entities are never exposed directly. */
public final class Dtos {
    private Dtos() {}

    public record MeDto(String username, String displayName, String role,
                        String groupName, long monthlyAmountCents) {}

    public record ContributionDto(Long id, String month, LocalDate dueDate, long amountDueCents,
                                  String status, boolean overdue, String reference,
                                  String rejectReason) {}

    public record MemberOverview(List<ContributionDto> contributions, long paidCents,
                                 long pendingCents, long owedCents) {}

    /** Used for both the "to confirm" list and the "recently confirmed" list. */
    public record ClaimDto(Long id, String memberName, String month, long amountDueCents,
                           String reference, Instant claimedAt, String decidedBy, Instant decidedAt) {}

    public record OverdueMemberDto(Long userId, String memberName, String phone,
                                   List<String> months, long totalCents) {}

    public record MemberRow(String memberName, long confirmedCents, long owedCents, int overdueCount) {}

    public record ChairSummary(String month, long expectedThisMonthCents, long collectedThisMonthCents,
                               long owedTotalCents, long pendingCount, int membersBehind,
                               List<MemberRow> members) {}

    public record AuditDto(Long id, String actor, String action, String detail, Instant createdAt) {}
}
