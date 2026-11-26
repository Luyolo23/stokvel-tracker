package com.stokveltracker.config;

import com.stokveltracker.domain.*;
import com.stokveltracker.repo.ContributionRepository;
import com.stokveltracker.repo.GroupRepository;
import com.stokveltracker.repo.UserRepository;
import com.stokveltracker.service.ContributionService;
import com.stokveltracker.service.Dates;
import java.time.Instant;
import java.time.YearMonth;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates a fully made-up demo stokvel on first start (no real personal data).
 * Phone numbers are deliberately invalid placeholders.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private final GroupRepository groups;
    private final UserRepository users;
    private final ContributionRepository contributions;
    private final ContributionService service;
    private final PasswordEncoder encoder;
    private final String password;

    public DataSeeder(GroupRepository groups, UserRepository users, ContributionRepository contributions,
                      ContributionService service, PasswordEncoder encoder,
                      @Value("${app.seed.password}") String password) {
        this.groups = groups;
        this.users = users;
        this.contributions = contributions;
        this.service = service;
        this.encoder = encoder;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (users.count() == 0) {
            seed();
        }
        service.generateFor(YearMonth.from(Dates.today()));
    }

    private void seed() {
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
            service.generateFor(now.minusMonths(i));
        }

        Instant at = Instant.now();
        for (Contribution c : contributions.findAll()) {
            String who = c.getUser().getUsername();
            YearMonth p = YearMonth.parse(c.getPeriod());
            if (p.isBefore(now)) {
                boolean sipho = who.equals("sipho.demo")
                        && (p.equals(now.minusMonths(1)) || p.equals(now.minusMonths(2)));
                if (sipho) {
                    continue; // left DUE: overdue for two months
                }
                if (who.equals("ayanda.demo") && p.equals(now.minusMonths(1))) {
                    c.reject("treasurer.demo", "Reference does not match the bank deposit", at);
                } else {
                    c.claim("EFT-" + p, at);
                    c.confirm("treasurer.demo", at);
                }
            } else {
                switch (who) {
                    case "member.demo", "sipho.demo" -> { /* current month stays DUE */ }
                    case "pule.demo" -> c.claim("PULE-" + p, at); // waiting for the treasurer
                    default -> {
                        c.claim("EFT-" + p, at);
                        c.confirm("treasurer.demo", at);
                    }
                }
            }
        }
    }
}
