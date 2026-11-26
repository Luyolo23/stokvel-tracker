package com.stokveltracker.web;

import com.stokveltracker.service.ContributionService;
import com.stokveltracker.service.Dtos.AuditDto;
import com.stokveltracker.service.Dtos.ChairSummary;
import java.security.Principal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Restricted to the CHAIR role in SecurityConfig. */
@RestController
@RequestMapping("/api/chair")
public class ChairController {
    private final ContributionService service;

    public ChairController(ContributionService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    public ChairSummary summary(Principal principal) {
        return service.summary(principal.getName());
    }

    @GetMapping("/audit")
    public List<AuditDto> audit() {
        return service.auditTrail();
    }
}
