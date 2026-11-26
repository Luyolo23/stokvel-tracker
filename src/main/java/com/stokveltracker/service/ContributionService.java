package com.stokveltracker.service;

import com.stokveltracker.domain.*;
import com.stokveltracker.repo.AuditLogRepository;
import com.stokveltracker.repo.ContributionRepository;
import com.stokveltracker.repo.UserRepository;
import com.stokveltracker.service.Dtos.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ContributionService {

    private static final Set<ContributionStatus> UNPAID =
            EnumSet.of(ContributionStatus.DUE, ContributionStatus.REJECTED);

    private final UserRepository users;
    private final ContributionRepository contributions;
    private final AuditLogRepository auditLogs;
    private final AuditService audit;

    public ContributionService(UserRepository users, ContributionRepository contributions,
                               AuditLogRepository auditLogs, AuditService audit) {
        this.users = users;
        this.contributions = contributions;
        this.auditLogs = auditLogs;
        this.audit = audit;
    }

    // ------------------------------------------------------------------ generation

    /** Creates a DUE contribution for every active user for the given month. Safe to call repeatedly. */
    public void generateFor(YearMonth month) {
        String period = month.toString();
        for (AppUser u : users.findByActiveTrue()) {
            if (!contributions.existsByUserAndPeriod(u, period)) {
                StokvelGroup g = u.getGroup();
                contributions.save(new Contribution(u, period,
                        Dates.dueDate(month, g.getDueDay()), g.getMonthlyAmountCents()));
            }
        }
    }

    @Scheduled(cron = "0 5 0 * * *", zone = "Africa/Johannesburg")
    public void generateCurrentMonth() {
        generateFor(YearMonth.from(Dates.today()));
    }

    // ------------------------------------------------------------------ member

    @Transactional(readOnly = true)
    public MemberOverview overview(String username) {
        AppUser user = user(username);
        LocalDate today = Dates.today();
        List<Contribution> list = contributions.findByUserOrderByPeriodDesc(user);
        long paid = sum(list, ContributionStatus.CONFIRMED);
        long pending = sum(list, ContributionStatus.PENDING);
        long owed = sum(list, ContributionStatus.DUE) + sum(list, ContributionStatus.REJECTED);
        return new MemberOverview(list.stream().map(c -> toDto(c, today)).toList(), paid, pending, owed);
    }

    public ContributionDto claim(String username, Long id, String reference) {
        Contribution c = load(id);
        if (!c.getUser().getUsername().equals(username)) {
            throw ApiException.forbidden("You can only claim your own payments");
        }
        if (!UNPAID.contains(c.getStatus())) {
            throw ApiException.badRequest("This month is already " + c.getStatus().name().toLowerCase());
        }
        c.claim(reference.trim(), Instant.now());
        audit.record(username, "CLAIM", c.getPeriod() + " claimed as paid, ref: " + reference.trim());
        return toDto(c, Dates.today());
    }

    // ------------------------------------------------------------------ treasurer

    @Transactional(readOnly = true)
    public List<ClaimDto> pending(String treasurerUsername) {
        StokvelGroup group = user(treasurerUsername).getGroup();
        return contributions.findByGroupAndStatus(group, ContributionStatus.PENDING).stream()
                .sorted(Comparator.comparing(Contribution::getClaimedAt))
                .map(this::toClaim).toList();
    }

    @Transactional(readOnly = true)
    public List<ClaimDto> recentlyConfirmed(String treasurerUsername) {
        StokvelGroup group = user(treasurerUsername).getGroup();
        return contributions.findByGroupAndStatus(group, ContributionStatus.CONFIRMED).stream()
                .filter(c -> c.getDecidedAt() != null)
                .sorted(Comparator.comparing(Contribution::getDecidedAt).reversed())
                .limit(20)
                .map(this::toClaim).toList();
    }

    public void confirm(String treasurerUsername, Long id) {
        Contribution c = decidable(treasurerUsername, id, ContributionStatus.PENDING);
        c.confirm(treasurerUsername, Instant.now());
        audit.record(treasurerUsername, "CONFIRM",
                c.getUser().getDisplayName() + " " + c.getPeriod() + " confirmed, ref: " + c.getReference());
    }

    public void reject(String treasurerUsername, Long id, String reason) {
        Contribution c = decidable(treasurerUsername, id, ContributionStatus.PENDING);
        c.reject(treasurerUsername, reason.trim(), Instant.now());
        audit.record(treasurerUsername, "REJECT",
                c.getUser().getDisplayName() + " " + c.getPeriod() + " rejected: " + reason.trim());
    }

    /** Correction instead of deletion: puts a confirmed month back to DUE and logs why. */
    public void reverse(String treasurerUsername, Long id, String reason) {
        Contribution c = decidable(treasurerUsername, id, ContributionStatus.CONFIRMED);
        String oldRef = c.getReference();
        c.reverse();
        audit.record(treasurerUsername, "REVERSE",
                c.getUser().getDisplayName() + " " + c.getPeriod() + " reversed (old ref: "
                        + oldRef + "): " + reason.trim());
    }

    @Transactional(readOnly = true)
    public List<OverdueMemberDto> overdue(String treasurerUsername) {
        StokvelGroup group = user(treasurerUsername).getGroup();
        List<Contribution> list = contributions.findOverdue(group, UNPAID, Dates.today());
        Map<Long, List<Contribution>> byUser = list.stream()
                .collect(Collectors.groupingBy(c -> c.getUser().getId(), LinkedHashMap::new, Collectors.toList()));
        List<OverdueMemberDto> result = new ArrayList<>();
        for (List<Contribution> items : byUser.values()) {
            AppUser u = items.get(0).getUser();
            result.add(new OverdueMemberDto(u.getId(), u.getDisplayName(), u.getPhone(),
                    items.stream().map(Contribution::getPeriod).toList(),
                    items.stream().mapToLong(Contribution::getAmountDueCents).sum()));
        }
        result.sort(Comparator.comparingLong(OverdueMemberDto::totalCents).reversed());
        return result;
    }

    // ------------------------------------------------------------------ chair

    @Transactional(readOnly = true)
    public ChairSummary summary(String chairUsername) {
        StokvelGroup group = user(chairUsername).getGroup();
        LocalDate today = Dates.today();
        String period = YearMonth.from(today).toString();
        List<Contribution> all = contributions.findAllByGroup(group);

        long expected = all.stream().filter(c -> c.getPeriod().equals(period))
                .mapToLong(Contribution::getAmountDueCents).sum();
        long collected = all.stream()
                .filter(c -> c.getPeriod().equals(period) && c.getStatus() == ContributionStatus.CONFIRMED)
                .mapToLong(Contribution::getAmountDueCents).sum();
        long owed = all.stream().filter(c -> UNPAID.contains(c.getStatus()))
                .mapToLong(Contribution::getAmountDueCents).sum();
        long pending = all.stream().filter(c -> c.getStatus() == ContributionStatus.PENDING).count();

        Map<Long, List<Contribution>> byUser = all.stream()
                .collect(Collectors.groupingBy(c -> c.getUser().getId()));
        List<MemberRow> rows = new ArrayList<>();
        int behind = 0;
        for (List<Contribution> items : byUser.values()) {
            AppUser u = items.get(0).getUser();
            long confirmed = sum(items, ContributionStatus.CONFIRMED);
            long memberOwed = sum(items, ContributionStatus.DUE) + sum(items, ContributionStatus.REJECTED);
            int overdueCount = (int) items.stream().filter(c -> isOverdue(c, today)).count();
            if (overdueCount > 0) behind++;
            rows.add(new MemberRow(u.getDisplayName(), confirmed, memberOwed, overdueCount));
        }
        rows.sort(Comparator.comparingInt(MemberRow::overdueCount).reversed()
                .thenComparing(MemberRow::memberName));
        return new ChairSummary(period, expected, collected, owed, pending, behind, rows);
    }

    @Transactional(readOnly = true)
    public List<AuditDto> auditTrail() {
        return auditLogs.findTop50ByOrderByCreatedAtDesc().stream()
                .map(a -> new AuditDto(a.getId(), a.getActor(), a.getAction(), a.getDetail(), a.getCreatedAt()))
                .toList();
    }

    // ------------------------------------------------------------------ helpers

    private AppUser user(String username) {
        return users.findByUsername(username)
                .orElseThrow(() -> ApiException.notFound("Unknown user"));
    }

    private Contribution load(Long id) {
        return contributions.findById(id)
                .orElseThrow(() -> ApiException.notFound("Contribution not found"));
    }

    /** Loads a contribution a treasurer is allowed to act on, enforcing the rules in one place. */
    private Contribution decidable(String treasurerUsername, Long id, ContributionStatus expected) {
        AppUser treasurer = user(treasurerUsername);
        Contribution c = load(id);
        if (!c.getUser().getGroup().getId().equals(treasurer.getGroup().getId())) {
            throw ApiException.forbidden("That payment belongs to another group");
        }
        if (c.getUser().getId().equals(treasurer.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN,
                    "You cannot decide on your own payment. Ask the chairperson.");
        }
        if (c.getStatus() != expected) {
            throw ApiException.badRequest("Expected status " + expected + " but it is " + c.getStatus());
        }
        return c;
    }

    private static long sum(List<Contribution> list, ContributionStatus status) {
        return list.stream().filter(c -> c.getStatus() == status)
                .mapToLong(Contribution::getAmountDueCents).sum();
    }

    private static boolean isOverdue(Contribution c, LocalDate today) {
        return UNPAID.contains(c.getStatus()) && c.getDueDate().isBefore(today);
    }

    private ContributionDto toDto(Contribution c, LocalDate today) {
        return new ContributionDto(c.getId(), c.getPeriod(), c.getDueDate(), c.getAmountDueCents(),
                c.getStatus().name(), isOverdue(c, today), c.getReference(), c.getRejectReason());
    }

    private ClaimDto toClaim(Contribution c) {
        return new ClaimDto(c.getId(), c.getUser().getDisplayName(), c.getPeriod(), c.getAmountDueCents(),
                c.getReference(), c.getClaimedAt(), c.getDecidedBy(), c.getDecidedAt());
    }
}
