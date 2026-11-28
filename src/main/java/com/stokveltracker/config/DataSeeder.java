package com.stokveltracker.config;

import com.stokveltracker.domain.*;
import com.stokveltracker.repo.ContributionRepository;
import com.stokveltracker.repo.GroupRepository;
import com.stokveltracker.repo.UserRepository;
import com.stokveltracker.service.AuditService;
import com.stokveltracker.service.ContributionService;
import com.stokveltracker.service.Dates;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates a fully made-up demo stokvel on first start (no real personal data).
 * Phone numbers are deliberately invalid placeholders.
 *
 * Seeding is opt-in via {@code app.seed.enabled} so an empty production database is
 * never given publicly known accounts by accident.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    private static final String DEFAULT_PASSWORD = "Stokvel2026!";

    private final GroupRepository groups;
    private final UserRepository users;
    private final ContributionRepository contributions;
    private final ContributionService service;
    private final AuditService audit;
    private final PasswordEncoder encoder;
    private final String password;
    private final boolean enabled;

    public DataSeeder(GroupRepository groups, UserRepository users, ContributionRepository contributions,
                      ContributionService service, AuditService audit, PasswordEncoder encoder,
                      @Value("${app.seed.password}") String password,
                      @Value("${app.seed.enabled:false}") boolean enabled) {
        this.groups = groups;
        this.users = users;
        this.contributions = contributions;
        this.service = service;
        this.audit = audit;
        this.encoder = encoder;
        this.password = password;
        this.enabled = enabled;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (enabled && users.count() == 0) {
            seed();
        }
        // Always backfill: this also covers an app that was asleep across a month boundary.
        service.generateMissingMonths();

        // Preserve audit rows created before entries were group-scoped. Ambiguous with
        // more than one group, so only do it for the single-group demo.
        List<StokvelGroup> allGroups = groups.findAll();
        if (allGroups.size() == 1) {
            audit.assignUngroupedTo(allGroups.get(0));
        }
    }

    private void seed() {
        if (DEFAULT_PASSWORD.equals(password)) {
            log.warn("Seeding demo accounts with the default password. "
                    + "Set SEED_PASSWORD before exposing this app.");
        }
        StokvelGroup g = groups.save(new StokvelGroup("Ubuntu Savers Stokvel (demo)", 50_000, 5));
        String hash = encoder.encode(password);
        users.save(new AppUser("chair.demo", "Nomsa Demo", hash, Role.CHAIR, "27000000001", g));
        users.save(new AppUser("treasurer.demo", "Thabo Demo", hash, Role.TREASURER, "27000000002", g));
        users.save(new AppUser("member.demo", "Lerato Demo", hash, Role.MEMBER, "27000000003", g));
        users.save(new AppUser("sipho.demo", "Sipho Demo", hash, Role.MEMBER, "27000000004", g));
        users.save(new AppUser("ayanda.demo", "Ayanda Demo", hash, Role.MEMBER, "27000000005", g));
        users.save(new AppUser("pule.demo", "Pule Demo", hash, Role.MEMBER, "27000000006", g));

        YearMonth now = YearMonth.from(Dates.today());
        for (int i = 3; i >= 0; i--) {
            service.generateFor(g, now.minusMonths(i));
        }

        Instant at = Instant.now();
        for (Contribution c : contributions.findAll()) {
            String who = c.getUser().getUsername();
            String name = c.getUser().getDisplayName();
            YearMonth p = YearMonth.parse(c.getPeriod());
            // A treasurer cannot decide on their own payment, so the chair confirms it.
            String decider = who.equals("treasurer.demo") ? "chair.demo" : "treasurer.demo";

            if (p.isBefore(now)) {
                boolean sipho = who.equals("sipho.demo")
                        && (p.equals(now.minusMonths(1)) || p.equals(now.minusMonths(2)));
                if (sipho) {
                    continue; // left DUE: overdue
                }
                if (who.equals("ayanda.demo") && p.equals(now.minusMonths(1))) {
                    String reason = "Reference does not match the bank deposit";
                    c.claim("EFT-" + p, at);
                    c.reject(decider, reason, at);
                    audit.record(g, decider, "REJECT", name + " " + p + " rejected: " + reason);
                } else {
                    paid(c, decider, at);
                }
            } else {
                switch (who) {
                    case "member.demo", "sipho.demo" -> { /* current month stays DUE */ }
                    case "pule.demo" -> {
                        c.claim("PULE-" + p, at); // waiting for the treasurer
                        audit.record(g, who, "CLAIM", p + " claimed as paid, ref: PULE-" + p);
                    }
                    default -> paid(c, decider, at);
                }
            }
        }
    }

    /** Seeds a month as claimed by the member and confirmed by someone allowed to decide. */
    private void paid(Contribution c, String decider, Instant at) {
        String ref = "EFT-" + c.getPeriod();
        c.claim(ref, at);
        c.confirm(decider, at);
        audit.record(c.getUser().getGroup(), decider, "CONFIRM",
                c.getUser().getDisplayName() + " " + c.getPeriod() + " confirmed, ref: " + ref);
    }
}
