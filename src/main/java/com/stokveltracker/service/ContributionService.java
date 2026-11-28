package com.stokveltracker.service;

import com.stokveltracker.domain.*;
import com.stokveltracker.repo.AuditLogRepository;
import com.stokveltracker.repo.ContributionRepository;
import com.stokveltracker.repo.GroupRepository;
import com.stokveltracker.repo.UserRepository;
import com.stokveltracker.service.Dtos.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
    private final GroupRepository groups;
    private final AuditService audit;

    public ContributionService(UserRepository users, ContributionRepository contributions,
                               AuditLogRepository auditLogs, GroupRepository groups, AuditService audit) {
        this.users = users;
        this.contributions = contributions;
        this.auditLogs = auditLogs;
        this.groups = groups;
        this.audit = audit;
    }

    // ------------------------------------------------------------------ generation

    /** Creates a DUE contribution for every active user in every group for the given month. */
    public void generateFor(YearMonth month) {
        for (StokvelGroup group : groups.findAll()) {
            generateFor(group, month);
        }
    }

    /** Creates a DUE contribution for every active member of one group for the given month. */
    public void generateFor(StokvelGroup group, YearMonth month) {
        String period = month.toString();
        for (AppUser u : users.findByGroupAndActiveTrue(group)) {
            if (!contributions.existsByUserAndPeriod(u, period)) {
                contributions.save(new Contribution(u, period,
                        Dates.dueDate(month, group.getDueDay()), group.getMonthlyAmountCents()));
            }
        }
    }

    /**
     * Fills every month between the latest month we have data for and the current month.
     * This matters when the service is asleep or down across a month boundary: without
     * it, the skipped month would never exist and nobody would be marked overdue for it.
     */
    public void generateMissingMonths() {
        YearMonth now = YearMonth.from(Dates.today());
        for (StokvelGroup group : groups.findAll()) {
            String latest = contributions.findMaxPeriodByGroup(group);
            YearMonth start = latest == null ? now : YearMonth.parse(latest);
            if (start.isAfter(now)) {
                start = now;
            }
            for (YearMonth month = start; !month.isAfter(now); month = month.plusMonths(1)) {
                generateFor(group, month);
            }
        }
    }

    @Scheduled(cron = "0 5 0 * * *", zone = "Africa/Johannesburg")
    public void generateCurrentMonth() {
        generateMissingMonths();
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
        AppUser user = user(username);
        Contribution c = load(id);
        if (!c.getUser().getId().equals(user.getId())) {
            throw ApiException.forbidden("You can only claim your own payments");
        }
        if (!UNPAID.contains(c.getStatus())) {
            throw ApiException.badRequest("This month is already " + c.getStatus().name().toLowerCase());
        }
        c.claim(reference.trim(), Instant.now());
        audit.record(user.getGroup(), username, "CLAIM",
                c.getPeriod() + " claimed as paid, ref: " + reference.trim());
        return toDto(c, Dates.today());
    }

    /** Lets a member take back a claim while the treasurer has not decided it yet. */
    public ContributionDto withdraw(String username, Long id) {
        AppUser user = user(username);
        Contribution c = load(id);
        if (!c.getUser().getId().equals(user.getId())) {
            throw ApiException.forbidden("You can only withdraw your own claims");
        }
        if (c.getStatus() != ContributionStatus.PENDING) {
            throw ApiException.badRequest("Only a claim awaiting confirmation can be withdrawn");
        }
        c.withdraw();
        audit.record(user.getGroup(), username, "WITHDRAW", c.getPeriod() + " claim withdrawn");
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
    public ConfirmedPage recentlyConfirmed(String treasurerUsername, int page, int size) {
        StokvelGroup group = user(treasurerUsername).getGroup();
        Page<Contribution> result = contributions.findPageByGroupAndStatus(group,
                ContributionStatus.CONFIRMED, pageRequest(page, size));
        return toConfirmedPage(result);
    }

    public void confirm(String treasurerUsername, Long id) {
        Contribution c = decidable(treasurerUsername, id, ContributionStatus.PENDING);
        c.confirm(treasurerUsername, Instant.now());
        audit.record(c.getUser().getGroup(), treasurerUsername, "CONFIRM",
                c.getUser().getDisplayName() + " " + c.getPeriod() + " confirmed, ref: " + c.getReference());
    }

    public void reject(String treasurerUsername, Long id, String reason) {
        Contribution c = decidable(treasurerUsername, id, ContributionStatus.PENDING);
        c.reject(treasurerUsername, reason.trim(), Instant.now());
        audit.record(c.getUser().getGroup(), treasurerUsername, "REJECT",
                c.getUser().getDisplayName() + " " + c.getPeriod() + " rejected: " + reason.trim());
    }

    /** Correction instead of deletion: puts a confirmed month back to DUE and logs why. */
    public void reverse(String treasurerUsername, Long id, String reason) {
        Contribution c = decidable(treasurerUsername, id, ContributionStatus.CONFIRMED);
        String oldRef = c.getReference();
        c.reverse();
        audit.record(c.getUser().getGroup(), treasurerUsername, "REVERSE",
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

    /**
     * The chair countersigns the treasurer's own payments. The treasurer may not decide
     * them, so without this path they would stay PENDING forever.
     */
    @Transactional(readOnly = true)
    public List<ClaimDto> chairPending(String chairUsername) {
        StokvelGroup group = user(chairUsername).getGroup();
        return contributions.findByGroupRoleAndStatus(group, Role.TREASURER, ContributionStatus.PENDING)
                .stream()
                .sorted(Comparator.comparing(Contribution::getClaimedAt))
                .map(this::toClaim).toList();
    }

    @Transactional(readOnly = true)
    public ConfirmedPage chairRecentlyConfirmed(String chairUsername, int page, int size) {
        StokvelGroup group = user(chairUsername).getGroup();
        Page<Contribution> result = contributions.findPageByGroupRoleAndStatus(group, Role.TREASURER,
                ContributionStatus.CONFIRMED, pageRequest(page, size));
        return toConfirmedPage(result);
    }

    public void chairConfirm(String chairUsername, Long id) {
        Contribution c = decidableByChair(chairUsername, id, ContributionStatus.PENDING);
        c.confirm(chairUsername, Instant.now());
        audit.record(c.getUser().getGroup(), chairUsername, "CONFIRM",
                c.getUser().getDisplayName() + " " + c.getPeriod() + " confirmed, ref: " + c.getReference());
    }

    public void chairReject(String chairUsername, Long id, String reason) {
        Contribution c = decidableByChair(chairUsername, id, ContributionStatus.PENDING);
        c.reject(chairUsername, reason.trim(), Instant.now());
        audit.record(c.getUser().getGroup(), chairUsername, "REJECT",
                c.getUser().getDisplayName() + " " + c.getPeriod() + " rejected: " + reason.trim());
    }

    public void chairReverse(String chairUsername, Long id, String reason) {
        Contribution c = decidableByChair(chairUsername, id, ContributionStatus.CONFIRMED);
        String oldRef = c.getReference();
        c.reverse();
        audit.record(c.getUser().getGroup(), chairUsername, "REVERSE",
                c.getUser().getDisplayName() + " " + c.getPeriod() + " reversed (old ref: "
                        + oldRef + "): " + reason.trim());
    }

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
            rows.add(new MemberRow(u.getId(), u.getDisplayName(), confirmed, memberOwed, overdueCount));
        }
        rows.sort(Comparator.comparingInt(MemberRow::overdueCount).reversed()
                .thenComparing(MemberRow::memberName));
        return new ChairSummary(period, expected, collected, owed, pending, behind, rows);
    }

    @Transactional(readOnly = true)
    public List<AuditDto> auditTrail(String chairUsername) {
        StokvelGroup group = user(chairUsername).getGroup();
        return auditLogs.findTop50ByGroupOrderByCreatedAtDesc(group).stream()
                .map(a -> new AuditDto(a.getId(), a.getActor(), a.getAction(), a.getDetail(), a.getCreatedAt()))
                .toList();
    }

    // ------------------------------------------------------------------ helpers

    private AppUser user(String username) {
        return users.findByUsername(username)
                .filter(AppUser::isActive)
                .orElseThrow(() -> ApiException.unauthorized("Your account is no longer active"));
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

    /** The chair may only countersign the treasurer's own payments. */
    private Contribution decidableByChair(String chairUsername, Long id, ContributionStatus expected) {
        AppUser chair = user(chairUsername);
        Contribution c = load(id);
        if (!c.getUser().getGroup().getId().equals(chair.getGroup().getId())) {
            throw ApiException.forbidden("That payment belongs to another group");
        }
        if (c.getUser().getRole() != Role.TREASURER) {
            throw ApiException.forbidden("The chair can only countersign the treasurer's own payments");
        }
        if (c.getStatus() != expected) {
            throw ApiException.badRequest("Expected status " + expected + " but it is " + c.getStatus());
        }
        return c;
    }

    private static PageRequest pageRequest(int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        return PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "decidedAt"));
    }

    private ConfirmedPage toConfirmedPage(Page<Contribution> result) {
        List<ClaimDto> items = result.getContent().stream()
                .filter(c -> c.getDecidedAt() != null)
                .map(this::toClaim)
                .toList();
        return new ConfirmedPage(items, result.hasNext());
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
